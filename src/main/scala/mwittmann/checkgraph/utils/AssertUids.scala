package mwittmann.checkgraph.utils

import cats.effect.IO
import org.neo4j.driver.v1.Transaction

object AssertUids {
  def check(graph: WrappedNeo4jDriver, baseVertexLabel: String): IO[Int] = for {
    _           <- setUidUniquenessConstraint(graph, baseVertexLabel)
    existsOnAll <- checkUidExistence(graph, baseVertexLabel)
  } yield existsOnAll

  private def checkUidExistence(graph: WrappedNeo4jDriver, baseLabel: String): IO[Int] = graph.tx { tx: Transaction => IO {
    val q = s"MATCH (e :$baseLabel) WHERE NOT EXISTS(e.uid) RETURN e"
    val r = tx.run(q)
    r.list().size()
  }}

  private def setUidUniquenessConstraint(
    graph: WrappedNeo4jDriver, baseLabel: String
  ): IO[Unit] = graph.tx { tx: Transaction => IO {
    tx.run(s"CREATE CONSTRAINT ON (lakeElem :$baseLabel) ASSERT lakeElem.uid IS UNIQUE").summary()

    // Existence checks are an 'enterprise' feature; pretty disappointing :/
    //tx.run("CREATE CONSTRAINT ON (lakeElem:Lake) ASSERT exists(lakeElem.uid)").summary()
  }}
}
