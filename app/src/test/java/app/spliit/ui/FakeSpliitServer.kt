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
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    private val answers = ConcurrentHashMap<String, String>()
    private val recorded = Collections.synchronizedList(mutableListOf<String>())

    /** Answers `procedure` (e.g. `groups.list`) with [payload], wrapped in the superjson envelope. */
    fun answer(procedure: String, payload: String) {
        answers[procedure] = envelope(payload)
    }

    /** Every procedure called, in order. */
    fun calls(): List<String> = recorded.toList()

    fun callsTo(procedure: String): Int = recorded.count { it == procedure }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val procedure = request.url.encodedPath.substringAfter("/api/trpc/")
        recorded += procedure

        val body = answers[procedure]
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
