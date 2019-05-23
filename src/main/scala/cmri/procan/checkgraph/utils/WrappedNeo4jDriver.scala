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

import java.util
import scala.collection.JavaConverters._

import org.neo4j.driver.v1._

// Wraps the Java Neo4j client
trait WrappedNeo4jClient {
  def tx[S](work: Transaction => S): S

  def tx[S](work: String): StatementResult
}

class WrappedNeo4jDriver(driver: Driver) extends WrappedNeo4jClient {

  override def tx[S](work: Transaction => S): S = {
    val session = driver.session()
    val tx: Transaction = session.beginTransaction()
    try {
      val v = work(tx)
      tx.success()
      v
    } catch {
      case e: Throwable =>
        tx.failure()
        throw e
    } finally {
      tx.close()
      session.close()
    }
  }

  def tx[S](work: String): StatementResult = tx { tx => tx.run(work) }

  def close(): Unit = driver.close()
}

trait WrappedTransaction {
  def run(statementTemplate: String, statementParameters: util.Map[String, AnyRef]): StatementResult
}

class WrappedDriverTransaction(tx: Transaction) extends WrappedTransaction {
  override def run(statementTemplate: String, statementParameters: util.Map[String, AnyRef]): StatementResult =
    tx.run(statementTemplate, statementParameters)
}
