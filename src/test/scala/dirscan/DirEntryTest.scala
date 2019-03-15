package dirscan

import java.nio.file.Files
import java.nio.file.attribute.FileTime

class DirEntryTest extends BaseSpec {
  "DirEntry.modifiedOrNew" should {
    "contain files whose last modified date has changed" in withTestDir { dir =>
      def ls = DirEntry.entriesInDir(dir)

      Given("Some initial directory entries")
      val originalFiles@List(a, _, _) = (1 to 3).map(i => dir.resolve(s"file$i.txt").text = i.toString).toList

      val before = ls
      before.map(_.fileName) should contain theSameElementsAs originalFiles.map(_.fileName)

      Then("modifiedOrNew should return no elements when the contents are unchanged")
      DirEntry.modifiedOrNew(before, ls) shouldBe (empty)

      When("a file's last modified time is updated")
      val newLastMod: FileTime = FileTime.fromMillis(a.lastModifiedMillis + 1000)
      Files.setLastModifiedTime(a, newLastMod)
      ls.find(_.fileName == a.fileName).map(_.lastModified) should contain(newLastMod.toMillis)

      Then("modifiedOrNew should include that changed file")
      DirEntry.modifiedOrNew(before, ls).map(_.value.fileName) should contain only (a.fileName)
    }
  }

}
