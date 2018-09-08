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

      Right(result) : ProgramResult[S]
    } catch {
      case t: Exception => Left(t)
    } finally {
      driver.close()
    }
  }

  def wrappedDriver(): WrappedNeo4jDriver = {
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
