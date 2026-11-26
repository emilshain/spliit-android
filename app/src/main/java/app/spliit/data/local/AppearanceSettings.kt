package app.spliit.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/** The theme the app draws in, chosen on the home screen's settings sheet. */
enum class ThemeChoice { System, Light, Dark }

/**
 * The colour templates offered when the wallpaper is not the source of the palette: either the
 * phone cannot do adaptive colour, or the switch is off. [Spliit] is the app's own palette; the
 * rest are seed colours the M3 engine turns into a full expressive scheme.
 */
enum class AppearancePalette { Spliit, Iris, Lagoon, Ember, Meadow }

/**
 * How the app should look, remembered on this device.
 *
 * The platform's own setting is the default: until somebody picks a side the app follows the
 * phone. Picking Light or Dark overrides it for this install only — like the groups, there is
 * no account to carry it.
 */
class AppearanceSettings(private val dataStore: DataStore<Preferences>) {

    /** The app's own store: the one DataStore the process shares, keyed by name. */
    constructor(context: Context) : this(context.appearanceDataStore)

    val theme: Flow<ThemeChoice> = dataStore.data.map { preferences ->
        when (preferences[THEME]) {
            "light" -> ThemeChoice.Light
            "dark" -> ThemeChoice.Dark
            else -> ThemeChoice.System
        }
    }

    /**
     * Whether the palette follows the wallpaper, where the platform allows it. On until it is
     * turned off: a phone that offers adaptive colour is a phone whose owner expects it.
     */
    val dynamicColor: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR] ?: true
    }

    /** Which template to draw with when adaptive colour is not in use. */
    val palette: Flow<AppearancePalette> = dataStore.data.map { preferences ->
        AppearancePalette.entries.firstOrNull {
            it.name.equals(preferences[PALETTE], ignoreCase = true)
        } ?: AppearancePalette.Spliit
    }

    suspend fun setTheme(choice: ThemeChoice) {
        dataStore.edit { preferences -> preferences[THEME] = choice.name.lowercase() }
    }

    suspend fun setPalette(palette: AppearancePalette) {
        dataStore.edit { preferences -> preferences[PALETTE] = palette.name.lowercase() }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val THEME: Preferences.Key<String> = stringPreferencesKey("theme")
        val PALETTE: Preferences.Key<String> = stringPreferencesKey("palette")
        val DYNAMIC_COLOR: Preferences.Key<Boolean> = booleanPreferencesKey("dynamic_color")
    }
}
