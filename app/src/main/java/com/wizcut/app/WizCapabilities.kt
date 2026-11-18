package com.wizcut.app

/**
 * Derives what a bulb can do from its WiZ module name, which the bulb
 * itself reports through a `getSystemConfig` request.
 *
 * Module names encode their feature set, e.g.:
 * - ESP20_SHRGB_01  – dimming + RGB color + tunable white
 * - ESP20_TUN_01    – dimming + tunable white (no RGB)
 * - ESP01_DIM_01    – dimming only
 * - ESP20_SOCKET_01 – on/off relay, no light control
 *
 * WiZ tunable-white and RGB bulbs cover 2700–6500 K; some firmware
 * also reports an explicit `kelvinRange` in getSystemConfig, which is
 * parsed in [WizController.getSystemConfig] and takes precedence.
 */
object WizCapabilities {

    /** Documented WiZ range for tunable-white and color bulbs. */
    const val KELVIN_MIN = 2700
    const val KELVIN_MAX = 6500

    fun supportsRgb(moduleName: String): Boolean {
        val name = moduleName.uppercase()
        return "RGB" in name || "COLOR" in name
    }

    fun supportsKelvin(moduleName: String): Boolean {
        if (supportsRgb(moduleName)) return true
        val name = moduleName.uppercase()
        return "TUN" in name || "TW" in name
    }

    fun kelvinRange(moduleName: String): IntRange? {
        return if (supportsKelvin(moduleName)) {
            KELVIN_MIN..KELVIN_MAX
        } else {
            null
        }
    }
}
