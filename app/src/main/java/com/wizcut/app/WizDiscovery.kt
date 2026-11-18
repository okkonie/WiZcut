
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
    val isOnline: Boolean = false,
    val moduleName: String? = null,
    val colorTemp: Int? = null,
    val kelvinMin: Int? = null,
    val kelvinMax: Int? = null
) {
    /** True only once the bulb reported its module name. */
    val capabilitiesKnown: Boolean get() = moduleName != null

    val supportsRgb: Boolean
        get() = moduleName?.let { WizCapabilities.supportsRgb(it) } == true

    /** Kelvin range reported by the bulb, or derived from its module name. */
    val kelvinRange: IntRange?
        get() {
            if (kelvinMin != null && kelvinMax != null && kelvinMax > kelvinMin) {
                return kelvinMin..kelvinMax
            }
            return moduleName?.let { WizCapabilities.kelvinRange(it) }
        }
}

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

    /**
     * Broadcast round. Two requests go out every second:
     *
     * - `getTime` — the wake probe. WiZ bulbs in standby (turned off)
     *   ignore broadcast `getPilot`, but they do answer broadcast
     *   `getTime`, which wakes them.
     * - `getPilot` — state for awake bulbs, answered directly.
     *
     * A responder that only sent `getTime` (no mac) is asked afterwards
     * with a unicast `getPilot`; standby bulbs answer unicast requests.
     */
    private fun discoverInternal(devices: LinkedHashMap<String, WizDevice>) {

        val pilotRequest = JSONObject().apply {
            put("method", "getPilot")
            put("params", JSONObject())
        }.toString().toByteArray(Charsets.UTF_8)

        val wakeRequest = JSONObject().apply {
            put("method", "getTime")
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

            val buffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            var nextSend = 0L

            // Addresses that answered but never identified themselves.
            val woke = linkedMapOf<String, InetAddress>()

            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                if (System.currentTimeMillis() >= nextSend) {
                    targets.forEach { address ->
                        try {
                            socket.send(
                                DatagramPacket(
                                    wakeRequest,
                                    wakeRequest.size,
                                    address,
                                    PORT
                                )
                            )
                            socket.send(
                                DatagramPacket(
                                    pilotRequest,
                                    pilotRequest.size,
                                    address,
                                    PORT
                                )
                            )
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

                    val ip = response.address.hostAddress ?: continue
                    val mac = result.optString("mac")

                    if (mac.isBlank()) {
                        // A `getTime` reply: the bulb is awake, but this
                        // firmware answered no pilot request yet.
                        woke[ip] = response.address
                        continue
                    }

                    devices[mac] = WizDevice(
                        ip = ip,
                        mac = mac,
                        isOn = result.optBoolean("state"),
                        brightness = if (result.has("dimming")) {
                            result.optInt("dimming")
                        } else {
                            null
                        },
                        isOnline = true
                    )

                } catch (_: java.net.SocketTimeoutException) {
                    // Continue listening until the discovery timeout.
                } catch (_: Exception) {
                    // Ignore malformed or unrelated UDP responses.
                }
            }

            // Ask each woken address directly for its pilot state.
            val pending = woke.filterKeys { ip ->
                devices.values.none { device -> device.ip == ip }
            }
            socket.setSoTimeout(1500)

            for ((ip, address) in pending) {
                try {
                    socket.send(
                        DatagramPacket(
                            pilotRequest,
                            pilotRequest.size,
                            address,
                            PORT
                        )
                    )

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

                    devices[mac] = WizDevice(
                        ip = ip,
                        mac = mac,
                        isOn = result.optBoolean("state"),
                        brightness = if (result.has("dimming")) {
                            result.optInt("dimming")
                        } else {
                            null
                        },
                        isOnline = true
                    )
                } catch (_: Exception) {
                    // Not a WiZ bulb, or it stayed asleep.
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
