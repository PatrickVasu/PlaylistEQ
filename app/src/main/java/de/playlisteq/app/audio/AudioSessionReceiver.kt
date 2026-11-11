package de.playlisteq.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import de.playlisteq.app.core.LiveState

/** Empfängt "Audio-Session geöffnet/geschlossen" von Musik-Apps. */
class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        handle(intent)
    }

    companion object {
        fun handle(intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (session <= 0) return
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unbekannt"
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
                AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL -> LiveState.addSession(session, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> LiveState.removeSession(session)
            }
        }
    }
}
