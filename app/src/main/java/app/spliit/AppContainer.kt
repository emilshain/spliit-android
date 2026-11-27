package app.spliit

import android.content.Context
import app.spliit.data.local.AppearanceSettings
import app.spliit.data.local.ConnectivityMonitor
import app.spliit.data.local.ExpenseOutbox
import app.spliit.data.local.GroupCache
import app.spliit.data.local.RecentGroupsRepository
import app.spliit.data.local.ServerSettings
import app.spliit.data.model.GroupSummary
import app.spliit.data.remote.TrpcClient
import app.spliit.domain.ServerInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.Call
import java.util.concurrent.ConcurrentHashMap

/**
 * The composition root: the stores the app reads from, and the clients it talks through.
 *
 * A client belongs to an instance rather than to the app, and there is deliberately no "the
 * app's client": every request belongs to a group, and sending one to the wrong server is a
 * group that appears not to exist. They are cached because building one allocates an HTTP
 * stack, and there are only ever a handful of instances.
 */
class AppContainer(
    context: Context,
    /** The on-disk stores, overridable so a test can give a screen an in-memory one. */
    recentGroupStore: RecentGroupsRepository = RecentGroupsRepository(context.applicationContext),
    serverSettingsStore: ServerSettings = ServerSettings(context.applicationContext),
    /** How the app should look: following the phone, or light/dark as chosen on the home screen. */
    appearanceSettingsStore: AppearanceSettings = AppearanceSettings(context.applicationContext),
    /** Where HTTP goes: the default client in the app, a canned instance in a test. */
    private val callFactory: Call.Factory = TrpcClient.defaultCallFactory,
    /** The last good read of each group, so its page opens with no network. */
    val groupCache: GroupCache = GroupCache(context.applicationContext),
    /** Expense changes that could not be sent yet, kept until an instance answers. */
    val outbox: ExpenseOutbox = ExpenseOutbox(context.applicationContext),
    /** When a network comes back, so the backlog can go without reopening a screen. */
    val connectivity: ConnectivityMonitor = ConnectivityMonitor(context.applicationContext),
) {

    val serverSettings = serverSettingsStore
    val appearance = appearanceSettingsStore
    val recentGroups = recentGroupStore

    private val clients = ConcurrentHashMap<String, TrpcClient>()

    /**
     * The group details the home list last got back, so returning to it shows the counts and
     * dates it showed before while the instances are asked again. A cache of a read, not a
     * store: nothing is written to disk and losing it costs one round trip.
     */
    var lastGroupSummaries: Map<String, GroupSummary> = emptyMap()

    /** Where a group with no address of its own is taken to be, and where new ones start. */
    val defaultInstanceUrl: Flow<String> = serverSettings.baseUrl

    suspend fun defaultInstance(): String = serverSettings.baseUrl.first()

    /** A client for an instance named directly: a group being created or added from a link. */
    fun client(instanceUrl: String): TrpcClient {
        val normalized = ServerInstance.normalize(instanceUrl) ?: instanceUrl.trimEnd('/')
        return clients.getOrPut(normalized) { TrpcClient(normalized, callFactory) }
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
