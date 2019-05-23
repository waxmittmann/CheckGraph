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
