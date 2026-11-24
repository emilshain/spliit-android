package app.spliit

import android.content.Context
import app.spliit.data.local.RecentGroupsRepository
import app.spliit.data.local.ServerSettings
import app.spliit.data.remote.TrpcClient
import app.spliit.domain.ServerInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/**
 * The composition root: the stores the app reads from, and the clients it talks through.
 *
 * A client belongs to an instance rather than to the app, and there is deliberately no "the
 * app's client": every request belongs to a group, and sending one to the wrong server is a
 * group that appears not to exist. They are cached because building one allocates an HTTP
 * stack, and there are only ever a handful of instances.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val serverSettings = ServerSettings(appContext)
    val recentGroups = RecentGroupsRepository(appContext)

    private val clients = ConcurrentHashMap<String, TrpcClient>()

    /** Where a group with no address of its own is taken to be, and where new ones start. */
    val defaultInstanceUrl: Flow<String> = serverSettings.baseUrl

    suspend fun defaultInstance(): String = serverSettings.baseUrl.first()

    /** A client for an instance named directly: a group being created or added from a link. */
    fun client(instanceUrl: String): TrpcClient {
        val normalized = ServerInstance.normalize(instanceUrl) ?: instanceUrl.trimEnd('/')
        return clients.getOrPut(normalized) { TrpcClient(normalized) }
    }

    /** The instance a group is on: the address stored with it, or the default. */
    suspend fun instanceForGroup(groupId: String): String =
        recentGroups.instanceUrl(groupId) ?: defaultInstance()

    /** A client for the instance a group is on. */
    suspend fun clientForGroup(groupId: String): TrpcClient = client(instanceForGroup(groupId))

    /**
     * Remembers where the last group was deliberately created, so the form opens there next
     * time. Only the create form calls this: adding a group somebody else made says nothing
     * about where this person makes theirs.
     */
    suspend fun noteInstanceUsedForNewGroup(instanceUrl: String) {
        serverSettings.setBaseUrl(instanceUrl)
    }

    /**
     * The instances worth offering in the create form: everywhere this list already has a
     * group, the default, and spliit.app — which is where most people are, whatever else is
     * here.
     */
    suspend fun knownInstances(): List<String> {
        val fallback = defaultInstance()
        val seen = LinkedHashSet<String>()
        seen += fallback
        for (group in recentGroups.snapshot()) seen += group.instanceURL ?: fallback
        seen += ServerInstance.DEFAULT_BASE_URL
        return seen.toList()
    }

    /**
     * Everything that has to happen once, before the first screen reads a store.
     *
     * A group written by an older build (or restored from elsewhere) carries no address; filling
     * it in now means changing the default can never move somebody's groups out from under them.
     */
    suspend fun prepare() {
        recentGroups.stampInstances(defaultInstance())
    }
}
