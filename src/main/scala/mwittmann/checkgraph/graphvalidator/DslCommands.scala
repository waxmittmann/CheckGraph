//package mwittmann.checkgraph.graphvalidator
//
//import java.util.UUID
//
//import cats.data._
//import cats.implicits._
//import cats.free.Free
//import cats.free.Free.liftF
//import cats.~>
//import mwittmann.checkgraph.graphvalidator.Dsl.{DslState, ErrorOr}
//import mwittmann.checkgraph.utils.WrappedNeo4jClient
//import org.neo4j.driver.v1.StatementResult
//
//object DslCommands {
//  type QSA[A] = State[DslStateData, A]
//  type ErrorOr[A] = Either[DslError, A]
//  type DslState[A] = StateT[ErrorOr, DslStateData, A]
//
//  /**
//    * result smart constructors
//    */
//  private def value[A](v: A): DslState[A] = StateT[ErrorOr, DslStateData, A](s => Right((s, v)))
//  private def state[A](fn: DslStateData => ErrorOr[(DslStateData, A)]): DslState[A] = StateT[ErrorOr, DslStateData, A](fn)
//  private def fail[A](err: DslError): ErrorOr[(DslStateData, A)] = Left(err)
//  private def success[A](r: DslStateData, a: A): ErrorOr[(DslStateData, A)] = Right((r, a))
//
//
//  case class DslStateData(
//    // 'Config' data
//    graphLabel: String,
//    graph: WrappedNeo4jClient,
//    baseAttributes: Map[String, N4jType],
//
//    // Vertices + edges seen so far
//    seenVertices: Set[UUID] = Set.empty,
//    seenEdges: Set[(Long, Long)] = Set.empty
//  ) { `this` =>
//    def seenVertex(newSeen: UUID): DslStateData = `this`.copy(seenVertices = seenVertices + newSeen)
//    def seenEdge(edgeId: (Long, Long)): DslStateData = `this`.copy(seenEdges = seenEdges + edgeId)
//  }
//
//  case class DslError(err: String, state: DslStateData)
//
//  //  type CheckNeo4j
//
//  /*
//
//
//    for {
//      (a, b, c) <-
//        n(attrs, "A") `<--` n(attrs, "B") `<--` n(attrs, "C")
//
//      (a) <-
//        matchEdge(
//          matchEdge(
//            matchNode(..., "A"),
//            matchNode(..., "B"),
//            "..."
//          ),
//          matchNode(..., "C"),
//          "..."
//        )
//
//      (arb) <- cypherSingle("MATCH ... RETURN singleVertex")
//
//    } yield ()
//   */
//
//  sealed trait MatchedNeo4j
//  case class MatchedVertex(id: Long, uid: UUID, labels: Set[String], attributes:  Map[String, N4jValue]) extends MatchedNeo4j
////  case class MatchedStatement(matches: List[MatchNeo4j]) extends MatchNeo4j
//
//  type EdgeLabel = String
//
//  sealed trait DslCommand[A]
//  type FreeDslCommand[A] = Free[DslCommand, A]
//
////  sealed trait MatchNeo4j[A <: MatchedNeo4j] extends DslCommand[A]
//
////  case class MatchId[S](s: S) extends DslCommand[S]
//
//  sealed trait GetVertex extends DslCommand[MatchedVertex]
//
//  case class MatchVertex(
//    labels: Set[String],
//    attributes: Map[String, N4jValue]
//  ) extends GetVertex
//
//  case class UseMatchedVertex(matchedVertex: MatchedVertex) extends GetVertex
//
//  case class MatchedPath(
//    first: MatchedVertex,
//    rest: List[(MatchedVertex, Set[EdgeLabel])]
//  ) {
//    def edgeTo(matchedVertex: MatchedVertex, edgeLabels: Set[EdgeLabel]): MatchedPath =
//      this.copy(rest = rest :+ (matchedVertex, edgeLabels))
//  }
//
//  object MatchedPath {
//    def from(vertex: MatchedVertex): MatchedPath = MatchedPath(vertex, List.empty)
//  }
//
//  def matchVertex(labels: Set[EdgeLabel], attributes: Map[String, N4jValue]): FreeDslCommand[MatchedVertex] =
//    liftF[DslCommand, MatchedVertex](MatchVertex(labels, attributes))
//
//  def useMatchedVertex(matched: MatchedVertex): FreeDslCommand[MatchedVertex] =
//    liftF[DslCommand, MatchedVertex](UseMatchedVertex(matched))
//
//  case class MatchPath(
//    first: GetVertex,
//    rest: List[(GetVertex, Set[EdgeLabel])]
//  ) extends DslCommand[MatchedPath] {
//
//    def edgeTo(matchVertex: GetVertex, edgeLabels: Set[EdgeLabel]): MatchPath =
//      this.copy(rest = rest :+ (matchVertex, edgeLabels))
//
//  }
//
////  case class MatchEdge(
////    matchedLeft: MatchedPath,
////    matchedRight: MatchedVertex,
////    edgeLabels: Set[EdgeLabel]
////  ) extends DslCommand[MatchedPath]
//
//  def matchEdge(
//    matchedLeft: MatchPath,
//    matchedRight: GetVertex,
//    edgeLabels: Set[EdgeLabel]
//  ): FreeDslCommand[MatchedPath] =
//    liftF[DslCommand, MatchedPath](matchedLeft.edgeTo(matchedRight, edgeLabels))
//
//  def matchEdge(
//    matchedLeft: MatchPath,
//    matchedRight: MatchedVertex,
//    edgeLabels: Set[EdgeLabel]
//  ): FreeDslCommand[MatchedPath] =
//    liftF[DslCommand, MatchedPath](matchedLeft.edgeTo(UseMatchedVertex(matchedRight), edgeLabels))
//
//  def matchEdge(
//    //    matchedLeft: MatchNeo4j[A],
//    matchedLeft: GetVertex,
//    matchedRight: GetVertex,
//    edgeLabels: Set[EdgeLabel]
//  ): FreeDslCommand[MatchedPath] =
//    liftF[DslCommand, MatchedPath](MatchPath(matchedLeft, List((matchedRight, edgeLabels))))
//
//  def matchEdge(
//    //    matchedLeft: MatchNeo4j[A],
//    matchedLeft: GetVertex,
//    matchedRight: MatchedVertex,
//    edgeLabels: Set[EdgeLabel]
//  ): FreeDslCommand[MatchedPath] =
//    liftF[DslCommand, MatchedPath](MatchPath(matchedLeft, List((UseMatchedVertex(matchedRight), edgeLabels))))
//
//  def matchEdge(
//    //    matchedLeft: MatchNeo4j[A],
//    matchedLeft: MatchedVertex,
//    matchedRight: GetVertex,
//    edgeLabels: Set[EdgeLabel]
//  ): FreeDslCommand[MatchedPath] =
//    liftF[DslCommand, MatchedPath](MatchPath(UseMatchedVertex(matchedLeft), List((matchedRight, edgeLabels))))
//
//  def matchEdge(
//    //    matchedLeft: MatchNeo4j[A],
//    matchedLeft: MatchedVertex,
//    matchedRight: MatchedVertex,
//    edgeLabels: Set[EdgeLabel]
//  ): FreeDslCommand[MatchedPath] =
//    liftF[DslCommand, MatchedPath](MatchPath(UseMatchedVertex(matchedLeft), List((UseMatchedVertex(matchedRight), edgeLabels))))
//
//
//  def compiler: DslCommand ~> DslState = new (DslCommand ~> DslState) {
//    override def apply[A](fa: DslCommand[A]): DslState[A] = fa match {
//      case MatchVertex(labels, attributes)  => Neo4jHelpers.matchVertex(labels, attributes).map(_.asInstanceOf[A])
//
//      case MatchPath(first, rest)           => Neo4jHelpers.matchPath(first, rest).map(_.asInstanceOf[A])
//
//      case UseMatchedVertex(vertex)         => value(vertex.asInstanceOf[A]) // Eww...
//    }
//  }
//
//  object Neo4jHelpers {
//
//    def edgeLabels(labels: Set[String]): String =
//      labels.map(l => s":$l").mkString(" ")
//
//    def vertexLabels(state: DslStateData, labels: Set[String]): String =
//      (state.graphLabel + labels).map(l => s":$l").mkString(" ")
//
//    def vertexAttributes(state: DslStateData, attributes: Map[String, N4jValue]): String =
//      s"{ ${attributes.map { case (key, value) => s"$key: ${N4jValueRender.render(value)}"}.mkString(",")} }"
//
//    def vertex(name: String, state: DslStateData, labels: Set[String], attributes: Map[String, N4jValue]): String =
//      s"($name ${vertexLabels(state, labels)} ${vertexAttributes(state, attributes)} )"
//
//    def matchVertex(labels: Set[String], attributes:  Map[String, N4jValue]): DslState[MatchedVertex] = state { s: DslStateData =>
//      val q = s"MATCH ${vertex("n", s, labels, attributes)} RETURN n"
//
//      val result = s.graph.tx(q).list()
//
//      if(result.size() == 0)
//        fail(DslError(s"Query $q returned no results.", s))
//      else if (result.size() == 1) {
//        val vertex = result.get(0)
//        success(s, MatchedVertex(vertex.get("id").asLong(), UUID.fromString(vertex.get("uid").asString()), labels, attributes))
//      } else
//        fail(DslError(s"Query $q returned more than one result.", s))
//    }
//
//    def matchPath(firstGet: GetVertex, rest: List[(GetVertex, Set[EdgeLabel])]): DslState[MatchedPath] = {
//      val firstVertex: DslState[MatchedVertex] = compiler(firstGet)
//      val asPath: DslState[MatchedPath] = firstVertex.map(mv => MatchedPath(mv, List.empty))
//
////      rest.foldLeft(firstVertex)
//
////      foldLeftTE(firstVertex) { case (curState, (gv, edgeLabels)) =>
////        val curVertex: DslState[MatchedVertex] = compiler(gv)
////      }(rest)
//
//      //  def foldLeft[B](z: B)(@deprecatedName('f) op: (B, A) => B): B = {
//      rest.foldLeft(asPath) { case (result, (currentGet, edgeLabels)) =>
//        //result.
//
////        val x: DslState[MatchedPath] = state { s =>
////          val y: DslState[MatchedPath] = for {
////            curVertex   <- compiler(currentGet)
////            prevResult  <- result
////            _           <- matchEdge(prevResult.rest.last._1, curVertex, edgeLabels)
////          } yield prevResult.edgeTo(curVertex, edgeLabels)
////
////          y
//
//        val y: DslState[MatchedPath] = for {
//          curVertex   <- compiler(currentGet)
//          prevResult  <- result
//          _           <- matchEdge(prevResult.rest.last._1, curVertex, edgeLabels)
//        } yield prevResult.edgeTo(curVertex, edgeLabels)
//        y
//
//      }
//
//
////      }
//
////      rest.map { case (gv, edgeLabels) =>
////        val curVertex: DslState[MatchedVertex] = compiler(gv)
////
////      }
//
////      ???
//    }
//
//
//    def matchEdge(left: MatchedVertex, right: MatchedVertex, labels: Set[EdgeLabel]): DslState[Unit] = state { s =>
//      val q = s"MATCH (a { id: ${left.id}) -[${edgeLabels(labels)}]-> (b { id: ${right.id}) RETURN a, b"
//      val result = s.graph.tx(q).list()
//
//      if(result.size() == 0)
//        fail(DslError(s"Query $q returned no results.", s))
//      else if (result.size() == 1) {
//        success(s, ())
//      } else
//        fail(DslError(s"Query $q returned more than one result. Impossibru!", s))
//    }
//
//
//    def foldLeftTE[A, B, C](initial: A)(fn: (A, B) => Either[C, A])(li: List[B]): Either[C, A] = {
//      li match {
//        case h :: t => fn(initial, h).flatMap(newInitial => foldLeftTE(newInitial)(fn)(t))
//        case Nil    => Right(initial)
//      }
//    }
//
//
//    //    def checkEdge(
////      from: MatchedVertex,
////      to: VertexToMatch,
////      relationshipLabel: String
////    )(implicit gcd: GraphCheckData): DslState[MatchedVertex] = state { (dslState: DslStateData) =>
////      import gcd.primaryLabelName
////
////      val fromNode =
////        s"(${(primaryLabelName :: from.labels).map(l => s":$l").mkString(" ")} { uid: '${from.uid.toString}' })"
////
////      val toNode =
////        s"(v ${(primaryLabelName :: to.labels).map(l => s":$l").mkString(" ")} { ${attributesToNeo(to.attributes)} })"
////
////      checkRunQueryHelper(dslState, fromNode, toNode, relationshipLabel)
////    }
////
////    private def checkRunQueryHelper(
////      dslState: DslStateData,
////      fromNode: String,
////      toNode: String,
////      relationshipLabel: String
////    )(implicit gcd: GraphCheckData): ErrorOr[(DslStateData, MatchedVertex)] = {
////      val q = s"MATCH $fromNode -[e :$relationshipLabel]-> $toNode RETURN e, v"
////      gcd.graph.readTx { tx =>
////        val result = tx.run(q)
////        if (result.hasNext) {
////          for {
////            row <- catchError(
////              result.single(),
////              s"Had Neo4j error for $fromNode -[:$relationshipLabel]-> $toNode",
////              dslState
////            )
////
////            result <- {
////              val v = row.get("v").asNode()
////              val e = {
////                val edge = row.get("e").asRelationship()
////                (edge.startNodeId(), edge.endNodeId())
////              }
////
////              val fieldNotFound = gcd.baseAttributes.find { case (field, t) => !v.containsKey(field) }
////
////              fieldNotFound.map { case (field, t) =>
////                fail(DslError(
////                  s"Matched $fromNode -[:$relationshipLabel]-> $toNode but failed to find attribute with name $field.", dslState
////                ))
////              }.getOrElse {
////                val seenUid = UUID.fromString(v.get("uid").asString())
////                success(dslState.seenVertex(seenUid).seenEdge(e), MatchedVertex(seenUid, v.labels().asScala.toList))
////              }
////            }
////          } yield result
////        } else
////          fail(DslError(s"Failed to match $fromNode -[:$relationshipLabel]-> $toNode", dslState))
////      }
////    }
//
//
//  }
//
//
//  implicit class MatchVertexImplicits(leftMatch: MatchVertex) {
//    def `<--`(rightMatch: MatchVertex, edgeLabels: Set[String]): MatchPath =
//      MatchPath(leftMatch, List((rightMatch, edgeLabels)))
//  }
//
//
//  val p: Free[DslCommand, (MatchedPath, MatchedPath)] =
//    for {
//      //x <- matchVertex(Set("A")) `<--` matchVertex(Set("B")) `<--` matchVertex(Set("C"))
//
//      v1  <- matchVertex(Set("A"), Map.empty)
//      v2  <- matchVertex(Set("B"), Map.empty)
//      a   <- matchEdge(v1, v2, Set("RELATES_TO"))
//
//      v3  <- matchVertex(Set("C"), Map.empty)
//      b   <- matchEdge(a.rest.last._1, v3, Set("RELATES_TO"))
//    } yield (a, b)
//
//
//
//  //type DslState[A] = StateT[ErrorOr, DslStateData, A]
//
//
//
//}
