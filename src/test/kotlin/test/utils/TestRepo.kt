package test.utils

import app.domain.entities.Author
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.StringWriter

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import java.util.*

/**
 * A wrapper around Git repo allowing to add/remove/edit files and make commits.
 */
class TestRepo(val repoPath: String) {
    val userName = "Contributor"
    val userEmail = "test@sourcerer.com"

    val git = initGit()

    init {
        val config = git.repository.config
        config.setString("user", null, "name", userName)
        config.setString("user", null, "email", userEmail)
        config.save()
    }

    private fun initGit(): Git {
        destroy()
        return Git.init().setDirectory(File(repoPath)).call()
    }

    fun createFile(fileName: String, content: List<String>) {
        val file = File("$repoPath/$fileName")
        file.parentFile?.mkdirs()
        val writer = BufferedWriter(FileWriter(file))
        for (line in content) {
            writer.write(line)
            writer.newLine()
        }
        writer.close()
        git.add().addFilepattern(fileName).call()
    }

    fun deleteFile(fileName: String) {
        File("$repoPath/$fileName").delete()
    }

    fun insertLines(fileName: String, insIndex: Int, insLines: List<String>) {
        val file = File("$repoPath/$fileName")
        val reader = BufferedReader(FileReader(file))

        val tmpStrWriter = StringWriter()
        val tmpWriter = BufferedWriter(tmpStrWriter)

        var lineIdx = 0
        for (line in reader.lines()) {
            if (lineIdx == insIndex) {
                for (insLine in insLines) {
                    tmpWriter.write(insLine)
                    tmpWriter.newLine()
                }
            }
            tmpWriter.write(line)
            tmpWriter.newLine()
            lineIdx++
        }

        if (lineIdx == insIndex) {
            for (insLine in insLines) {
                tmpWriter.write(insLine)
                tmpWriter.newLine()
            }
        }

        tmpWriter.flush()

        val writer = FileWriter(file)
        writer.write(tmpStrWriter.toString())
        writer.close()
    }

    fun deleteLines(fileName: String, startIndex: Int, endIndex: Int) {
        val file = File("$repoPath/$fileName")
        val reader = BufferedReader(FileReader(file))

        val tmpStrWriter = StringWriter()
        val tmpWriter = BufferedWriter(tmpStrWriter)

        var lineIdx = 0
        for (line in reader.lines()) {
            if (lineIdx < startIndex || lineIdx > endIndex) {
                tmpWriter.write(line)
                tmpWriter.newLine()
            }
            lineIdx++
        }
        tmpWriter.flush()

        val writer = FileWriter(file)
        writer.write(tmpStrWriter.toString())
        writer.close()
    }

    fun commit(message: String,
               author: Author = Author(userName, userEmail),
               date: Date = Date(),
               timeZone: TimeZone = TimeZone.getDefault()): RevCommit {
        val personIdent = PersonIdent(author.name, author.email, date, timeZone)
        return git.commit().setMessage(message).setAll(true)
                  .setAuthor(personIdent).setCommitter(personIdent).call()
    }

    fun destroy() {
        Runtime.getRuntime().exec("rm -r $repoPath").waitFor()
    }
}
