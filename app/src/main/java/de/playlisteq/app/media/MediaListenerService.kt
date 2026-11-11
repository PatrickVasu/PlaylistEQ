package de.playlisteq.app.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import de.playlisteq.app.core.LiveState
import de.playlisteq.app.model.NowPlaying

/**
 * Benachrichtigungszugriff wird nur benötigt, damit Android uns die aktiven Media-Sessions verrät:
 * welche App spielt, Titel, Künstler, Album und Warteschlangen-/Playlist-Titel.
 */
class MediaListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onQueueTitleChanged(title: CharSequence?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        val cn = ComponentName(this, MediaListenerService::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            setControllers(m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            // Zugriff wurde (noch) nicht erteilt
        }
    }

    override fun onListenerDisconnected() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        setControllers(emptyList())
        super.onListenerDisconnected()
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers.clear()
        controllers.addAll(list)
        controllers.forEach { it.registerCallback(callback, handler) }
        publish()
    }

    private fun publish() {
        val active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val previousPkg = LiveState.nowPlaying.value?.packageName
        val c = active
            ?: controllers.firstOrNull { it.packageName == previousPkg }
            ?: controllers.firstOrNull()
        if (c == null) {
            LiveState.nowPlaying.value = null
            return
        }
        val md = c.metadata
        LiveState.nowPlaying.value = NowPlaying(
            packageName = c.packageName,
            appLabel = AppInfo.label(this, c.packageName),
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            queueTitle = c.queueTitle?.toString()?.trim()?.takeUnless { isGeneric(it) },
            contextTitle = findContextTitle(c, md),
            playing = c === active,
            details = collectDetails(c, md),
        )
    }

    /** Sucht Extras wie com.spotify.music.extra.context_title (Name der laufenden Playlist). */
    @Suppress("DEPRECATION")
    private fun findContextTitle(c: MediaController, md: MediaMetadata?): String? {
        val bundles = listOfNotNull(c.playbackState?.extras, c.extras)
        for (b in bundles) {
            for (k in b.keySet()) {
                if (k.endsWith("context_title", ignoreCase = true)) {
                    val v = b.get(k)?.toString()?.trim()
                    if (!v.isNullOrBlank() && !isGeneric(v)) return v
                }
            }
        }
        md?.let { m ->
            for (k in m.keySet()) {
                if (k.endsWith("context_title", ignoreCase = true)) {
                    val v = m.getText(k)?.toString()?.trim()
                    if (!v.isNullOrBlank() && !isGeneric(v)) return v
                }
            }
        }
        return null
    }

    /** Platzhalter, die manche Apps statt eines Playlist-Namens melden. */
    private fun isGeneric(t: String): Boolean =
        t.isBlank() || t.lowercase() in GENERIC_QUEUE_TITLES

    @Suppress("DEPRECATION")
    private fun collectDetails(c: MediaController, md: MediaMetadata?): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out += "Paket" to c.packageName
        out += "Warteschlangen-Titel" to (c.queueTitle?.toString() ?: "–")
        md?.let { m ->
            m.keySet().sorted().forEach { k ->
                if (k.contains("ART") || k.contains("ICON")) return@forEach
                val v = m.getText(k)?.toString()
                    ?: m.getLong(k).takeIf { it != 0L }?.toString()
                    ?: return@forEach
                out += k.removePrefix("android.media.metadata.") to v
            }
        }
        c.extras?.let { b -> b.keySet().sorted().forEach { k -> out += "extra: $k" to b.get(k).toString() } }
        c.playbackState?.extras?.let { b -> b.keySet().sorted().forEach { k -> out += "state: $k" to b.get(k).toString() } }
        c.queue?.let { q ->
            out += "Warteschlange (Anzahl)" to q.size.toString()
            q.take(3).forEachIndexed { i, item ->
                val d = item.description
                out += "Eintrag ${i + 1}" to listOfNotNull(d.title, d.subtitle, d.description).joinToString(" · ")
                d.extras?.let { b -> b.keySet().sorted().forEach { k -> out += "  extra: $k" to b.get(k).toString() } }
            }
        }
        return out
    }

    companion object {
        private val GENERIC_QUEUE_TITLES = setOf(
            "warteschlange", "queue", "now playing", "wird gerade abgespielt", "läuft gerade",
            "wiedergabe", "als nächstes", "up next", "nächste titel", "playing queue", "aktuelle wiedergabe",
        )
    }
}
