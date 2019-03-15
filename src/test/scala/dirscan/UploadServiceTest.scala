package dirscan

import java.nio.file.Path

import scala.concurrent.duration._

class UploadServiceTest extends BaseSpec {

  "UploadService.uploadFilesToWorkDir" should {
    "move files which match a pattern into a working directory" in withTestDir { dir =>
      val service = serviceForDir(dir)
      service.watchDir.resolve("someid__meh.txt").text = "meh"
      val anotherFile = service.watchDir.resolve("anotherId__meh.txt").text = "meh2"
      service.watchDir.resolve("someid.ready").text = "i'm ready"
      service.watchDir.resolve("someid__run.sh").text = "go!"
      val (workDir, moved) = service.uploadFilesToWorkDir("someid")

      withClue(dir.renderTree) {
        moved.map(_.fileName) should contain only("meh.txt", "run.sh")
        workDir.children.length shouldBe 2
        service.uploadDir.children.map(_.fileName) should contain only ("someid")
        service.watchDir.children.map(_.fileName) should contain only("uploads", anotherFile.fileName)
      }
    }
  }
  "UploadService.run" should {
    "invoke scripts in a unique directory once a <ready> file has been uploaded" in withTestDir { watchDir =>

      Given("A Running Service")
      val service = serviceForDir(watchDir)
      val future = service.start(DiffStream.implicits.ioScheduler)

      try {
        When("We upload a series of files with the unique id 'foo'")
        watchDir.resolve("foo__file1.txt").text = "hello"
        watchDir.resolve("foo__file2.txt").text = " world"
        watchDir.resolve("foo__runme.sh").text = "cat file1.txt file2.txt > output.txt"
        watchDir.resolve("foo.ready").text = "runme.sh"

        Then("A 'foo' directory should be created which contains the files and the run script should've executed")
        val outputTxt = service.uploadDir.resolve("foo/output.txt")
        eventually {
          outputTxt.exists() shouldBe true
        }

        outputTxt.text shouldBe "hello world"

      } finally {

        future.cancel()
      }
    }
  }

  def serviceForDir(watchDir: Path): UploadService = {
    val uploadDir = watchDir.resolve("uploads")
    UploadService.fromRootConfig().copy(watchDir = watchDir,
      uploadDir = uploadDir,
      createUploadDirIfNotPresent = true,
      pollFrequency = 300.millis)
  }
}
