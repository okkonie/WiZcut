# WiZcut

Android app (Kotlin, Jetpack Compose) controlling WiZ bulbs over local UDP. No cloud, no third-party libs. Package: `com.wizcut.app`.

## Key facts

- WiZ protocol: JSON over UDP, port 38899. `getPilot` (broadcast for discovery, unicast for state), `setPilot` (power/dimming/temp), `getSystemConfig` (module name → capabilities). Implemented in `WizDiscovery.kt` / `WizController.kt` with `java.net.DatagramSocket`.
- Bulbs in standby (turned off) ignore broadcast `getPilot` — they answer broadcast `getTime` (which wakes them) and unicast `getPilot` only. `WizDiscovery` sends `getTime` as a wake probe alongside `getPilot`, then unicasts `getPilot` to responders that never sent a mac. Without this, off bulbs are invisible.
- Temp controls must come from the bulb: `getSystemConfig` reports module name (TUN/TW → kelvin, 2700–6500 K default, device `kelvinRange` overrides). Bulbs that don't identify themselves get brightness only. Never assume capabilities. RGB capability is detected but the picker was removed — reuse `supportsRgb` when bringing it back.
- Warmth UI is four chips, not a slider (`WarmthControls` / `WarmthChip` in `MainActivity.kt`): Candle at `kelvinMin`, Cool at 70% of `kelvinMax` (top of the range is too cold to offer), Warm/Neutral splitting that span in thirds, rounded to 100 K. Chip tint comes from `warmthColor(kelvin)` in `ui/theme/Color.kt`; selected chip filled solid. Tap sends `{"temp": N}` immediately. Keep it distinct from the brightness slider — never stack two identical sliders.
- All bulb state flows through `DeviceRepository.kt` — shared by UI, shortcuts, and widgets. Keep it that way.
- Icons: shared vector drawables in `res/drawable/` (`lightbulb_24`, `arrow_up_24`, `arrow_down_24`) are used by the Compose UI (`painterResource`), the widget (RemoteViews), and the launcher shortcuts. They carry no hardcoded tint — callers tint them. The launcher icon reuses the `lightbulb_24` path (white on `#FF151515`).
- Launcher shortcuts: one per bulb (id = MAC), toggle via invisible `ToggleActivity` (NoDisplay), no app UI.
- Widgets: one per bulb via `WizWidget.kt` (AppWidgetProvider). Config picker `WidgetConfigActivity.kt`; widget→MAC mapping in `WidgetPrefs.kt` (separate from `WizPreferences`). Widget commands run on worker thread with `goAsync()`.
- Bulbs persisted in `WizPreferences.kt` (SharedPreferences). Keyed by MAC, not IP — IPs change.
- UI: single Material 3 screen — name field, power button, brightness slider, warmth chips. No search button: the app searches by itself on first open when nothing is saved, and pull to search re-searches (`searchBulbs` in `MainActivity.kt`, `PullToRefreshBox`). The header status line is the only progress readout (searching, bulb count, no bulbs, failed). No toasts in the app UI: a failed command marks the bulb offline, which dims and disables the card, and the control snaps back to the bulb's real state (`ToggleActivity.kt` still toasts — launcher shortcuts have no UI). Query all saved bulbs on open; dim/disable unreachable ones.
- Bulb RGB state is no longer read or stored (`WizDevice.color` dropped). Only kelvin state persists.
- minSdk 26, targetSdk 36, compileSdk 37, Kotlin 2.2, AGP 9.1.1. No new external dependencies.

## Rules

- Scope is power, brightness, and kelvin. RGB picker removed for now — don't re-add it unless asked. Scenes not started — don't half-add them.
- Keep protocol code dependency-free (`java.net` only). No coroutines/Flow rewrites unless asked.
- Network I/O off main thread. UDP broadcasts block ~4s during scan.
- Permissions: `INTERNET` plus `CHANGE_WIFI_MULTICAST_STATE` (multicast lock in `WizDiscovery.discover`, needed or broadcasts get filtered). Nothing else declared. No cloud endpoints, no new permissions.

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
```
