package app.infrastructure.cli

import app.BuildConfig
import app.domain.errors.HashingException
import app.domain.valueobjects.ProcessEntry
import app.infrastructure.config.Container
import java.util.*
import kotlin.concurrent.fixedRateTimer

/**
 * Update repositories console UI state.
 * Orchestrates the hashing of all tracked repositories.
 */
class UpdateRepoState(
    private val context: ConsoleContext,
    private val container: Container
) : ConsoleState {

    override fun doAction() {
        container.logger.info { "Hashing started" }

        val localRepos = container.configurator.getLocalRepos()
        assignProcess(localRepos)

        val heartbeatTimer = runHeartbeatTimer(localRepos)
        for (repo in localRepos) {
            try {
                container.logger.print("Hashing $repo repository...", indentLine = true)
                container.hashRepositoryUseCase.execute(repo)
                container.logger.print("Hashing $repo completed.")
            } catch (e: HashingException) {
                e.errors.forEach { error ->
                    container.logger.error(error, "Error while hashing")
                }
            } catch (e: Throwable) {
                container.logger.error(e, "Error while hashing")
            }
        }
        heartbeatTimer.cancel()

        container.logger.print("The repositories have been hashed.")
        container.logger.print("Take a look at the updates in your profile at " +
            BuildConfig.PROFILE_URL + container.configurator.getUsername(),
            indentLine = true)
        container.logger.info("hashing/success") { "Hashing success" }
    }

    override fun next() {
        context.changeState(CloseState(container))
    }

    private fun assignProcess(localRepos: List<app.domain.entities.LocalRepo>) {
        try {
            val process = container.serverApi
                .postProcessCreate(requestNumEntries = localRepos.size)
                .getOrThrow()
            if (process.entries.isEmpty()) return
            process.entries.subList(0, localRepos.size).forEachIndexed { index, e ->
                localRepos[index].processEntryId = e.id
            }
        } catch (e: Throwable) {
            container.logger.error(e, "Failed to create process entries")
        }
    }

    private fun runHeartbeatTimer(localRepos: List<app.domain.entities.LocalRepo>): Timer {
        val entries = localRepos
            .filter { it.processEntryId != null }
            .map { ProcessEntry(id = it.processEntryId!!) }
        return fixedRateTimer(
            name = "heartbeat",
            daemon = true,
            initialDelay = BuildConfig.HEARTBEAT_RATE,
            period = BuildConfig.HEARTBEAT_RATE,
            action = {
                try {
                    container.serverApi.postProcess(entries).onErrorThrow()
                } catch (e: Throwable) {
                    container.logger.error(e)
                }
            }
        )
    }
}
