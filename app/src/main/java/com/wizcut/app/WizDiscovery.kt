
package com.wizcut.app

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class WizDevice(
    val ip: String,
    val mac: String,
    val isOn: Boolean,
    val brightness: Int?,
    val isOnline: Boolean = false
)

object WizDiscovery {

    private const val PORT = 38899
    private const val TIMEOUT_MS = 4000

    fun discover(): List<WizDevice> {
        val devices = linkedMapOf<String, WizDevice>()

        val request = JSONObject().apply {
            put("method", "getPilot")
            put("params", JSONObject())
        }.toString().toByteArray(Charsets.UTF_8)

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 500

            val broadcastAddress = InetAddress.getByName("255.255.255.255")

            val packet = DatagramPacket(
                request,
                request.size,
                broadcastAddress,
                PORT
            )

            socket.send(packet)

            val buffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                try {
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

                    val result = json.optJSONObject("result") ?: continue

                    val mac = result.optString("mac")
                    if (mac.isBlank()) continue

                    val device = WizDevice(
                        ip = response.address.hostAddress ?: continue,
                        mac = mac,
                        isOn = result.optBoolean("state"),
                        brightness = if (result.has("dimming")) {
                            result.optInt("dimming")
                        } else {
                            null
                        },
                        isOnline = true
                    )

                    devices[mac] = device

                } catch (_: java.net.SocketTimeoutException) {
                    // Continue listening until the discovery timeout.
                } catch (_: Exception) {
                    // Ignore malformed or unrelated UDP responses.
                }
            }
        }

        return devices.values.toList()
    }
}
