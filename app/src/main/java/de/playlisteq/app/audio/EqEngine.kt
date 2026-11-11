package de.playlisteq.app.audio

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import de.playlisteq.app.core.LiveState
import de.playlisteq.app.model.Bands
import de.playlisteq.app.model.EqProfile

/**
 * Hängt pro Audio-Session einen DynamicsProcessing-Effekt ein.
 * DynamicsProcessing erlaubt eine frei wählbare Bandanzahl – daher sind 8, 16 und 32 Bänder möglich.
 * Ein Limiter am Ende verhindert Übersteuerung bei starken Anhebungen.
 */
class EqEngine {
    private class Slot(val dp: DynamicsProcessing, val bandCount: Int, var signature: String)

    private val slots = HashMap<Int, Slot>()

    /** Bringt die laufenden Effekte auf den gewünschten Stand (Session -> Profil, null = aus). */
    fun sync(desired: Map<Int, EqProfile?>) {
        (slots.keys - desired.keys).toList().forEach { release(it) }
        var error: String? = null
        for ((session, profile) in desired) {
            try {
                apply(session, profile)
            } catch (t: Throwable) {
                Log.w(TAG, "Session $session fehlgeschlagen", t)
                release(session)
                error = if (session == 0) {
                    "Globaler Modus wird von diesem Gerät nicht unterstützt (${t.message ?: t.javaClass.simpleName})."
                } else {
                    "Session $session: ${t.message ?: t.javaClass.simpleName}"
                }
            }
        }
        LiveState.engineError.value = error
    }

    fun releaseAll() = slots.keys.toList().forEach { release(it) }

    private fun apply(session: Int, profile: EqProfile?) {
        if (profile == null) {
            release(session)
            return
        }
        val sig = profile.signature()
        val slot = slots[session]
        if (slot != null && slot.signature == sig) return

        if (slot != null && slot.bandCount == profile.bandCount) {
            // Gleiche Bandanzahl: nur Werte aktualisieren (ohne Knackser durch Neuanlage)
            val cut = Bands.cutoffs(profile.bandCount)
            profile.gains.forEachIndexed { i, g ->
                slot.dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cut[i], g))
            }
            slot.dp.setInputGainAllChannelsTo(profile.preamp)
            slot.signature = sig
            return
        }

        release(session)
        val dp = DynamicsProcessing(PRIORITY, session, buildConfig(profile))
        dp.setEnabled(true)
        slots[session] = Slot(dp, profile.bandCount, sig)
    }

    private fun release(session: Int) {
        slots.remove(session)?.let {
            runCatching { it.dp.setEnabled(false) }
            runCatching { it.dp.release() }
        }
    }

    private fun buildConfig(profile: EqProfile): DynamicsProcessing.Config {
        val n = profile.bandCount
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            true, n,      // Pre-EQ = unser grafischer EQ
            false, 0,     // kein Multiband-Kompressor
            false, 0,     // kein Post-EQ
            true,         // Limiter
        )
        val cut = Bands.cutoffs(n)
        val eq = DynamicsProcessing.Eq(true, true, n)
        for (i in 0 until n) {
            eq.setBand(i, DynamicsProcessing.EqBand(true, cut[i], profile.gains[i]))
        }
        builder.setPreEqAllChannelsTo(eq)
        builder.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f)
        )
        builder.setInputGainAllChannelsTo(profile.preamp)
        return builder.build()
    }

    companion object {
        private const val TAG = "EqEngine"
        private const val CHANNELS = 2
        private const val PRIORITY = 100
    }
}
