package com.panelswipe.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val colors = remember {
                runCatching { dynamicDarkColorScheme(ctx) }.getOrElse { darkColorScheme() }
            }
            MaterialTheme(colorScheme = colors) {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    SettingsScreen(Modifier.padding(inner))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var divider by remember { mutableStateOf(prefs.dividerFraction) }
    var swap by remember { mutableStateOf(prefs.swapSides) }
    var minDistance by remember { mutableStateOf(prefs.minSwipeDistanceDp.toFloat()) }
    var serviceEnabled by remember { mutableStateOf(Prefs.isAccessibilityServiceEnabled(context)) }

    // Re-check whether the accessibility service is on whenever we come back to
    // the foreground (e.g. after the user enables it in system settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = Prefs.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PanelSwipe",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Swipe down on the home screen — the left zone opens " +
                "Notifications, the right zone opens Quick Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )

        // --- Accessibility permission ---
        SettingCard {
            Text(
                text = if (serviceEnabled) "Accessibility service: ON"
                else "Accessibility service: OFF",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (serviceEnabled)
                    "PanelSwipe is ready. It only observes touch gestures on your " +
                        "home screen and never reads screen content."
                else
                    "PanelSwipe needs the Accessibility permission to detect your " +
                        "swipe and open the right panel. Tap below, then enable " +
                        "“PanelSwipe”.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (serviceEnabled) "Open Accessibility settings" else "Enable PanelSwipe")
            }
        }

        // --- Master toggle ---
        SettingCard {
            ToggleRow(
                title = "Enable PanelSwipe",
                subtitle = "Turn the home-screen hot zone on or off.",
                checked = enabled,
                onCheckedChange = { enabled = it; prefs.enabled = it }
            )
        }

        // --- Hot-zone divider ---
        SettingCard {
            val rightPct = (divider * 100).roundToInt()
            val leftPct = 100 - rightPct
            Text("Hot-zone divider", style = MaterialTheme.typography.titleMedium)
            Text(
                "Where the screen splits between the two panels. Set this to match " +
                    "the divider you configured in QuickStar.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            Slider(
                value = divider,
                onValueChange = { divider = it },
                onValueChangeFinished = { prefs.dividerFraction = divider },
                valueRange = 0.05f..0.95f
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (swap) "Quick Settings ($leftPct%)" else "Notifications ($leftPct%)",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    if (swap) "Notifications ($rightPct%)" else "Quick Settings ($rightPct%)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // --- Swap sides ---
        SettingCard {
            ToggleRow(
                title = "Swap sides",
                subtitle = if (swap)
                    "Left opens Quick Settings, right opens Notifications."
                else
                    "Left opens Notifications, right opens Quick Settings.",
                checked = swap,
                onCheckedChange = { swap = it; prefs.swapSides = it }
            )
        }

        // --- Sensitivity ---
        SettingCard {
            Text("Swipe sensitivity", style = MaterialTheme.typography.titleMedium)
            Text(
                "How far you must swipe down before a panel opens: " +
                    "${minDistance.roundToInt()} dp.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            Slider(
                value = minDistance,
                onValueChange = { minDistance = it },
                onValueChangeFinished = { prefs.minSwipeDistanceDp = minDistance.roundToInt() },
                valueRange = 16f..200f
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("More sensitive", style = MaterialTheme.typography.labelMedium)
                Text("Less sensitive", style = MaterialTheme.typography.labelMedium)
            }
        }

        // --- Tips ---
        SettingCard {
            Text("Tips", style = MaterialTheme.typography.titleMedium)
            Text(
                "• In One UI Settings, turn OFF “Swipe down for notification panel” " +
                    "on the home screen, otherwise One UI will always open " +
                    "Notifications and override PanelSwipe.\n\n" +
                    "• The system status bar keeps its own hot zone — PanelSwipe " +
                    "only handles swipes that start below it.\n\n" +
                    "• PanelSwipe only acts while your home screen is showing; it " +
                    "never interferes with swipes inside other apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
