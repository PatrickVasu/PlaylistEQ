package de.playlisteq.app.core

import de.playlisteq.app.audio.EqEngine
import de.playlisteq.app.model.AppSettings
import de.playlisteq.app.model.EqProfile
import de.playlisteq.app.model.MatchField
import de.playlisteq.app.model.NowPlaying
import de.playlisteq.app.model.Rule
import de.playlisteq.app.model.SpotifyContext
import de.playlisteq.app.spotify.SpotifyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Entscheidet, welches Profil für welche App/Playlist gilt, und steuert die Engine. */
object EqCoordinator {
    const val SPOTIFY_PKG = "com.spotify.music"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = EqEngine()
    private var started = false

    private data class Config(val settings: AppSettings, val profiles: List<EqProfile>, val rules: List<Rule>)
    private data class Snapshot(
        val cfg: Config,
        val np: NowPlaying?,
        val sessions: Map<Int, String>,
        val sp: SpotifyContext?,
    )

    fun start() {
        if (started) return
        started = true

        val cfgFlow = combine(Store.settings, Store.profiles, Store.rules) { s, p, r -> Config(s, p, r) }
        scope.launch {
            combine(cfgFlow, LiveState.nowPlaying, LiveState.sessions, LiveState.spotify) { c, n, se, sp ->
                Snapshot(c, n, se, sp)
            }.collect { refresh(it) }
        }

        // Spotify: bei jedem Titelwechsel die aktuelle Playlist über die Web API nachschlagen
        scope.launch {
            val trackKey = LiveState.nowPlaying
                .map { np -> np?.takeIf { it.packageName == SPOTIFY_PKG }?.let { "${it.title}|${it.artist}" } }
                .distinctUntilChanged()
            combine(trackKey, SpotifyClient.connectedTick) { key, _ -> key }.collectLatest { key ->
                if (key == null || !SpotifyClient.isConnected()) return@collectLatest
                delay(700)
                val ctx = withContext(Dispatchers.IO) { runCatching { SpotifyClient.fetchContext() }.getOrNull() }
                if (ctx != null) LiveState.spotify.value = ctx
            }
        }
    }

    private fun refresh(snap: Snapshot) {
        val s = snap.cfg.settings
        if (!s.enabled) {
            engine.releaseAll()
            LiveState.applied.value = emptyMap()
            LiveState.currentProfileName.value = null
            LiveState.engineError.value = null
            return
        }
        val desired = HashMap<Int, EqProfile?>()
        if (s.globalMode) {
            desired[0] = resolve(snap.np?.packageName, snap)
        } else {
            snap.sessions.forEach { (id, pkg) -> desired[id] = resolve(pkg, snap) }
        }
        engine.sync(desired)
        LiveState.applied.value = desired.mapValues { it.value?.name ?: "Aus" }
        LiveState.currentProfileName.value = resolve(snap.np?.packageName, snap)?.name
    }

    /**
     * Reihenfolge: 1. passende Playlist/Album/Künstler/Titel-Regel der App,
     * 2. "Ganze App"-Regel, 3. Standardprofil.
     */
    private fun resolve(pkg: String?, snap: Snapshot): EqProfile? {
        val byId = snap.cfg.profiles.associateBy { it.id }
        if (pkg != null) {
            val appRules = snap.cfg.rules.filter { it.packageName == pkg }
            val np = snap.np?.takeIf { it.packageName == pkg }
            if (np != null) {
                appRules.filter { it.field != MatchField.APP }
                    .firstOrNull { matches(it, np, snap.sp) }
                    ?.let { r -> byId[r.profileId]?.let { return it } }
            }
            appRules.firstOrNull { it.field == MatchField.APP }
                ?.let { r -> byId[r.profileId]?.let { return it } }
        }
        return snap.cfg.settings.defaultProfileId?.let { byId[it] }
    }

    private fun matches(rule: Rule, np: NowPlaying, sp: SpotifyContext?): Boolean {
        val candidates: List<String> = when (rule.field) {
            MatchField.PLAYLIST -> playlistCandidates(np, sp)
            MatchField.ALBUM -> listOfNotNull(np.album)
            MatchField.ARTIST -> listOfNotNull(np.artist)
            MatchField.TITLE -> listOfNotNull(np.title)
            MatchField.APP -> return true
        }
        val pattern = normalizePattern(rule.pattern)
        if (pattern.isEmpty()) return false
        return candidates.any { c ->
            val v = c.trim().lowercase()
            if (rule.contains) v.contains(pattern) else v == pattern
        }
    }

    private fun playlistCandidates(np: NowPlaying, sp: SpotifyContext?): List<String> {
        val list = mutableListOf<String>()
        if (np.packageName == SPOTIFY_PKG && sp?.type == "playlist") {
            sp.name?.let { list += it }
            sp.uri?.let { list += it }
        }
        np.queueTitle?.let { list += it }
        return list
    }

    /** Erlaubt auch Spotify-Links: open.spotify.com/playlist/ID?si=… -> spotify:playlist:id */
    private fun normalizePattern(raw: String): String {
        val p = raw.trim()
        val marker = "open.spotify.com/playlist/"
        val idx = p.indexOf(marker)
        if (idx >= 0) {
            val id = p.substring(idx + marker.length).substringBefore("?").substringBefore("/")
            return "spotify:playlist:$id".lowercase()
        }
        return p.lowercase()
    }

    /** Playlist-Name zur Anzeige / zum Anlegen einer Regel. */
    fun playlistOf(np: NowPlaying, sp: SpotifyContext?): String? {
        if (np.packageName == SPOTIFY_PKG && sp?.type == "playlist") {
            return sp.name ?: sp.uri
        }
        return np.queueTitle?.takeIf { it.isNotBlank() }
    }
}
