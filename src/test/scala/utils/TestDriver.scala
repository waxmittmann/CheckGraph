package utils

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
}
