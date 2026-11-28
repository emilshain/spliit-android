package app.spliit.ui

import androidx.test.core.app.ApplicationProvider
import app.spliit.AppContainer
import app.spliit.data.local.InMemoryPreferencesDataStore
import app.spliit.data.local.RecentGroup
import app.spliit.data.local.RecentGroupsRepository
import app.spliit.data.local.ServerSettings
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * A Spliit instance that answers from a table of canned payloads, for UI tests.
 *
 * The screens talk to real `TrpcClient`s, so the envelope, the procedure paths and the decoding
 * are the production ones; only the network is replaced. A procedure with no answer fails as a
 * server error, the way an instance that predates it would.
 */
class FakeSpliitServer : Interceptor {

    /** One call the screens made, with its body when it had one. */
    data class Call(val procedure: String, val body: String?, val url: String)

    private val answers = ConcurrentHashMap<String, String>()
    private val handlers = ConcurrentHashMap<String, (Request) -> String?>()
    private val failures = ConcurrentHashMap<String, Int>()
    private val unreachable = Collections.synchronizedSet(mutableSetOf<String>())
    private val recorded = Collections.synchronizedList(mutableListOf<Call>())

    /** Answers `procedure` (e.g. `groups.list`) with [payload], wrapped in the superjson envelope. */
    fun answer(procedure: String, payload: String) {
        answers[procedure] = envelope(payload)
    }

    /**
     * Answers `procedure` from the request itself, for a procedure whose page depends on its
     * cursor. Return null for "this instance has no answer".
     */
    fun answerMatching(procedure: String, handler: (Request) -> String?) {
        handlers[procedure] = handler
        // The handler is the more specific answer: a page depends on its cursor, so a fixed
        // payload for the same procedure would hide it.
        answers.remove(procedure)
    }

    /** Fails `procedure` the way a server having a bad day would, rather than as a missing one. */
    fun fail(procedure: String, status: Int = 500) {
        failures[procedure] = status
    }

    /**
     * Makes `procedure` unreachable rather than refused: the call fails with the IO error a phone
     * with no signal produces, which is what the app's offline paths are written against.
     */
    fun offline(procedure: String) {
        unreachable += procedure
    }

    /** Every procedure called, in order. */
    fun calls(): List<String> = recorded.map { it.procedure }

    fun callsTo(procedure: String): Int = recorded.count { it.procedure == procedure }

    /** The URL of the most recent call to [procedure]. */
    fun lastUrl(procedure: String): String? =
        recorded.lastOrNull { it.procedure == procedure }?.url

    /** The body of the most recent call to [procedure], if it had one. */
    fun lastBody(procedure: String): String? =
        recorded.lastOrNull { it.procedure == procedure }?.body

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val procedure = request.url.encodedPath.substringAfter("/api/trpc/")
        recorded += Call(
            procedure = procedure,
            body = request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() },
            url = request.url.toString(),
        )

        if (procedure in unreachable) throw IOException("Failed to connect to $procedure")

        val failedStatus = failures[procedure]
        val body = if (failedStatus != null) {
            """{"error":{"json":{"message":"Server error for $procedure","code":-32603,
               "data":{"code":"INTERNAL_SERVER_ERROR","httpStatus":$failedStatus}}}}"""
        } else {
            answers[procedure] ?: handlers[procedure]?.invoke(request)?.let { envelope(it) }
        }
        // An instance that has never heard of a procedure answers exactly what a tRPC router
        // does, so the client's own fallback logic is what gets exercised.
        val status = when {
            failedStatus != null -> failedStatus
            body == null -> 404
            else -> 200
        }
        val text = when {
            failedStatus != null -> body.orEmpty()
            body != null -> body
            else -> """{"error":{"json":{"message":"No procedure found on path $procedure",
                      "code":-32004,"data":{"code":"NOT_FOUND","httpStatus":404,
                      "path":"$procedure"}}}}"""
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message(if (status == 200) "OK" else "Not Found")
            .body(text.toResponseBody("application/json".toMediaType()))
            .build()
    }

    /** A container whose stores live in memory and whose only server is this one. */
    fun container(): AppContainer = AppContainer(
        context = ApplicationProvider.getApplicationContext(),
        recentGroupStore = RecentGroupsRepository(InMemoryPreferencesDataStore()),
        serverSettingsStore = ServerSettings(InMemoryPreferencesDataStore()),
        callFactory = OkHttpClient.Builder().addInterceptor(this).build(),
    )
}

/** The shape every successful tRPC response takes. */
fun envelope(payload: String): String = """{"result":{"data":{"json":$payload}}}"""

/** Puts [groups] in the store, as though they had been opened before. */
fun seedGroups(repository: RecentGroupsRepository, vararg groups: RecentGroup) {
    runBlocking { groups.forEach { repository.remember(it) } }
}
