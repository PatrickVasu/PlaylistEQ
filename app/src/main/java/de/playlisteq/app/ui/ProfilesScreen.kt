package de.playlisteq.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.playlisteq.app.core.Store
import de.playlisteq.app.model.Bands
import de.playlisteq.app.model.EqProfile
import de.playlisteq.app.model.Preset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen() {
    val profiles by Store.profiles.collectAsState()
    val settings by Store.settings.collectAsState()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }

    val editing = profiles.firstOrNull { it.id == editingId }
    if (editing != null) {
        ProfileEditor(editing, onClose = { editingId = null })
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("EQ-Profile", style = MaterialTheme.typography.headlineMedium) }
            items(profiles, key = { it.id }) { p ->
                Card(onClick = { editingId = p.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (settings.defaultProfileId == p.id) {
                                Text("Standard · ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${p.bandCount} Bänder", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        EqCurve(p.gains, Modifier.fillMaxWidth().height(56.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showNew = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Neues Profil") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showNew) {
        NewProfileDialog(
            onDismiss = { showNew = false },
            onCreate = { p ->
                Store.upsertProfile(p)
                showNew = false
                editingId = p.id
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewProfileDialog(onDismiss: () -> Unit, onCreate: (EqProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var bands by remember { mutableIntStateOf(16) }
    var preset by remember { mutableStateOf(Preset.FLAT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Profil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Anzahl Bänder", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bands.ALLOWED.forEach { n -> FilterChip(selected = bands == n, onClick = { bands = n }, label = { Text("$n") }) }
                }
                Text("Vorlage", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Preset.entries.forEach { pr -> FilterChip(selected = preset == pr, onClick = { preset = pr }, label = { Text(pr.title) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(EqProfile.fromPreset(name.ifBlank { preset.title }, preset, bands)) }) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(profile: EqProfile, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val settings by Store.settings.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    val id = profile.id
    val freqs = remember(profile.bandCount) { Bands.centers(profile.bandCount) }
    val sliderWidth = if (profile.bandCount >= 32) 34.dp else 44.dp

    fun setGain(i: Int, v: Float) {
        val snapped = ((v * 2f).roundToInt() / 2f).coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        Store.updateProfile(id) { p ->
            if (i >= p.gains.size || p.gains[i] == snapped) p
            else p.copy(gains = p.gains.toMutableList().also { it[i] = snapped })
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            Text("Profil bearbeiten", style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = profile.name,
            onValueChange = { v -> Store.updateProfile(id) { it.copy(name = v) } },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Anzahl Bänder", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Bands.ALLOWED.forEach { n ->
                FilterChip(
                    selected = profile.bandCount == n,
                    onClick = { Store.updateProfile(id) { it.copy(gains = Bands.resample(it.gains, n)) } },
                    label = { Text("$n-Band") },
                )
            }
        }

        Text("Vorverstärkung: ${fmtDb(profile.preamp)} dB", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = profile.preamp,
            onValueChange = { v -> Store.updateProfile(id) { it.copy(preamp = (v * 2f).roundToInt() / 2f) } },
            valueRange = -12f..6f,
        )

        Text(
            "Regler ziehen · Doppeltippen = 0 dB" + if (profile.bandCount >= 16) " · seitlich wischen für alle Bänder" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            profile.gains.forEachIndexed { i, g ->
                Column(Modifier.width(sliderWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmtDb(g), style = MaterialTheme.typography.labelSmall)
                    VerticalSlider(
                        value = g,
                        onValueChange = { v -> setGain(i, v) },
                        modifier = Modifier.width(sliderWidth).height(240.dp),
                    )
                    Text(Bands.label(freqs.getOrElse(i) { 0f }), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Store.updateProfile(id) { it.copy(gains = List(it.bandCount) { 0f }, preamp = 0f) } }) {
                Text("Alles auf 0")
            }
            OutlinedButton(onClick = { showPresets = true }) { Text("Vorlage anwenden") }
            OutlinedButton(
                enabled = settings.defaultProfileId != id,
                onClick = { Store.updateSettings { it.copy(defaultProfileId = id) } },
            ) { Text(if (settings.defaultProfileId == id) "Ist Standard" else "Als Standard") }
            TextButton(onClick = { confirmDelete = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("Vorlage anwenden") },
            text = {
                Column {
                    Preset.entries.forEach { pr ->
                        TextButton(onClick = {
                            Store.updateProfile(id) { it.copy(gains = pr.gains(it.bandCount)) }
                            showPresets = false
                        }) { Text(pr.title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresets = false }) { Text("Schließen") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Profil löschen?") },
            text = { Text("„${profile.name}“ und alle Regeln, die es verwenden, werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onClose()
                    Store.deleteProfile(id)
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}
