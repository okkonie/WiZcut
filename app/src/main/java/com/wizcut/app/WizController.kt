package com.wizcut.app

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WizController {

    private const val PORT = 38899

    fun setPower(device: WizDevice, state: Boolean): WizDevice {
        sendCommand(
            device.ip,
            JSONObject().put("state", state)
        )

        return getPilot(device)
    }

    fun setBrightness(device: WizDevice, brightness: Int): WizDevice {
        val value = brightness.coerceIn(10, 100)

        sendCommand(
            device.ip,
            JSONObject().put("dimming", value)
        )

        return getPilot(device)
    }

    fun setColorTemp(device: WizDevice, kelvin: Int): WizDevice {
        sendCommand(
            device.ip,
            JSONObject().put("temp", kelvin)
        )

        return getPilot(device)
    }

    private fun sendCommand(ip: String, params: JSONObject) {
        require(ip.isNotBlank()) {
            "Bulb IP address is unknown. Run discovery first."
        }

        val message = JSONObject().apply {
            put("method", "setPilot")
            put("params", params)
        }.toString().toByteArray(Charsets.UTF_8)

        DatagramSocket().use { socket ->
            val address = InetAddress.getByName(ip)

            socket.send(
                DatagramPacket(
                    message,
                    message.size,
                    address,
                    PORT
                )
            )
        }
    }

    /**
     * Sends a request without parameters and parses the JSON response.
     * Throws if the bulb does not respond within 2 seconds.
     */
    private fun request(ip: String, method: String): JSONObject {
        require(ip.isNotBlank()) {
            "Bulb IP address is unknown. Run discovery first."
        }

        val message = JSONObject().apply {
            put("method", method)
            put("params", JSONObject())
        }.toString().toByteArray(Charsets.UTF_8)

        DatagramSocket().use { socket ->
            socket.soTimeout = 2000

            val address = InetAddress.getByName(ip)

            socket.send(
                DatagramPacket(
                    message,
                    message.size,
                    address,
                    PORT
                )
            )

            val buffer = ByteArray(4096)
            val response = DatagramPacket(buffer, buffer.size)

            socket.receive(response)

            return JSONObject(
                String(
                    response.data,
                    response.offset,
                    response.length,
                    Charsets.UTF_8
                )
            )
        }
    }

    /**
     * Reads a bulb's current state with a getPilot request. Power and
     * brightness always come back; r/g/b or temp are present depending
     * on the bulb's current mode (color vs. white).
     */
    fun getPilot(device: WizDevice): WizDevice {
        val result = request(device.ip, "getPilot")
            .optJSONObject("result")
            ?: throw Exception("Invalid bulb response")

        val color = if (result.has("r") || result.has("g") || result.has("b")) {
            (result.optInt("r", 0) shl 16) or
                (result.optInt("g", 0) shl 8) or
                result.optInt("b", 0)
        } else {
            null
        }

        val colorTemp = if (result.has("temp")) result.optInt("temp") else null

        return WizDevice(
            ip = device.ip,
            mac = result.optString("mac", device.mac),
            isOn = result.optBoolean("state", device.isOn),
            brightness = result.optInt(
                "dimming",
                device.brightness ?: 100
            ),
            isOnline = true,
            moduleName = device.moduleName,
            color = color ?: device.color,
            colorTemp = colorTemp ?: device.colorTemp,
            kelvinMin = device.kelvinMin,
            kelvinMax = device.kelvinMax
        )
    }

    /**
     * Asks the bulb which module it is (e.g. ESP20_SHRGB_01), which
     * decides whether color and temperature controls are offered.
     * Some firmware also reports an explicit kelvinRange; it is used
     * when present and valid, otherwise the range is derived from the
     * module name in [WizCapabilities].
     */
    fun getSystemConfig(device: WizDevice): WizDevice {
        val result = request(device.ip, "getSystemConfig")
            .optJSONObject("result")
            ?: throw Exception("Invalid bulb response")

        val moduleName = result.optString("moduleName").takeIf { it.isNotBlank() }
            ?: return device

        var updated = device.copy(moduleName = moduleName)

        val kelvin = result.optJSONObject("kelvinRange")

        if (kelvin != null) {
            val min = kelvin.optInt("min", -1)
            val max = kelvin.optInt("max", -1)

            if (min in 1000..10000 && max in 1000..10000 && max > min) {
                updated = updated.copy(kelvinMin = min, kelvinMax = max)
            }
        }

        return updated
    }
}
