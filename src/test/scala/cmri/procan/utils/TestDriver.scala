package cmri.procan.utils

import org.neo4j.driver.v1._

import cmri.procan.checkgraph.utils.WrappedNeo4jDriver

// Todo: uri and auth should come from config
object TestDriver {
  private val token: AuthToken = AuthTokens.basic("neo4j", "test")

  val driver: Driver = GraphDatabase.driver(
    "bolt://127.0.0.1:7687",
    token,
    Config.build.toConfig
  )

  val wrappedDriver = new WrappedNeo4jDriver(driver)
}
