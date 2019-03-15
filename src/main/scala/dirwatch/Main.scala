package dirwatch

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends StrictLogging {
  def main(args: Array[String]): Unit = {
    val service = UploadService.fromRootConfig()
    logger.info(s"Starting $service, Cntrl+C to stop")
    val fut = service.start(DiffStream.implicits.ioScheduler)
    Await.result(fut, Duration.Inf)
    logger.info("Done")
  }
}
