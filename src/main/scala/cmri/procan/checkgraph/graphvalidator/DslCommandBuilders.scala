package cmri.procan.checkgraph.graphvalidator

import cmri.procan.checkgraph.graphvalidator.DslCommands._
import cmri.procan.checkgraph.graphvalidator.DslCommands.{GetVertex, MatchPath, MatchVertex, MatchedVertex}

object DslCommandBuilders extends DslCommandBuilderMethods

trait DslCommandBuilderMethods {
  // Create a DSLCommand for matching a single vertex
  def vertex(labels: Set[EdgeLabel], attributes: Map[String, N4jValue] = Map.empty): MatchVertex =
    MatchVertex(labels, attributes)

  // Create a DSLCommand for matching an edge between the first vertex of a matched path and the input vertex
  def edge(
    matchedLeft: MatchPath,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = matchedLeft.edgeTo(matchedRight, edgeLabels)

  // Create a DSLCommand for matching an edge between the first vertex of a matched path and the input vertex
  def edge(
    matchedLeft: MatchPath,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = matchedLeft.edgeTo(UseMatchedVertex(matchedRight), edgeLabels)

  // Create a DSLCommand for matching an edge between two vertices
  def edge(
    matchedLeft: GetVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(matchedLeft, List((matchedRight, edgeLabels)))

  // Create a DSLCommand for matching an edge between two vertices
  def edge(
    matchedLeft: GetVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(matchedLeft, List((UseMatchedVertex(matchedRight), edgeLabels)))

  // Create a DSLCommand for matching an edge between two vertices
  def edge(
    matchedLeft: MatchedVertex,
    matchedRight: GetVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(UseMatchedVertex(matchedLeft), List((matchedRight, edgeLabels)))

  // Create a DSLCommand for matching an edge between two vertices
  def edge(
    matchedLeft: MatchedVertex,
    matchedRight: MatchedVertex,
    edgeLabels: Set[EdgeLabel]
  ): MatchPath = MatchPath(UseMatchedVertex(matchedLeft), List((UseMatchedVertex(matchedRight), edgeLabels)))

}
