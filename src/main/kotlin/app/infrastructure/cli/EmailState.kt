package app.infrastructure.cli

import app.domain.entities.User
import app.domain.valueobjects.UserEmail
import app.infrastructure.config.Container

/**
 * Email management console UI state.
 * Handles adding/confirming user emails for profile building.
 */
class EmailState(
    private val context: ConsoleContext,
    private val container: Container
) : ConsoleState {

    override fun doAction() {
        val user = container.configurator.getUser()

        if (user.emails.isNotEmpty()) {
            container.logger.print("List of your emails:", indentLine = true)
            user.emails.forEach { email -> println(email) }
        } else {
            container.logger.print("Add at least one email to build your profile.",
                indentLine = true)
        }

        val knownEmails = user.emails.map { it.email }
        val newEmails = hashSetOf<String>()
        val configEmails = hashSetOf<String>()

        // Add emails from git configs of tracked repos.
        val reposEmails = hashMapOf<String, HashSet<String>>()
        for (repo in container.configurator.getLocalRepos()) {
            try {
                val tempRepo = app.domain.entities.LocalRepo(repo.path)
                container.gitRepository.parseGitConfig(repo.path, tempRepo)
                val email = tempRepo.author.email.toLowerCase()
                if (email.isNotEmpty() && !knownEmails.contains(email)) {
                    configEmails.add(email)
                }
                // Fetch emails from repo for "no-email" warning.
                val (_, authors, _) = container.gitRepository
                    .fetchRehashesAndAuthors(repo.path)
                reposEmails[repo.path] = authors.map { it.email }.toHashSet()
            } catch (e: Exception) {
                container.logger.error(e, "Error while parsing repo")
            }
        }

        if (configEmails.isNotEmpty()) {
            container.logger.print("Your git config contains untracked emails:")
            configEmails.forEach { email -> println(email) }
            if (confirm("Do you want to add this emails to your account?",
                    defaultIsYes = true)) {
                newEmails.addAll(configEmails)
            }
        }

        // Show warning if no commits from user in some repos.
        val reposUserMissing = mutableListOf<String>()
        for (repo in container.configurator.getLocalRepos()) {
            val presentedEmails = reposEmails[repo.path]
            val updatedEmails = knownEmails + newEmails
            if (presentedEmails != null) {
                var userMissing = true
                for (email in presentedEmails) {
                    if (updatedEmails.contains(email)) {
                        userMissing = false
                        break
                    }
                }
                if (userMissing) {
                    reposUserMissing.add(repo.path)
                }
            }
        }
        if (reposUserMissing.isNotEmpty()) {
            if (reposUserMissing.size == 1) {
                container.logger.print("${reposUserMissing.first()} repo does not " +
                    "contains commits from emails you've specified")
            } else {
                container.logger.print("Following repos do not contain commits from " +
                    "emails you've specified:")
                reposUserMissing.forEach { container.logger.print(it) }
            }
        }

        // Ask user to enter additional emails.
        if (confirm("Do you want to specify additional emails that you use in repositories?",
                defaultIsYes = false)) {
            while (true) {
                container.logger.print("Type an email, or hit Enter to continue.")
                val email = (readLine() ?: "").toLowerCase()
                if (email.isBlank()) break
                if (!knownEmails.contains(email)) newEmails.add(email)
            }
        }

        if (newEmails.isNotEmpty()) {
            val newUserEmails = newEmails.map { UserEmail(email = it) }
            user.emails.addAll(newUserEmails)

            val userNewEmails = User(emails = newUserEmails.toHashSet())
            container.serverApi.postUser(userNewEmails)
        }

        // Warn user about need of confirmation.
        val unverified = user.emails.filter { !it.verified }
        if (unverified.isNotEmpty() || newEmails.isNotEmpty()) {
            container.logger.print("Confirm your emails to show all statistics in profile.")
        }
    }

    override fun next() {
        context.changeState(UpdateRepoState(context, container))
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
