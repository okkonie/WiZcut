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
     * Reads a bulb's current power and brightness state with a getPilot
     * request. Throws if the bulb does not respond within 2 seconds.
     */
    fun getPilot(device: WizDevice): WizDevice {
        require(device.ip.isNotBlank()) {
            "Bulb IP address is unknown. Run discovery first."
        }

        val message = JSONObject().apply {
            put("method", "getPilot")
            put("params", JSONObject())
        }.toString().toByteArray(Charsets.UTF_8)

        DatagramSocket().use { socket ->
            socket.soTimeout = 2000

            val address = InetAddress.getByName(device.ip)

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

            val json = JSONObject(
                String(
                    response.data,
                    response.offset,
                    response.length,
                    Charsets.UTF_8
                )
            )

            val result = json.optJSONObject("result")
                ?: throw Exception("Invalid bulb response")

            return WizDevice(
                ip = device.ip,
                mac = result.optString("mac", device.mac),
                isOn = result.optBoolean("state", device.isOn),
                brightness = result.optInt(
                    "dimming",
                    device.brightness ?: 100
                ),
                isOnline = true
            )
        }
    }
}