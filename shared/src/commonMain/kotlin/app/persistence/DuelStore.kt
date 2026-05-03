package app.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.model.Match
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

/**
 * Single DataStore Preferences file holding both the user's last-used
 * [DuelSettings] AND the match history. Match list is JSON-encoded under one
 * key — the data is small, fits in memory, and avoiding a separate Room/SQL
 * dependency keeps the KMP setup minimal.
 *
 * Capped at [MAX_HISTORY] most-recent matches; older entries silently drop.
 *
 * Constructed via the platform [DuelStoreFactory] which handles the singleton
 * requirement (DataStore throws if you create two stores against the same file
 * within one process).
 */
class DuelStore internal constructor(filePath: String) {

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        produceFile = { filePath.toPath() }
    )

    val settings: Flow<DuelSettings> = store.data.map { prefs ->
        DuelSettings(
            startingLp = prefs[Keys.startingLp] ?: DEFAULT_LP,
            player1 = prefs[Keys.player1].orEmpty(),
            player2 = prefs[Keys.player2].orEmpty(),
        )
    }

    val matches: Flow<List<Match>> = store.data.map { prefs ->
        prefs[Keys.matchesJson]?.let { json ->
            runCatching { Json.decodeFromString<List<Match>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun saveSettings(settings: DuelSettings) {
        store.edit { prefs ->
            prefs[Keys.startingLp] = settings.startingLp
            prefs[Keys.player1] = settings.player1
            prefs[Keys.player2] = settings.player2
        }
    }

    /**
     * Insert (or replace by id) a match at the head of the history. Trims to
     * [MAX_HISTORY]. The replace-by-id path supports re-saving a still-running
     * match, though current callers only save once at duel end.
     */
    suspend fun saveMatch(match: Match) {
        store.edit { prefs ->
            val current = prefs[Keys.matchesJson]?.let {
                runCatching { Json.decodeFromString<List<Match>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = (listOf(match) + current.filterNot { it.id == match.id }).take(MAX_HISTORY)
            prefs[Keys.matchesJson] = Json.encodeToString(updated)
        }
    }

    suspend fun clearHistory() {
        store.edit { prefs -> prefs.remove(Keys.matchesJson) }
    }

    private object Keys {
        val startingLp = intPreferencesKey("starting_lp")
        val player1 = stringPreferencesKey("player1")
        val player2 = stringPreferencesKey("player2")
        val matchesJson = stringPreferencesKey("matches_json")
    }

    companion object {
        const val DEFAULT_LP = 8000
        private const val MAX_HISTORY = 200
        const val FILE_NAME = "duelistlp.preferences_pb"
    }
}

data class DuelSettings(
    val startingLp: Int = DuelStore.DEFAULT_LP,
    val player1: String = "",
    val player2: String = "",
)

/**
 * Platform factory. Android resolves against `applicationContext.filesDir`
 * (initialized from `AppActivity.onCreate`); iOS resolves the user's
 * `Documents/` directory via `NSFileManager`. Both produce a singleton store
 * at [DuelStore.FILE_NAME].
 */
expect object DuelStoreFactory {
    fun create(): DuelStore
}
