package com.wizcut.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Single source of truth for the bulb list, shared by the main UI and
 * the shortcut toggle activity so both stay in sync within the process.
 * Every mutation is persisted immediately.
 */
object DeviceRepository {

    var devices by mutableStateOf<List<WizDevice>>(emptyList())
        private set

    /** Loads from storage only if nothing is loaded yet (e.g. a shortcut fired before the UI). */
    fun ensureLoaded(context: Context) {
        if (devices.isEmpty()) {
            load(context)
        }
    }

    fun load(context: Context) {
        devices = WizPreferences.load(context)
    }

    fun update(context: Context, updated: WizDevice) {
        devices = devices.map {
            if (it.mac == updated.mac) updated else it
        }

        WizPreferences.save(context, devices)
    }

    fun replaceAll(context: Context, newDevices: List<WizDevice>) {
        devices = newDevices
        WizPreferences.save(context, newDevices)
    }
}
