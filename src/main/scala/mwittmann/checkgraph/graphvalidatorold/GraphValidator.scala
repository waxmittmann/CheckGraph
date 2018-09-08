//package mwittmann.checkgraph.graphvalidator
//
//import java.util.UUID
//import scala.collection.JavaConverters._
//
//import org.neo4j.driver.v1.types.Relationship
//
//import mwittmann.checkgraph.utils.CatchError.catchError
//import mwittmann.checkgraph.graphvalidator.Dsl.ErrorOr
//import mwittmann.checkgraph.utils.{WrappedNeo4jClient, PrettyPrint, WrappedNeo4jDriver}
//import mwittmann.checkgraph.utils.WrappedNeo4j.Implicits._
//
//case class GraphCheckData(
//  graph: WrappedNeo4jClient,
//  primaryLabelName: String,
//  baseAttributes: Map[String, N4jType]
//)
//
//object GraphValidator { // (graph: Neo4jGraph, primaryLabelName: String) { `this` =>
//  /**
//    * Methods for checking various general properties such as total number of nodes or edges
//    */
//  def getAllEdges(implicit gcd: GraphCheckData): Set[(Long, Long)] = gcd.graph.readTx { tx =>
//    import gcd.primaryLabelName
//
//    val q = s"MATCH (n :$primaryLabelName) -[e]-> (:$primaryLabelName) RETURN COLLECT(e) AS edges"
//
//    val asList: Seq[(Long, Long)] = catchError(tx.run(q).next().get("edges"))
//      .asList[Relationship](_.asRelationship()).asScala.map { r => (r.startNodeId(), r.endNodeId()) }
//
//    val duplicates = asList.groupBy(identity).filter(_._2.size > 1)
//    if (duplicates.nonEmpty)
//      throw new Exception(s"Had edge duplicates!\n${duplicates.map { case (uid, l) => (uid, l.size)}}")
//    else
//      asList.toSet
//  }
//
//  def getAllUids(implicit gcd: GraphCheckData): Set[UUID] = gcd.graph.readTx { tx =>
//    import gcd.primaryLabelName
//
//    val q = s"MATCH (n :$primaryLabelName) RETURN COLLECT(n.uid) AS uids"
//    val asList = catchError(tx.run(q).next().get("uids")).asList(_.asString()).asScala.toList.map(UUID.fromString)
//    val duplicates = asList.groupBy(identity).filter(_._2.size > 1)
//    if (duplicates.nonEmpty)
//      throw new Exception(s"Had vertex duplicates!\n${duplicates.map { case (uid, l) => (uid, l.size)}}")
//    else
//      asList.toSet
//  }
//
//  def nodeNr(implicit gcd: GraphCheckData): Int = gcd.graph.readTx { tx =>
//    import gcd.primaryLabelName
//
//    val q = s"MATCH (n :$primaryLabelName) RETURN count(n)"
//    catchError(tx.run(q).next().get("count(n)")).asInt()
//  }
//
//  def edgeNr(implicit gcd: GraphCheckData): Int = gcd.graph.readTx { tx =>
//    import gcd.primaryLabelName
//
//    val q = s"MATCH (n :$primaryLabelName) -[r]-> (:$primaryLabelName) RETURN count(r)"
//    catchError(tx.run(q).next().get("count(r)")).asInt()
//  }
//
//  /**
//    * Methods for checking that a vertex with provided labels and attributes exists. These methods are fine for
//    * validating very simple graphs (up to a couple of vertices), for more complex graphs use the DSL.
//    */
//  def existsWithGraph(
//    otherLabelNames: List[String],
//    attributesByName: Map[String, N4jValue]
//  )(implicit gcd: GraphCheckData): Either[(String, String), Unit] =
//    `exists?`(otherLabelNames, attributesByName).left.map { failedQuery =>
//      (failedQuery, wholeGraphString)
//    }
//
//  def wholeGraphString(implicit gcd: GraphCheckData): String = gcd.graph.readTx { tx =>
//    import gcd.primaryLabelName
//
//    val q = s"MATCH (n :$primaryLabelName) RETURN n"
//    import scala.collection.JavaConverters._
//
//    catchError(tx.run(q).list().asScala.toList
//      .map(v => PrettyPrint.prettyPrint(v.wrap)(tx.typeSystem())))
//      .mkString("\n\n")
//  }
//
//  def `exists?`(
//    labelNames: List[String],
//    attributesByName: Map[String, N4jValue]
//  )(implicit gcd: GraphCheckData): Either[String, Unit] = gcd.graph.readTx { tx =>
//    import gcd.primaryLabelName
//
//    val labels = (primaryLabelName :: labelNames).map(labelName => s":$labelName").mkString(" ")
//    val attributes = attributesToNeo(attributesByName)
//
//    val q = s"MATCH (n $labels {$attributes}) RETURN n"
//    val result = catchError(tx.run(q))
//    if (result.hasNext) {
//      result.next()
//      Right(())
//    } else
//      Left(q)
//  }
//
//  def attributesToNeo(attributesByName: Map[String, N4jValue]): String =
//    attributesByName.map { case (name: String, attr: N4jValue) =>
//      val attrStr: String = N4jValueRender.renderInCypher(attr)
//      s"$name: $attrStr"
//    }.mkString(",")
//
//  def attributesToNeoM(attributesByName: Map[String, N4jValue]): Map[String, AnyRef] =
//    attributesByName.map { case (name: String, attr: N4jValue) =>
//      (name, N4jValueRender.render(attr))
//    }
//
//  /**
//    * Checks the result from running a dsl program
//    */
//  def checkResult(checkResult: ErrorOr[(DslStateData, Unit)])(implicit gcd: GraphCheckData): Either[String, Unit] =
//    checkResult.right.flatMap { case (state, _) => checkState(state) }.left.map { dslError =>
//      s"Failed with DslError '$dslError'"
//    }
//
//  private def checkState(state: DslStateData)(implicit gcd: GraphCheckData): Either[String, Unit] = {
//    // Check that we visited all vertices
//    val totalVertices = nodeNr
//    val allUids = getAllUids
//    val totalEdges = edgeNr
//    val allEdges = getAllEdges
//
//    for {
//      _ <- if (allUids.size != totalVertices)
//        Left(s"All uids ${allUids.size} doesn't match total vertices count ${totalVertices}. Indicates duplicates.")
//      else
//        Right(())
//
//      _ <- if (allUids.diff(state.seenVertices).nonEmpty)
//        Left(s"Following uids were not seen: ${allUids.diff(state.seenVertices)}")
//      else
//        Right(())
//
//      _ <- if (allEdges.size != totalEdges)
//        Left(s"All edges ${allEdges.size} doesn't match total edge count ${totalEdges}. Indicates duplicates.")
//      else
//        Right(())
//
//      _ <- if (allEdges.diff(state.seenEdges).nonEmpty)
//        Left(s"Following edges were not seen: ${allEdges.diff(state.seenEdges)}")
//      else
//        Right(())
//    } yield ()
//  }
//}
