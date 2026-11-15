
package com.wizcut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import com.wizcut.app.ui.theme.WiZcutTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WiZcutTheme {
                DiscoveryScreen()
            }
        }
    }

    @Composable
    private fun DiscoveryScreen() {

        var devices by remember {
            mutableStateOf(WizPreferences.load(this))
        }

        var scanning by remember {
            mutableStateOf(false)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                    )
                    .padding(20.dp)
            ) {

                Text(
                    text = "WiZcut",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (!scanning) {
                            scanning = true

                            thread {
                                try {
                                    val discovered = WizDiscovery.discover()

                                    runOnUiThread {
                                        val merged = linkedMapOf<String, WizDevice>()

                                        // Keep previously saved bulbs.
                                        devices.forEach {
                                            merged[it.mac] = it
                                        }

                                        // Update or add discovered bulbs.
                                        discovered.forEach {
                                            merged[it.mac] = it
                                        }

                                        devices = merged.values.toList()

                                        WizPreferences.save(
                                            this@MainActivity,
                                            devices
                                        )

                                        scanning = false
                                    }

                                } catch (e: Exception) {
                                    runOnUiThread {
                                        scanning = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (scanning) "Searching..." else "Find WiZ bulbs"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "My bulbs (${devices.size})",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = devices,
                        key = { it.mac }
                    ) { device ->

                        BulbCard(
                            device = device,
                            name = WizPreferences.loadName(
                                this@MainActivity,
                                device.mac
                            ),
                            onNameChange = { newName ->
                                WizPreferences.saveName(
                                    this@MainActivity,
                                    device.mac,
                                    newName
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun BulbCard(
        device: WizDevice,
        name: String,
        onNameChange: (String) -> Unit
    ) {
        var editedName by remember(device.mac, name) {
            mutableStateOf(name)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                TextField(
                    value = editedName,
                    onValueChange = {
                        editedName = it
                        onNameChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Bulb name") },
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (device.isOn) "On" else "Off",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (device.isOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Switch(
                        checked = device.isOn,
                        onCheckedChange = null
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Brightness",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                val brightness = device.brightness
                    ?.coerceIn(0, 100)

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        if (brightness != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(brightness / 100f)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.primary
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = brightness?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
