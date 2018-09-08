package mwittmann.checkgraph.graphvalidator2

import java.util.UUID
import scala.util.Random

import cats.free.Free
import mwittmann.checkgraph.graphvalidator2.N4jUid
import mwittmann.checkgraph.graphvalidator2.DslCommands.{DslCommand, DslState, DslStateData, MatchedPath, matchEdge, matchVertex}
import mwittmann.checkgraph.Example.wrappedDriver
import org.specs2.mutable.Specification

import utils.TestDriver

class DslCompilerSpec extends Specification {

  "compiler" should {
    "work for a simple example" in {
      val driver = TestDriver.wrappedDriver

      // Create test data
      val aUid = UUID.randomUUID()
      val bUid = UUID.randomUUID()
      val cUid = UUID.randomUUID()

      val graphLabel = s"G_${Random.alphanumeric.take(20).mkString}"

      val q =
        s"""
           |CREATE (a :A :$graphLabel { uid: '${aUid.toString}' })
           |  -[:RELATES_TO]-> (b :B :$graphLabel { uid: '${bUid.toString}' })
           |  -[:RELATES_TO]-> (c :C :$graphLabel { uid: '${cUid.toString}' })
           |RETURN a, b, c
       """.stripMargin
      driver.tx(q)

      // Program to test
      val program: Free[DslCommand, (MatchedPath, MatchedPath)] =
        for {

          v1  <- matchVertex(Set("A"), Map("uid" -> N4jUid(aUid)))
          v2  <- matchVertex(Set("B"), Map("uid" -> N4jUid(bUid)))
          a   <- matchEdge(v1, v2, Set("RELATES_TO"))

          v3  <- matchVertex(Set("C"), Map("uid" -> N4jUid(cUid)))
          b   <- matchEdge(a.vertices.last, v3, Set("RELATES_TO"))
        } yield (a, b)

      // Run test program, check result
      val result = CheckGraph.check(graphLabel, program)
      println(result)
      val resultValue: (DslStateData, (MatchedPath, MatchedPath)) = result.right.get.right.get

      val (MatchedPath(path1), MatchedPath(path2)) = resultValue._2

      path1.map(_.uid) mustEqual List(aUid, bUid)
      path2.map(_.uid) mustEqual List(bUid, cUid)
    }
  }
}
