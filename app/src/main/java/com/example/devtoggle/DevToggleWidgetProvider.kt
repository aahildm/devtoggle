package com.example.devtoggle

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

/**
 * Home screen widget: a single tap toggles development_settings_enabled
 * via Shizuku, same underlying logic as the QS tile and the in-app switch.
 */
class DevToggleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.example.devtoggle.ACTION_TOGGLE_WIDGET"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, DevToggleWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id, null)
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int, knownState: Boolean?) {
            val views = RemoteViews(context.packageName, R.layout.widget_dev_toggle)

            val toggleIntent = Intent(context, DevToggleWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, widgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            if (knownState == null) {
                views.setTextViewText(R.id.widgetLabel, "Dev Options")
                views.setTextViewText(R.id.widgetState, "Tap to check")
            } else {
                views.setTextViewText(R.id.widgetLabel, "Dev Options")
                views.setTextViewText(R.id.widgetState, if (knownState) "ON" else "OFF")
            }

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, null)
        }
        // Best-effort read so the widget shows real state right after being placed.
        if (DevToggleController.isShizukuReady()) {
            DevToggleController.readState(
                context = context,
                onResult = { enabled ->
                    val manager = AppWidgetManager.getInstance(context)
                    for (id in appWidgetIds) {
                        updateWidget(context, manager, id, enabled)
                    }
                }
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            if (!DevToggleController.isShizukuReady()) {
                Toast.makeText(context, "Open DevToggle app first to grant Shizuku permission", Toast.LENGTH_LONG).show()
                return
            }
            DevToggleController.toggle(
                context = context,
                onResult = { newState ->
                    updateAllWidgets(context)
                    Toast.makeText(
                        context,
                        "Dev options ${if (newState) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onError = { message ->
                    Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
