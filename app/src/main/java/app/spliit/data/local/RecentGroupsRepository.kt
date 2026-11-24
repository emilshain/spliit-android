package app.spliit.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.spliit.data.model.Participant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.recentGroupsDataStore by preferencesDataStore(name = "recent_groups")

/**
 * The user's recent groups, most recently opened first.
 *
 * Spliit has no accounts, so this list is the only way back to a group — which is why nothing
 * here ever throws away a row it merely cannot read, and why every mutation rewrites the whole
 * small array atomically rather than patching a row.
 *
 * The iOS app mirrors this file to iCloud. Android has no equivalent, so the phone copy is the
 * only copy and there are no tombstones: a removed group is simply gone from this device.
 */
class RecentGroupsRepository(
    private val dataStore: DataStore<Preferences>,
    private val now: () -> Instant = Instant::now,
) {

    /**
     * The app's own store: the one DataStore the process shares, keyed by name on the
     * application context.
     */
    constructor(context: Context) : this(context.recentGroupsDataStore, Instant::now)

    /** All remembered groups, newest first. */
    val groups: Flow<List<RecentGroup>> = dataStore.data.map { preferences ->
        RecentGroupsLogic.ordered(RecentGroupsCodec.decode(preferences[GROUPS]))
    }

    /** The three sections the home screen shows, in the order it shows them. */
    val sections: Flow<RecentGroupSections> = groups.map { ordered ->
        RecentGroupSections(
            starred = ordered.filter { it.isStarred && !it.isArchived },
            recent = ordered.filter { !it.isStarred && !it.isArchived },
            archived = ordered.filter { it.isArchived },
        )
    }

    suspend fun snapshot(): List<RecentGroup> = groups.first()

    suspend fun remember(group: RecentGroup) {
        edit { current ->
            val existing = current.firstOrNull { it.groupId == group.groupId }
            val merged = RecentGroupsLogic.remember(existing, group, now())
            listOf(merged) + current.filterNot { it.groupId == group.groupId }
        }
    }

    suspend fun forget(groupId: String) {
        edit { current -> RecentGroupsLogic.forget(current, groupId) }
    }

    suspend fun setStarred(isStarred: Boolean, groupId: String) {
        edit { current -> RecentGroupsLogic.setStarred(current, groupId, isStarred, now()) }
    }

    suspend fun setArchived(isArchived: Boolean, groupId: String) {
        edit { current -> RecentGroupsLogic.setArchived(current, groupId, isArchived, now()) }
    }

    suspend fun setActiveParticipant(participant: ActiveParticipant, groupId: String) {
        edit { current ->
            RecentGroupsLogic.setActiveParticipant(current, groupId, participant, now())
        }
    }

    suspend fun setDefaultSplit(split: DefaultSplit, groupId: String) {
        edit { current -> RecentGroupsLogic.setDefaultSplit(current, groupId, split, now()) }
    }

    suspend fun stampInstances(url: String) {
        edit { current -> RecentGroupsLogic.stampInstances(current, url) }
    }

    suspend fun activeParticipant(groupId: String): ActiveParticipant? =
        snapshot().firstOrNull { it.groupId == groupId }?.activeParticipant

    suspend fun defaultSplit(groupId: String): DefaultSplit? =
        snapshot().firstOrNull { it.groupId == groupId }?.defaultSplit

    suspend fun instanceUrl(groupId: String): String? =
        snapshot().firstOrNull { it.groupId == groupId }?.instanceURL

    /** Who to credit in the activity log, or null when this phone has not said. */
    suspend fun actorId(groupId: String, participants: List<Participant>): String? =
        RecentGroupsLogic.actorId(snapshot(), groupId, participants)

    private suspend fun edit(change: (List<RecentGroup>) -> List<RecentGroup>) {
        dataStore.edit { preferences ->
            val current = RecentGroupsCodec.decode(preferences[GROUPS])
            preferences[GROUPS] = RecentGroupsCodec.encode(change(current))
        }
    }

    private companion object {
        val GROUPS: Preferences.Key<String> = stringPreferencesKey("groups")
    }
}

/** The home screen's three sections. Each group appears in exactly one. */
data class RecentGroupSections(
    val starred: List<RecentGroup>,
    val recent: List<RecentGroup>,
    val archived: List<RecentGroup>,
) {
    val isEmpty: Boolean get() = starred.isEmpty() && recent.isEmpty() && archived.isEmpty()
}
