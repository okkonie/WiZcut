package com.wizcut.app

import android.content.Context

/**
 * Maps each home screen widget instance to the MAC address of the bulb
 * it controls. Kept separate from [WizPreferences] so widget plumbing
 * stays isolated from bulb storage.
 */
object WidgetPrefs {

    private const val PREFS_NAME = "wiz_widget_prefs"

    fun pin(context: Context, appWidgetId: Int, mac: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("widget_$appWidgetId", mac)
            .apply()
    }

    fun macFor(context: Context, appWidgetId: Int): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("widget_$appWidgetId", null)
    }

    fun clear(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("widget_$appWidgetId")
            .apply()
    }
}
