package cmri.procan.checkgraph.graphvalidator

import cmri.procan.checkgraph.graphvalidator.DslCommands._
import cmri.procan.checkgraph.graphvalidator.DslCommands.{GetVertex, MatchPath, MatchVertex, MatchedVertex}

object DslCommandBuilders extends DslCommandBuilderMethods

trait DslCommandBuilderMethods {
  def vertex(labels: Set[EdgeLabel], attributes: Map[String, N4jValue] = Map.empty): MatchVertex =
    MatchVertex(labels, attributes)

  def edge(
    matchedLeft: MatchPath,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = matchedLeft.edgeTo(matchedRight, edgeLabels)

  def edge(
    matchedLeft: MatchPath,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = matchedLeft.edgeTo(UseMatchedVertex(matchedRight), edgeLabels)

  def edge(
    matchedLeft: GetVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(matchedLeft, List((matchedRight, edgeLabels)))

  def edge(
    matchedLeft: GetVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(matchedLeft, List((UseMatchedVertex(matchedRight), edgeLabels)))

  def edge(
    matchedLeft: MatchedVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(UseMatchedVertex(matchedLeft), List((matchedRight, edgeLabels)))

  def edge(
    matchedLeft: MatchedVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(UseMatchedVertex(matchedLeft), List((UseMatchedVertex(matchedRight), edgeLabels)))

}
