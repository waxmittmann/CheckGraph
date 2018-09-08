package mwittmann.checkgraph.graphvalidator2

import mwittmann.checkgraph.graphvalidator2.DslCommands._
import mwittmann.checkgraph.utils.WrappedNeo4jDriver
import org.neo4j.driver.v1._
import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._
import cats.~>

object CheckGraph {

  type CheckProgram[S] = Free[DslCommand, S]

  type ProgramResult[S] = Either[Throwable, ErrorOr[(DslStateData, S)]]

  def check[S](
    graphLabel: String,
    program: CheckProgram[S]
  ): ProgramResult[S] = {
    val driver = wrappedDriver()
    try {

      val compiledProgram: DslState[S] = program.foldMap(DslCompiler.compiler)

      val result: ErrorOr[(DslStateData, S)] = compiledProgram.run(DslStateData(
        graphLabel = graphLabel,
        graph = driver,
        baseAttributes = Map.empty
      ))


      val checkedResult =
        result.flatMap { case (state, v) =>
          val x: ErrorOr[(DslStateData, S)] = checkResultState(state).right.map(_ => (state, v))
          x
        }

      Right(checkedResult) : ProgramResult[S]
      //Right(result) : ProgramResult[S]
    } catch {
      case t: Exception => Left(t)
    } finally {
      driver.close()
    }
  }

  private def checkResultState(result: DslStateData): Either[DslError, Unit] = {
    val seenEdges = result.seenEdges
    val seenVertices = result.seenVertices

    val actualEdges = getAllEdgeIds
    val actualVertices = getAllVertexIds

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

  private def getAllEdgeIds: Set[Long] = ???

  private def getAllVertexIds: Set[Long] = ???

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
