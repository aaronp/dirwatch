package dirwatch

import java.nio.file.Path

import scala.concurrent.duration._

class UploadServiceTest extends BaseSpec {

  "UploadService.uploadFilesToWorkDir" should {
    "move files which match a pattern into a working directory" in withService { service =>
      service.uploadDir.resolve("someid__meh.txt").text = "meh"
      val anotherFile = service.uploadDir.resolve("anotherId__meh.txt").text = "meh2"
      service.uploadDir.resolve("someid.ready").text = "i'm ready"
      service.uploadDir.resolve("someid__run.sh").text = "go!"
      val (workDir, moved) = service.uploadFilesToWorkDir("someid")

      withClue(service.uploadDir.renderTree) {
        moved.map(_.fileName) should contain only("meh.txt", "run.sh")
        workDir.children.length shouldBe 2
        service.execDir.children.map(_.fileName) should contain only ("someid")
        service.uploadDir.children.map(_.fileName) should contain only("uploads", anotherFile.fileName)
      }
    }
  }
  "UploadService.run" should {
    "keep running after scripts error" in withService { service =>

      Given("A running service")

      When("An script which errors is uploaded")
      service.uploadDir.resolve("bang__run.sh").text = "exit 123"
      service.uploadDir.resolve("bang.ready").text = ""

      And("We try to upload another script")

      Then("The new script should succeed")
    }
    "invoke scripts in a unique directory once a <ready> file has been uploaded" in withService { service =>

      Given("A running service")
      val watchDir = service.uploadDir

      When("We upload a series of files with the unique id 'foo'")
      watchDir.resolve("foo__file1.txt").text = "hello"
      watchDir.resolve("foo__file2.txt").text = " world"
      watchDir.resolve("foo__runme.sh").text = "cat file1.txt file2.txt > output.txt"
      watchDir.resolve("foo.ready").text = "runme.sh"

      Then("A 'foo' directory should be created which contains the files and the run script should've executed")
      val outputTxt = service.execDir.resolve("foo/output.txt")
      eventually {
        outputTxt.exists() shouldBe true
      }

      outputTxt.text shouldBe "hello world"
    }
  }

  def withService(test: UploadService => Unit) = withTestDir { watchDir =>
    val service: UploadService = serviceForDir(watchDir)
    val future = service.start(DiffStream.implicits.ioScheduler)
    try {
      test(service)
    } finally {
      future.cancel()
    }
  }

  def serviceForDir(watchDir: Path): UploadService = {
    val uploadDir = watchDir.resolve("uploads")
    UploadService.fromRootConfig().copy(uploadDir = watchDir,
      execDir = uploadDir,
      createUploadDirIfNotPresent = true,
      pollFrequency = 300.millis)
  }
}
