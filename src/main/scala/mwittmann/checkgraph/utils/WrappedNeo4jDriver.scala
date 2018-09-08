package mwittmann.checkgraph.utils

import java.util
import scala.collection.JavaConverters._

import org.neo4j.driver.v1._

trait WrappedTransaction {
  def run(statementTemplate: String, statementParameters: util.Map[String, AnyRef]): StatementResult
}

class WrappedDriverTransaction(tx: Transaction) extends WrappedTransaction {
  override def run(statementTemplate: String, statementParameters: util.Map[String, AnyRef]): StatementResult =
    tx.run(statementTemplate, statementParameters)
}

trait WrappedNeo4jClient {
  def tx[S](work: Transaction => S): S
  def readTx[S](work: Transaction => S): S

  def tx[S](work: String): StatementResult

  def rawSingle(q: String, p: Map[String, AnyRef]): Unit
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
      case e: Throwable => {
        tx.failure()
        throw e
      }
    } finally {
      tx.close()
      session.close()
    }
  }

  def tx[S](work: String): StatementResult = tx { tx => tx.run(work) }

  def rawSingle(q: String, p: Map[String, AnyRef]): Unit = {
    tx(_.run(q, p.asJava).single())
  }

  // Todo: Use readonly transaction
  override def readTx[S](work: Transaction => S): S = tx(work)

  def close(): Unit = driver.close()
}
