package cmri.procan.checkgraph.utils

import org.neo4j.driver.v1.Transaction
import org.neo4j.driver.v1.types.TypeSystem

import cmri.procan.checkgraph.utils.WrappedNeo4j._

object PrettyPrint {

  def prettyPrintTx(ele: WrappedNeo4j)(implicit tx: Transaction): String = prettyPrint(ele)(tx.typeSystem())

  def prettyPrint(ele: WrappedNeo4j)(ts: TypeSystem): String = ele.getChildren(ts) match {
    case ListChildren(children) => children.zipWithIndex.map { case (wrapped, index) =>
      s"$index->\n${StringUtils.indent(prettyPrint(wrapped)(ts))}"
    }.mkString("\n")

    case NodeChildren(children) => children.map { case (name, wrapped) =>
      s"$name ->\n${StringUtils.indent(prettyPrint(wrapped)(ts))}".stripMargin
    }.mkString("\n")

    case NoChildren             => {
      StringUtils.indent(StringUtils.shortenString(ele.getLeaf.getOrElse("Should be leaf")))
    }
  }

}
