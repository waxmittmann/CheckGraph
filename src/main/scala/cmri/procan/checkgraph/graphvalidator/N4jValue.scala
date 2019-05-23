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
package cmri.procan.checkgraph.graphvalidator

import java.util.UUID
import scala.collection.JavaConverters._
import scala.util.Try

import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.v1.Value

sealed trait N4jType
case object N4jNull extends N4jType with N4jValue { val `type`: N4jType = N4jNull }
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

object N4jValue {
  val ts: InternalTypeSystem = InternalTypeSystem.TYPE_SYSTEM

  def toN4jType(value: Value): N4jValue = {
    if (value.`type`() == ts.INTEGER()) {
      N4jLong(value.asLong())
    } else if (value.`type`() == ts.FLOAT()) {
      N4jDouble(value.asFloat())
    } else if (value.`type`() == ts.BOOLEAN()) {
      N4jBoolean(value.asBoolean())
    } else if (value.`type`() == ts.NULL()) {
      N4jNull
    } else if (value.`type`() == ts.STRING()) {
      N4jString(value.asString())
    } else if (value.`type`() == ts.LIST()) {
      val list: List[Value] = value.asList[Value](v => v).asScala.toList
      assert(list.forall(_.`type`() == ts.STRING()))
      N4jStringList(list.map(_.asString()))
    } else {
      throw new Exception(s"Unhandled type '${value.`type`().name()}' with value '${value.asObject()}'")
    }
  }

}

object N4jValueRender {
  def renderInCypher(attr: N4jValue): String = attr match {
    case N4jNull          => "null"
    case N4jInt(v)        => v.toString
    case N4jLong(v)       => v.toString
    case N4jDouble(v)     => v.toString
    case N4jString(v)     => s"'$v'"
    case N4jUid(v)        => s"'${v.toString}'"
    case N4jBoolean(v)    => v.toString // Todo: double-check this works as expected
    case N4jStringList(v) => s"[${v.map(li => s"'$li'").mkString(",")}]"
  }

  def render(attr: N4jValue): AnyRef = attr match {
    case N4jNull          => null
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
  implicit class ForN4jString(str: N4jString) {
    def toUid: Option[N4jUid] = Try(N4jUid(UUID.fromString(str.v))).toOption
  }

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
