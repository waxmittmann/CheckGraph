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
