package dirscan

import concurrent.Await
import concurrent.duration._

class UploadServiceTest extends BaseSpec {

  "UploadService.run" should {
    "invoke scripts in a unique directory once a <ready> file has been uploaded" in withTestDir { watchDir =>

      Given("A Running Service")
      val uploadDir = watchDir.resolve("uploads")
      val service = UploadService.fromRootConfig().copy(watchDir = watchDir,
        uploadDir = uploadDir,
        createUploadDirIfNotPresent = true,
        pollFrequency = 300.millis)
      val future = service.start(DiffStream.implicits.ioScheduler)

      When("We upload a series of files with the unique id 'foo'")
      watchDir.resolve("foo__file1.txt").text = "hello"
      watchDir.resolve("foo__file2.txt").text = "world"
      watchDir.resolve("foo__runme.sh").text = "cat file1.txt file2.txt > output.txt"
      watchDir.resolve("foo.ready").text = "runme.sh"

      Then("A 'foo' directory should be created which contains the files and the run script should've executed")
      val outputTxt = uploadDir.resolve("output.txt")
      eventually {
        outputTxt.exists() shouldBe true
      }

      outputTxt.text shouldBe "hello world"

      future.cancel()
      Await.result(future, testTimeout)
    }
  }
}
