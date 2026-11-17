package com.wizcut.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.concurrent.thread

/**
 * Home screen widget for a single bulb, responsive to its size:
 * - 1x1: power toggle only
 * - 3 cells wide (or tall): brightness down, power, brightness up
 *
 * Brightness buttons dim when the bulb is at 10% / 100% (or offline).
 * The power button is white while on, gray while off.
 *
 * Button taps arrive as broadcasts to this provider. The bulb commands
 * are blocking UDP calls, so they run on a worker thread while the
 * broadcast is kept alive with [goAsync].
 */
class WizWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            render(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_BRIGHTNESS_DOWN, ACTION_BRIGHTNESS_UP, ACTION_TOGGLE -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val pendingResult = goAsync()

                    thread {
                        try {
                            handleAction(context, appWidgetId, intent.action)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            WidgetPrefs.clear(context, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // Widget was resized: re-render, possibly switching layout.
        render(context, appWidgetManager, appWidgetId)
    }

    companion object {

        const val ACTION_TOGGLE = "com.wizcut.app.action.WIDGET_TOGGLE"
        const val ACTION_BRIGHTNESS_UP = "com.wizcut.app.action.WIDGET_BRIGHTNESS_UP"
        const val ACTION_BRIGHTNESS_DOWN = "com.wizcut.app.action.WIDGET_BRIGHTNESS_DOWN"

        private const val SLOT_BRIGHTNESS_DOWN = 0
        private const val SLOT_POWER = 1
        private const val SLOT_BRIGHTNESS_UP = 2

        /**
         * A row/column of three 44dp buttons with 12dp gaps and 8dp
         * padding needs ~172dp. Below this in both dimensions the
         * widget shrinks to the power button only.
         */
        private const val MIN_FULL_SIZE_DP = 170

        private const val ICON_DIM = 0x66FFFFFF

        /**
         * Picks the layout for the widget's current size. Uses the
         * portrait dimensions reported by the launcher; a widget at
         * least 3 cells in one dimension shows all three buttons,
         * anything smaller shows only the power toggle.
         */
        private fun layoutFor(options: Bundle?): Int {
            if (options == null) return R.layout.wiz_widget_row

            val width = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0
            )
            val height = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0
            )

            // Options not populated yet: assume the default add size.
            if (width == 0 && height == 0) return R.layout.wiz_widget_row

            return when {
                width >= MIN_FULL_SIZE_DP -> R.layout.wiz_widget_row
                height >= MIN_FULL_SIZE_DP -> R.layout.wiz_widget_column
                else -> R.layout.wiz_widget_compact
            }
        }

        /**
         * Builds the RemoteViews for one widget instance: picks the
         * size-appropriate layout and applies the button states.
         */
        fun render(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val layout = layoutFor(appWidgetManager.getAppWidgetOptions(appWidgetId))
            val views = RemoteViews(context.packageName, layout)

            DeviceRepository.ensureLoaded(context)

            val mac = WidgetPrefs.macFor(context, appWidgetId)
            val device = mac?.let { wanted ->
                DeviceRepository.devices.firstOrNull { it.mac == wanted }
            }

            val online = device != null && device.isOnline
            val isOn = online && device!!.isOn
            val brightness = device?.brightness ?: 100

            // Power button uses the same circle as the brightness
            // buttons; only the icon tint reflects the state.
            views.setInt(
                R.id.btnPowerIcon,
                "setColorFilter",
                if (isOn) Color.WHITE else ICON_DIM
            )

            if (layout != R.layout.wiz_widget_compact) {
                // Brightness buttons dim at their limits or when offline.
                val downEnabled = online && brightness > 10
                val upEnabled = online && brightness < 100

                views.setInt(
                    R.id.btnBrightnessDown,
                    "setBackgroundResource",
                    if (downEnabled) R.drawable.widget_button_circle
                    else R.drawable.widget_button_circle_dim
                )
                views.setInt(
                    R.id.btnBrightnessDownIcon,
                    "setColorFilter",
                    if (downEnabled) Color.WHITE else ICON_DIM
                )
                views.setInt(
                    R.id.btnBrightnessUp,
                    "setBackgroundResource",
                    if (upEnabled) R.drawable.widget_button_circle
                    else R.drawable.widget_button_circle_dim
                )
                views.setInt(
                    R.id.btnBrightnessUpIcon,
                    "setColorFilter",
                    if (upEnabled) Color.WHITE else ICON_DIM
                )
            }

            views.setOnClickPendingIntent(
                R.id.btnPower,
                pendingIntent(context, appWidgetId, ACTION_TOGGLE, SLOT_POWER)
            )

            if (layout != R.layout.wiz_widget_compact) {
                views.setOnClickPendingIntent(
                    R.id.btnBrightnessDown,
                    pendingIntent(
                        context,
                        appWidgetId,
                        ACTION_BRIGHTNESS_DOWN,
                        SLOT_BRIGHTNESS_DOWN
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.btnBrightnessUp,
                    pendingIntent(
                        context,
                        appWidgetId,
                        ACTION_BRIGHTNESS_UP,
                        SLOT_BRIGHTNESS_UP
                    )
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Runs a widget button action on the calling (worker) thread:
         * reads the bulb's real state, applies the change, persists it,
         * and re-renders the widget.
         */
        private fun handleAction(
            context: Context,
            appWidgetId: Int,
            action: String?
        ) {
            val mac = WidgetPrefs.macFor(context, appWidgetId)

            if (mac == null) {
                render(context, AppWidgetManager.getInstance(context), appWidgetId)
                return
            }

            DeviceRepository.ensureLoaded(context)

            val device = DeviceRepository.devices.firstOrNull { it.mac == mac }

            if (device == null) {
                render(context, AppWidgetManager.getInstance(context), appWidgetId)
                return
            }

            try {
                // Base the change on the bulb's real state so the widget
                // stays correct even if it was changed by another app.
                val fresh = WizController.getPilot(device)

                val updated = when (action) {
                    ACTION_TOGGLE -> WizController.setPower(fresh, !fresh.isOn)
                    ACTION_BRIGHTNESS_UP -> WizController.setBrightness(
                        fresh,
                        (fresh.brightness ?: 100) + 10
                    )
                    ACTION_BRIGHTNESS_DOWN -> WizController.setBrightness(
                        fresh,
                        (fresh.brightness ?: 100) - 10
                    )
                    else -> fresh
                }

                DeviceRepository.update(context, updated)
            } catch (e: Exception) {
                DeviceRepository.update(context, device.copy(isOnline = false))
            } finally {
                render(context, AppWidgetManager.getInstance(context), appWidgetId)
            }
        }

        /**
         * Unique request code per widget + button so PendingIntents of
         * different widgets never collide.
         */
        private fun pendingIntent(
            context: Context,
            appWidgetId: Int,
            action: String,
            slot: Int
        ): PendingIntent {
            val intent = Intent(context, WizWidget::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            return PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
