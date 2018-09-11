package mwittmann.checkgraph.graphvalidator

import java.util.UUID
import scala.util.Random

import cats.free.Free
import mwittmann.checkgraph.graphvalidator.CheckGraph.ProgramResult
import mwittmann.checkgraph.graphvalidator.N4jUid
import mwittmann.checkgraph.graphvalidator.DslCommands.{DslCommand, DslState, DslStateData, MatchedPath, edge, vertex}
import org.specs2.mutable.Specification
import DslCommands._
import mwittmann.checkgraph.utils.CatchError

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

          v1  <- vertex(Set("A"), Map("uid" -> N4jUid(aUid)))
          v2  <- vertex(Set("B"), Map("uid" -> N4jUid(bUid)))
          a   <- edge(v1, v2, Set("RELATES_TO"))

          v3  <- vertex(Set("C"), Map("uid" -> N4jUid(cUid)))
          b   <- edge(a.vertices.last, v3, Set("RELATES_TO"))
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
           |  -[:RELATES_TO_AB]-> (b1 :B :$graphLabel { uid: '${b1Uid.toString}' })
           |  -[:RELATES_TO_BC]-> (c1 :C :$graphLabel { uid: '${c1Uid.toString}' }),
           |(a1)
           |  -[:RELATES_TO_AB]-> (b2 :B :$graphLabel { uid: '${b2Uid.toString}' })
           |  -[:RELATES_TO_BC]-> (c2 :C :$graphLabel { uid: '${c2Uid.toString}' })
           |RETURN a1, b1, c1, b2, c2
       """.stripMargin

      CatchError.catchError(driver.tx(q))

      // Program to test
      val program: Free[DslCommand, (MatchedPath, MatchedPath)] =
        for {
          p1 <- vertex(Set("A"), Map("uid" -> N4jUid(a1Uid))) -->
            vertex(Set("B"), Map.empty)  -->
            vertex(Set("C"), Map("uid" -> N4jUid(c1Uid)))

          p2 <- p1.first -"RELATES_TO_AB"->
            vertex(Set("B"), Map.empty) -"RELATES_TO_BC"->
            vertex(Set("C"), Map("uid" -> N4jUid(c2Uid)))
        } yield (p1, p2)

      // Run test program, check result
      val result: ProgramResult[(MatchedPath, MatchedPath)] = CheckGraph.check(graphLabel, program)
      val resultValue = getValue(result)
      val (MatchedPath(path1), MatchedPath(path2)) = resultValue._2

      path1.map(_.uid) mustEqual List(a1Uid, b1Uid, c1Uid)
      path2.map(_.uid) mustEqual List(a1Uid, b2Uid, c2Uid)
    }
  }

  def getValue[S](result: ProgramResult[S]): (DslStateData, S) = {
    result must beRight
    val r1: ErrorOr[(DslStateData, S)] = result.right.get
    r1 must beRight
    r1.right.get
  }
}
