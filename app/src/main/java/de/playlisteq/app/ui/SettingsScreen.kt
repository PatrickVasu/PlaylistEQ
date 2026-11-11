package de.playlisteq.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.playlisteq.app.core.LiveState
import de.playlisteq.app.core.Store
import de.playlisteq.app.model.EqProfile
import de.playlisteq.app.spotify.SpotifyClient

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by Store.settings.collectAsState()
    val profiles by Store.profiles.collectAsState()
    val spotifyStatus by LiveState.spotifyStatus.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

        SectionCard {
            Text("Standardprofil", style = MaterialTheme.typography.titleMedium)
            Text(
                "Gilt für alles, wofür keine Regel passt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val options: List<EqProfile?> = listOf<EqProfile?>(null) + profiles
            Dropdown(
                label = "",
                selectedText = profiles.firstOrNull { it.id == settings.defaultProfileId }?.name ?: "Aus (kein EQ)",
                options = options,
                optionText = { it?.name ?: "Aus (kein EQ)" },
                onSelect = { p -> Store.updateSettings { it.copy(defaultProfileId = p?.id) } },
            )
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Globaler Modus", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Für Apps, die keine eigene Audio-Session melden (z. B. YouTube, YouTube Music). " +
                            "Der EQ hängt sich dann an die Gesamtausgabe und wechselt das Profil je nach gerade " +
                            "spielender App/Playlist. Funktioniert nicht auf jedem Gerät – falls nicht, erscheint " +
                            "im Tab „Jetzt“ ein Hinweis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.globalMode,
                    onCheckedChange = { on -> Store.updateSettings { it.copy(globalMode = on) } },
                )
            }
        }

        SectionCard {
            Text("Spotify verbinden (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Spotify verrät Android den Playlist-Namen nicht zuverlässig. Mit dieser Verbindung fragt " +
                    "PlaylistEQ ihn direkt bei Spotify ab.\n\n" +
                    "1. developer.spotify.com/dashboard öffnen → „Create app“\n" +
                    "2. Bei „Redirect URI“ genau diese Adresse eintragen:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SpotifyClient.REDIRECT_URI,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(SpotifyClient.REDIRECT_URI)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Kopieren")
                }
            }
            Text(
                "3. „Web API“ anhaken, speichern\n4. Die „Client ID“ hier einfügen und verbinden:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.spotifyClientId,
                onValueChange = { v -> Store.updateSettings { it.copy(spotifyClientId = v.trim()) } },
                label = { Text("Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("Status: $spotifyStatus", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = settings.spotifyClientId.isNotBlank(),
                    onClick = { SpotifyClient.startLogin(ctx, settings.spotifyClientId) },
                ) { Text(if (SpotifyClient.isConnected()) "Neu verbinden" else "Mit Spotify verbinden") }
                if (SpotifyClient.isConnected()) {
                    TextButton(onClick = { SpotifyClient.disconnect() }) { Text("Trennen") }
                }
            }
        }

        SectionCard {
            Text("So funktioniert die Erkennung", style = MaterialTheme.typography.titleMedium)
            Text(
                "• Spotify: echter Playlist-Name über die Spotify-Verbindung, sonst der von Spotify gemeldete Wiedergabe-Titel.\n" +
                    "• YouTube / YouTube Music: Playlist nur, wenn die App sie als Warteschlangen-Titel meldet. " +
                    "Sonst nach Album, Künstler oder Titel filtern oder eine „Ganze App“-Regel nutzen.\n" +
                    "• Lokale Player (VLC, Poweramp, Musicolet …): Playlist bzw. Ordner über den Warteschlangen-Titel, Album immer.\n\n" +
                    "Tipp: Im Tab „Jetzt“ siehst du genau, was gerade erkannt wird – genau diese Werte kannst du in Regeln verwenden.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
