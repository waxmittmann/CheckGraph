package mwittmann.checkgraph.graphvalidator

import java.util
import java.util.UUID
import scala.collection.immutable
import scala.collection.JavaConverters._

import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._
import cats.~>
import mwittmann.checkgraph.graphvalidator.{N4jType, N4jValue, N4jValueRender}
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
    // Vertices + edges seen so far
    seenVertices: Set[Long] = Set.empty,
    seenEdges: Set[Long] = Set.empty
  ) {
    `this` =>
    def seeEdges(seenEdgeIds: Set[Long]): DslStateData = this.copy(seenEdges = seenEdges ++ seenEdgeIds)

    def seeVertices(seenVertexIds: Set[Long]): DslStateData = this.copy(seenVertices = seenVertices ++ seenVertexIds)

    def seenVertex(newSeen: Long): DslStateData = `this`.copy(seenVertices = seenVertices + newSeen)

    def seenEdge(edgeId: Long): DslStateData = `this`.copy(seenEdges = seenEdges + edgeId)
  }

  case class DslError(err: String, state: DslStateData)

  case class MatchedVertex(id: Long, uid: UUID, labels: Set[String], attributes: Map[String, N4jValue])

  type EdgeLabel = String

  sealed trait DslCommand[A]

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

  case class MatchPath(
    first: GetVertex,
    rest: List[(GetVertex, Set[EdgeLabel])]
  ) extends DslCommand[MatchedPath] {
    def edgeTo(matchVertex: GetVertex, edgeLabels: Set[EdgeLabel]): MatchPath =
      this.copy(rest = (matchVertex, edgeLabels) :: rest)
  }

  type FreeDslCommand[A] = Free[DslCommand, A]

  def matchVertex(labels: Set[EdgeLabel], attributes: Map[String, N4jValue]): MatchVertex =
    MatchVertex(labels, attributes)

  def matchEdge(
    matchedLeft: MatchPath,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = matchedLeft.edgeTo(matchedRight, edgeLabels)

  def matchEdge(
    matchedLeft: MatchPath,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = matchedLeft.edgeTo(UseMatchedVertex(matchedRight), edgeLabels)

  def matchEdge(
    matchedLeft: GetVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(matchedLeft, List((matchedRight, edgeLabels)))

  def matchEdge(
    matchedLeft: GetVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(matchedLeft, List((UseMatchedVertex(matchedRight), edgeLabels)))

  def matchEdge(
    matchedLeft: MatchedVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(UseMatchedVertex(matchedLeft), List((matchedRight, edgeLabels)))

  def matchEdge(
    matchedLeft: MatchedVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(UseMatchedVertex(matchedLeft), List((UseMatchedVertex(matchedRight), edgeLabels)))

  implicit def asFree(mp: MatchPath): FreeDslCommand[MatchedPath] =
    liftF[DslCommand, MatchedPath](mp)

  implicit def asFree(mp: MatchVertex): FreeDslCommand[MatchedVertex] =
    liftF[DslCommand, MatchedVertex](mp)

  implicit class FreeMatchVertexImplicits(leftMatch: MatchVertex) {
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      MatchPath(leftMatch, List((rightMatch, edgeLabels)))
  }

  implicit class FreeMatchPathImplicits(leftMatch: MatchPath) {
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      leftMatch.edgeTo(rightMatch, edgeLabels)
  }
}