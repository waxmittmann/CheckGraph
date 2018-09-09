package mwittmann.checkgraph.graphvalidator

import java.util.UUID
import scala.util.Random

import cats.free.Free
import mwittmann.checkgraph.graphvalidator.CheckGraph.ProgramResult
import mwittmann.checkgraph.graphvalidator.N4jUid
import mwittmann.checkgraph.graphvalidator.DslCommands.{DslCommand, DslState, DslStateData, MatchedPath, matchEdge, matchVertex}
import org.specs2.mutable.Specification
import DslCommands.MatchVertexImplicits

import utils.TestDriver

class CheckGraphSpec extends Specification {

  "check" should {
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
      val result: ProgramResult[(MatchedPath, MatchedPath)] = CheckGraph.check(graphLabel, program)
      val resultValue = getValue(result)
      val (MatchedPath(path1), MatchedPath(path2)) = resultValue._2

      path1.map(_.uid) mustEqual List(aUid, bUid)
      path2.map(_.uid) mustEqual List(bUid, cUid)
    }

    "work for an edge where we don't have a unique for the middle vertex" in {
      val driver = TestDriver.wrappedDriver

      // Create test data
      val a1Uid = UUID.randomUUID()
      val b1Uid = UUID.randomUUID()
      val b2Uid = UUID.randomUUID()
      val c1Uid = UUID.randomUUID()
      val c2Uid = UUID.randomUUID()

      val graphLabel = s"G_${Random.alphanumeric.take(20).mkString}"

      val q =
        s"""
           |CREATE (a1 :A :$graphLabel { uid: '${a1Uid.toString}' })
           |  -[:RELATES_TO]-> (b1 :B :$graphLabel { uid: '${b1Uid.toString}' })
           |  -[:RELATES_TO]-> (c1 :C :$graphLabel { uid: '${c1Uid.toString}' }),
           |(a1)
           |  -[:RELATES_TO]-> (b2 :B :$graphLabel { uid: '${b2Uid.toString}' })
           |  -[:RELATES_TO]-> (c2 :C :$graphLabel { uid: '${c2Uid.toString}' }),
           |RETURN a1, b1, c1, b2, c2
       """.stripMargin

      driver.tx(q)

      // Program to test
      val program: Free[DslCommand, (MatchedPath, MatchedPath)] =
        for {
          p1 <- matchVertex(Set("A"), Map("uid" -> N4jUid(a1Uid))) -->
            matchVertex(Set("B"), Map.empty) -->
            matchVertex(Set("C"), Map("uid" -> N4jUid(c1Uid)))

          p2 <- matchVertex(Set("A"), Map("uid" -> N4jUid(a1Uid))) -->
            matchVertex(Set("B"), Map.empty) -->
            matchVertex(Set("C"), Map("uid" -> N4jUid(c2Uid)))
        } yield (p1, p2)


    }
  }

  def getValue[S](result: ProgramResult[S]): (DslStateData, S) = {
    result must beRight
    val r1 = result.right.get
    r1 must beRight
    r1.right.get
  }
}
