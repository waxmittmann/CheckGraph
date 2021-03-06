/**
MIT License

Copyright (c) 2017-2019 Children's Medical Research Institute (CMRI)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
  */
package cmri.procan.checkgraph.graphvalidator

import java.util.UUID

import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._

import DslCommands._

object DslCommands {
  type QSA[A] = State[DslStateData, A]
  type ErrorOr[A] = Either[DslError, A]
  type DslState[A] = StateT[ErrorOr, DslStateData, A]
  type Program[A] = Free[DslCommand, A]

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

  case object Noop extends DslCommand[Unit]

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
      this.copy(rest = rest :+ (matchVertex, edgeLabels))
  }
}

trait DslCommandsMethods {
  /**
    * result smart constructors
    */
  def value[A](v: A): DslState[A] = StateT[ErrorOr, DslStateData, A](s => Right((s, v)))
  def state[A](fn: DslStateData => ErrorOr[(DslStateData, A)]): DslState[A] = StateT[ErrorOr, DslStateData, A](fn)
  def fail[A](err: DslError): ErrorOr[(DslStateData, A)] = Left(err)
  def success[A](r: DslStateData, a: A): ErrorOr[(DslStateData, A)] = Right((r, a))

  /**
    * Noop
    */
  val noop: Program[Unit] = liftF[DslCommand, Unit](Noop)

  implicit def liftFree(mp: MatchPath): Program[MatchedPath] =
    liftF[DslCommand, MatchedPath](mp)

  implicit def liftFree(mp: MatchVertex): Program[MatchedVertex] =
    liftF[DslCommand, MatchedVertex](mp)

  implicit class MatchedPathImplicits(matchedPath: MatchedPath) {
    def first: MatchedVertex = matchedPath.vertices.head
    def last: MatchedVertex = matchedPath.vertices.last
  }
}
