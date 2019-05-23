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
