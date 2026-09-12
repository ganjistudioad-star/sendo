package com.example.networkwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = AppPreferences(context)
            if (prefs.isServiceActive && prefs.getPhoneList().isNotEmpty()) {
                val serviceIntent = Intent(context, NetworkMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                prefs.addLog(
                    LogItem(
                        message = "تمت إعادة تشغيل الخدمة تلقائياً بعد إقلاع الهاتف",
                        type = LogType.INFO
                    )
                )
            }
        }
    }
}
