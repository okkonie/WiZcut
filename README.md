# WiZcut

A minimal Android app for controlling [WiZ](https://www.wizconnected.com/) smart light bulbs directly over your local network — no cloud, no account, and no third-party libraries. Communication with the bulbs is implemented from scratch using plain UDP datagrams.

## Features

- **Discover bulbs** – Broadcasts on the local network and finds all WiZ bulbs that respond (no pairing required).
- **Power on/off** – Toggle each bulb individually.
- **Brightness control** – Drag a slider to dim a bulb from 10% to 100%.
- **Rename bulbs** – Give each bulb a custom name, kept across launches.
- **Launcher shortcuts** – Long-press the app icon to toggle any bulb straight from the home screen. One shortcut per bulb, named after it; the toggle happens in the background without opening the app, confirmed by a toast.
- **Persistent device list** – Discovered bulbs and their last known state are saved locally, so the app opens instantly with your bulbs ready.
- **Fresh state on open** – Every bulb is queried directly when the app opens, so the power and brightness shown are always the bulbs' actual state, not a stale snapshot. Unreachable bulbs are dimmed with their controls disabled until they respond again.
- **Fully local** – The only permission the app requests is `INTERNET`. Nothing leaves your Wi-Fi network.

## How it works

WiZ bulbs expose a simple JSON-over-UDP API on port **38899**. WiZcut uses it directly:

| Action       | Method                                          | Payload                                                                                         |
| ------------ | ----------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Discover     | Broadcast `getPilot` to `255.255.255.255:38899` | Every bulb replies with its MAC, power state, and dimming level                                 |
| Toggle / dim | Unicast `setPilot` to the bulb's IP             | `{"state": true}` or `{"dimming": 50}`                                                          |
| Read state   | Unicast `getPilot`                              | Every bulb is queried when the app opens, and again after each command to confirm the new state |

The UI is a single Jetpack Compose (Material 3) screen: tap **Find bulbs** to scan for 4 seconds, then use the name field, slider, and switch on each bulb card.

Shortcuts are implemented as Android dynamic shortcuts: every saved bulb gets one (id = MAC address, labeled with its name). Each points at an invisible `NoDisplay` trampoline activity, so tapping it reads the bulb's current state and flips it in the background — the app UI never opens, and if it is already open, its cards update live through the shared in-process device repository.

## Requirements

- Android 8.0 (API 26) or newer
- A phone connected to the same Wi-Fi network as your bulbs
- WiZ bulbs that are already connected to your network (initial bulb setup is done with the official WiZ app — WiZcut does not configure Wi-Fi credentials for bulbs)

## Building

Open the project in [Android Studio](https://developer.android.com/studio) and run it on a device or emulator, or build from the command line:

```bash
# Debug APK
./gradlew assembleDebug          # output: app/build/outputs/apk/debug/

# Run unit tests
./gradlew test

# Install on a connected device
./gradlew installDebug

# Launch on wireless adb device
adb shell monkey -p com.wizvut.app 1
```

## Project structure

```
app/src/main/java/com/wizcut/app/
├── MainActivity.kt       # Compose UI: discovery screen, bulb list, controls
├── ToggleActivity.kt     # Invisible shortcut trampoline: toggles a bulb, no UI
├── WizShortcuts.kt       # Publishes one dynamic launcher shortcut per bulb
├── DeviceRepository.kt   # Shared bulb list state (UI + shortcuts stay in sync)
├── WizDiscovery.kt       # UDP broadcast discovery + WizDevice model
├── WizController.kt      # setPilot / getPilot commands (power, brightness)
├── WizPreferences.kt     # Saves bulbs and names to SharedPreferences
└── ui/theme/             # Material 3 theme (colors, typography, shapes)
```

## Tech stack

- **Kotlin** 2.2
- **Jetpack Compose** with Material 3 (Compose BOM 2024.09.00)
- **Android Gradle Plugin** 9.1.1, compileSdk 37, targetSdk 36, minSdk 26
- No external dependencies — the WiZ protocol is implemented with `java.net.DatagramSocket`

## Limitations

- On/off and brightness only — no color, color temperature, scenes, or scheduling (yet).
- Bulbs are identified by IP address, which can change; offline bulbs are dimmed and disabled — re-run **Find bulbs** to refresh their addresses and bring them back.
- Discovery relies on UDP broadcast, so it won't work across networks with client isolation enabled (common on guest Wi-Fi).

## TODO

- [ ] app icon
- [ ] color + color temperature selection
