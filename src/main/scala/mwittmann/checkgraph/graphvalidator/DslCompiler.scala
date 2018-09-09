package mwittmann.checkgraph.graphvalidator

import java.util.UUID

import cats.~>
import mwittmann.checkgraph.graphvalidator.{N4jValue, N4jValueRender}
import mwittmann.checkgraph.graphvalidator.DslCommands.{DslCommand, DslError, DslState, DslStateData, EdgeLabel, GetVertex, MatchPath, MatchVertex, MatchedPath, MatchedVertex, UseMatchedVertex, fail, success, value}
import org.neo4j.driver.v1.{Record, StatementResult}
import java.util.UUID
import scala.collection.immutable
import scala.collection.JavaConverters._

import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._
import cats.~>
import mwittmann.checkgraph.graphvalidator.{N4jType, N4jValue, N4jValueRender}
import mwittmann.checkgraph.utils.{CatchError, PrettyPrint, WrappedNeo4j, WrappedNeo4jClient}
import org.neo4j.driver.v1.Record
import DslCommands._
import mwittmann.checkgraph.utils.WrappedNeo4j.WrappedRecord
import org.neo4j.driver.internal.types.InternalTypeSystem

object DslCompiler {
  case class DslCompilerConfig(
    graphLabel: String,
    graph: WrappedNeo4jClient,
    baseAttributes: Map[String, N4jType]
  )

  def compiler(config: DslCompilerConfig): DslCommand ~> DslState = new (DslCommand ~> DslState) {
    val helpers = new Neo4jHelpers(config)
    override def apply[A](fa: DslCommand[A]): DslState[A] = fa match {
      case MatchVertex(labels, attributes)  => helpers.matchVertex(labels, attributes).map(_.asInstanceOf[A]) // Eww...

      case MatchPath(first, rest)           => helpers.matchPath(first, rest).map(_.asInstanceOf[A]) // Eww...

      case UseMatchedVertex(vertex)         => value(vertex.asInstanceOf[A]) // Eww...
    }
  }

  class Neo4jHelpers(config: DslCompilerConfig) {

    def matchVertex(
      labels: Set[String], attributes:  Map[String, N4jValue]
    ): DslState[MatchedVertex] = state { s: DslStateData =>
      val q = s"MATCH ${vertex("n", s, labels, attributes)} RETURN n, ID(n) AS nid"

      val result = config.graph.tx(q).list()

      try {
        if (result.size() == 0)
          fail(DslError(s"Query $q returned no results.", s))
        else if (result.size() == 1) {
          val vertex = result.get(0)
          val vertexId = vertex.get("nid").asLong()
          success(
            s.seeVertices(Set(vertexId)),
            MatchedVertex(
              vertexId,
              UUID.fromString(vertex.get("n").get("uid").asString()),
              labels,
              attributes
            )
          )
        } else
          fail(DslError(s"Query $q returned more than one result.", s))
      } catch {
        case e: Exception => fail(DslError(s"Query $q produced exception:\n$e", s))
      }
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
        rest.reverse.zipWithIndex.map(v => (v._1, v._2 + 1))
          .foldLeft(List.empty[String], List.empty[String], List.empty[String]) {
            case ((curQuery: List[String], curWhereId: List[String], curReturn: List[String]), ((gv, curLabels), curIndex)) =>
              val (curS, maybeCurId) = renderVertex(s, s"a$curIndex", gv)
              val connector = s"-[e$curIndex ${labels(curLabels)}]->"
              val queryPart: String = s"$connector $curS"

              val returnPart = s"a$curIndex, ID(a$curIndex) AS a${curIndex}Id, ID(e$curIndex) AS e${curIndex}Id"

              (queryPart +: curQuery, maybeCurId.map(_ +: curWhereId).getOrElse(curWhereId), returnPart +: curReturn)
          }


      val queryP = (firstQuery +: otherQuery).mkString
      val whereIdP = {
        val wheres = maybeFirstWhereId.map(_ +: otherWhereId).getOrElse(otherWhereId)
        if (wheres.nonEmpty)
          s"WHERE ${wheres.mkString(" AND ")}"
        else
          ""
      }

      val returnP = (firstReturn +: otherReturn).mkString(",")
//      val fullQuery = s"MATCH $queryP WHERE $whereIdP RETURN $returnP"
      val fullQuery = s"MATCH $queryP $whereIdP RETURN $returnP"

//      val result = config.graph.tx(fullQuery).list()

      tryCatch(
        (r, s) => {
          val (path, seenVertexIds, seenEdgeIds) = collectVertices(1 + rest.length, r)
          success(s.seeEdges(seenEdgeIds).seeVertices(seenVertexIds), path)
        },
//        config.graph.tx(fullQuery),
        fullQuery,
        s
      )

//      try {
//        if (result.size() == 0)
//          fail(DslError(s"Query $fullQuery returned no results.", s))
//        else if (result.size() == 1) {
//          val (path, seenVertexIds, seenEdgeIds) = collectVertices(1 + rest.length, result.iterator().next())
//          success(s.seeEdges(seenEdgeIds).seeVertices(seenVertexIds), path)
//        } else
//          fail(DslError(s"Query $fullQuery returned more than one result. Impossibru!", s))
//      } catch {
//        case e: Exception => fail(DslError(s"Query $fullQuery produced exception:\n$e", s))
//      }
    }

    private def tryCatch[S](
      fn: (Record, DslStateData) => ErrorOr[(DslStateData, S)],
//      statementResult: StatementResult,
      fullQuery: String,
      s: DslStateData
    ): ErrorOr[(DslStateData, S)] = {
      try {
        val statementResult = CatchError.catchError(config.graph.tx(fullQuery))
        val result = statementResult.list()
        if (result.size() == 0)
          fail(DslError(s"Query $fullQuery returned no results.", s))
        else if (result.size() == 1) {
          fn(result.iterator.next(), s)
//          val (path, seenVertexIds, seenEdgeIds) = collectVertices(1 + rest.length, result.iterator().next())
//          success(s.seeEdges(seenEdgeIds).seeVertices(seenVertexIds), path)
        } else
          fail(DslError(s"Query $fullQuery returned more than one result. Impossibru!", s))
      } catch {
        case e: Exception => fail(DslError(s"Query $fullQuery produced exception:\n$e", s))
      }
    }

    private def labels(labels: Set[String], withGraphLabel: Boolean = false): String = {
      val l = if (withGraphLabel) labels + config.graphLabel else labels
      l.map(l => s":$l").mkString(" ")
    }

    private def vertexAttributes(state: DslStateData, attributes: Map[String, N4jValue]): String =
      s"{ ${attributes.map { case (key, value) => s"$key: ${N4jValueRender.renderInCypher(value)}"}.mkString(",")} }"

    private def vertex(name: String, state: DslStateData, vlabels: Set[String], attributes: Map[String, N4jValue]): String =
      s"($name ${labels(vlabels, withGraphLabel = true)} ${vertexAttributes(state, attributes)} )"

    private def vertex(name: String, matchedVertex: MatchedVertex): String =
      s"($name ${labels(matchedVertex.labels)} { } )"

    // Returns (Path, matched node ids, matched edge ids)
    private def collectVertices(vertexNr: Int, result: Record): (MatchedPath, Set[Long], Set[Long]) = {
      val verticesAndIds = (0 until vertexNr).map { vertexIndex =>
        val vertexId = result.get(s"a${vertexIndex}Id").asLong()

        val curVertex = result.get(s"a$vertexIndex")
        val uid = UUID.fromString(curVertex.get("uid").asString())
        // Todo: Extract labels and attributes
        (MatchedVertex(vertexId, uid, Set.empty, Map.empty), vertexId)
      }.toList

      // Have to do edges separately since we have (vertex-1) edges
      val edgeIds = (1 until vertexNr).map { vertexIndex =>
        val edgeId = result.get(s"e${vertexIndex}Id").asLong()
        edgeId
      }.toSet

      (MatchedPath(verticesAndIds.map(_._1)), verticesAndIds.map(_._2).toSet, edgeIds)
    }
  }
}
