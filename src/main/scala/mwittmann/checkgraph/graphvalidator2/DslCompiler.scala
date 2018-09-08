package mwittmann.checkgraph.graphvalidator2

import java.util.UUID

import cats.~>
import mwittmann.checkgraph.graphvalidator2.{N4jValue, N4jValueRender}
import mwittmann.checkgraph.graphvalidator2.DslCommands.{DslCommand, DslError, DslState, DslStateData, EdgeLabel, GetVertex, MatchPath, MatchVertex, MatchedPath, MatchedVertex, UseMatchedVertex, fail, success, value}
import org.neo4j.driver.v1.Record
import java.util
import java.util.UUID
import scala.collection.immutable
import scala.collection.JavaConverters._

import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._
import cats.~>
import mwittmann.checkgraph.graphvalidator2.{N4jType, N4jValue, N4jValueRender}
import mwittmann.checkgraph.utils.{PrettyPrint, WrappedNeo4j, WrappedNeo4jClient}
import org.neo4j.driver.v1.Record
import DslCommands._
import mwittmann.checkgraph.utils.WrappedNeo4j.WrappedRecord
import org.neo4j.driver.internal.types.InternalTypeSystem

object DslCompiler {
  def compiler: DslCommand ~> DslState = new (DslCommand ~> DslState) {
    override def apply[A](fa: DslCommand[A]): DslState[A] = fa match {
      case MatchVertex(labels, attributes)  => Neo4jHelpers.matchVertex(labels, attributes).map(_.asInstanceOf[A]) // Eww...

      case MatchPath(first, rest)           => Neo4jHelpers.matchPath(first, rest).map(_.asInstanceOf[A]) // Eww...

      case UseMatchedVertex(vertex)         => value(vertex.asInstanceOf[A]) // Eww...
    }
  }

  object Neo4jHelpers {

    def labels(labels: Set[String]): String =
      labels.map(l => s":$l").mkString(" ")

    def labelsWithGraphLabel(state: DslStateData, labels: Set[String]): String = {
      println(s"Labels with graph label: ${(state.graphLabel + labels)}")
      (labels + state.graphLabel).map(l => s":$l").mkString(" ")
    }

    def vertexAttributes(state: DslStateData, attributes: Map[String, N4jValue]): String =
      s"{ ${attributes.map { case (key, value) => s"$key: ${N4jValueRender.renderInCypher(value)}"}.mkString(",")} }"

    def vertex(name: String, state: DslStateData, labels: Set[String], attributes: Map[String, N4jValue]): String =
      s"($name ${labelsWithGraphLabel(state, labels)} ${vertexAttributes(state, attributes)} )"

    def vertex(name: String, matchedVertex: MatchedVertex): String =
      s"($name ${labels(matchedVertex.labels)} { } )"
//      s"($name ${labels(matchedVertex.labels)} { id: ${matchedVertex.id} } )"
//      s"($name ${labels(matchedVertex.labels)}) WHERE ID($name) = ${matchedVertex.id}"

    def matchVertex(labels: Set[String], attributes:  Map[String, N4jValue]): DslState[MatchedVertex] = state { s: DslStateData =>
      val q = s"MATCH ${vertex("n", s, labels, attributes)} RETURN n, ID(n) AS nid"

      val result = s.graph.tx(q).list()

      if(result.size() == 0)
        fail(DslError(s"Query $q returned no results.", s))
      else if (result.size() == 1) {
        println(PrettyPrint.prettyPrint(new WrappedRecord(result.get(0)))(InternalTypeSystem.TYPE_SYSTEM))
        val vertex = result.get(0)
        //println(vertex.get("n.uid").asString())
        success(s, MatchedVertex(vertex.get("nid").asLong(), UUID.fromString(vertex.get("n").get("uid").asString()), labels, attributes))
      } else
        fail(DslError(s"Query $q returned more than one result.", s))
    }

    def renderVertex(s: DslStateData, vertexName: String, getV: GetVertex): (String, Option[String]) = getV match {
      case MatchVertex(labels, attributes) =>
        (vertex(vertexName, s, labels, attributes), None)

      case UseMatchedVertex(matchedVertex) =>
        (vertex(vertexName, matchedVertex), Some(s"ID($vertexName) = ${matchedVertex.id}"))
    }

    def matchPath(firstGet: GetVertex, rest: List[(GetVertex, Set[EdgeLabel])]): DslState[MatchedPath] = state { s =>

      val (firstQuery, maybeFirstWhereId) = renderVertex(s, "a0", firstGet)
      val firstReturn = s"a0, ID(a0) AS a0Id"

      val (otherQuery, otherWhereId, otherReturn) =
        rest.zipWithIndex.map(v => (v._1, v._2 + 1))
          .foldLeft(List.empty[String], List.empty[String], List.empty[String]) {
            case ((curQuery: List[String], curWhereId: List[String], curReturn: List[String]), ((gv, curLabels), curIndex)) =>
              val (curS, maybeCurId) = renderVertex(s, s"a$curIndex", gv)
              val connector = s"-[${labels(curLabels)}]->"
              val queryPart: String = s"$connector $curS"

              val returnPart = s"a$curIndex, ID(a$curIndex) AS a${curIndex}Id"

              (queryPart +: curQuery, maybeCurId.map(_ +: curWhereId).getOrElse(curWhereId), returnPart +: curReturn)
          }

      val queryP = (firstQuery +: otherQuery).mkString
      val whereIdP = maybeFirstWhereId.map(_ +: otherWhereId).getOrElse(otherWhereId).mkString(" AND ")
      val returnP = (firstReturn +: otherReturn).mkString(",")
      val fullQuery = s"MATCH $queryP WHERE $whereIdP RETURN $returnP"
//      val q =
//
//      val q = s"$firstS ${otherS.mkString}"

      val result = s.graph.tx(fullQuery).list()

      if(result.size() == 0)
        fail(DslError(s"Query $fullQuery returned no results.", s))
      else if (result.size() == 1)
        success(s, collectVertices(1 + rest.length, result.iterator().next()))
      else
        fail(DslError(s"Query $fullQuery returned more than one result. Impossibru!", s))
    }

    def collectVertices(vertexNr: Int, result: Record): MatchedPath = {
      val rawVertices = (0 until vertexNr).map { vertexIndex =>
        val id = result.get(s"a${vertexIndex}Id").asLong()

        val curVertex = result.get(s"a$vertexIndex")
        val uid = UUID.fromString(curVertex.get("uid").asString())
        // Todo: Extract labels and attributes
        MatchedVertex(id, uid, Set.empty, Map.empty)
      }.toList
      MatchedPath(rawVertices)
    }

    def matchEdge(left: MatchedVertex, right: MatchedVertex, curLabels: Set[EdgeLabel]): DslState[Unit] = state { s =>
      val q = s"MATCH (a { id: ${left.id}) -[${labels(curLabels)}]-> (b { id: ${right.id}) RETURN a, b"
      val result = s.graph.tx(q).list()

      if(result.size() == 0)
        fail(DslError(s"Query $q returned no results.", s))
      else if (result.size() == 1) {
        success(s, ())
      } else
        fail(DslError(s"Query $q returned more than one result. Impossibru!", s))
    }
  }
}