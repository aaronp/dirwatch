package dirwatch

import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import com.typesafe.scalalogging.StrictLogging
import org.junit.runner.RunWith
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers, WordSpec}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Properties

@RunWith(classOf[JUnitRunner])
abstract class BaseSpec extends WordSpec with Matchers with Eventually with BeforeAndAfterAll with GivenWhenThen with eie.io.LowPriorityIOImplicits {

  def testTimeout: FiniteDuration = 2.seconds

  private var context: ExecutorService = null

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  implicit lazy val implicitContext = {
    require(context != null)
    ExecutionContext.fromExecutorService(context)
  }

  override def beforeAll(): Unit = {
    context = BaseSpec.newThreadPool(getClass.getName)
  }

  override def afterAll(): Unit = {
    context.shutdown()
    context = null
  }

  def using[A <: AutoCloseable, T](resource: => A)(thunk: A => T): T = {
    lazy val r = resource
    try {
      thunk(r)
    } finally {
      r.close()
    }
  }

  def withTestDir[T](thunk: Path => T): T = {
    val dir = Properties.userDir.asPath.resolve("target/" + UUID.randomUUID).mkDirs()
    require(dir.isDir)
    try {
      thunk(dir)
    } finally {
      dir.delete(recursive = true)
    }
  }
}

object BaseSpec {

  def newThreadPool(name: String): ExecutorService = {
    Executors.newCachedThreadPool(new ThreadFactory with StrictLogging {
      private val counter = new AtomicInteger(0)

      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        val threadName = s"$name-${counter.incrementAndGet()}}"
        logger.info(s"Creating thread $threadName")
        t.setName(threadName)
        t
      }
    })
  }
}