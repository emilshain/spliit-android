package app.spliit.data.remote

import app.spliit.data.model.CategoriesResponse
import app.spliit.data.model.ExpensesListResponse
import app.spliit.data.model.GroupResponse
import app.spliit.domain.ServerInstance
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Talks to the real `spliit.app`, so the client, its superjson envelope and its decoding are
 * proven against the server rather than only against recorded fixtures.
 *
 * Read-only on purpose: `groups.get` and `groups.expenses.list` for an ID that does not exist
 * answer with a well-formed empty result, which exercises the whole path without writing
 * anything to somebody else's instance. No group is created and no expense is touched.
 *
 * Skipped — not failed — when the instance cannot be reached, so the suite still runs on a
 * plane, in CI without egress, or behind a firewall.
 */
class SpliitAppIntegrationTest {

    private val client = TrpcClient(ServerInstance.DEFAULT_BASE_URL)

    @Before
    fun skipWhenOffline() {
        assumeTrue(
            "spliit.app is unreachable from this machine; skipping the live integration test",
            REACHABLE,
        )
    }

    /**
     * `categories.list` takes no input at all, so this is the one call that proves the
     * no-query-string path and the `{"result":{"data":{"json":…}}}` unwrapping by itself.
     */
    @Test
    fun `categories list decodes the server's real catalogue`() = runBlocking {
        val response: CategoriesResponse = client.call(SpliitApi.categories())

        assertTrue("the server returned no categories", response.categories.isNotEmpty())
        val general = response.categories.first { it.id == 0 }
        assertEquals("Uncategorized", general.grouping)
        assertEquals("General", general.name)
        assertTrue(
            "expected the Food and Drink grouping, got " +
                response.categories.map { it.grouping }.distinct(),
            response.categories.any { it.grouping == "Food and Drink" },
        )
    }

    /**
     * An unknown ID is how the app discovers a group was deleted, so the server's `null` has to
     * arrive as `null` and not as a decoding failure. It also proves the input envelope survives
     * the query string — a bare payload here would be rejected.
     */
    @Test
    fun `groups get answers null for an id that does not exist`() = runBlocking {
        val response: GroupResponse = client.call(SpliitApi.group(UNKNOWN_GROUP_ID))

        assertNull("a group should not exist under a probe ID", response.group)
    }

    /**
     * The expense list for a group that does not exist: the server still answers a page, with
     * the cursor advanced by the page size. That is enough to prove the procedure name, the
     * query encoding of `groupId`/`cursor`/`limit`, and the page decoding — none of which the
     * fixtures can prove about the live server.
     */
    @Test
    fun `an expense list answers an empty page for an unknown group`() = runBlocking {
        val response: ExpensesListResponse =
            client.call(SpliitApi.expenses(groupId = UNKNOWN_GROUP_ID, cursor = 0, limit = 20))

        assertTrue("expected no expenses, got ${response.expenses.map { it.title }}", response.expenses.isEmpty())
        assertEquals(false, response.hasMore)
        assertEquals("nextCursor should advance by the page size", 20, response.nextCursor)
    }

    /**
     * A tRPC error has to arrive as [TrpcServerException] with the server's own code, not as a
     * generic failure. `groups.get` with a malformed input is the cheapest way to ask for one:
     * the schema rejects an empty group ID before the procedure ever runs.
     */
    @Test
    fun `a rejected input surfaces as a tRPC error with a code`() = runBlocking {
        val failure = runCatching { client.call(SpliitApi.group("")) }.exceptionOrNull()

        assertTrue("expected a TrpcServerException, got $failure", failure is TrpcServerException)
        val server = failure as TrpcServerException
        // Observed from spliit.app: Zod rejects the empty ID before the procedure runs, and the
        // tRPC layer reports it as HTTP 400 / BAD_REQUEST naming the procedure.
        assertEquals("BAD_REQUEST", server.code)
        assertEquals(400, server.httpStatus)
        assertEquals("groups.get", server.path)
    }

    private companion object {
        /** Shape of a real group ID, but one no group has. */
        const val UNKNOWN_GROUP_ID = "spliitandroidprobe0001"

        /**
         * Probed once per JVM. Any failure — DNS, TLS, timeout, a proxy refusing the call —
         * means "offline" as far as this test is concerned.
         */
        val REACHABLE: Boolean by lazy {
            // An explicit switch, so CI can skip these without having to break its network
            // (and so the skip path itself is testable): `-Dspliit.skipLive=true`.
            if (System.getProperty("spliit.skipLive") == "true") return@lazy false

            val probe = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()
            try {
                probe.newCall(
                    Request.Builder()
                        .url("${ServerInstance.DEFAULT_BASE_URL}/api/trpc/categories.list")
                        .get()
                        .build(),
                ).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            } finally {
                probe.dispatcher.executorService.shutdown()
                probe.connectionPool.evictAll()
            }
        }
    }
}
