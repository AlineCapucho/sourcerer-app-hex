package app.infrastructure.cli

import app.domain.entities.LocalRepo
import app.infrastructure.config.Container
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Add repository dialog console UI state.
 */
class AddRepoState(
    private val context: ConsoleContext,
    private val container: Container
) : ConsoleState {

    override fun doAction() {
        if (container.configurator.getLocalRepos().isNotEmpty()) return

        while (true) {
            container.logger.print("")
            container.logger.print("Type one or more paths to repository. You can specify " +
                "multiple repository paths separated by space on the same line.")
            container.logger.print("If you finished specifying repositories, just hit 'Enter' to continue.")
            val pathsString = readLine() ?: ""

            if (pathsString.isEmpty()) {
                if (container.configurator.getLocalRepos().isEmpty()) {
                    container.logger.print("Add at least one valid repository.")
                } else {
                    container.logger.print("Finished processing git repositories")
                    break
                }
            } else {
                val paths = pathsString.split(' ')
                paths.forEach { pathStr ->
                    val path = toAbsolutePath(pathStr)
                    if (container.gitRepository.isValidRepo(path.toString())) {
                        processPath(path)
                    } else {
                        Files.walk(path)
                            .filter { p -> isGitDir(p) }
                            .forEach { p -> processPath(p.parent) }
                    }
                }
            }
        }

        container.logger.info("config/setup") { "Config setup" }
    }

    private fun processPath(path: Path) {
        if (container.gitRepository.isValidRepo(path.toString())) {
            container.logger.print("Added git repository at $path.")
            val localRepo = LocalRepo(path.toString())
            localRepo.hashAllContributors = confirm(
                "Do you want to hash commits of all contributors?",
                defaultIsYes = true)
            container.configurator.addLocalRepoPersistent(localRepo)
            container.configurator.saveToFile()
            container.logger.print("Successfully processed $path")
        } else {
            container.logger.warn { "No valid git repository found at specified path $path" }
            container.logger.print("Make sure that master branch with at least one commit exists.")
        }
    }

    override fun next() {
        context.changeState(EmailState(context, container))
    }

    private fun toAbsolutePath(pathStr: String): Path {
        val substitutePath = if (pathStr.startsWith("~/")) {
            System.getProperty("user.home") + pathStr.substring(1)
        } else pathStr
        return Paths.get(substitutePath).toAbsolutePath().normalize()
    }

    private fun isGitDir(p: Path): Boolean {
        return Files.isDirectory(p) &&
            p.fileName.toString().equals(".git", ignoreCase = true)
    }

    private fun confirm(message: String, defaultIsYes: Boolean): Boolean {
        val yes = if (defaultIsYes) "Y" else "y"
        val no = if (!defaultIsYes) "N" else "n"
        container.logger.print("$message [$yes/$no]")
        val oppositeDefaultValue = if (defaultIsYes) no else yes
        return if ((readLine() ?: "").toLowerCase() == oppositeDefaultValue) {
            !defaultIsYes
        } else defaultIsYes
    }
}
