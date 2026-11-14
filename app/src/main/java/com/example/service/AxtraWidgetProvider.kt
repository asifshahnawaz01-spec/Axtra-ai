package com.example.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.core.AxtraConstants

class AxtraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val isActive = AxtraForegroundService.isServiceRunning.value
        for (widgetId in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = AxtraConstants.ACTION_TALK_TO_AXTRA
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val views = RemoteViews(context.packageName, R.layout.widget_axtra).apply {
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                setOnClickPendingIntent(R.id.widget_mic_button, pendingIntent)
                setTextViewText(
                    R.id.widget_status,
                    context.getString(if (isActive) R.string.widget_status_active else R.string.widget_status_idle)
                )
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
