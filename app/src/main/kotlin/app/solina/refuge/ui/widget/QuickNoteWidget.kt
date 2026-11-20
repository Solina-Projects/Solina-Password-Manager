package app.solina.refuge.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.solina.refuge.MainActivity
import app.solina.refuge.R

/**
 * ویجت «یادداشت سریع» روی صفحهٔ خانه (فاز ۳).
 * ویجت هیچ محتوایی نشان نمی‌دهد — نه عنوان، نه متن یادداشت — فقط یک دکمه است
 * که ویرایشگر یادداشت سریع را داخل برنامه باز می‌کند. پس حتی وقتی خزانه قفل است
 * هم چیزی از یادداشت‌ها روی صفحهٔ خانه لو نمی‌رود؛ بازشدن ویرایشگر یعنی بازکردن
 * قفل خزانه در مسیر معمول برنامه.
 */
class QuickNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_QUICK_NOTE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_quick_note)
            views.setOnClickPendingIntent(R.id.widget_quick_note_button, pending)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
