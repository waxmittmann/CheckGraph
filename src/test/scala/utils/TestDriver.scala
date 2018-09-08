package utils

import scala.util.Random

import mwittmann.checkgraph.graphvalidator2.{GraphCheckData, N4jType}
import mwittmann.checkgraph.utils.WrappedNeo4jDriver
import org.neo4j.driver.v1._

object TestDriver {
  private val token: AuthToken = AuthTokens.basic("neo4j", "test")

  val driver: Driver = GraphDatabase.driver(
    "bolt://127.0.0.1:7687",
    token,
    Config.build
      .withEncryptionLevel(Config.EncryptionLevel.NONE)
      .toConfig
  )

  val wrappedDriver = new WrappedNeo4jDriver(driver)

  def gcd(ba: Map[String, N4jType] = Map.empty) =
    GraphCheckData(wrappedDriver, s"G${Random.alphanumeric.take(20).mkString}", ba)
}
