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

        val body = answers[procedure]
            ?: handlers[procedure]?.invoke(request)?.let { envelope(it) }
        val status = if (body == null) 404 else 200
        val text = body ?: """{"error":{"json":{"message":"No answer for $procedure"}}}"""

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
