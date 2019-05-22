package cmri.procan.checkgraph.graphvalidator

import java.util.UUID
import scala.collection.JavaConverters._

import cats.implicits._
import cats.~>
import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.v1.Record

import DslCommands._
import AllDsl._
import cmri.procan.checkgraph.utils.WrappedNeo4j.WrappedRecord
import cmri.procan.checkgraph.utils.{CatchError, PrettyPrint, WrappedNeo4jClient}

// Compiler to execute DslCommands, turning them into DslState
object DslCompiler {
  case class DslCompilerConfig(
    graphLabel: String,
    graph: WrappedNeo4jClient,
    baseAttributes: Map[String, N4jType]
  )

  def compiler(config: DslCompilerConfig): DslCommand ~> DslState = new (DslCommand ~> DslState) {
    val helpers = new Neo4jHelpers(config)
    override def apply[A](fa: DslCommand[A]): DslState[A] = fa match {
      case MatchVertex(labels, attributes)  => helpers.matchVertex(labels, attributes)
      case MatchPath(first, rest)           => helpers.matchPath(first, rest)
      case UseMatchedVertex(vertex)         => AllDsl.value(vertex)
      case Noop                             => AllDsl.value(())
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
          val r = result.get(0)
          val vertex = r.get("n")

          val readAttributes: Map[String, N4jValue] = vertex.asMap(N4jValue.toN4jType).asScala.toMap

          val vertexId = r.get("nid").asLong()
          success(
            s.seeVertices(Set(vertexId)),
            MatchedVertex(
              vertexId,
              UUID.fromString(vertex.get("uid").asString()),
              labels,
              readAttributes
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
        rest.zipWithIndex.map(v => (v._1, v._2 + 1))
          .reverse.foldLeft(List.empty[String], List.empty[String], List.empty[String]) {
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
      val fullQuery = s"MATCH $queryP $whereIdP RETURN $returnP"

      tryCatch(
        (r, s) => {
          val (path, seenVertexIds, seenEdgeIds) = collectVertices(1 + rest.length, r)
          success(s.seeEdges(seenEdgeIds).seeVertices(seenVertexIds), path)
        },
        fullQuery,
        s
      )
    }

    private def tryCatch[S](
      fn: (Record, DslStateData) => ErrorOr[(DslStateData, S)],
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
        } else
          fail(DslError(s"Query $fullQuery returned more than one result. " +
            s"Results:\n${result.asScala.map(r => PrettyPrint.prettyPrint(new WrappedRecord(r))(InternalTypeSystem.TYPE_SYSTEM))
              .mkString("\n")}", s))
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

        val readAttributes: Map[String, N4jValue] = curVertex.asMap(N4jValue.toN4jType).asScala.toMap

        (MatchedVertex(vertexId, uid, Set.empty, readAttributes), vertexId)
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
