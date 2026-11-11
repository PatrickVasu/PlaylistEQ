package de.playlisteq.app.media

import android.content.Context
import android.content.Intent

object AppInfo {
    data class App(val pkg: String, val label: String)

    /** Bekannte Musik-Apps erscheinen in der Auswahl ganz oben. */
    private val KNOWN_MEDIA = listOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.google.android.youtube",
        "app.revanced.android.apps.youtube.music",
        "app.revanced.android.youtube",
        "org.videolan.vlc",
        "com.maxmpz.audioplayer",
        "in.krosbits.musicolet",
        "com.kodarkooperativet.blackplayerex",
        "com.apple.android.music",
        "deezer.android.app",
        "com.aspiro.tidal",
        "com.amazon.mp3",
        "com.soundcloud.android",
    )

    private val cache = HashMap<String, String>()

    fun label(context: Context, pkg: String): String = cache.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    @Suppress("DEPRECATION")
    fun launchable(context: Context): List<App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedWith(
                compareBy<App> { KNOWN_MEDIA.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenBy { it.label.lowercase() }
            )
    }
}
