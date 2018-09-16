package cmri.procan.checkgraph.graphvalidator

import cmri.procan.checkgraph.graphvalidator.DslCommands._

object PartialMatchBuilders extends PartialMatchBuildersTrait

trait PartialMatchBuildersTrait {
  implicit class FreeMatchVertexImplicits(leftMatch: MatchVertex) {
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      MatchPath(leftMatch, List((rightMatch, edgeLabels)))

    def -<(edgeLabels: String*): PartialMatchPath =
      PartialMatchPath(MatchPath(leftMatch, List.empty), edgeLabels.toSet)
  }

  implicit class MatchedVertexImplicits(matched: MatchedVertex) {
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      MatchPath(UseMatchedVertex(matched), List((rightMatch, edgeLabels)))

    def -(edgeLabels: String*): PartialMatchPath =
      PartialMatchPath(MatchPath(UseMatchedVertex(matched), List.empty), edgeLabels.toSet)
  }

  case class PartialMatchPath(
    mp: MatchPath,
    matchLabels: Set[EdgeLabel]
  ) {
    def ->(rightMatch: MatchedVertex): MatchPath =
      mp.edgeTo(UseMatchedVertex(rightMatch), matchLabels)

    def ->(rightMatch: GetVertex): MatchPath =
      mp.edgeTo(rightMatch, matchLabels)
  }

  implicit class FreeMatchPathImplicits(leftMatch: MatchPath) {
    def -->(rightMatch: GetVertex, edgeLabels: Set[String] = Set.empty): MatchPath =
      leftMatch.edgeTo(rightMatch, edgeLabels)

    def -(edgeLabels: String*): PartialMatchPath =
      PartialMatchPath(leftMatch, edgeLabels.toSet)
  }
}
