package de.playlisteq.app

import android.app.Application
import de.playlisteq.app.core.EqCoordinator
import de.playlisteq.app.core.Store
import de.playlisteq.app.spotify.SpotifyClient

class PlaylistEqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        SpotifyClient.init(this)
        EqCoordinator.start()
    }
}
