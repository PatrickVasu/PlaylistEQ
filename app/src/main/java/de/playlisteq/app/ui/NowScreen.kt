package de.playlisteq.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.playlisteq.app.core.EqCoordinator
import de.playlisteq.app.core.LiveState
import de.playlisteq.app.core.Store
import de.playlisteq.app.media.AppInfo
import de.playlisteq.app.model.MatchField
import de.playlisteq.app.model.Rule

private fun listenerEnabled(ctx: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private fun notifGranted(ctx: Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowScreen(onCreateRule: (Rule) -> Unit) {
    val ctx = LocalContext.current
    val settings by Store.settings.collectAsState()
    val profiles by Store.profiles.collectAsState()
    val np by LiveState.nowPlaying.collectAsState()
    val sp by LiveState.spotify.collectAsState()
    val sessions by LiveState.sessions.collectAsState()
    val applied by LiveState.applied.collectAsState()
    val current by LiveState.currentProfileName.collectAsState()
    val error by LiveState.engineError.collectAsState()
    val followUp by LiveState.followUp.collectAsState()
    val reason by LiveState.reason.collectAsState()

    var listenerOk by remember { mutableStateOf(listenerEnabled(ctx)) }
    var notifOk by remember { mutableStateOf(notifGranted(ctx)) }
    var showDetails by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                listenerOk = listenerEnabled(ctx)
                notifOk = notifGranted(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun draft(field: MatchField, pattern: String) {
        val n = np ?: return
        onCreateRule(
            Rule(
                packageName = n.packageName,
                appLabel = n.appLabel,
                field = field,
                pattern = pattern,
                profileId = settings.defaultProfileId ?: profiles.firstOrNull()?.id.orEmpty(),
            )
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PlaylistEQ", style = MaterialTheme.typography.headlineMedium)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.enabled) "Aktives Profil: ${current ?: "Aus (keine Regel / kein Standard)"}"
                        else "Ausgeschaltet",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (settings.enabled) reason?.let {
                        Text("Grund: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = settings.enabled, onCheckedChange = { on -> Store.updateSettings { it.copy(enabled = on) } })
            }
        }

        if (!listenerOk) {
            SectionCard {
                Text("Zugriff nötig", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Damit PlaylistEQ erkennt, welche App und welche Playlist gerade läuft, braucht es den " +
                        "Benachrichtigungszugriff. Es werden keine Benachrichtigungen gelesen oder gespeichert.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("Zugriff erteilen")
                }
            }
        }
        if (!notifOk && Build.VERSION.SDK_INT >= 33) {
            SectionCard {
                Text("Hinweis-Benachrichtigung erlauben", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Der EQ läuft im Hintergrund und zeigt dafür eine dauerhafte Benachrichtigung mit dem aktiven Profil.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Erlauben") }
            }
        }

        SectionCard {
            Text("Läuft gerade", style = MaterialTheme.typography.titleMedium)
            val n = np
            if (n == null) {
                Text("Keine Wiedergabe erkannt.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val playlist = EqCoordinator.playlistOf(n, sp)
                InfoLine("App", n.appLabel + if (n.playing) "" else " (pausiert)")
                InfoLine("Playlist", playlist ?: "– nicht erkannt –")
                followUp?.let { InfoLine("", "Empfohlene Songs nach „$it“ – Profil bleibt aktiv") }
                InfoLine("Titel", n.title ?: "–")
                InfoLine("Künstler", n.artist ?: "–")
                InfoLine("Album", n.album ?: "–")
                if (n.packageName == EqCoordinator.SPOTIFY_PKG && sp == null) {
                    Text(
                        "Tipp: Verbinde Spotify in den Einstellungen, dann wird der echte Playlist-Name erkannt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("EQ-Profil festlegen für …", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlist != null) {
                        FilledTonalButton(onClick = { draft(MatchField.PLAYLIST, playlist) }) { Text("diese Playlist") }
                    }
                    n.album?.let { a -> OutlinedButton(onClick = { draft(MatchField.ALBUM, a) }) { Text("dieses Album") } }
                    OutlinedButton(onClick = { draft(MatchField.APP, "") }) { Text("ganze App") }
                }
            }
        }

        np?.let { n ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Technische Details", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Ausblenden" else "Anzeigen") }
                }
                if (showDetails) {
                    Text(
                        "Alles, was ${n.appLabel} an Android meldet. Steht hier irgendwo der Playlist-Name, kann PlaylistEQ ihn nutzen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    n.details.forEach { (k, v) -> InfoLine(k, v) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(n.details.joinToString("\n") { "${it.first}: ${it.second}" }))
                    }) { Text("Kopieren") }
                }
            }
        }

        SectionCard {
            Text("Verbundene Audio-Ausgaben", style = MaterialTheme.typography.titleMedium)
            if (settings.globalMode) {
                InfoLine("Global (alle Apps)", applied[0] ?: "–")
            } else if (sessions.isEmpty()) {
                Text("Noch keine App hat eine Audio-Session gemeldet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                sessions.forEach { (id, pkg) -> InfoLine("${AppInfo.label(ctx, pkg)} #$id", applied[id] ?: "–") }
            }
            val n = np
            if (!settings.globalMode && n != null && n.playing && sessions.values.none { it == n.packageName }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${n.appLabel} meldet keine eigene Audio-Session (typisch für YouTube / YouTube Music). " +
                        "Schalte in den Einstellungen den „Globalen Modus“ ein, damit der EQ auch dort wirkt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        error?.let {
            SectionCard {
                Text("Problem", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(0.35f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.65f), style = MaterialTheme.typography.bodyMedium)
    }
}
