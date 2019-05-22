package cmri.procan.checkgraph.graphvalidator

import org.neo4j.driver.v1._
import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._
import cats.~>
import scala.collection.JavaConverters._

import cmri.procan.checkgraph.graphvalidator.DslCommands._
import cmri.procan.checkgraph.utils.WrappedNeo4jDriver
import cmri.procan.checkgraph.graphvalidator.DslCompiler.DslCompilerConfig
import cmri.procan.checkgraph.graphvalidator.DslCommands.{DslCommand, DslError, DslStateData}
import cmri.procan.checkgraph.utils.WrappedNeo4jDriver

object CheckGraph {

  type CheckProgram[S] = Free[DslCommand, S]

  sealed trait ProgramResult[S] { val success: Boolean }
  sealed trait ProgramError[S] extends ProgramResult[S] { val success: Boolean = false }
  case class UnexpectedError[S](throwable: Throwable) extends ProgramError[S]
  case class CheckError[S](dslError: DslError) extends ProgramError[S]
  case class ProgramSuccess[S](s: S, state: DslStateData) extends ProgramResult[S] { val success: Boolean = true }

  // Run a CheckGraph program using the provided `graphLabel` to isolate the subgraph of interest. Will error out for
  // any failed match and for any vertex or edge that remains unmatched by the program.
  def run[S](
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

      val checkedResult: Either[DslError, (DslStateData, S)] =
        result.flatMap { case (state, v) => checkResultState(driver, graphLabel, state).right.map(_ => (state, v)) }

      checkedResult match {
        case Left(value) => CheckError(value)
        case Right((state, s)) => ProgramSuccess(s, state)
      }
    } catch {
      case t: Throwable => UnexpectedError(t)
    } finally {
      driver.close()
    }
  }

  // Run CheckGraph and turn the result into an Either.
  def runAndGetValue[S](
    graphLabel: String,
    program: CheckProgram[S]
  ): Either[String, (DslStateData, S)] =
    getValue(run(graphLabel, program))

  // Turn the result of executing a CheckProgram into an either
  def getValue[S](result: ProgramResult[S]): Either[String, (DslStateData, S)] = {
    result match {
      case CheckError(dslError)     => Left(s"Expected success, got DslError:\n${dslError.err}")
      case UnexpectedError(t)       => Left(s"Expected success, got exception:\n$t")
      case ProgramSuccess(s, state) => Right((state, s))
    }
  }

  // Get the result of executing a CheckProgram if the program passed, else throw an exception
  def unsafeGetValue[S](result: ProgramResult[S]): (DslStateData, S) = {
    getValue(result) match {
      case Left(value)  => throw new Exception(value)
      case Right(value) => value
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

  // Todo: This should be set up via config
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
