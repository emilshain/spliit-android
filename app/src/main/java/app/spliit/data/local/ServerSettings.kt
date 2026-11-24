package app.spliit.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.spliit.domain.ServerInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.serverDataStore by preferencesDataStore(name = "server")

/**
 * The instance this install points at by default.
 *
 * Per-group overrides live on the saved group, as they do in the iOS app; this is the fallback
 * for groups that carry no address, and the default a fresh install starts on.
 */
class ServerSettings(private val dataStore: DataStore<Preferences>) {

    /** The app's own store: the one DataStore the process shares, keyed by name. */
    constructor(context: Context) : this(context.serverDataStore)

    val baseUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[BASE_URL] ?: ServerInstance.DEFAULT_BASE_URL
    }

    /**
     * Stores [raw] after normalising it.
     *
     * @return false when the input is not an address, leaving the previous value alone.
     */
    suspend fun setBaseUrl(raw: String): Boolean {
        val normalized = ServerInstance.normalize(raw) ?: return false
        dataStore.edit { preferences ->
            preferences[BASE_URL] = normalized
        }
        return true
    }

    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(BASE_URL)
        }
    }

    private companion object {
        val BASE_URL: Preferences.Key<String> = stringPreferencesKey("base_url")
    }
}
