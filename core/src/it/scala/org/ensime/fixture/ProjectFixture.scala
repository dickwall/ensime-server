package org.ensime.fixture

import org.scalatest._
import akka.testkit._

import akka.pattern.{ AskTimeoutException, Patterns }
import org.ensime.api._
import org.ensime.core._

import scala.collection.immutable.ListMap
import scala.concurrent.Await

object ProjectFixture extends Matchers {
  private[fixture] def startup(
    implicit
    testkit: TestKitFix,
    config: EnsimeConfig
  ): (TestActorRef[Project], TestProbe) = {
    import testkit._

    val probe = TestProbe()
    probe.ignoreMsg {
      // these are too noisy for tests
      case e: SendBackgroundMessageEvent => true
      case e: DebugOutputEvent => true
      case ClearAllScalaNotesEvent => true
      case ClearAllJavaNotesEvent => true
    }

    val project = TestActorRef[Project](Project(probe.ref), "project")

    project ! ConnectionInfoReq
    expectMsg(ConnectionInfo())

    if (config.scalaLibrary.isEmpty)
      probe.expectMsg(Broadcaster.Persist(IndexerReadyEvent))
    else
      probe.receiveN(3) should contain only (
        Broadcaster.Persist(AnalyzerReadyEvent),
        Broadcaster.Persist(FullTypeCheckCompleteEvent),
        Broadcaster.Persist(IndexerReadyEvent)
      )

    (project, probe)
  }
}

trait ProjectFixture {
  /**
   * the project actor and a probe that receives async messages.
   */
  def withProject(
    testCode: (TestActorRef[Project], TestProbe) => Any
  )(
    implicit
    testkit: TestKitFix,
    config: EnsimeConfig
  ): Any
}

trait IsolatedProjectFixture extends ProjectFixture {
  override def withProject(testCode: (TestActorRef[Project], TestProbe) => Any)(implicit testkit: TestKitFix, config: EnsimeConfig): Any = {
    val (project, probe) = ProjectFixture.startup
    testCode(project, probe)
  }
}

trait SharedProjectFixture extends ProjectFixture
    with SharedEnsimeConfigFixture
    with SharedTestKitFixture {

  private var _project: TestActorRef[Project] = _
  private var _probe: TestProbe = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    implicit val testkit = _testkit
    implicit val config = _config
    val (project, probe) = ProjectFixture.startup
    _project = project
    _probe = probe
  }

  override def withProject(testCode: (TestActorRef[Project], TestProbe) => Any)(implicit testkit: TestKitFix, config: EnsimeConfig): Any =
    testCode(_project, _probe)
}
