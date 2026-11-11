package de.playlisteq.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.playlisteq.app.core.Store
import de.playlisteq.app.media.AppInfo
import de.playlisteq.app.model.MatchField
import de.playlisteq.app.model.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onEdit: (Rule) -> Unit) {
    val rules by Store.rules.collectAsState()
    val profiles by Store.profiles.collectAsState()
    val names = profiles.associate { it.id to it.name }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Regeln", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Eine Regel verknüpft App + Playlist (oder Album, Künstler, Titel) mit einem EQ-Profil. " +
                            "Playlist-Regeln haben Vorrang vor „Ganze App“, danach gilt das Standardprofil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rules.isEmpty()) {
                item {
                    Text(
                        "Noch keine Regeln. Am einfachsten: Playlist abspielen, dann im Tab „Jetzt“ auf „diese Playlist“ tippen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(rules.sortedWith(compareBy<Rule>({ it.appLabel.lowercase() }, { it.field == MatchField.APP })), key = { it.id }) { r ->
                Card(onClick = { onEdit(r) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.appLabel.ifBlank { r.packageName }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (r.field == MatchField.APP) "Ganze App"
                                else "${r.field.title} ${if (r.contains) "enthält" else "="} „${r.pattern}“" +
                                    if (r.field == MatchField.PLAYLIST && r.includeFollowUps) " + empfohlene Songs" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "→ ${names[r.profileId] ?: "(Profil fehlt)"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { Store.deleteRule(r.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Regel löschen")
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(Rule(profileId = Store.settings.value.defaultProfileId ?: profiles.firstOrNull()?.id.orEmpty())) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Neue Regel") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleDialog(initial: Rule, onDismiss: () -> Unit, onSave: (Rule) -> Unit) {
    val ctx = LocalContext.current
    val profiles by Store.profiles.collectAsState()
    val isNew = remember(initial.id) { Store.rules.value.none { it.id == initial.id } }
    var rule by remember(initial.id) { mutableStateOf(initial) }
    var apps by remember { mutableStateOf<List<AppInfo.App>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { AppInfo.launchable(ctx) } }

    val valid = rule.packageName.isNotBlank() &&
        profiles.any { it.id == rule.profileId } &&
        (rule.field == MatchField.APP || rule.pattern.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Neue Regel" else "Regel bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Dropdown(
                    label = "App",
                    selectedText = rule.appLabel.ifBlank { "App auswählen" },
                    options = apps,
                    optionText = { it.label },
                    onSelect = { rule = rule.copy(packageName = it.pkg, appLabel = it.label) },
                )
                Text("Bedingung", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MatchField.entries.forEach { f ->
                        FilterChip(selected = rule.field == f, onClick = { rule = rule.copy(field = f) }, label = { Text(f.title) })
                    }
                }
                if (rule.field != MatchField.APP) {
                    OutlinedTextField(
                        value = rule.pattern,
                        onValueChange = { rule = rule.copy(pattern = it) },
                        label = { Text(rule.field.hint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rule.contains, onCheckedChange = { rule = rule.copy(contains = it) })
                        Text("Teil des Namens reicht (enthält)", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (rule.field == MatchField.PLAYLIST) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rule.includeFollowUps, onCheckedChange = { rule = rule.copy(includeFollowUps = it) })
                            Text(
                                "Empfohlene Songs danach einschließen (Profil bleibt aktiv, bis eine andere Regel greift " +
                                    "oder die Wiedergabe über 10 Min. pausiert)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Dropdown(
                    label = "EQ-Profil",
                    selectedText = profiles.firstOrNull { it.id == rule.profileId }?.name ?: "Profil auswählen",
                    options = profiles,
                    optionText = { "${it.name} (${it.bandCount} Bänder)" },
                    onSelect = { rule = rule.copy(profileId = it.id) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        when (rule.field) {
                            MatchField.APP -> rule.copy(pattern = "", contains = false, includeFollowUps = false)
                            MatchField.PLAYLIST -> rule.copy(pattern = rule.pattern.trim())
                            else -> rule.copy(pattern = rule.pattern.trim(), includeFollowUps = false)
                        }
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
