package app.infrastructure.api

import app.BuildConfig
import app.application.ports.*
import app.domain.entities.*
import app.domain.valueobjects.Process
import app.domain.valueobjects.ProcessEntry
import app.infrastructure.serialization.ProtobufMapper
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.google.protobuf.InvalidProtocolBufferException
import java.security.InvalidParameterException

/**
 * Adapter: Implements ServerApiPort using Fuel HTTP client and Protobuf serialization.
 * This is an output adapter — communicates with the remote Sourcerer server.
 */
class HttpServerApiAdapter(
    private val configurator: ConfigurationPort
) : ServerApiPort {

    companion object {
        private val HEADER_VERSION_CODE = "app-version-code"
        private val HEADER_CONTENT_TYPE = "Content-Type"
        private val HEADER_CONTENT_TYPE_PROTO = "application/octet-stream"
        private val HEADER_COOKIE = "Cookie"
        private val HEADER_SET_COOKIE = "Set-Cookie"
        private val KEY_TOKEN = "Token="
    }

    val fuelManager = FuelManager()
    private var token = ""

    private fun cookieRequestInterceptor() = { req: Request ->
        if (token.isNotEmpty()) {
            req.header(Pair(HEADER_COOKIE, KEY_TOKEN + token))
        }
        req
    }

    private fun cookieResponseInterceptor() = { _: Request, res: Response ->
        val newToken = res.headers[HEADER_SET_COOKIE]
            ?.find { it.startsWith(KEY_TOKEN) }
        if (newToken != null && newToken.isNotBlank()) {
            token = newToken.substringAfter(KEY_TOKEN).substringBefore(';')
        }
        res
    }

    init {
        fuelManager.basePath = BuildConfig.API_BASE_PATH
        fuelManager.addRequestInterceptor { cookieRequestInterceptor() }
        fuelManager.addResponseInterceptor { cookieResponseInterceptor() }
    }

    private val username
        get() = configurator.getUsername()

    private val password
        get() = configurator.getPassword()

    private fun post(path: String): Request {
        return fuelManager.request(Method.POST, path)
    }

    private fun get(path: String): Request {
        return fuelManager.request(Method.GET, path)
    }

    private fun delete(path: String): Request {
        return fuelManager.request(Method.DELETE, path)
    }

    private fun getVersionCodeHeader(): Pair<String, String> {
        return Pair(HEADER_VERSION_CODE, BuildConfig.VERSION_CODE.toString())
    }

    private fun getContentTypeHeader(): Pair<String, String> {
        return Pair(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_PROTO)
    }

    private fun <T> makeRequest(request: Request,
                                requestName: String,
                                parser: (ByteArray) -> T): Result<T> {
        var error: ApiError? = null
        var data: T? = null

        try {
            val (_, res, result) = request.responseString()
            val (_, e) = result
            if (e == null) {
                data = parser(res.data)
            } else {
                error = ApiError(e)
            }
        } catch (e: InvalidProtocolBufferException) {
            error = ApiError(e)
        } catch (e: InvalidParameterException) {
            error = ApiError(e)
        }

        return Result(data, error)
    }

    override fun authorize(): Result<Unit> {
        val request = post("/auth").authenticate(username, password)
            .header(getVersionCodeHeader())
        return makeRequest(request, "getToken", {})
    }

    override fun getUser(): Result<User> {
        val request = get("/user")
        return makeRequest(request, "getUser",
            { body -> ProtobufMapper.deserializeUser(body) })
    }

    override fun postUser(user: User): Result<Unit> {
        val request = post("/user").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeUser(user))
        return makeRequest(request, "postUser", {})
    }

    override fun postRepo(repo: Repo): Result<Repo> {
        if (repo.rehash.isBlank()) {
            throw IllegalArgumentException()
        }
        val request = post("/repo").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeRepo(repo))
        return makeRequest(request, "getRepo",
            { body -> ProtobufMapper.deserializeRepo(body) })
    }

    override fun postCommits(commitsList: List<Commit>): Result<Unit> {
        val request = post("/commits").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeCommitGroup(commitsList))
        return makeRequest(request, "postCommits", {})
    }

    override fun deleteCommits(commitsList: List<Commit>): Result<Unit> {
        val request = delete("/commits").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeCommitGroup(commitsList))
        return makeRequest(request, "deleteCommits", {})
    }

    override fun postFacts(factsList: List<Fact>): Result<Unit> {
        val request = post("/facts").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeFactGroup(factsList))
        return makeRequest(request, "postFacts", {})
    }

    override fun postAuthors(authorsList: List<Author>): Result<Unit> {
        val request = post("/authors").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeAuthorGroup(authorsList))
        return makeRequest(request, "postAuthors", {})
    }

    override fun postProcessCreate(requestNumEntries: Int): Result<Process> {
        val process = app.domain.valueobjects.Process(requestNumEntries = requestNumEntries)
        val request = post("/process/create").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeProcess(process))
        return makeRequest(request, "postProcessCreate",
            { body -> ProtobufMapper.deserializeProcess(body) })
    }

    override fun postProcess(processEntries: List<ProcessEntry>): Result<Unit> {
        val process = app.domain.valueobjects.Process(entries = processEntries)
        val request = post("/process").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeProcess(process))
        return makeRequest(request, "postProcess", {})
    }

    override fun postAuthorDistances(authorDistanceList: List<AuthorDistance>): Result<Unit> {
        val request = post("/distances").header(getContentTypeHeader())
            .body(ProtobufMapper.serializeAuthorDistanceGroup(authorDistanceList))
        return makeRequest(request, "postDistances", {})
    }
}
