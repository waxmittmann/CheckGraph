/**
MIT License

Copyright (c) 2017-2019 Children's Medical Research Institute (CMRI)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
  */
package cmri.procan.checkgraph.utils

import cats.effect.IO
import org.neo4j.driver.v1.Transaction

object AssertUids {
  // Assert that all vertices have uids and all uids are unique
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

    // Existence checks are an 'enterprise' feature
    //tx.run("CREATE CONSTRAINT ON (lakeElem:Lake) ASSERT exists(lakeElem.uid)").summary()
  }}
}
