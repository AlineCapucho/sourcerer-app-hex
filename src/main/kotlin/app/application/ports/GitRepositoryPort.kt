package app.application.ports

import app.domain.entities.Author
import app.domain.entities.Commit
import app.domain.entities.Repo
import app.domain.services.CommitPathData
import io.reactivex.Observable

/**
 * Port for accessing Git repositories.
 * This is an output port — abstracts all Git operations so the application
 * layer does not depend on JGit or any specific Git library.
 */
interface GitRepositoryPort {

    /**
     * Checks if the given path is a valid Git repository.
     */
    fun isValidRepo(path: String): Boolean

    /**
     * Fetches all commit rehashes and authors from the repository history.
     * Returns: (rehashes list, authors set, commits count per email)
     */
    fun fetchRehashesAndAuthors(repoPath: String):
        Triple<List<String>, HashSet<Author>, HashMap<String, Int>>

    /**
     * Returns an observable stream of commits for hashing.
     * @param repoPath path to the local git repository
     * @param repo server repo with known commits
     * @param filteredEmails emails to filter commits by (null = all)
     * @param extractCoauthors whether to extract co-authors from commit messages
     */
    fun getCommitsObservable(repoPath: String,
                             repo: Repo,
                             filteredEmails: HashSet<String>? = null,
                             extractCoauthors: Boolean = false): Observable<Commit>

    /**
     * Parses the git config of a repository to extract author and remote info.
     * Updates the provided localRepo in place.
     */
    fun parseGitConfig(repoPath: String,
                       localRepo: app.domain.entities.LocalRepo)

    /**
     * Returns commit path data for calculating author distances.
     * Each entry contains (email, paths modified, timestamp).
     */
    fun getCommitPathData(repoPath: String): List<CommitPathData>
}
