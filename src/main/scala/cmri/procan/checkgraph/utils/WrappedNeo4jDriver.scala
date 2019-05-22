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
