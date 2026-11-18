# WiZcut

Android app (Kotlin, Jetpack Compose) controlling WiZ bulbs over local UDP. No cloud, no third-party libs. Package: `com.wizcut.app`.

## Key facts

- WiZ protocol: JSON over UDP, port 38899. `getPilot` (broadcast for discovery, unicast for state), `setPilot` (power/dimming/temp), `getSystemConfig` (module name → capabilities). Implemented in `WizDiscovery.kt` / `WizController.kt` with `java.net.DatagramSocket`.
- Temp controls must come from the bulb: `getSystemConfig` reports module name (TUN/TW → kelvin, 2700–6500 K default, device `kelvinRange` overrides). Bulbs that don't identify themselves get brightness only. Never assume capabilities. RGB capability is detected but the picker was removed — reuse `supportsRgb` when bringing it back.
- All bulb state flows through `DeviceRepository.kt` — shared by UI, shortcuts, and widgets. Keep it that way.
- Icons: shared vector drawables in `res/drawable/` (`lightbulb_24`, `arrow_up_24`, `arrow_down_24`) are used by the Compose UI (`painterResource`), the widget (RemoteViews), and the launcher shortcuts. They carry no hardcoded tint — callers tint them. The launcher icon reuses the `lightbulb_24` path (white on `#FF151515`).
- Launcher shortcuts: one per bulb (id = MAC), toggle via invisible `ToggleActivity` (NoDisplay), no app UI.
- Widgets: one per bulb via `WizWidget.kt` (AppWidgetProvider). Config picker `WidgetConfigActivity.kt`; widget→MAC mapping in `WidgetPrefs.kt` (separate from `WizPreferences`). Widget commands run on worker thread with `goAsync()`.
- Bulbs persisted in `WizPreferences.kt` (SharedPreferences). Keyed by MAC, not IP — IPs change.
- UI: single Material 3 screen. Query all bulbs on open; dim/disable unreachable ones.
- minSdk 26, targetSdk 36, compileSdk 37, Kotlin 2.2, AGP 9.1.1. No new external dependencies.

## Rules

- Scope is power, brightness, and kelvin. RGB picker removed for now — don't re-add it unless asked. Scenes not started — don't half-add them.
- Keep protocol code dependency-free (`java.net` only). No coroutines/Flow rewrites unless asked.
- Network I/O off main thread. UDP broadcasts block ~4s during scan.
- Only permission allowed: `INTERNET`.

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
```
