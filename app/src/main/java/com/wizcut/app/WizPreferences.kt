package com.wizcut.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

object WizPreferences {

    private const val PREFS_NAME = "wiz_preferences"
    private const val BULBS_KEY = "saved_bulbs"

    fun load(context: Context): List<WizDevice> {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val json = prefs.getString(BULBS_KEY, "[]") ?: "[]"
        val array = JSONArray(json)
        val devices = mutableListOf<WizDevice>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val mac = item.optString("mac")
            if (mac.isBlank()) continue

            devices.add(
                WizDevice(
                    ip = item.optString("ip", ""),
                    mac = mac,
                    isOn = item.optBoolean("isOn", false),
                    brightness = if (item.has("brightness") &&
                        !item.isNull("brightness")
                    ) {
                        item.optInt("brightness")
                    } else {
                        null
                    },
                    isOnline = item.optBoolean(
                        "isOnline",
                        item.optString("ip").isNotBlank()
                    ),
                    moduleName = if (item.isNull("moduleName")) {
                        null
                    } else {
                        item.optString("moduleName").takeIf { it.isNotBlank() }
                    },
                    color = if (item.has("color") && !item.isNull("color")) {
                        item.optInt("color")
                    } else {
                        null
                    },
                    colorTemp = if (item.has("colorTemp") && !item.isNull("colorTemp")) {
                        item.optInt("colorTemp")
                    } else {
                        null
                    },
                    kelvinMin = if (item.has("kelvinMin") && !item.isNull("kelvinMin")) {
                        item.optInt("kelvinMin")
                    } else {
                        null
                    },
                    kelvinMax = if (item.has("kelvinMax") && !item.isNull("kelvinMax")) {
                        item.optInt("kelvinMax")
                    } else {
                        null
                    }
                )
            )
        }

        return devices
    }

    fun loadName(context: Context, mac: String): String {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        return prefs.getString(
            "name_$mac",
            "WiZ bulb ${mac.takeLast(4)}"
        ) ?: "WiZ bulb ${mac.takeLast(4)}"
    }

    fun save(
        context: Context,
        devices: List<WizDevice>
    ) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val array = JSONArray()

        devices.forEach { device ->
            val item = JSONObject().apply {
                put("mac", device.mac)
                put("isOn", device.isOn)
                put("ip", device.ip)
                put("isOnline", device.isOnline)

                if (device.brightness != null) {
                    put("brightness", device.brightness)
                } else {
                    put("brightness", JSONObject.NULL)
                }

                if (device.moduleName != null) {
                    put("moduleName", device.moduleName)
                } else {
                    put("moduleName", JSONObject.NULL)
                }

                if (device.color != null) {
                    put("color", device.color)
                } else {
                    put("color", JSONObject.NULL)
                }

                if (device.colorTemp != null) {
                    put("colorTemp", device.colorTemp)
                } else {
                    put("colorTemp", JSONObject.NULL)
                }

                if (device.kelvinMin != null) {
                    put("kelvinMin", device.kelvinMin)
                } else {
                    put("kelvinMin", JSONObject.NULL)
                }

                if (device.kelvinMax != null) {
                    put("kelvinMax", device.kelvinMax)
                } else {
                    put("kelvinMax", JSONObject.NULL)
                }
            }

            array.put(item)
        }

        prefs.edit {
            putString(BULBS_KEY, array.toString())
        }
    }

    fun saveName(
        context: Context,
        mac: String,
        name: String
    ) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        prefs.edit {
            putString("name_$mac", name)
        }
    }
}
