package dirscan

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.util

import dirscan.Execute.BufferLogger


class ExecuteTest extends BaseSpec {

  "Execute" should {
    "invoke a script" in withTestDir { dir =>

      val file = dir.resolve("test.sh").text = "echo testing123"
      val perms: util.Set[PosixFilePermission] = PosixFilePermissions.fromString("rwxrwxrwx")
      import scala.collection.JavaConverters._
      file.setFilePermissions(perms.asScala.toSet)

      val logger = BufferLogger(dir, file.fileName)
      Execute.invokeRunScript(dir, file.fileName, logger) shouldBe 0

      logger.stdOut shouldBe "testing123"
      logger.stdErr() should be(empty)
    }
  }
}
