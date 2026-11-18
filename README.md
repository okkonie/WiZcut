# WiZcut

A minimal Android app for controlling WiZ smart light bulbs directly over your local network — no cloud, no account, and no third-party libraries. Communication with the bulbs is implemented from scratch using plain UDP messages.

## Features

- **Discover bulbs** – Searches automatically on the first open when nothing is saved yet; otherwise pull to search again. Finds all WiZ bulbs that respond (no pairing required).
- **Power on/off** – Toggle each bulb individually.
- **Brightness control** – Slider to dim a bulb
- **Warmth presets** – Tunable-white bulbs get four chips instead of a second slider: Candle, Warm, Neutral, Cool
- **Device-driven features** – Temperature controls appear only for bulbs that report the capability over `getSystemConfig`. Dimmable-only bulbs show brightness only.
- **Rename bulbs** – Give each bulb a custom name.
- **Launcher shortcuts** – One shortcut per bulb, named after it; the toggle happens in the background without opening the app.
- **Home screen widget** – Pin a widget per bulb. Small widget is a power toggle and brightness down/up buttons.
- **Persistent device list** – Discovered bulbs and their last known state are saved locally.
- **Fresh state on open** – Every bulb is queried directly when the app opens, so the power and brightness shown are always the bulbs' actual state. Unreachable bulbs are dimmed with their controls disabled until they respond again.
- **Fully local** – No cloud calls, no accounts. Two permissions: `INTERNET`, and `CHANGE_WIFI_MULTICAST_STATE` for the multicast lock that keeps broadcast discovery working on phones that filter broadcast UDP.

## How it works

WiZ bulbs expose a simple JSON-over-UDP API on port **38899**. WiZcut uses it directly:

| Action       | Method                                          | Payload                                                                                                                                                                                                                                                               |
| ------------ | ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Discover     | Broadcast `getTime` + `getPilot` to `255.255.255.255:38899`, then unicast `getPilot` to any responder that gave no MAC | Awake bulbs reply with MAC, power state, and dimming level. Bulbs in standby (turned off) ignore broadcast `getPilot`, but they answer broadcast `getTime` (which wakes them) and unicast `getPilot`, so off bulbs are found too |
| Toggle / dim | Unicast `setPilot` to the bulb's IP             | `{"state": true}`, `{"dimming": 50}`, or `{"temp": 4000}`                                                                                                                                                                                                             |
| Read state   | Unicast `getPilot`                              | Every bulb is queried when the app opens, and again after each command to confirm the new state                                                                                                                                                                       |
| Capabilities | Unicast `getSystemConfig`                       | Bulb reports its module name (e.g. `ESP20_SHRGB_01` = RGB + tunable white, `ESP20_TUN_01` = tunable only, `ESP01_DIM_01` = dim only) and, on some firmware, an explicit `kelvinRange`. RGB capability is detected from the module name, but no color picker ships yet |

The UI is a single Jetpack Compose (Material 3) screen: pull to search (the scan runs about 4 seconds), then use the name field, the on/off button next to it, the brightness slider and the warmth chips on each bulb card. There is no search button — the first open searches by itself when no bulbs are saved. Nothing pops up as a toast: the single status line under the title carries the state, and a failed command just shows as a dimmed, disabled card.

Shortcuts are implemented as Android dynamic shortcuts: every saved bulb gets one (id = MAC address, labeled with its name). Each points at an invisible `NoDisplay` trampoline activity, so tapping it reads the bulb's current state and flips it in the background — the app UI never opens, and if it is already open, its cards update live through the shared in-process device repository.

## Requirements

- Android 8.0 (API 26) or newer
- A phone connected to the same Wi-Fi network as your bulbs
- WiZ bulbs that are already connected to your network (initial bulb setup is done with the official WiZ app — this does not configure Wi-Fi credentials for bulbs)

## Building

Open the project in Android Studio, or build from the command line:

```bash
# Debug APK
./gradlew assembleDebug          # output: app/build/outputs/apk/debug/

# Install on a connected device
./gradlew installDebug

# Launch on wireless adb device
adb shell monkey -p com.wizcut.app 1
```

## Project structure

```
app/src/main/java/com/wizcut/app/
├── MainActivity.kt       # Compose UI: discovery screen, bulb list, brightness slider, warmth chips
├── ToggleActivity.kt     # Invisible shortcut trampoline: toggles a bulb, no UI
├── WizShortcuts.kt       # Publishes one dynamic launcher shortcut per bulb
├── WizWidget.kt         # Home screen widget: per-bulb toggle + brightness buttons
├── WidgetConfigActivity.kt # Bulb picker shown when adding a widget
├── WidgetPrefs.kt      # Maps widget instance IDs to bulb MAC addresses
├── DeviceRepository.kt   # Shared bulb list state (UI + shortcuts + widgets stay in sync)
├── WizDiscovery.kt       # UDP broadcast discovery + WizDevice model
├── WizController.kt      # setPilot / getPilot / getSystemConfig commands (power, brightness, temperature)
├── WizCapabilities.kt    # Maps bulb module names to kelvin capability (RGB detection kept, currently unused)
├── WizPreferences.kt     # Saves bulbs and names to SharedPreferences
└── ui/theme/             # Material 3 theme (colors, typography, shapes)
```

## Tech stack

- **Kotlin** 2.2
- **Jetpack Compose** with Material 3 (Compose BOM 2024.09.00)
- **Android Gradle Plugin** 9.1.1, compileSdk 37, targetSdk 36, minSdk 26
- No external dependencies — the WiZ protocol is implemented with `java.net.DatagramSocket`

## Limitations

- Power, brightness and white temperature only — no RGB color picker, no scenes or scheduling (yet).
- Bulbs are identified by IP address, which can change; offline bulbs are dimmed and disabled — pull to search again to refresh their addresses.
- Discovery relies on UDP broadcast, so it won't work across networks with client isolation enabled.
