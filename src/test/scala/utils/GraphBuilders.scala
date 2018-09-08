package utils

import java.util.UUID

import mwittmann.checkgraph.graphvalidator2.{GraphCheckData, GraphValidator, N4jUid, N4jValue}
import scala.collection.JavaConverters._

object GraphBuilders {

  def createVertex(
    uid: UUID, props: Map[String, N4jValue], labels: List[String]
  )(implicit gcd: GraphCheckData): Unit = gcd.graph.tx { tx =>

    val propsWithUid = props + ("uid" -> N4jUid(uid))

    val propsR = "{" + propsWithUid.map { case (k, _) => s"$k: $$$k"}.mkString(",") + "}"

    val labelsR = (gcd.primaryLabelName :: labels).map(l => s":$l").mkString(" ")

    val q = s"CREATE (n $labelsR $propsR) RETURN n"

    val p = GraphValidator.attributesToNeoM(propsWithUid).asJava

    tx.run(q, p).single()
  }

}
