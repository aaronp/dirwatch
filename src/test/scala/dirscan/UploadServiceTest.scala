package dirscan

class UploadServiceTest extends BaseSpec {

  "UploadService.run" should {
    "invoke scripts in a unique directory once a <ready> file has been uploaded" in withTestDir { watchDir =>

      UploadService().copy(watchDir = watchDir, uploadDir = watchDir.resolve("uploads"))
    }
  }
}
