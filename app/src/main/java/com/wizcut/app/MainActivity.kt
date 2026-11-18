package com.wizcut.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wizcut.app.ui.theme.DarkBackground
import com.wizcut.app.ui.theme.LightGray
import com.wizcut.app.ui.theme.SurfaceVariant
import com.wizcut.app.ui.theme.White
import com.wizcut.app.ui.theme.WiZcutTheme
import kotlin.concurrent.thread
import kotlin.math.roundToInt

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

    /**
     * Runs a bulb command on a worker thread, then updates the shared
     * repository (and with it every card, widget, and shortcut). Marks
     * the bulb offline and toasts on failure.
     */
    private fun sendCommand(
        device: WizDevice,
        failureMessage: String,
        command: () -> WizDevice
    ) {
        if (device.ip.isBlank()) {
            Toast.makeText(
                this,
                "Find bulbs to refresh their addresses",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        thread {
            try {
                val updated = command()

                runOnUiThread {
                    DeviceRepository.update(this, updated)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    DeviceRepository.update(
                        this,
                        device.copy(isOnline = false)
                    )

                    Toast.makeText(
                        this,
                        "$failureMessage: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
        // Bulbs whose module is not known yet are also asked for their
        // capabilities (getSystemConfig).
        LaunchedEffect(Unit) {
            devices
                .filter { it.ip.isNotBlank() }
                .forEach { device ->
                    thread {
                        try {
                            var fresh = WizController.getPilot(device)

                            if (!fresh.capabilitiesKnown) {
                                // Failure here is not fatal — the bulb
                                // already answered getPilot, so it is
                                // online; color controls just stay
                                // hidden until it identifies itself.
                                try {
                                    fresh = WizController.getSystemConfig(fresh)
                                } catch (_: Exception) {
                                }
                            }

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
                    .verticalScroll(rememberScrollState())
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
                                        val discovered =
                                            WizDiscovery.discover(this@MainActivity)

                                        runOnUiThread {
                                            val merged = linkedMapOf<String, WizDevice>()

                                            DeviceRepository.devices.forEach {
                                                merged[it.mac] = it
                                            }

                                            // Keep everything known about a saved
                                            // bulb (module name, color state);
                                            // only refresh address and state.
                                            discovered.forEach { fresh ->
                                                val saved = merged[fresh.mac]
                                                merged[fresh.mac] = saved?.copy(
                                                    ip = fresh.ip,
                                                    isOn = fresh.isOn,
                                                    brightness = fresh.brightness,
                                                    isOnline = true
                                                ) ?: fresh
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

                                            // Ask newly found bulbs which module
                                            // they are, so color/temperature
                                            // controls appear if supported.
                                            merged.values
                                                .filter {
                                                    !it.capabilitiesKnown &&
                                                        it.ip.isNotBlank()
                                                }
                                                .forEach { device ->
                                                    thread {
                                                        try {
                                                            val withCaps =
                                                                WizController.getSystemConfig(
                                                                    device
                                                                )

                                                            runOnUiThread {
                                                                DeviceRepository.update(
                                                                    this@MainActivity,
                                                                    withCaps
                                                                )
                                                            }
                                                        } catch (_: Exception) {
                                                            // Bulb does not identify
                                                            // itself; controls stay
                                                            // hidden for it.
                                                        }
                                                    }
                                                }
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

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    devices.forEach { device ->

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
                                sendCommand(device, "Power command failed") {
                                    WizController.setPower(device, requestedState)
                                }
                            },
                            onBrightnessChange = { brightness ->
                                sendCommand(device, "Brightness command failed") {
                                    WizController.setBrightness(device, brightness)
                                }
                            },
                            onTempChange = { kelvin ->
                                sendCommand(device, "Temperature command failed") {
                                    WizController.setColorTemp(device, kelvin)
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
        onBrightnessChange: (Int) -> Unit,
        onTempChange: (Int) -> Unit
    ) {
        var editedName by remember(device.mac, name) {
            mutableStateOf(name)
        }

        val focusManager = LocalFocusManager.current

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = {
                            editedName = it
                            onNameChange(it)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Bulb name") },
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    val container = if (device.isOn) White else SurfaceVariant
                    val content = if (device.isOn) DarkBackground else LightGray

                    Button(
                        onClick = {
                            onPowerChange(!device.isOn)
                        },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = container,
                            contentColor = content,
                            // The card already dims when offline; don't
                            // dim the button a second time.
                            disabledContainerColor = container,
                            disabledContentColor = content
                        ),
                        contentPadding = PaddingValues(0.dp),
                        enabled = device.isOnline
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lightbulb_24),
                            contentDescription = if (device.isOn) {
                                "Turn off"
                            } else {
                                "Turn on"
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Brightness",
                        style = MaterialTheme.typography.labelMedium,
                        color = LightGray
                    )

                    Text(
                        text = "${sliderValue.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = LightGray
                    )
                }

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
                }

                // Temperature slider, only when the bulb reports a
                // tunable-white capability.
                device.kelvinRange?.let {
                    Spacer(modifier = Modifier.height(16.dp))

                    TemperatureControls(
                        device = device,
                        onTempChange = onTempChange
                    )
                }
            }
        }
    }

    /**
     * Warm-to-cool white temperature control, using the same native
     * slider style as the brightness control. The kelvin range comes
     * from the bulb (reported kelvinRange or its module type).
     */
    @Composable
    private fun TemperatureControls(
        device: WizDevice,
        onTempChange: (Int) -> Unit
    ) {
        val range = device.kelvinRange ?: return

        var value by remember(device.mac, device.colorTemp) {
            mutableFloatStateOf((device.colorTemp ?: range.first).toFloat())
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.labelMedium,
                    color = LightGray
                )

                Text(
                    text = "${value.roundToInt()} K",
                    style = MaterialTheme.typography.labelMedium,
                    color = LightGray
                )
            }

            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = {
                    onTempChange(value.roundToInt())
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                enabled = device.isOnline,
                colors = SliderDefaults.colors(
                    thumbColor = White,
                    activeTrackColor = White,
                    inactiveTrackColor = SurfaceVariant
                )
            )
        }
    }
}
