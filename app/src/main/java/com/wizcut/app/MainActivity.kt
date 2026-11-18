package com.wizcut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wizcut.app.ui.theme.DarkBackground
import com.wizcut.app.ui.theme.DarkSurface
import com.wizcut.app.ui.theme.LightGray
import com.wizcut.app.ui.theme.SurfaceVariant
import com.wizcut.app.ui.theme.White
import com.wizcut.app.ui.theme.WiZcutTheme
import com.wizcut.app.ui.theme.warmthColor
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
     * repository (and with it every card, widget, and shortcut). On
     * failure the bulb is marked offline, which dims and disables the
     * card — no toast, the card itself shows the state.
     */
    private fun sendCommand(
        device: WizDevice,
        command: () -> WizDevice
    ) {
        if (device.ip.isBlank()) return

        thread {
            try {
                val updated = command()

                runOnUiThread {
                    DeviceRepository.update(this, updated)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    DeviceRepository.update(
                        this,
                        device.copy(isOnline = false)
                    )
                }
            }
        }
    }

    /**
     * Broadcast discovery, merged into the saved bulb list. The header
     * status line shows what is happening: the search running, the bulb
     * count when it lands, or the error. Shared by the automatic search
     * on first open and by pull to refresh.
     */
    private fun searchBulbs(onDone: (found: Int, error: String?) -> Unit) {
        thread {
            try {
                val discovered = WizDiscovery.discover(this)

                runOnUiThread {
                    val merged = linkedMapOf<String, WizDevice>()

                    DeviceRepository.devices.forEach {
                        merged[it.mac] = it
                    }

                    // Keep everything known about a saved bulb (module
                    // name, kelvin); only refresh address and state.
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

                    val found = discovered.size

                    onDone(found, null)

                    // Ask newly found bulbs which module they are, so
                    // warmth controls appear when the bulb supports them.
                    merged.values
                        .filter {
                            !it.capabilitiesKnown && it.ip.isNotBlank()
                        }
                        .forEach { device ->
                            thread {
                                try {
                                    val withCaps =
                                        WizController.getSystemConfig(device)

                                    runOnUiThread {
                                        DeviceRepository.update(
                                            this@MainActivity,
                                            withCaps
                                        )
                                    }
                                } catch (_: Exception) {
                                    // Bulb does not identify itself;
                                    // warmth controls stay hidden.
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    onDone(0, e.message ?: "no response")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DiscoveryScreen() {

        val devices = DeviceRepository.devices

        var scanning by remember {
            mutableStateOf(false)
        }

        val pullState = rememberPullToRefreshState()
        var scanError by remember { mutableStateOf<String?>(null) }

        // With nothing saved yet the app searches the network by itself.
        // Otherwise query every known bulb once when the app opens, so
        // each card shows the bulb's actual state instead of the last
        // saved one. Bulbs whose module is not known yet are also asked
        // for their capabilities (getSystemConfig).
        LaunchedEffect(Unit) {
            if (devices.isEmpty()) {
                scanError = null
                scanning = true
                searchBulbs { _, error ->
                    scanning = false
                    scanError = error
                }
            }

            devices
                .filter { it.ip.isNotBlank() }
                .forEach { device ->
                    thread {
                        try {
                            var fresh = WizController.getPilot(device)

                            if (!fresh.capabilitiesKnown) {
                                // Failure here is not fatal — the bulb
                                // already answered getPilot, so it is
                                // online; warmth controls just stay
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
            PullToRefreshBox(
                isRefreshing = scanning,
                onRefresh = {
                    if (!scanning) {
                        scanError = null
                        scanning = true
                        searchBulbs { _, error ->
                            scanning = false
                            scanError = error
                        }
                    }
                },
                state = pullState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "WiZcut",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = when {
                                scanning -> "Searching for bulbs…"
                                scanError != null -> "Search failed · pull to search"
                                devices.isEmpty() -> "No bulbs found · pull to search"
                                else -> "${devices.size} bulbs · pull to search"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = LightGray
                        )
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
                                    sendCommand(device) {
                                        WizController.setPower(device, requestedState)
                                    }
                                },
                                onBrightnessChange = { brightness ->
                                    sendCommand(device) {
                                        WizController.setBrightness(device, brightness)
                                    }
                                },
                                onTempChange = { kelvin ->
                                    sendCommand(device) {
                                        WizController.setColorTemp(device, kelvin)
                                    }
                                }
                            )
                        }

                    }
                }

                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = scanning,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = White,
                    containerColor = DarkSurface
                )
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
                .alpha(if (device.isOnline) 1f else 0.5f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(0.dp)

        ) {
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

                // Warmth presets, only when the bulb reports a
                // tunable-white capability. Chips rather than a second
                // slider, so they cannot be mistaken for brightness.
                device.kelvinRange?.let {
                    Spacer(modifier = Modifier.height(16.dp))

                    WarmthControls(
                        device = device,
                        range = it,
                        onTempChange = onTempChange
                    )
                }
            }
        }
    }

    /**
     * Warmth presets as chips, each tinted with the color of the light
     * it sets; the selected one is filled solid. The kelvin range comes
     * from the bulb (reported kelvinRange or its module type).
     */
    @Composable
    private fun WarmthControls(
        device: WizDevice,
        range: IntRange,
        onTempChange: (Int) -> Unit
    ) {
        val stops = warmthStops(range)
        val current = device.colorTemp


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stops.forEach { stop ->
                WarmthChip(
                    stop = stop,
                    selected = current != null &&
                        stop.kelvin == nearestStop(stops, current).kelvin,
                    enabled = device.isOnline,
                    onClick = {
                        onTempChange(stop.kelvin)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    /**
     * One warmth preset: outline and label in the light color it sets,
     * filled solid in that color when selected.
     */
    @Composable
    private fun WarmthChip(
        stop: WarmthStop,
        selected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val light = warmthColor(stop.kelvin)
        val shape = RoundedCornerShape(10.dp)

        Box(
            modifier = modifier
                .clip(shape)
                .background(if (selected) light else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = light.copy(alpha = if (selected) 1f else 0.45f),
                    shape = shape
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stop.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) DarkBackground else light.copy(alpha = 0.9f),
                maxLines = 1
            )
        }
    }

    private data class WarmthStop(val label: String, val kelvin: Int)

    /**
     * Candle at the bulb's warmest kelvin, Cool at 70% of its brightest
     * reported kelvin — the top of the range is too cold to offer. Warm
     * and Neutral split the span in thirds.
     */
    private fun warmthStops(range: IntRange): List<WarmthStop> {
        val candle = range.first
        val cool = (range.last * 0.7f).roundToInt()
            .coerceIn(candle, range.last)
        val span = cool - candle

        return listOf(
            "Candle" to candle,
            "Warm" to candle + (span / 3f).roundToInt(),
            "Neutral" to candle + (2f * span / 3f).roundToInt(),
            "Cool" to cool
        ).map { (label, kelvin) ->
            WarmthStop(label, (kelvin / 100f).roundToInt() * 100)
        }.distinctBy { it.kelvin }
    }

    /** Nearest preset to a value the bulb actually reports. */
    private fun nearestStop(stops: List<WarmthStop>, kelvin: Int): WarmthStop {
        return stops.minByOrNull { kotlin.math.abs(it.kelvin - kelvin) }
            ?: stops.first()
    }
}
