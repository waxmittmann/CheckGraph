package mwittmann.checkgraph.graphvalidator2

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
import mwittmann.checkgraph.utils.WrappedNeo4jClient
import org.neo4j.driver.v1.Record

object DslCommands {
  type QSA[A] = State[DslStateData, A]
  type ErrorOr[A] = Either[DslError, A]
  type DslState[A] = StateT[ErrorOr, DslStateData, A]

  /**
    * result smart constructors
    */
  def value[A](v: A): DslState[A] = StateT[ErrorOr, DslStateData, A](s => Right((s, v)))
  def state[A](fn: DslStateData => ErrorOr[(DslStateData, A)]): DslState[A] = StateT[ErrorOr, DslStateData, A](fn)
  def fail[A](err: DslError): ErrorOr[(DslStateData, A)] = Left(err)
  def success[A](r: DslStateData, a: A): ErrorOr[(DslStateData, A)] = Right((r, a))

  case class DslStateData(
    // 'Config' data
    graphLabel: String,
    graph: WrappedNeo4jClient,
    baseAttributes: Map[String, N4jType],

    // Vertices + edges seen so far
//    seenVertices: Set[UUID] = Set.empty,
    seenVertices: Set[Long] = Set.empty,
//    seenEdges: Set[(Long, Long)] = Set.empty
    seenEdges: Set[Long] = Set.empty
  ) { `this` =>
    def seenVertex(newSeen: Long): DslStateData = `this`.copy(seenVertices = seenVertices + newSeen)
    def seenEdge(edgeId: Long): DslStateData = `this`.copy(seenEdges = seenEdges + edgeId)
  }

  case class DslError(err: String, state: DslStateData)

  sealed trait MatchedNeo4j
  case class MatchedVertex(id: Long, uid: UUID, labels: Set[String], attributes:  Map[String, N4jValue]) extends MatchedNeo4j

  type EdgeLabel = String

  sealed trait DslCommand[A]
  type FreeDslCommand[A] = Free[DslCommand, A]

  sealed trait GetVertex extends DslCommand[MatchedVertex]

  case class MatchVertex(
    labels: Set[String],
    attributes: Map[String, N4jValue]
  ) extends GetVertex

  case class UseMatchedVertex(matchedVertex: MatchedVertex) extends GetVertex

  case class MatchedPath(
    vertices: List[MatchedVertex]
  )

  object MatchedPath {
    def from(vertex: MatchedVertex): MatchedPath = MatchedPath(List(vertex))
  }

  def matchVertex(labels: Set[EdgeLabel], attributes: Map[String, N4jValue]): FreeDslCommand[MatchedVertex] =
    liftF[DslCommand, MatchedVertex](MatchVertex(labels, attributes))

  def useMatchedVertex(matched: MatchedVertex): FreeDslCommand[MatchedVertex] =
    liftF[DslCommand, MatchedVertex](UseMatchedVertex(matched))

  case class MatchPath(
    first: GetVertex,
    rest: List[(GetVertex, Set[EdgeLabel])]
  ) extends DslCommand[MatchedPath] {

    def edgeTo(matchVertex: GetVertex, edgeLabels: Set[EdgeLabel]): MatchPath =
      this.copy(rest = rest :+ (matchVertex, edgeLabels))

  }

  def matchEdge(
    matchedLeft: MatchPath,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): FreeDslCommand[MatchedPath] =
    liftF[DslCommand, MatchedPath](matchedLeft.edgeTo(matchedRight, edgeLabels))

  def matchEdge(
    matchedLeft: MatchPath,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): FreeDslCommand[MatchedPath] =
    liftF[DslCommand, MatchedPath](matchedLeft.edgeTo(UseMatchedVertex(matchedRight), edgeLabels))

  def matchEdge(
    matchedLeft: GetVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): FreeDslCommand[MatchedPath] =
    liftF[DslCommand, MatchedPath](MatchPath(matchedLeft, List((matchedRight, edgeLabels))))

  def matchEdge(
    matchedLeft: GetVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): FreeDslCommand[MatchedPath] =
    liftF[DslCommand, MatchedPath](MatchPath(matchedLeft, List((UseMatchedVertex(matchedRight), edgeLabels))))

  def matchEdge(
    matchedLeft: MatchedVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): FreeDslCommand[MatchedPath] =
    liftF[DslCommand, MatchedPath](MatchPath(UseMatchedVertex(matchedLeft), List((matchedRight, edgeLabels))))

  def matchEdge(
    matchedLeft: MatchedVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): FreeDslCommand[MatchedPath] =
    liftF[DslCommand, MatchedPath](MatchPath(UseMatchedVertex(matchedLeft), List((UseMatchedVertex(matchedRight), edgeLabels))))

  implicit class MatchVertexImplicits(leftMatch: MatchVertex) {
    def `<--`(rightMatch: MatchVertex, edgeLabels: Set[String]): MatchPath =
      MatchPath(leftMatch, List((rightMatch, edgeLabels)))
  }

}
