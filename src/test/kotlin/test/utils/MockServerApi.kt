package test.utils

import app.application.ports.ApiError
import app.application.ports.Result
import app.application.ports.ServerApiPort
import app.domain.entities.*
import app.domain.valueobjects.Process
import app.domain.valueobjects.ProcessEntry

/**
 * Mock implementation of ServerApiPort for testing.
 */
class MockServerApi(
    var mockUser: User = User(),
    var mockRepo: Repo = Repo(),
    var mockProcessEntries: List<ProcessEntry> = listOf()
) : ServerApiPort {

    var receivedRepos: MutableList<Repo> = mutableListOf()
    var receivedAddedCommits: MutableList<Commit> = mutableListOf()
    var receivedFacts: MutableList<Fact> = mutableListOf()
    var receivedAuthors: MutableList<Author> = mutableListOf()
    var receivedUsers: MutableList<User> = mutableListOf()
    var receivedDeletedCommits: MutableList<Commit> = mutableListOf()
    var receivedDistances: MutableList<AuthorDistance> = mutableListOf()

    override fun authorize(): Result<Unit> = Result()

    override fun getUser(): Result<User> = Result(mockUser)

    override fun postUser(user: User): Result<Unit> {
        receivedUsers.add(user)
        return Result()
    }

    override fun postRepo(repo: Repo): Result<Repo> {
        receivedRepos.add(repo)
        return Result(mockRepo)
    }

    override fun postCommits(commitsList: List<Commit>): Result<Unit> {
        receivedAddedCommits.addAll(commitsList)
        return Result()
    }

    override fun deleteCommits(commitsList: List<Commit>): Result<Unit> {
        receivedDeletedCommits.addAll(commitsList)
        return Result()
    }

    override fun postFacts(factsList: List<Fact>): Result<Unit> {
        receivedFacts.addAll(factsList)
        return Result()
    }

    override fun postAuthors(authorsList: List<Author>): Result<Unit> {
        receivedAuthors.addAll(authorsList)
        return Result()
    }

    override fun postProcessCreate(requestNumEntries: Int): Result<Process> {
        return Result(Process(entries = mockProcessEntries))
    }

    override fun postProcess(processEntries: List<ProcessEntry>): Result<Unit> {
        return Result()
    }

    override fun postAuthorDistances(authorDistanceList: List<AuthorDistance>): Result<Unit> {
        receivedDistances.addAll(authorDistanceList)
        return Result()
    }
}
