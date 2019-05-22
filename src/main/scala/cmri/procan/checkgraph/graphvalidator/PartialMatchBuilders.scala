package cmri.procan.checkgraph.graphvalidator

import cmri.procan.checkgraph.graphvalidator.DslCommands._

object PartialMatchBuilders extends PartialMatchBuildersTrait

trait PartialMatchBuildersTrait {
  implicit class FreeMatchVertexImplicits(leftMatch: MatchVertex) {
    // Match from `leftMatch` to `rightMatch` via an edge with `edgeLabels`
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      MatchPath(leftMatch, List((rightMatch, edgeLabels)))

    // Start matching from `leftMatch` to `rightMatch` via an edge with `edgeLabels`
    def -<(edgeLabels: String*): PartialMatchPath =
      PartialMatchPath(MatchPath(leftMatch, List.empty), edgeLabels.toSet)
  }

  implicit class MatchedVertexImplicits(matched: MatchedVertex) {
    // Match from `leftMatch` to `rightMatch` via an edge with `edgeLabels`
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      MatchPath(UseMatchedVertex(matched), List((rightMatch, edgeLabels)))

    // Start matching from `leftMatch` to `rightMatch` via an edge with `edgeLabels`
    def -(edgeLabels: String*): PartialMatchPath =
      PartialMatchPath(MatchPath(UseMatchedVertex(matched), List.empty), edgeLabels.toSet)
  }

  case class PartialMatchPath(
    mp: MatchPath,
    matchLabels: Set[EdgeLabel]
  ) {
    // Complete a match to `rightMatch`
    def ->(rightMatch: MatchedVertex): MatchPath =
      mp.edgeTo(UseMatchedVertex(rightMatch), matchLabels)

    // Complete a match to `rightMatch`
    def ->(rightMatch: GetVertex): MatchPath =
      mp.edgeTo(rightMatch, matchLabels)
  }

  implicit class FreeMatchPathImplicits(leftMatch: MatchPath) {
    // Match from `leftMatch`'s first vertex  to `rightMatch` via an edge with `edgeLabels`
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      leftMatch.edgeTo(rightMatch, edgeLabels)

    // Start matching from `leftMatch`'s first vertex to `rightMatch` via an edge with `edgeLabels`
    def -(edgeLabels: String*): PartialMatchPath =
      PartialMatchPath(leftMatch, edgeLabels.toSet)
  }
}
