//package mwittmann.checkgraph.graphvalidator
//
//import cats.syntax._
//import cats.implicits._
//
//import mwittmann.checkgraph.graphvalidator.Dsl.DslState
//
///**
//  * Implicits for applying DSL commands to matched vertices directly
//  */
//object DslImplicits {
//
//  val noCheck: DslState[Unit] = ().pure[DslState]
//
//  implicit class FromBool(b: Boolean) {
//    def ifTrue(fn: => DslState[_]): DslState[Unit] = if (b) fn.map(_ => ()) else noCheck
//    def ifFalse(fn: => DslState[_]): DslState[Unit] = if (b) noCheck else fn.map(_ => ())
//  }
//
//  implicit class ByUidImplicits(byUid: MatchedVertex) {
//    def checkEdgeTo(
//      to: VertexToMatch, relationshipLabel: String
//    )(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      Dsl.checkEdge(byUid, to, relationshipLabel)
//
//    def -->(to: VertexToMatch, relationshipLabel: String)(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      checkEdgeTo(to, relationshipLabel)
//
//    def checkEdgeTo(
//      attributes: Map[String, N4jValue], labels: List[String], relationshipLabel: String
//    )(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      Dsl.checkEdge(byUid, VertexToMatch(attributes, labels), relationshipLabel)
//
//    def -->(
//      attributes: Map[String, N4jValue], labels: List[String], relationshipLabel: String
//    )(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      checkEdgeTo(attributes, labels, relationshipLabel)
//
//    def checkEdgeFrom(from: VertexToMatch, relationshipLabel: String)(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      Dsl.checkEdge(from, byUid, relationshipLabel)
//
//    def `<--`(from: VertexToMatch, relationshipLabel: String)(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      checkEdgeFrom(from, relationshipLabel)
//
//    def checkEdgeFrom(
//      attributes: Map[String, N4jValue], labels: List[String], relationshipLabel: String
//    )(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      Dsl.checkEdge(VertexToMatch(attributes, labels), byUid, relationshipLabel)
//
//    def `<--`(
//      attributes: Map[String, N4jValue], labels: List[String], relationshipLabel: String
//    )(implicit gcd: GraphCheckData): DslState[MatchedVertex] =
//      checkEdgeFrom(attributes, labels, relationshipLabel)
//
//    def checkEdgeTo(
//      to: MatchedVertex, relationshipLabel: String
//    )(implicit gcd: GraphCheckData): DslState[Unit] =
//      Dsl.matchEdge(byUid, to, relationshipLabel)
//
//    def -->(to: MatchedVertex, relationshipLabel: String)(implicit gcd: GraphCheckData): DslState[Unit] =
//      checkEdgeTo(to, relationshipLabel)
//  }
//}
