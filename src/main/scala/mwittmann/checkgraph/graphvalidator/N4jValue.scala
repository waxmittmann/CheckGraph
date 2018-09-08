/*
package mwittmann.checkgraph.graphvalidator

import java.util.UUID
import scala.collection.JavaConverters._

sealed trait N4jType
case object N4jInt extends N4jType
case object N4jDouble extends N4jType
case object N4jString extends N4jType
case object N4jUid extends N4jType
case object N4jBoolean extends N4jType
case object N4jLong extends N4jType
case object N4jStringList extends N4jType

sealed trait N4jValue { val `type`: N4jType }
case class N4jInt(v: Int)                 extends N4jValue { val `type`: N4jType = N4jInt }
case class N4jDouble(v: Double)           extends N4jValue { val `type`: N4jType = N4jDouble }
case class N4jString(v: String)           extends N4jValue { val `type`: N4jType = N4jString }
case class N4jUid(v: UUID)                extends N4jValue { val `type`: N4jType = N4jUid }
case class N4jBoolean(v: Boolean)         extends N4jValue { val `type`: N4jType = N4jBoolean }
case class N4jLong(v: Long)               extends N4jValue { val `type`: N4jType = N4jLong }
case class N4jStringList(v: List[String]) extends N4jValue { val `type`: N4jType = N4jString }

object N4jValueRender {
  def renderInCypher(attr: N4jValue): String = attr match {
    case N4jInt(v)        => v.toString
    case N4jLong(v)       => v.toString
    case N4jDouble(v)     => v.toString
    case N4jString(v)     => s"'$v'"
    case N4jUid(v)        => s"'${v.toString}'"
    case N4jBoolean(v)    => v.toString // Todo: double-check this works as expected
    case N4jStringList(v) => s"[${v.map(li => s"'$li'").mkString(",")}]"
  }

  def render(attr: N4jValue): AnyRef = attr match {
    case N4jInt(v)        => v.asInstanceOf[AnyRef]
    case N4jLong(v)       => v.asInstanceOf[AnyRef]
    case N4jDouble(v)     => v.asInstanceOf[AnyRef]
    case N4jString(v)     => v.asInstanceOf[AnyRef]
    case N4jUid(v)        => v.toString
    case N4jBoolean(v)    => v.asInstanceOf[AnyRef]
    case N4jStringList(v) => v.asJava
  }
}

object N4jValueImplicits {
  implicit class ForInt(i: Int) {
    def toN4j: N4jInt = N4jInt(i)
  }

  implicit class ForLong(i: Long) {
    def toN4j: N4jLong = N4jLong(i)
  }

  implicit class ForString(i: String) {
    def asN4j: N4jString = N4jString(i)
  }
}
*/
