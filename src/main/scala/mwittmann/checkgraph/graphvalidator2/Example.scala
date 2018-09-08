package mwittmann.checkgraph.graphvalidator2

import java.util.UUID
import scala.util.Random

import cats.free.Free
import mwittmann.checkgraph.graphvalidator2.{N4jType, N4jUid}
import mwittmann.checkgraph.graphvalidator2.DslCommands.{DslCommand, DslState, DslStateData, MatchedPath, matchEdge, matchVertex}
import mwittmann.checkgraph.utils.{WrappedNeo4jClient, WrappedNeo4jDriver}
import org.neo4j.driver.v1._
import cats.data._
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._
import cats.~>

object Example {

  def main (args: Array[String]): Unit = {

    val driver = wrappedDriver()

    val aUid = UUID.randomUUID()
    val bUid = UUID.randomUUID()
    val cUid = UUID.randomUUID()

    val graphLabel = s"G_${Random.alphanumeric.take(20).mkString}"

    val q =
      s"""
         |CREATE (a :A :$graphLabel { uid: '${aUid.toString}' })
         |  -[:RELATES_TO]-> (b :B :$graphLabel { uid: '${bUid.toString}' })
         |  -[:RELATES_TO]-> (c :C :$graphLabel { uid: '${cUid.toString}' })
         |RETURN a, b, c
       """.stripMargin

    val result = driver.tx(q)

    val program: Free[DslCommand, (MatchedPath, MatchedPath)] =
      for {

        v1  <- matchVertex(Set("A"), Map("uid" -> N4jUid(aUid)))
        v2  <- matchVertex(Set("B"), Map("uid" -> N4jUid(bUid)))
        a   <- matchEdge(v1, v2, Set("RELATES_TO"))

        v3  <- matchVertex(Set("C"), Map("uid" -> N4jUid(cUid)))
        b   <- matchEdge(a.vertices.last, v3, Set("RELATES_TO"))
      } yield (a, b)

    try {
      val compiledProgram: DslState[(MatchedPath, MatchedPath)] = program.foldMap(DslCompiler.compiler)

      val result = compiledProgram.run(DslStateData(
        graphLabel = graphLabel,
        graph = driver,
        baseAttributes = Map.empty
      ))

      println(result)
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

