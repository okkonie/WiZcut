package com.wizcut.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlin.concurrent.thread

/**
 * Invisible trampoline activity launched by the per-bulb shortcuts.
 * Reads the bulb's actual state, flips it, and finishes in onCreate —
 * nothing is ever rendered and the app never comes to the foreground.
 * The work continues on a background thread after finish().
 */
class ToggleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val mac = intent?.getStringExtra(WizShortcuts.EXTRA_MAC)

        if (action == WizShortcuts.ACTION_TOGGLE && mac != null) {
            toggle(mac)
        }

        // Theme.NoDisplay requires finishing before the window is shown.
        finish()
    }

    private fun toggle(mac: String) {
        DeviceRepository.ensureLoaded(applicationContext)

        val device = DeviceRepository.devices.firstOrNull { it.mac == mac }

        if (device == null) {
            toast("Bulb not found — open WiZcut and tap Find bulbs")
            return
        }

        thread {
            try {
                // Base the toggle on the bulb's real state so it stays
                // correct even if it was changed by another app.
                val fresh = WizController.getPilot(device)
                val updated = WizController.setPower(fresh, !fresh.isOn)

                DeviceRepository.update(applicationContext, updated)

                toast(
                    "${WizPreferences.loadName(applicationContext, mac)} " +
                        "turned ${if (updated.isOn) "on" else "off"}"
                )
            } catch (e: Exception) {
                DeviceRepository.update(
                    applicationContext,
                    device.copy(isOnline = false)
                )

                toast("Toggle failed: ${e.message}")
            }
        }
    }

    /**
     * Toasts must be shown from a thread with a Looper; the toggle runs
     * on a plain worker thread, so hop back to the main thread.
     */
    private fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
