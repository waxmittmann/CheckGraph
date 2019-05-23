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

import scala.collection.JavaConverters._
import scala.util.Try

import org.neo4j.driver.v1.types.{Node, Type, TypeSystem}
import org.neo4j.driver.v1.{Record, Value}

object WrappedNeo4j {

  sealed trait Children
  case class ListChildren(children: List[WrappedNeo4j]) extends Children
  case class NodeChildren(children: Map[String, WrappedNeo4j]) extends Children
  case object NoChildren extends Children

  sealed trait WrappedNeo4j {
    sealed trait Optionals {
      def getString(key: String): Option[String]
    }
    val option: Optionals
    val outer: WrappedNeo4j = this

    def getLeaf: Option[String]

    def getType(ts: TypeSystem): Option[Type]
    def isLeaf(ts: TypeSystem): Boolean = {
      def isLeaf(value: Type): Boolean =
        value == ts.BOOLEAN() ||
          value == ts.BYTES() ||
          value == ts.FLOAT() ||
          value == ts.INTEGER() ||
          value == ts.NULL()||
          value == ts.NUMBER()||
          value == ts.STRING()

      def isNonLeaf(value: Type): Boolean =
        value == ts.LIST() ||
          value == ts.MAP() ||
          value == ts.NODE() ||
          value == ts.PATH() ||
          value == ts.RELATIONSHIP()

      val maybeType = this.getType(ts)
      val isLeafR = maybeType.exists { (`type`: Type) => isLeaf(`type`) }
      val isNonLeafR = maybeType.forall { (`type`: Type) => isNonLeaf(`type`) }

      if (isLeafR)
        true
      else if (isNonLeafR)
        false
      else
        throw new Exception(s"Unknown type $maybeType")
    }

    def getChildren(ts: TypeSystem): Children

    def getKeys: Set[String]

    def containsKey(str: String): Boolean

    def getNode(key: String): WrappedNode

    def getStringList(key: String): List[String]
    def getNodeList(key: String): List[WrappedNode]
    def getList(key: String): List[WrappedValue]

    def getBoolean(key: String): Boolean

    def getString(key: String): String

    def getLong(key: String): Long

    def getInt(key: String): Int

    def getUid(key: String): java.util.UUID
  }

  class WrappedValue(value: Value) extends WrappedNeo4j {
    case object MyOptionals extends Optionals {
      def getString(key: String): Option[String] = if (outer.containsKey(key)) Some(outer.getString(key)) else None
    }
    val option: Optionals = MyOptionals

    def getLeaf: Option[String] = Try(value.asObject()).toEither
      .right.map(v => Some(v.toString))
      .left.map(_ => None)
      .merge

    def getType(ts: TypeSystem): Option[Type] = Some(value.`type`())

    def getChildren(ts: TypeSystem): Children =
      if (getType(ts).contains(ts.LIST()))
        ListChildren(value.asList((v: Value) => new WrappedValue(v): WrappedNeo4j).asScala.toList)
      else if (getType(ts).contains(ts.NODE()))
        NodeChildren(value.asMap[WrappedNeo4j]((v: Value) => new WrappedValue(v): WrappedNeo4j).asScala.toMap)
      else if (getType(ts).contains(ts.PATH()) || getType(ts).contains(ts.RELATIONSHIP()))
        throw new Exception("Unsupported for path")
      else if (getType(ts).isEmpty)
        throw new Exception("Type should never be empty for value")
      else
        NoChildren

    def getKeys: Set[String] = value.keys().asScala.toSet

    def containsKey(str: String): Boolean = value.containsKey(str) && !value.get(str).isNull

    def getNode(key: String): WrappedNode = new WrappedNode(value.get(key).asNode())

    def getStringList(key: String): List[String] = value.get(key).asList[String](_.asString()).asScala.toList

    def getNodeList(key: String): List[WrappedNode] = value.get(key).asList(v => new WrappedNode(v.asNode())).asScala.toList

    def getList(key: String): List[WrappedValue] = value.get(key).asList((v: Value) => new WrappedValue(v)).asScala.toList

    def asList: List[WrappedValue] = value.asList[WrappedValue]((v: Value) => new WrappedValue(v)).asScala.toList
    def asNodeList: List[WrappedNode] = value.asList[WrappedNode]((v: Value) => new WrappedNode(v.asNode())).asScala.toList

    def getBoolean(key: String): Boolean = value.get(key).asBoolean()

    def getString(key: String): String = value.get(key).asString()

    def getLong(key: String): Long = value.get(key).asLong()

    def getInt(key: String): Int = value.get(key).asInt()

    def getUid(key: String): java.util.UUID = java.util.UUID.fromString(value.get(key).asString())
  }

  class WrappedRecord(record: Record) extends WrappedNeo4j {
    case object MyOptionals extends Optionals {
      def getString(key: String): Option[String] = if (outer.containsKey(key)) Some(outer.getString(key)) else None
    }
    val option: Optionals = MyOptionals

    def getLeaf: Option[String] = None

    def getType(ts: TypeSystem): Option[Type] = None

    def getChildren(ts: TypeSystem): Children =
      NodeChildren(record.asMap[WrappedNeo4j]((v: Value) => new WrappedValue(v): WrappedNeo4j).asScala.toMap)

    def getKeys: Set[String] = record.keys().asScala.toSet

    def containsKey(str: String): Boolean = record.containsKey(str) && !record.get(str).isNull

    def getNode(key: String): WrappedNode = new WrappedNode(record.get(key).asNode())

    def getStringList(key: String): List[String] = record.get(key).asList[String](_.asString()).asScala.toList

    def getNodeList(key: String): List[WrappedNode] = record.get(key).asList(v => new WrappedNode(v.asNode())).asScala.toList

    def getList(key: String): List[WrappedValue] = record.get(key).asList((v: Value) => new WrappedValue(v)).asScala.toList

    def getBoolean(key: String): Boolean = record.get(key).asBoolean()

    def getString(key: String): String = record.get(key).asString()

    def getLong(key: String): Long = record.get(key).asLong()

    def getInt(key: String): Int = record.get(key).asInt()

    def getUid(key: String): java.util.UUID = java.util.UUID.fromString(record.get(key).asString())
  }

  class WrappedNode(node: Node) extends WrappedNeo4j {
    def labels(): Set[String] = node.labels().asScala.toSet

    case object MyOptionals extends Optionals {
      def getString(key: String): Option[String] = if (outer.containsKey(key)) Some(outer.getString(key)) else None
    }
    val option: Optionals = MyOptionals

    def getLeaf: Option[String] = None

    def getType(ts: TypeSystem): Option[Type] = Some(ts.NODE())

    def getChildren(ts: TypeSystem): Children =
      NodeChildren(node.asMap[WrappedNeo4j]((v: Value) => new WrappedValue(v): WrappedNeo4j).asScala.toMap)

    def getKeys: Set[String] = node.keys().asScala.toSet

    def containsKey(str: String): Boolean = node.containsKey(str) && !node.get(str).isNull

    def hasLabel(key: String): Boolean = node.hasLabel(key)

    def getNode(key: String): WrappedNode = new WrappedNode(node.get(key).asNode())

    def getStringList(key: String): List[String] = node.get(key).asList[String](_.asString()).asScala.toList

    def getNodeList(key: String): List[WrappedNode] = node.get(key).asList(v => new WrappedNode(v.asNode())).asScala.toList

    def getList(key: String): List[WrappedValue] = node.get(key).asList((v: Value) => new WrappedValue(v)).asScala.toList

    def getBoolean(key: String): Boolean = node.get(key).asBoolean()

    def getString(key: String): String = node.get(key).asString()

    def getLong(key: String): Long = node.get(key).asLong()

    def getInt(key: String): Int = node.get(key).asInt()

    def getUid(key: String): java.util.UUID = java.util.UUID.fromString(node.get(key).asString())

    // Unique to node
  }

  object Implicits {
    implicit class RecordImplicits(record: Record) {
      def wrap: WrappedRecord = new WrappedRecord(record)
    }

    implicit class NodeWrapper(node: Node) {
      def wrap: WrappedNode = new WrappedNode(node)
    }

    implicit class ValueImplicits(value: Value) {
      def wrap: WrappedValue = new WrappedValue(value)
    }
  }
}
