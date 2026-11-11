package de.playlisteq.app.core

import android.content.Context
import android.content.SharedPreferences
import de.playlisteq.app.model.AppSettings
import de.playlisteq.app.model.EqProfile
import de.playlisteq.app.model.Preset
import de.playlisteq.app.model.Rule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Dauerhafte Daten: Profile, Regeln, Einstellungen. */
object Store {
    private lateinit var prefs: SharedPreferences

    val profiles = MutableStateFlow<List<EqProfile>>(emptyList())
    val rules = MutableStateFlow<List<Rule>>(emptyList())
    val settings = MutableStateFlow(AppSettings())

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playlisteq", Context.MODE_PRIVATE)
        profiles.value = readList("profiles") { EqProfile.fromJson(it) }
        rules.value = readList("rules") { Rule.fromJson(it) }
        settings.value = prefs.getString("settings", null)
            ?.let { runCatching { AppSettings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: AppSettings()

        if (profiles.value.isEmpty()) {
            profiles.value = listOf(
                EqProfile.fromPreset("Neutral", Preset.FLAT, 16),
                EqProfile.fromPreset("Bass-Boost", Preset.BASS, 16),
                EqProfile.fromPreset("Stimme / Podcast", Preset.VOCAL, 16),
                EqProfile.fromPreset("Loudness", Preset.V_SHAPE, 32),
            )
            saveProfiles()
        }
    }

    private fun <T> readList(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { parse(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun saveProfiles() =
        prefs.edit().putString("profiles", JSONArray(profiles.value.map { it.toJson() }).toString()).apply()

    private fun saveRules() =
        prefs.edit().putString("rules", JSONArray(rules.value.map { it.toJson() }).toString()).apply()

    // ---- Profile ----
    fun upsertProfile(p: EqProfile) {
        profiles.update { list -> if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p }
        saveProfiles()
    }

    fun updateProfile(id: String, transform: (EqProfile) -> EqProfile) {
        profiles.update { list -> list.map { if (it.id == id) transform(it) else it } }
        saveProfiles()
    }

    fun deleteProfile(id: String) {
        profiles.update { list -> list.filterNot { it.id == id } }
        saveProfiles()
        rules.update { list -> list.filterNot { it.profileId == id } }
        saveRules()
        if (settings.value.defaultProfileId == id) updateSettings { it.copy(defaultProfileId = null) }
    }

    // ---- Regeln ----
    fun upsertRule(r: Rule) {
        rules.update { list -> if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r }
        saveRules()
    }

    fun deleteRule(id: String) {
        rules.update { list -> list.filterNot { it.id == id } }
        saveRules()
    }

    // ---- Einstellungen ----
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings.update(transform)
        prefs.edit().putString("settings", settings.value.toJson().toString()).apply()
    }
}
