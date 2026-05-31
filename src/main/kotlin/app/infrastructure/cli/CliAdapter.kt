package app.infrastructure.cli

import app.infrastructure.config.Container
import com.beust.jcommander.JCommander
import com.beust.jcommander.MissingCommandException
import com.beust.jcommander.Parameter
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Adapter: CLI entry point (input adapter).
 * Parses command-line arguments and delegates to the appropriate use case
 * or starts the interactive console UI.
 *
 * This is an input adapter — it receives commands from the user
 * and translates them into use case calls.
 */
class CliAdapter(private val container: Container) {

    fun run(argv: Array<String>) {
        container.logger.info("start") { "App started" }

        val options = CliOptions()
        val commandAdd = CommandAdd()
        val commandConfig = CommandConfig()
        val commandList = CommandList()
        val commandRemove = CommandRemove()

        val jc = JCommander.newBuilder()
            .programName("sourcerer")
            .addObject(options)
            .addCommand(commandAdd.name, commandAdd)
            .addCommand(commandConfig.name, commandConfig)
            .addCommand(commandList.name, commandList)
            .addCommand(commandRemove.name, commandRemove)
            .build()

        try {
            jc.parse(*argv)

            // Apply CLI credentials if provided.
            if (options.username.isNotEmpty()) {
                container.configurator.setUsernameCurrent(options.username)
            }
            if (options.password.isNotEmpty()) {
                container.configurator.setPasswordCurrent(options.password)
            }

            if (options.help) {
                showHelp(jc)
            } else if (options.setup) {
                doSetup()
            } else when (jc.parsedCommand) {
                commandAdd.name -> doAdd(commandAdd)
                commandConfig.name -> doConfig(commandConfig)
                commandList.name -> doList()
                commandRemove.name -> doRemove(commandRemove)
                else -> startUi()
            }
        } catch (e: MissingCommandException) {
            container.logger.warn { "No such command: ${e.unknownCommand}" }
        }

        container.logger.info("exit") { "App finished" }
    }

    private fun startUi() {
        ConsoleUi(container)
    }

    private fun doAdd(command: CommandAdd) {
        val paths = command.paths
        paths.forEach { pathStr ->
            val path = toAbsolutePath(pathStr)
            if (command.recursive) {
                Files.walk(path)
                    .filter { p -> isGitDir(p) }
                    .forEach { p ->
                        container.addRepositoryUseCase.execute(
                            p.parent.toString(), command.hashAll)
                    }
            } else {
                container.addRepositoryUseCase.execute(
                    path.toString(), command.hashAll)
            }
        }
    }

    private fun doConfig(command: CommandConfig) {
        val (key, value) = command.pair
        if (!arrayListOf("username", "password").contains(key)) {
            container.logger.warn { "No such key $key" }
            return
        }
        when (key) {
            "username" -> container.configurator.setUsernamePersistent(value)
            "password" -> container.configurator.setPasswordPersistent(value)
        }
        container.configurator.saveToFile()
        container.logger.info("config/changed") { "Config changed" }
    }

    private fun doList() {
        container.listRepositoriesUseCase.execute()
    }

    private fun doRemove(command: CommandRemove) {
        container.removeRepositoryUseCase.execute(command.path)
    }

    private fun doSetup() {
        if (!container.configurator.isFirstLaunch()) {
            if (confirm("Are you sure that you want to setup Sourcerer again?",
                    defaultIsYes = false)) {
                container.configurator.resetAndSave()
            }
        }
        startUi()
    }

    private fun showHelp(jc: JCommander) {
        container.logger.print("Sourcerer hashes your git repositories into intelligent " +
            "engineering profiles.")
        container.logger.print("If you don't have an account, please, proceed to " +
            "https://sourcerer.io/join")
        container.logger.print("More info at https://sourcerer.io and " +
            "https://github.com/sourcerer-io")
        jc.usage()
    }

    private fun toAbsolutePath(pathStr: String): java.nio.file.Path {
        val substitutePath = if (pathStr.startsWith("~/")) {
            System.getProperty("user.home") + pathStr.substring(1)
        } else pathStr
        return Paths.get(substitutePath).toAbsolutePath().normalize()
    }

    private fun isGitDir(p: java.nio.file.Path): Boolean {
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

// --- CLI Command Classes ---

class CliOptions {
    @Parameter(names = arrayOf("-u", "--username"),
        description = "Sourcerer account username", order = 0)
    var username: String = ""

    @Parameter(names = arrayOf("-p", "--password"),
        description = "Sourcerer account password", order = 1)
    var password: String = ""

    @Parameter(names = arrayOf("-h", "--help"),
        description = "List options and commands", order = 2)
    var help: Boolean = false

    @Parameter(names = arrayOf("--setup"),
        description = "Cleanup configs and run setup again", order = 3)
    var setup: Boolean = false

    @Parameter(names = arrayOf("--uninstall"),
        description = "Remove Sourcerer App", order = 4)
    var uninstall: Boolean = false
}

class CommandAdd {
    val name = "add"

    @Parameter(description = "Paths to git repositories")
    var paths: List<String> = mutableListOf()

    @Parameter(names = arrayOf("--all"),
        description = "Hash all contributors")
    var hashAll: Boolean = false

    @Parameter(names = arrayOf("-r", "--recursive"),
        description = "Recursively search for git repos")
    var recursive: Boolean = false
}

class CommandConfig {
    val name = "config"

    @Parameter(description = "Key-value pair to set")
    var pairList: List<String> = mutableListOf()

    val pair: Pair<String, String>
        get() = Pair(
            pairList.getOrElse(0) { "" },
            pairList.getOrElse(1) { "" }
        )
}

class CommandList {
    val name = "list"
}

class CommandRemove {
    val name = "remove"

    @Parameter(description = "Path to git repository to remove")
    var paths: List<String> = mutableListOf()

    val path: String?
        get() = paths.firstOrNull()
}
