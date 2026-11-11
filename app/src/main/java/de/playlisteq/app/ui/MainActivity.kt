package de.playlisteq.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import de.playlisteq.app.audio.AudioSessionReceiver
import de.playlisteq.app.core.Store
import de.playlisteq.app.model.Rule
import de.playlisteq.app.service.EqService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { AppTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Wenn Spotify & Co. über ihren Equalizer-Knopf öffnen, liefern sie gleich ihre Audio-Session mit. */
    private fun handleIntent(intent: Intent?) {
        if (intent != null) AudioSessionReceiver.handle(intent)
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Jetzt", Icons.Filled.PlayCircle),
    Tab("Profile", Icons.Filled.GraphicEq),
    Tab("Regeln", Icons.Filled.FilterList),
    Tab("Einstellungen", Icons.Filled.Settings),
)

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val settings by Store.settings.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ruleDraft by remember { mutableStateOf<Rule?>(null) }

    LaunchedEffect(settings.enabled) {
        if (settings.enabled) EqService.start(ctx) else EqService.stop(ctx)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> NowScreen(onCreateRule = { ruleDraft = it })
                1 -> ProfilesScreen()
                2 -> RulesScreen(onEdit = { ruleDraft = it })
                else -> SettingsScreen()
            }
        }
    }

    ruleDraft?.let { draft ->
        RuleDialog(
            initial = draft,
            onDismiss = { ruleDraft = null },
            onSave = { Store.upsertRule(it); ruleDraft = null },
        )
    }
}
