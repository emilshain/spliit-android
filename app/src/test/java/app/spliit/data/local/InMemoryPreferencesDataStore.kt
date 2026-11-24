package app.spliit.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A `DataStore<Preferences>` held in memory, for tests.
 *
 * Implements the same interface the repository writes through, including the atomic
 * read-modify-write `updateData` contract, so `edit` behaves here exactly as it does against
 * the real file-backed store — no Context, no Robolectric, no temp files.
 */
class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = flow { emitAll(state) }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        val updated = transform(state.value)
        state.value = updated
        updated
    }

    /** Writes a raw value, so a test can simulate a corrupt or legacy file. */
    suspend fun putRaw(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        updateData { it.toMutablePreferences().apply { set(key, value) } }
    }
}
