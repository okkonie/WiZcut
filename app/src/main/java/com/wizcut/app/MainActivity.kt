package com.wizcut.app

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.wizcut.app.ui.theme.DarkBackground
import com.wizcut.app.ui.theme.LightGray
import com.wizcut.app.ui.theme.MidGray
import com.wizcut.app.ui.theme.SurfaceVariant
import com.wizcut.app.ui.theme.White
import com.wizcut.app.ui.theme.WiZcutTheme
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DeviceRepository.load(this)
        WizShortcuts.publish(this, DeviceRepository.devices)

        setContent {
            WiZcutTheme {
                DiscoveryScreen()
            }
        }
    }

    @Composable
    private fun DiscoveryScreen() {

        val devices = DeviceRepository.devices

        var scanning by remember {
            mutableStateOf(false)
        }

        // Query every known bulb once when the app opens, so each card
        // shows the bulb's actual state instead of the last saved one.
        LaunchedEffect(Unit) {
            devices
                .filter { it.ip.isNotBlank() }
                .forEach { device ->
                    thread {
                        try {
                            val fresh = WizController.getPilot(device)

                            runOnUiThread {
                                DeviceRepository.update(this@MainActivity, fresh)
                            }
                        } catch (_: Exception) {
                            // Bulb is unreachable; dim its card and
                            // disable its controls.
                            runOnUiThread {
                                DeviceRepository.update(
                                    this@MainActivity,
                                    device.copy(isOnline = false)
                                )
                            }
                        }
                    }
                }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
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

                                thread {
                                    try {
                                        val discovered = WizDiscovery.discover()

                                        runOnUiThread {
                                            val merged = linkedMapOf<String, WizDevice>()

                                            DeviceRepository.devices.forEach {
                                                merged[it.mac] = it
                                            }

                                            discovered.forEach {
                                                merged[it.mac] = it
                                            }

                                            DeviceRepository.replaceAll(
                                                this@MainActivity,
                                                merged.values.toList()
                                            )

                                            WizShortcuts.publish(
                                                this@MainActivity,
                                                DeviceRepository.devices
                                            )

                                            scanning = false
                                        }

                                    } catch (e: Exception) {
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Discovery failed: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()

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

                                WizShortcuts.publish(
                                    this@MainActivity,
                                    DeviceRepository.devices
                                )
                            },
                            onPowerChange = { requestedState ->
                                thread {
                                    try {
                                        val updated = WizController.setPower(
                                            device,
                                            requestedState
                                        )

                                        runOnUiThread {
                                            DeviceRepository.update(
                                                this@MainActivity,
                                                updated
                                            )
                                        }
                                    } catch (e: Exception) {
                                        runOnUiThread {
                                            DeviceRepository.update(
                                                this@MainActivity,
                                                device.copy(isOnline = false)
                                            )

                                            Toast.makeText(
                                                this@MainActivity,
                                                "Power command failed: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onBrightnessChange = { brightness ->

                                if (device.ip.isBlank()) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Find bulbs to refresh their addresses",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    thread {
                                        try {
                                            val updated = WizController.setBrightness(
                                                device,
                                                brightness
                                            )

                                            runOnUiThread {
                                                DeviceRepository.update(
                                                    this@MainActivity,
                                                    updated
                                                )
                                            }
                                        } catch (e: Exception) {
                                            runOnUiThread {
                                                DeviceRepository.update(
                                                    this@MainActivity,
                                                    device.copy(isOnline = false)
                                                )

                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Brightness command failed: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (device.isOnline) 1f else 0.45f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(0.dp),
            border = null,
            elevation = CardDefaults.cardElevation(0.dp)

        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )

            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                OutlinedTextField(
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        enabled = device.isOnline,
                        colors = SliderDefaults.colors(
                            thumbColor = White,
                            activeTrackColor = White,
                            inactiveTrackColor = SurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = device.isOn,
                        onCheckedChange = onPowerChange,
                        enabled = device.isOnline,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = White,
                            checkedBorderColor = White,
                            uncheckedThumbColor = LightGray,
                            uncheckedTrackColor = SurfaceVariant,
                            uncheckedBorderColor = MidGray
                        )
                    )
                }
            }
        }
    }
}
