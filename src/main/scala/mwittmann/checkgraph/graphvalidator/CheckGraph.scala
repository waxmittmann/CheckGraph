package mwittmann.checkgraph.graphvalidator

import mwittmann.checkgraph.graphvalidator.DslCommands._
import mwittmann.checkgraph.utils.WrappedNeo4jDriver
import org.neo4j.driver.v1._
import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._
import cats.~>
import scala.collection.JavaConverters._

import mwittmann.checkgraph.graphvalidator.DslCompiler.DslCompilerConfig

object CheckGraph {

  type CheckProgram[S] = Free[DslCommand, S]

  type ProgramResult[S] = Either[Throwable, ErrorOr[(DslStateData, S)]]

  def check[S](
    graphLabel: String,
    program: CheckProgram[S]
  ): ProgramResult[S] = {
    val driver: WrappedNeo4jDriver = wrappedDriver()
    try {

      val compiledProgram: DslState[S] = program.foldMap(DslCompiler.compiler(DslCompilerConfig(
        graphLabel = graphLabel,
        graph = driver,
        baseAttributes = Map.empty
      )))

      val result: ErrorOr[(DslStateData, S)] = compiledProgram.run(DslStateData())

      val checkedResult =
        result.flatMap { case (state, v) => checkResultState(driver, graphLabel, state).right.map(_ => (state, v)) }

      Right(checkedResult) : ProgramResult[S]
    } catch {
      case t: Exception => Left(t)
    } finally {
      driver.close()
    }
  }

  private def checkResultState(
    driver: WrappedNeo4jDriver, graphLabel: String, result: DslStateData
  ): Either[DslError, Unit] = {
    val seenEdges = result.seenEdges
    val seenVertices = result.seenVertices

    val actualEdges = getAllEdgeIds(driver, graphLabel)
    val actualVertices = getAllVertexIds(driver, graphLabel)

    if (seenVertices.diff(actualVertices).nonEmpty) {
      Left(DslError(s"Saw unexpected vertices with ids: ${seenVertices.diff(actualVertices)}", result))
    } else if (actualVertices.diff(seenVertices).nonEmpty) {
      Left(DslError(s"Did not see expected vertices with ids: ${actualVertices.diff(seenVertices)}", result))
    } else if (seenEdges.diff(actualEdges).nonEmpty) {
      Left(DslError(s"Saw unexpected edges with ids: ${seenEdges.diff(actualEdges)}", result))
    } else if (actualEdges.diff(seenEdges).nonEmpty) {
      Left(DslError(s"Did not see expected edges with ids: ${actualEdges.diff(seenEdges)}", result))
    } else {
      Right()
    }
  }

  private def getAllEdgeIds(driver: WrappedNeo4jDriver, graphLabel: String): Set[Long] = {
    val q =
      s"""
         |MATCH (:$graphLabel) -[e]- (:$graphLabel) RETURN collect(ID(e)) AS ids
       """.stripMargin

    driver.tx(q).single().get("ids").asList(v => v.asLong()).asScala.toSet
  }

  private def getAllVertexIds(driver: WrappedNeo4jDriver, graphLabel: String): Set[Long] = {
    val q =
      s"""
         |MATCH (v :$graphLabel) RETURN collect(ID(v)) AS ids
       """.stripMargin

    driver.tx(q).single().get("ids").asList(v => v.asLong()).asScala.toSet
  }

  private def wrappedDriver(): WrappedNeo4jDriver = {
    val token: AuthToken = AuthTokens.basic("neo4j", "test")

    val driver: Driver = GraphDatabase.driver(
      "bolt://127.0.0.1:7687",
      token,
      Config.build
        .withEncryptionLevel(Config.EncryptionLevel.NONE)
        .toConfig
    )

    new WrappedNeo4jDriver(driver)
  }
}
