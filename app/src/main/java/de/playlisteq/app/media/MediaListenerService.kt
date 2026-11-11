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
            queueTitle = c.queueTitle?.toString(),
            playing = c === active,
        )
    }
}
