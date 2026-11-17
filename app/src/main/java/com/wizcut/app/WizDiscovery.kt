
package com.wizcut.app

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

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

    fun discover(context: Context? = null): List<WizDevice> {
        val devices = linkedMapOf<String, WizDevice>()

        // Some phones filter out broadcast/multicast UDP unless the
        // app holds a multicast lock; hold one during discovery.
        val lock = context?.let {
            try {
                (it.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .createMulticastLock("wizcut-discovery")
                    .apply { setReferenceCounted(false); acquire() }
            } catch (_: Exception) {
                null
            }
        }

        try {
            discoverInternal(devices)
        } finally {
            lock?.release()
        }

        return devices.values.toList()
    }

    private fun discoverInternal(devices: LinkedHashMap<String, WizDevice>) {

        val request = JSONObject().apply {
            put("method", "getPilot")
            put("params", JSONObject())
        }.toString().toByteArray(Charsets.UTF_8)

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 500

            // Some networks drop the limited 255.255.255.255 broadcast,
            // so also send to each interface's subnet broadcast address
            // (e.g. 192.168.1.255). UDP is lossy, so keep retrying
            // while listening for responses.
            val targets = mutableListOf(InetAddress.getByName("255.255.255.255"))
            targets.addAll(collectSubnetBroadcasts())

            val packet = DatagramPacket(
                request,
                request.size,
                targets.first(),
                PORT
            )

            val buffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            var nextSend = 0L

            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                if (System.currentTimeMillis() >= nextSend) {
                    targets.forEach { address ->
                        try {
                            packet.address = address
                            socket.send(packet)
                        } catch (_: Exception) {
                            // Skip unreachable interfaces.
                        }
                    }
                    nextSend = System.currentTimeMillis() + 1000
                }

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
    }

    /**
     * Collects the subnet broadcast address of every up, running
     * interface that has an IPv4 address with a prefix length.
     */
    private fun collectSubnetBroadcasts(): List<InetAddress> {
        val broadcasts = mutableListOf<InetAddress>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
                ?: return broadcasts

            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()

                if (!nif.isUp || nif.isLoopback) continue

                for (address in nif.interfaceAddresses) {
                    val broadcast = address?.broadcast ?: continue
                    broadcasts.add(broadcast)
                }
            }
        } catch (_: Exception) {
            // Ignore; fall back to the limited broadcast only.
        }

        return broadcasts
    }
}
