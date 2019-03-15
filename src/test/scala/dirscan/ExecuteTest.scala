package dirscan

import dirscan.Execute.BufferLogger


class ExecuteTest extends BaseSpec {

  "Execute.runScriptInDir" should {
    "invoke a script" in withTestDir { dir =>

      val file = dir.resolve("test.sh").text = "echo testing123"


      val logger = BufferLogger(dir, file.fileName)
      Execute.makeRunnable(file)
      Execute.runScriptInDir(dir, file.fileName, logger) shouldBe 0

      logger.stdOut shouldBe "testing123"
      logger.stdErr() should be(empty)
    }
  }
}
