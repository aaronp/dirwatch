package dirscan

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.StrictLogging
import monix.execution.{Cancelable, CancelableFuture, ExecutionModel, Scheduler}
import monix.reactive.{Observable, OverflowStrategy, Pipe}

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Random

object BackPressureExample extends App with StrictLogging {


  case class Result(time: Long, started: Long, done: Long) {
    override def toString = {
      s"$time took ${done - started}ms"
    }
  }

  def runJob(input: Long): Result = {
    logger.info("Running " + input)
    println("Running " + input)
    val now = System.currentTimeMillis
    val millis = (Random.nextInt() % 5000).abs + 1000
    Thread.sleep(millis)
    Result(input, now, System.currentTimeMillis)
  }

  def emitter(every: FiniteDuration)(implicit s: Scheduler): (Cancelable, Observable[Long]) = {
    val counter = new AtomicLong(0)
    val (input, output) = Pipe.behavior(counter.incrementAndGet).unicast
    var lastTime = System.currentTimeMillis

    val cancel = s.scheduleWithFixedDelay(every, every) {
      val newLastTime = System.currentTimeMillis
      println(s"TICK (after ${newLastTime - lastTime}ms)")
      lastTime = newLastTime
      input.onNext(counter.incrementAndGet)
    }
    cancel -> output
  }

  def run() = {

    implicit val sched = Scheduler.computation(10, executionModel = ExecutionModel.AlwaysAsyncExecution)


    //    val everySecond: Observable[Long] = Observable.interval(500.millis)
    val (_, source) = emitter(500.millis)


    val res: CancelableFuture[Unit] = {
      val x: Observable[Long] = source.dump("tick").asyncBoundary(OverflowStrategy.DropOld(2))
      x.map(runJob).dump("output").foreach { r =>
        println(r)
      }
    }

    println(res)
    println("waiting...")
    Await.result(res, 30.seconds)
    println("done")
  }

  run()
}

