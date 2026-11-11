package de.playlisteq.app.core

import de.playlisteq.app.model.NowPlaying
import de.playlisteq.app.model.SpotifyContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Flüchtiger Laufzeit-Zustand (wird nicht gespeichert). */
object LiveState {
    /** Was gerade abgespielt wird (aus den System-Media-Sessions). */
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** Offene Audio-Sessions: Session-ID -> Paketname der App. */
    val sessions = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** Aktuelle Spotify-Playlist laut Web API. */
    val spotify = MutableStateFlow<SpotifyContext?>(null)

    /** Session-ID -> Name des angewendeten Profils. */
    val applied = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** Profil, das für die gerade spielende App/Playlist gilt. */
    val currentProfileName = MutableStateFlow<String?>(null)

    /** Gesetzt, wenn gerade empfohlene Songs nach dieser Playlist laufen. */
    val followUp = MutableStateFlow<String?>(null)

    /** Warum das aktuelle Profil gilt (Anzeige im Tab „Jetzt“). */
    val reason = MutableStateFlow<String?>(null)

    val engineError = MutableStateFlow<String?>(null)
    val spotifyStatus = MutableStateFlow("Nicht verbunden")

    fun addSession(id: Int, pkg: String) = sessions.update { it + (id to pkg) }
    fun removeSession(id: Int) = sessions.update { it - id }
}
