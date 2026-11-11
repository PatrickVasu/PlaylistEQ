package de.playlisteq.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Frequenzraster für 8, 16 oder 32 Bänder (logarithmisch verteilt, 31 Hz – 16 kHz). */
object Bands {
    val ALLOWED = listOf(8, 16, 32)
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    private const val F_LOW = 31.5
    private const val F_HIGH = 16000.0

    fun centers(n: Int): FloatArray =
        FloatArray(n) { i -> (F_LOW * (F_HIGH / F_LOW).pow(i.toDouble() / (n - 1))).toFloat() }

    /** Obere Grenzfrequenz jedes Bands (so erwartet es DynamicsProcessing). */
    fun cutoffs(n: Int): FloatArray {
        val c = centers(n)
        return FloatArray(n) { i -> if (i == n - 1) 20000f else sqrt(c[i] * c[i + 1]) }
    }

    fun label(f: Float): String =
        if (f >= 1000f) {
            val k = f / 1000f
            if (k >= 10f) "${k.roundToInt()}k" else String.format(Locale.US, "%.1fk", k)
        } else "${f.roundToInt()}"

    /** Rechnet eine Kurve auf eine andere Bandanzahl um (Interpolation auf log. Frequenzachse). */
    fun resample(gains: List<Float>, newCount: Int): List<Float> {
        if (gains.size == newCount) return gains
        val src = centers(gains.size).map { ln(it) }
        val dst = centers(newCount)
        return List(newCount) { j ->
            val x = ln(dst[j])
            val v = when {
                x <= src.first() -> gains.first()
                x >= src.last() -> gains.last()
                else -> {
                    var i = 0
                    while (src[i + 1] < x) i++
                    val t = (x - src[i]) / (src[i + 1] - src[i])
                    gains[i] + (gains[i + 1] - gains[i]) * t
                }
            }
            (v * 2f).roundToInt() / 2f
        }
    }
}

enum class Preset(val title: String, private val curve: (Float) -> Float) {
    FLAT("Neutral", { 0f }),
    BASS("Bass-Boost", { f -> 7f / (1f + (f / 110f).pow(2)) }),
    TREBLE("Höhen", { f -> val x = (f / 5000f).pow(2); 6f * x / (1f + x) }),
    VOCAL("Stimme", { f ->
        val l = ln(f / 1800f)
        4.5f * exp(-(l * l) / 0.8f) - 1.5f / (1f + (f / 120f).pow(2))
    }),
    V_SHAPE("Loudness (V)", { f ->
        val x = (f / 7000f).pow(2)
        6f / (1f + (f / 100f).pow(2)) + 5f * x / (1f + x)
    });

    fun gains(n: Int): List<Float> =
        Bands.centers(n).map { c -> ((curve(c) * 2f).roundToInt() / 2f).coerceIn(Bands.MIN_DB, Bands.MAX_DB) }
}

data class EqProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gains: List<Float>,
    val preamp: Float = 0f,
) {
    val bandCount: Int get() = gains.size
    fun signature(): String = "$id|$preamp|${gains.joinToString(",")}"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("preamp", preamp.toDouble())
        .put("gains", JSONArray(gains.map { it.toDouble() }))

    companion object {
        fun fromJson(o: JSONObject): EqProfile {
            val arr = o.getJSONArray("gains")
            return EqProfile(
                id = o.getString("id"),
                name = o.optString("name", "Profil"),
                gains = List(arr.length()) { arr.getDouble(it).toFloat() },
                preamp = o.optDouble("preamp", 0.0).toFloat(),
            )
        }

        fun fromPreset(name: String, preset: Preset, bands: Int) =
            EqProfile(name = name, gains = preset.gains(bands))
    }
}

enum class MatchField(val title: String, val hint: String) {
    PLAYLIST("Playlist", "Playlist-Name (oder Spotify-Playlist-Link)"),
    ALBUM("Album", "Album-Name"),
    ARTIST("Künstler", "Künstler-Name"),
    TITLE("Titel", "Song- oder Videotitel"),
    APP("Ganze App", ""),
}

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String = "",
    val appLabel: String = "",
    val field: MatchField = MatchField.PLAYLIST,
    val pattern: String = "",
    val contains: Boolean = false,
    val profileId: String = "",
    /** Profil bleibt aktiv, wenn nach der Playlist empfohlene Songs / Autoplay weiterlaufen. */
    val includeFollowUps: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("follow", includeFollowUps)
        .put("pkg", packageName)
        .put("label", appLabel)
        .put("field", field.name)
        .put("pattern", pattern)
        .put("contains", contains)
        .put("profile", profileId)

    companion object {
        fun fromJson(o: JSONObject) = Rule(
            id = o.getString("id"),
            packageName = o.optString("pkg"),
            appLabel = o.optString("label"),
            field = runCatching { MatchField.valueOf(o.optString("field")) }.getOrDefault(MatchField.PLAYLIST),
            pattern = o.optString("pattern"),
            contains = o.optBoolean("contains", false),
            profileId = o.optString("profile"),
            includeFollowUps = o.optBoolean("follow", false),
        )
    }
}

data class AppSettings(
    val enabled: Boolean = true,
    val globalMode: Boolean = false,
    val defaultProfileId: String? = null,
    val spotifyClientId: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("global", globalMode)
        .put("default", defaultProfileId ?: "")
        .put("spotifyClientId", spotifyClientId)

    companion object {
        fun fromJson(o: JSONObject) = AppSettings(
            enabled = o.optBoolean("enabled", true),
            globalMode = o.optBoolean("global", false),
            defaultProfileId = o.optString("default").ifBlank { null },
            spotifyClientId = o.optString("spotifyClientId"),
        )
    }
}

/** Was gerade läuft – gelesen aus den Media-Sessions des Systems. */
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val queueTitle: String?,
    /** Wiedergabe-Kontext, den die App mitschickt (bei Spotify: …extra.context_title). */
    val contextTitle: String? = null,
    val playing: Boolean,
    /** Alle Rohdaten, die die App meldet (für die Ansicht „Technische Details“). */
    val details: List<Pair<String, String>> = emptyList(),
)

/** Wiedergabe-Kontext laut Spotify Web API (z. B. die Playlist). */
data class SpotifyContext(
    val type: String?,
    val uri: String?,
    val name: String?,
)
