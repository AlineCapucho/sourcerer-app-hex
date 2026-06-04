package app.infrastructure.hashers

import app.domain.entities.Author
import app.domain.entities.Fact
import app.domain.entities.Repo
import app.domain.services.FactCodes
import app.domain.services.MetaHashingService

/**
 * Adapter: Implements MetaHashingService.
 *
 * Calculates repository-level meta facts including team size (with author
 * deduplication using 3-gram Jaccard similarity), commit share per user,
 * and average commit share.
 */
class MetaHasherAdapter : MetaHashingService {

    override fun calculateMetaFacts(repoRehash: String,
                                    authors: Set<Author>,
                                    commitsCount: Map<String, Int>,
                                    userEmails: List<String>): List<Fact> {
        val serverRepo = Repo(rehash = repoRehash)

        // Sometimes contributors use multiple emails to contribute to single
        // project, as we don't know exactly who is who (except current user),
        // let's at least filter authors by similarity.
        val otherAuthors = authors.filter { author ->
            !userEmails.contains(author.email)
        }
        // Current user may not be a contributor of repo.
        val isUserAuthor = otherAuthors.size < authors.size
        val numAuthors = getAuthorsNum(otherAuthors) +
            if (isUserAuthor) 1 else 0

        val facts = mutableListOf<Fact>()

        // Repository facts: team size.
        facts.add(FactCodes.REPO_TEAM_SIZE, 0, numAuthors, serverRepo)

        // Repository facts: commit share.
        val numAllCommits = commitsCount.values.fold(0) { acc, i -> acc + i }
        val avgCommits = Math.round(numAllCommits.toDouble() / numAuthors)
            .toInt()
        facts.add(FactCodes.COMMIT_SHARE_REPO_AVG, 0, avgCommits, serverRepo)

        if (isUserAuthor) {
            val numUserCommits = userEmails
                .mapNotNull { email -> commitsCount[email] }
                .fold(0) { acc, i -> acc + i }
            val userEmail = userEmails.first()
            facts.add(FactCodes.COMMIT_SHARE, 0, numUserCommits, serverRepo,
                userEmail)
        }

        return facts
    }

    private fun getAuthorsNum(authors: List<Author>): Int {
        val names = authors.map { it.name }
        val emails = authors.map { it.email.split("@")[0] }
        val namesQgrams = names.map { getThreegrams(it) }
        val emailsQgrams = emails.map { getThreegrams(it) }

        val results = Array(authors.size) { Array(authors.size) { 0 } }

        for (i in 0..authors.size - 2) {
            for (j in i + 1 until authors.size) {
                if (isSameAuthor(namesQgrams[i], namesQgrams[j])) {
                    results[j][i] = 1
                }
                if (isSameAuthor(emailsQgrams[i], emailsQgrams[j])) {
                    results[j][i] = 1
                }
            }
        }

        return results.filter { it.sum() == 0 }.size
    }

    private fun isSameAuthor(firstThreegrams: Set<String>,
                             secondThreegrams: Set<String>): Boolean {
        val intersectionSize = firstThreegrams.intersect(secondThreegrams).size
        val unionSize = firstThreegrams.union(secondThreegrams).size
        if (unionSize == 0) return false
        val jaccardValue = intersectionSize.toFloat() / unionSize
        return jaccardValue >= 0.3
    }

    private fun getThreegrams(str: String): Set<String> {
        val threegrams = mutableSetOf<String>()
        for (i in 0..str.length - 3) {
            threegrams.add(listOf(str[i], str[i + 1], str[i + 2])
                .joinToString(""))
        }
        return threegrams
    }

    private fun MutableList<Fact>.add(code: Int, key: Int, value: Any,
                                      repo: Repo, email: String? = null) {
        val fact = if (email != null) {
            Fact(repo, code, key, value.toString(), Author(email = email))
        } else {
            Fact(repo, code, key, value.toString())
        }
        this.add(fact)
    }
}
