//package mwittmann.checkgraph.graphvalidator
//
//import java.util.UUID
//import scala.collection.JavaConverters._
//
//import cats.data._
//import cats.implicits._
//import org.neo4j.driver.v1.Record
//
//import mwittmann.checkgraph.graphvalidator.GraphValidator.attributesToNeo
//
//case class DslStateData(
//  seenVertices: Set[UUID] = Set.empty,
//  seenEdges: Set[(Long, Long)] = Set.empty
//) { `this` =>
//  def seenVertex(newSeen: UUID): DslStateData = `this`.copy(seenVertices = seenVertices + newSeen)
//  def seenEdge(edgeId: (Long, Long)): DslStateData = `this`.copy(seenEdges = seenEdges + edgeId)
//}
//
//case class DslError(err: String, state: DslStateData)
//
//sealed trait VertexRef { val labels: List[String] }
//case class MatchedVertex(uid: UUID, labels: List[String])
//case class VertexToMatch(attributes: Map[String, N4jValue], labels: List[String])
//
///**
//  * The validation DSL which provides an easy-ish way to check every vertex and edge in a whole graph
//  */
//object Dsl {
//  type QSA[A] = State[DslStateData, A]
//  type ErrorOr[A] = Either[DslError, A]
//  type DslState[A] = StateT[ErrorOr, DslStateData, A]
//
//  /**
//    * result smart constructors
//    */
//  private def state[A](fn: DslStateData => ErrorOr[(DslStateData, A)]): DslState[A] = StateT[ErrorOr, DslStateData, A](fn)
//  private def fail[A](err: DslError): ErrorOr[(DslStateData, A)] = Left(err)
//  private def success[A](r: DslStateData, a: A): ErrorOr[(DslStateData, A)] = Right((r, a))
//
//  /**
//    * DSL commands
//    */
//  // Check edge exists between an already matched vertex and a vertex to be matched
//  def checkEdge(
//    from: MatchedVertex,
//    to: VertexToMatch,
//    relationshipLabel: String
//  )(implicit gcd: GraphCheckData): DslState[MatchedVertex] = state { (dslState: DslStateData) =>
//    import gcd.primaryLabelName
//
//    val fromNode =
//      s"(${(primaryLabelName :: from.labels).map(l => s":$l").mkString(" ")} { uid: '${from.uid.toString}' })"
//
//    val toNode =
//      s"(v ${(primaryLabelName :: to.labels).map(l => s":$l").mkString(" ")} { ${attributesToNeo(to.attributes)} })"
//
//    checkRunQueryHelper(dslState, fromNode, toNode, relationshipLabel)
//  }
//
//  private def checkRunQueryHelper(
//    dslState: DslStateData,
//    fromNode: String,
//    toNode: String,
//    relationshipLabel: String
//  )(implicit gcd: GraphCheckData): ErrorOr[(DslStateData, MatchedVertex)] = {
//    val q = s"MATCH $fromNode -[e :$relationshipLabel]-> $toNode RETURN e, v"
//    gcd.graph.readTx { tx =>
//      val result = tx.run(q)
//      if (result.hasNext) {
//        for {
//          row <- catchError(
//            result.single(),
//            s"Had Neo4j error for $fromNode -[:$relationshipLabel]-> $toNode",
//            dslState
//          )
//
//          result <- {
//            val v = row.get("v").asNode()
//            val e = {
//              val edge = row.get("e").asRelationship()
//              (edge.startNodeId(), edge.endNodeId())
//            }
//
//            val fieldNotFound = gcd.baseAttributes.find { case (field, t) => !v.containsKey(field) }
//
//            fieldNotFound.map { case (field, t) =>
//              fail(DslError(
//                s"Matched $fromNode -[:$relationshipLabel]-> $toNode but failed to find attribute with name $field.", dslState
//              ))
//            }.getOrElse {
//              val seenUid = UUID.fromString(v.get("uid").asString())
//              success(dslState.seenVertex(seenUid).seenEdge(e), MatchedVertex(seenUid, v.labels().asScala.toList))
//            }
//          }
//        } yield result
//      } else
//        fail(DslError(s"Failed to match $fromNode -[:$relationshipLabel]-> $toNode", dslState))
//    }
//  }
//
//  // Check edge exists between a vertex yet-to-be-matched and an already matched vertex
//  def checkEdge(
//    from: VertexToMatch,
//    to: MatchedVertex,
//    relationshipLabel: String
//  )(implicit gcd: GraphCheckData): DslState[MatchedVertex] = state { (dslState: DslStateData) =>
//    import gcd.primaryLabelName
//
//    val fromNode =
//      s"(v ${(primaryLabelName :: from.labels).map(l => s":$l").mkString(" ")} { ${attributesToNeo(from.attributes)} })"
//
//    val toNode =
//      s"(${(primaryLabelName :: to.labels).map(l => s":$l").mkString(" ")} { uid: '${to.uid.toString}' })"
//
//    checkRunQueryHelper(dslState, fromNode, toNode, relationshipLabel)
//  }
//
//  // Check edge exists between two previously matched vertices
//  def matchEdge(
//    from: MatchedVertex,
//    to: MatchedVertex,
//    relationshipLabel: String
//  )(implicit gcd: GraphCheckData): DslState[Unit] = state { (dslState: DslStateData) =>
//    import gcd.primaryLabelName
//
//    val fromNode =
//      s"(${(primaryLabelName :: from.labels).map(l => s":$l").mkString(" ")} { uid: '${from.uid.toString}' })"
//
//    val toNode =
//      s"(${(primaryLabelName :: to.labels).map(l => s":$l").mkString(" ")} { uid: '${to.uid.toString}' })"
//
//    val q = s"MATCH $fromNode -[e :$relationshipLabel]-> $toNode RETURN e"
//    gcd.graph.readTx { tx =>
//      val sr = tx.run(q)
//      if (sr.hasNext) {
//          for {
//            row <- catchError[Record](
//              sr.single(),
//              s"Had neo4j error matching $fromNode -[$relationshipLabel]-> $toNode",
//              dslState
//            )
//
//            result <- {
//              val edgeRel = row.get("e").asRelationship()
//              val edge = (edgeRel.startNodeId(), edgeRel.endNodeId())
//              success(dslState.seenEdge(edge), ())
//            }
//          } yield result
//      } else fail(DslError(s"Failed to match $fromNode -[$relationshipLabel]-> $toNode", dslState))
//    }
//  }
//
//  // Check vertex exists
//  def matchVertex(uid: UUID, labels: List[String])(implicit gcd: GraphCheckData): DslState[MatchedVertex] = state { (dslState: DslStateData) =>
//    import gcd.primaryLabelName
//
//    val q =
//      s"""
//         |MATCH (v ${(primaryLabelName :: labels).map(l => s":$l").mkString(" ")} { uid: '${uid.toString}' })
//         |RETURN v
//       """.stripMargin
//
//    gcd.graph.readTx { tx =>
//      val r = tx.run(q)
//      if (r.hasNext) {
//        val n = r.single().get("v").asNode()
//
//        val fieldNotFound = gcd.baseAttributes.find { case (field, t) => !n.containsKey(field) }
//
//        fieldNotFound.map { case (field, t) =>
//          fail(DslError(
//            s"Matched vertex with $uid and $labels, but failed to match attribute $field.", dslState
//          ))
//        }.getOrElse {
//          val seenUid = UUID.fromString(n.get("uid").asString())
//          success(
//            dslState.seenVertex(seenUid),
//            MatchedVertex(seenUid, n.labels().asScala.filterNot(_ == gcd.primaryLabelName).toList)
//          )
//        }
//      } else
//        fail(DslError(s"Failed to match vertex with $uid and $labels", dslState))
//    }
//  }
//
//  // Catch thrown errors and wrap into ErrorOr
//  private def catchError[S](
//    fn: => S,
//    context: String,
//    curState: DslStateData
//  ): ErrorOr[S] = try {
//    Right[DslError, S](fn) : Either[DslError, S]
//  } catch {
//    case e: Exception => Left(DslError(
//      s"""
//         |Failed due to:
//         |$e
//         |Context: $context
//       """.stripMargin, curState
//    )) : Either[DslError, S]
//  }
//
//  /**
//    * Utilities for combining commands
//    */
//  def two(first: DslState[MatchedVertex], second: DslState[MatchedVertex]): DslState[(MatchedVertex, MatchedVertex)] =
//    for {
//      f <- first
//      s <- second
//    } yield (f, s)
//
//
//  def many(states: DslState[MatchedVertex]*): DslState[List[MatchedVertex]] =
//    states.toList.sequence[DslState, MatchedVertex]
//}