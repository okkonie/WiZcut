
package com.wizcut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wizcut.app.ui.theme.WiZcutTheme
import kotlin.concurrent.thread
import kotlin.math.roundToInt

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

        var status by remember {
            mutableStateOf("Ready")
        }

        fun updateDevice(updated: WizDevice) {
            devices = devices.map {
                if (it.mac == updated.mac) updated else it
            }

            WizPreferences.save(this@MainActivity, devices)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(20.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {


                    Text(
                        text = "WiZcut",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Button(
                        onClick = {
                            if (!scanning) {
                                scanning = true
                                status = "Searching for bulbs..."

                                thread {
                                    try {
                                        val discovered = WizDiscovery.discover()

                                        runOnUiThread {
                                            val merged = linkedMapOf<String, WizDevice>()

                                            devices.forEach {
                                                merged[it.mac] = it
                                            }

                                            discovered.forEach {
                                                merged[it.mac] = it
                                            }

                                            devices = merged.values.toList()

                                            WizPreferences.save(
                                                this@MainActivity,
                                                devices
                                            )

                                            status = if (discovered.isEmpty()) {
                                                "No new responses. Showing saved bulbs."
                                            } else {
                                                "Found ${discovered.size} bulb(s)"
                                            }

                                            scanning = false
                                        }

                                    } catch (e: Exception) {
                                        runOnUiThread {
                                            status = "Discovery failed: ${e.message}"
                                            scanning = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.width(140.dp),
                        enabled = !scanning
                    ) {
                        Text(if (scanning) "Searching..." else "Find bulbs")
                    }

                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

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
                            },
                            onPowerChange = { requestedState ->

                                if (device.ip.isBlank()) {
                                    status = "Find bulbs to refresh their addresses"
                                } else {
                                    thread {
                                        try {
                                            val updated = WizController.setPower(
                                                device,
                                                requestedState
                                            )

                                            runOnUiThread {
                                                updateDevice(updated)
                                                status = " "
                                            }
                                        } catch (e: Exception) {
                                            runOnUiThread {
                                                status = "Power command failed: ${e.message}"
                                            }
                                        }
                                    }
                                }
                            },
                            onBrightnessChange = { brightness ->

                                if (device.ip.isBlank()) {
                                    status = "Find bulbs to refresh their addresses"
                                } else {
                                    thread {
                                        try {
                                            val updated = WizController.setBrightness(
                                                device,
                                                brightness
                                            )

                                            runOnUiThread {
                                                updateDevice(updated)
                                                status = " "
                                            }
                                        } catch (e: Exception) {
                                            runOnUiThread {
                                                status = "Brightness command failed: ${e.message}"
                                            }
                                        }
                                    }
                                }
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
        onNameChange: (String) -> Unit,
        onPowerChange: (Boolean) -> Unit,
        onBrightnessChange: (Int) -> Unit
    ) {
        var editedName by remember(device.mac, name) {
            mutableStateOf(name)
        }

        var sliderValue by remember(device.mac, device.brightness) {
            mutableFloatStateOf(
                (device.brightness ?: 100).toFloat()
            )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = {
                            editedName = it
                            onNameChange(it)
                        },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                        label = { Text("Bulb name") },
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = device.isOn,
                        onCheckedChange = onPowerChange,
                        enabled = device.ip.isNotBlank()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        onBrightnessChange(
                            sliderValue.roundToInt()
                        )
                    },
                    valueRange = 10f..100f,
                    enabled = device.ip.isNotBlank()
                )
            }
        }
    }
}
