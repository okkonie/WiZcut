# WiZcut

A minimal Android app for controlling WiZ smart light bulbs directly over your local network — no cloud, no account, and no third-party libraries. Communication with the bulbs is implemented from scratch using plain UDP messages.

## Features

- **Discover bulbs** – Broadcasts on the local network and finds all WiZ bulbs that respond (no pairing required).
- **Power on/off** – Toggle each bulb individually.
- **Brightness control** – Drag a slider to dim a bulb from 10% to 100%; the current percentage is shown next to the slider.
- **White temperature** – Bulbs that support tunable white get a slider from warm to cool with the current kelvin value shown. The kelvin range comes from the bulb itself (its reported `kelvinRange`, or its module type; WiZ tunable bulbs are 2700–6500 K).
- **RGB color** – Color-capable bulbs get a hue/saturation picker with a live preview circle, sent as plain `r`/`g`/`b` values.
- **Device-driven features** – Color and temperature controls appear only for bulbs that report the capability over `getSystemConfig`. Dimmable-only bulbs show brightness only; nothing is assumed.
- **Rename bulbs** – Give each bulb a custom name, kept across launches.
- **Launcher shortcuts** – Long-press the app icon to toggle any bulb straight from the home screen. One shortcut per bulb, named after it; the toggle happens in the background without opening the app, confirmed by a toast.
- **Home screen widget** – Pin a widget per bulb (transparent background, dark `#FF151515` button circles). Small widget is a power toggle; wider (or taller) widgets add brightness down/up buttons. Power button mirrors the app: white circle with a dark bulb while on, gray circle with a light bulb while off. Brightness buttons dim at limits and when offline.
- **Persistent device list** – Discovered bulbs and their last known state are saved locally, so the app opens instantly with your bulbs ready.
- **Fresh state on open** – Every bulb is queried directly when the app opens, so the power and brightness shown are always the bulbs' actual state, not a stale snapshot. Unreachable bulbs are dimmed with their controls disabled until they respond again.
- **Fully local** – The only permission the app requests is `INTERNET`. Nothing leaves your Wi-Fi network.

## How it works

WiZ bulbs expose a simple JSON-over-UDP API on port **38899**. WiZcut uses it directly:

| Action       | Method                                          | Payload                                                                                                                                                                               |
| ------------ | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Discover     | Broadcast `getPilot` to `255.255.255.255:38899` | Every bulb replies with its MAC, power state, and dimming level                                                                                                                       |
| Toggle / dim | Unicast `setPilot` to the bulb's IP             | `{"state": true}`, `{"dimming": 50}`, or `{"temp": 4000}`                                                                                                                             |
| Read state   | Unicast `getPilot`                              | Every bulb is queried when the app opens, and again after each command to confirm the new state                                                                                       |
| Capabilities | Unicast `getSystemConfig`                       | Bulb reports its module name (e.g. `ESP20_SHRGB_01` = RGB + tunable white, `ESP20_TUN_01` = tunable only, `ESP01_DIM_01` = dim only) and, on some firmware, an explicit `kelvinRange` |

The UI is a single Jetpack Compose (Material 3) screen: tap **Find bulbs** to scan for 4 seconds, then use the name field, the on/off button next to it, and the sliders on each bulb card.

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
├── MainActivity.kt       # Compose UI: discovery screen, bulb list, controls
├── ToggleActivity.kt     # Invisible shortcut trampoline: toggles a bulb, no UI
├── WizShortcuts.kt       # Publishes one dynamic launcher shortcut per bulb
├── WizWidget.kt         # Home screen widget: per-bulb toggle + brightness buttons
├── WidgetConfigActivity.kt # Bulb picker shown when adding a widget
├── WidgetPrefs.kt      # Maps widget instance IDs to bulb MAC addresses
├── DeviceRepository.kt   # Shared bulb list state (UI + shortcuts + widgets stay in sync)
├── WizDiscovery.kt       # UDP broadcast discovery + WizDevice model
├── WizController.kt      # setPilot / getPilot / getSystemConfig commands (power, brightness, temperature)
├── WizCapabilities.kt    # Maps bulb module names to RGB / kelvin capabilities
├── WizPreferences.kt     # Saves bulbs and names to SharedPreferences
└── ui/theme/             # Material 3 theme (colors, typography, shapes)
```

## Tech stack

- **Kotlin** 2.2
- **Jetpack Compose** with Material 3 (Compose BOM 2024.09.00)
- **Android Gradle Plugin** 9.1.1, compileSdk 37, targetSdk 36, minSdk 26
- No external dependencies — the WiZ protocol is implemented with `java.net.DatagramSocket`

## Limitations

- Power, brightness, white temperature, and RGB color — no scenes or scheduling (yet).
- Bulbs are identified by IP address, which can change; offline bulbs are dimmed and disabled — re-run **Find bulbs** to refresh their addresses and bring them back.
- Discovery relies on UDP broadcast, so it won't work across networks with client isolation enabled (common on guest Wi-Fi).
