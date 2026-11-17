package com.wizcut.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.wizcut.app.ui.theme.MidGray
import com.wizcut.app.ui.theme.SurfaceVariant
import com.wizcut.app.ui.theme.WiZcutTheme
import kotlin.concurrent.thread

/**
 * Shown when the user adds the widget: prompts which bulb the widget
 * should control, then pins the choice and renders the first update.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The launcher passes the widget id being added; bail out if
        // it is missing (result stays cancelled, so nothing is added).
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        DeviceRepository.ensureLoaded(this)

        setContent {
            WiZcutTheme {
                ConfigScreen(appWidgetId = appWidgetId)
            }
        }
    }

    @Composable
    private fun ConfigScreen(appWidgetId: Int) {

        val devices = DeviceRepository.devices

        // Refresh every known bulb once so the picker shows real state.
        LaunchedEffect(Unit) {
            devices
                .filter { it.ip.isNotBlank() }
                .forEach { device ->
                    thread {
                        try {
                            val fresh = WizController.getPilot(device)

                            runOnUiThread {
                                DeviceRepository.update(this@WidgetConfigActivity, fresh)
                            }
                        } catch (_: Exception) {
                            runOnUiThread {
                                DeviceRepository.update(
                                    this@WidgetConfigActivity,
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
                    .padding(20.dp)
            ) {

                Text(
                    text = getString(R.string.widget_config_title),
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = getString(R.string.widget_config_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MidGray,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (devices.isEmpty()) {
                    Text(
                        text = getString(R.string.widget_config_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MidGray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        startActivity(
                            Intent(this@WidgetConfigActivity, MainActivity::class.java)
                        )
                    }) {
                        Text(getString(R.string.widget_config_open_app))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        devices.forEach { device ->
                            BulbRow(
                                device = device,
                                name = WizPreferences.loadName(
                                    this@WidgetConfigActivity,
                                    device.mac
                                ),
                                onClick = { pick(appWidgetId, device.mac) }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BulbRow(
        device: WizDevice,
        name: String,
        onClick: () -> Unit
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .alpha(if (device.isOnline) 1f else 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = when {
                            !device.isOnline -> getString(R.string.widget_status_offline)
                            !device.isOn -> getString(R.string.widget_status_off)
                            else -> "${device.brightness ?: 100}%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MidGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                RadioButton(
                    selected = false,
                    onClick = onClick,
                    enabled = device.isOnline
                )
            }
        }
    }

    private fun pick(appWidgetId: Int, mac: String) {
        WidgetPrefs.pin(this, appWidgetId, mac)

        WizWidget.render(
            this,
            AppWidgetManager.getInstance(this),
            appWidgetId
        )

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        finish()
    }
}
