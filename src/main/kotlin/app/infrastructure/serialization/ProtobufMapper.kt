package app.infrastructure.serialization

import app.Protos
import app.domain.entities.*
import app.domain.valueobjects.*

/**
 * Mapper between domain entities and Protobuf messages.
 * This lives in infrastructure because Protobuf is an external detail.
 * The domain layer never touches Protos directly.
 */
object ProtobufMapper {

    // --- Commit ---
    fun toProto(commit: Commit): Protos.Commit {
        return Protos.Commit.newBuilder()
            .setRehash(commit.rehash)
            .setRepoRehash(commit.repo.rehash)
            .setTreeRehash(commit.treeRehash)
            .setAuthorName(commit.author.name)
            .setAuthorEmail(commit.author.email)
            .setDate(commit.dateTimestamp)
            .setIsQommit(commit.isQommit)
            .setNumLinesAdded(commit.numLinesAdded)
            .setNumLinesDeleted(commit.numLinesDeleted)
            .addAllStats(commit.stats.map { toProto(it) })
            .build()
    }

    fun commitFromProto(proto: Protos.Commit): Commit {
        return Commit(
            rehash = proto.rehash,
            repo = Repo(rehash = proto.repoRehash),
            treeRehash = proto.treeRehash,
            author = Author(proto.authorName, proto.authorEmail),
            dateTimestamp = proto.date,
            isQommit = proto.isQommit,
            numLinesAdded = proto.numLinesAdded,
            numLinesDeleted = proto.numLinesDeleted,
            stats = proto.statsList.map { commitStatsFromProto(it) }
        )
    }

    fun serializeCommitGroup(commits: List<Commit>): ByteArray {
        return Protos.CommitGroup.newBuilder()
            .addAllCommits(commits.map { toProto(it) })
            .build()
            .toByteArray()
    }

    // --- CommitStats ---
    fun toProto(stats: CommitStats): Protos.CommitStats {
        return Protos.CommitStats.newBuilder()
            .setNumLinesAdded(stats.numLinesAdded)
            .setNumLinesDeleted(stats.numLinesDeleted)
            .setType(stats.type)
            .setTech(stats.tech)
            .build()
    }

    fun commitStatsFromProto(proto: Protos.CommitStats): CommitStats {
        return CommitStats(
            numLinesAdded = proto.numLinesAdded,
            numLinesDeleted = proto.numLinesDeleted,
            type = proto.type,
            tech = proto.tech
        )
    }

    // --- Repo ---
    fun toProto(repo: Repo): Protos.Repo {
        return Protos.Repo.newBuilder()
            .setRehash(repo.rehash)
            .setInitialCommitRehash(repo.initialCommitRehash)
            .addAllEmails(repo.emails)
            .addAllCommits(repo.commits.map { toProto(it) })
            .setMeta(toProto(repo.meta))
            .setProcessEntryId(repo.processEntryId)
            .build()
    }

    fun repoFromProto(proto: Protos.Repo): Repo {
        return Repo(
            rehash = proto.rehash,
            initialCommitRehash = proto.initialCommitRehash,
            emails = proto.emailsList,
            commits = proto.commitsList.map { commitFromProto(it) },
            processEntryId = proto.processEntryId
        )
    }

    fun serializeRepo(repo: Repo): ByteArray {
        return toProto(repo).toByteArray()
    }

    fun deserializeRepo(bytes: ByteArray): Repo {
        return repoFromProto(Protos.Repo.parseFrom(bytes))
    }

    // --- RepoMeta ---
    fun toProto(meta: RepoMeta): Protos.RepoMeta {
        return Protos.RepoMeta.newBuilder()
            .setHosterId(meta.hosterId)
            .setService(meta.service)
            .setName(meta.name)
            .setOwnerName(meta.ownerName)
            .setDescription(meta.description)
            .setHtmlUrl(meta.htmlUrl)
            .setCloneUrl(meta.cloneUrl)
            .build()
    }

    // --- Author ---
    fun toProto(author: Author): Protos.Author {
        return Protos.Author.newBuilder()
            .setEmail(author.email)
            .setName(author.name)
            .setRepoRehash(author.repo.rehash)
            .build()
    }

    fun authorFromProto(proto: Protos.Author): Author {
        return Author(
            name = proto.name,
            email = proto.email,
            repo = Repo(rehash = proto.repoRehash)
        )
    }

    fun serializeAuthorGroup(authors: List<Author>): ByteArray {
        return Protos.AuthorGroup.newBuilder()
            .addAllAuthors(authors.map { toProto(it) })
            .build()
            .toByteArray()
    }

    // --- Fact ---
    fun toProto(fact: Fact): Protos.Fact {
        return Protos.Fact.newBuilder()
            .setRepoRehash(fact.repo.rehash)
            .setEmail(fact.author.email)
            .setCode(fact.code)
            .setKey(fact.key)
            .setValue1(fact.value)
            .setValue2(fact.value2)
            .setValue3(fact.value3)
            .build()
    }

    fun serializeFactGroup(facts: List<Fact>): ByteArray {
        return Protos.FactGroup.newBuilder()
            .addAllFacts(facts.map { toProto(it) })
            .build()
            .toByteArray()
    }

    // --- User ---
    fun userFromProto(proto: Protos.User): User {
        return User(
            repos = proto.reposList.map { repoFromProto(it) }.toMutableList(),
            emails = proto.emailsList.map { userEmailFromProto(it) }.toHashSet()
        )
    }

    fun serializeUser(user: User): ByteArray {
        return Protos.User.newBuilder()
            .addAllRepos(user.repos.map { toProto(it) })
            .addAllEmails(user.emails.map { toProto(it) })
            .build()
            .toByteArray()
    }

    fun deserializeUser(bytes: ByteArray): User {
        return userFromProto(Protos.User.parseFrom(bytes))
    }

    // --- UserEmail ---
    fun toProto(email: UserEmail): Protos.UserEmail {
        return Protos.UserEmail.newBuilder()
            .setEmail(email.email)
            .setPrimary(email.primary)
            .setVerified(email.verified)
            .build()
    }

    fun userEmailFromProto(proto: Protos.UserEmail): UserEmail {
        return UserEmail(
            email = proto.email,
            primary = proto.primary,
            verified = proto.verified
        )
    }

    // --- Process ---
    fun processFromProto(proto: Protos.Process): Process {
        return Process(
            id = proto.id,
            requestNumEntries = proto.requestNumEntries,
            entries = proto.entriesList.map { processEntryFromProto(it) }
        )
    }

    fun serializeProcess(process: Process): ByteArray {
        return Protos.Process.newBuilder()
            .setId(process.id)
            .setRequestNumEntries(process.requestNumEntries)
            .addAllEntries(process.entries.map { toProto(it) })
            .build()
            .toByteArray()
    }

    fun deserializeProcess(bytes: ByteArray): Process {
        return processFromProto(Protos.Process.parseFrom(bytes))
    }

    // --- ProcessEntry ---
    fun toProto(entry: ProcessEntry): Protos.ProcessEntry {
        return Protos.ProcessEntry.newBuilder()
            .setId(entry.id)
            .setStatus(entry.status)
            .setErrorCode(entry.errorCode)
            .build()
    }

    fun processEntryFromProto(proto: Protos.ProcessEntry): ProcessEntry {
        return ProcessEntry(
            id = proto.id,
            status = proto.status,
            errorCode = proto.errorCode
        )
    }

    // --- AuthorDistance ---
    fun toProto(distance: AuthorDistance): Protos.AuthorDistance {
        return Protos.AuthorDistance.newBuilder()
            .setRepoRehash(distance.repo.rehash)
            .setEmail(distance.email)
            .setScore(distance.score)
            .build()
    }

    fun serializeAuthorDistanceGroup(distances: List<AuthorDistance>): ByteArray {
        return Protos.AuthorDistanceGroup.newBuilder()
            .addAllAuthorDistances(distances.map { toProto(it) })
            .build()
            .toByteArray()
    }
}
