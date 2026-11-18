package com.wizcut.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Publishes one dynamic launcher shortcut per bulb. Each shortcut
 * launches [ToggleActivity], which flips the bulb without showing
 * any UI.
 */
object WizShortcuts {

    const val ACTION_TOGGLE = "com.wizcut.app.action.TOGGLE"
    const val EXTRA_MAC = "mac"

    /**
     * Replaces the whole published set, so new bulbs appear, removed
     * bulbs disappear, and renames update the labels.
     */
    fun publish(context: Context, devices: List<WizDevice>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)

        val shortcuts = devices
            .take(max)
            .map { device ->
                // An empty label makes ShortcutInfoCompat.Builder throw
                // IllegalArgumentException, so fall back to the default.
                val name = WizPreferences.loadName(context, device.mac)
                    .ifBlank { "WiZ bulb ${device.mac.takeLast(4)}" }

                val toggleIntent = Intent(context, ToggleActivity::class.java).apply {
                    action = ACTION_TOGGLE
                    putExtra(EXTRA_MAC, device.mac)
                }

                ShortcutInfoCompat.Builder(context, device.mac)
                    .setShortLabel(name)
                    .setLongLabel("Toggle $name")
                    .setIcon(
                        IconCompat.createWithResource(
                            context,
                            R.drawable.lightbulb_24
                        )
                    )
                    .setIntent(toggleIntent)
                    .build()
            }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
