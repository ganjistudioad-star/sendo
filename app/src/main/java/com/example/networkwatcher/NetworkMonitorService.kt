package com.example.networkwatcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NetworkMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "network_watcher_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STATUS_UPDATE = "com.example.networkwatcher.ACTION_STATUS_UPDATE"
        const val EXTRA_IS_CONNECTED = "extra_is_connected"
    }

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var prefs: AppPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var debounceJob: Job? = null
    private var activeRingtone: Ringtone? = null
    private var isCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            // Cancel any pending disconnect countdown
            debounceJob?.cancel()
            debounceJob = null

            val wasDisconnected = !prefs.lastStateConnected
            if (wasDisconnected) {
                prefs.lastStateConnected = true
                prefs.addLog(
                    LogItem(
                        message = "تمت استعادة اتصال الواي فاي",
                        details = "عادت الشبكة للعمل بنجاح",
                        type = LogType.RECONNECT
                    )
                )

                // Stop any playing alarm
                stopLocalAlarm()

                // Send Reconnect SMS if enabled
                if (prefs.isReconnectSmsEnabled) {
                    val numbers = prefs.getPhoneList()
                    val msg = prefs.reconnectMessage
                    sendSmsAlert(numbers, msg, isReconnect = true)
                }

                // Send Reconnect Telegram if enabled
                if (prefs.isTelegramEnabled) {
                    sendTelegramAlert(prefs.reconnectMessage, isReconnect = true)
                }

                updateNotification("متصل بالواي فاي ✅", "المراقبة مستمرة وتعمل بالخلفية")
                broadcastStatus(isConnected = true)
            } else {
                updateNotification("متصل بالواي فاي ✅", "المراقبة مستمرة وتعمل بالخلفية")
                broadcastStatus(isConnected = true)
            }
        }

        override fun onLost(network: Network) {
            // Debounce delay to prevent false alarms during brief disconnects / roaming
            debounceJob?.cancel()
            val delaySeconds = prefs.debounceDelaySeconds

            debounceJob = serviceScope.launch {
                delay(delaySeconds * 1000L)

                // Still disconnected after delay
                if (prefs.lastStateConnected) {
                    prefs.lastStateConnected = false
                    prefs.addLog(
                        LogItem(
                            message = "انقطع اتصال الواي فاي",
                            details = "تأكد الانقطاع بعد مهلة $delaySeconds ثانية",
                            type = LogType.DISCONNECT
                        )
                    )

                    // Local Alarm & Vibration
                    if (prefs.isLocalAlarmEnabled) {
                        triggerLocalAlarm()
                    }

                    // Send Disconnect SMS
                    val numbers = prefs.getPhoneList()
                    val msg = prefs.disconnectMessage
                    sendSmsAlert(numbers, msg, isReconnect = false)

                    // Send Telegram alert
                    if (prefs.isTelegramEnabled) {
                        sendTelegramAlert(msg, isReconnect = false)
                    }

                    updateNotification("⚠️ تنبيه: انقطع الواي فاي!", "تم إرسال التنبيهات المحددة")
                    broadcastStatus(isConnected = false)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        prefs = AppPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification("المراقبة نشطة", "جاري فحص اتصال الواي فاي..."))
        prefs.isServiceActive = true

        if (!isCallbackRegistered) {
            registerWifiWatcher()
            isCallbackRegistered = true
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.isServiceActive = false
        debounceJob?.cancel()
        stopLocalAlarm()

        if (isCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isCallbackRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerWifiWatcher() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            prefs.addLog(
                LogItem(
                    message = "خطأ في تسجيل مراقب الواي فاي",
                    details = e.localizedMessage ?: "Unknown error",
                    type = LogType.ERROR
                )
            )
        }
    }

    private fun sendSmsAlert(phoneNumbers: List<String>, message: String, isReconnect: Boolean) {
        if (phoneNumbers.isEmpty()) {
            prefs.addLog(
                LogItem(
                    message = "لم يتم إرسال SMS",
                    details = "لم يتم إدخال أي رقم هاتف في الإعدادات",
                    type = LogType.ERROR
                )
            )
            return
        }

        try {
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                prefs.addLog(LogItem(message = "تعذر الوصول إلى SmsManager", type = LogType.ERROR))
                return
            }

            for (phone in phoneNumbers) {
                val cleanPhone = phone.trim()
                if (cleanPhone.isNotEmpty()) {
                    val parts = smsManager.divideMessage(message)
                    if (parts.size > 1) {
                        smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
                    } else {
                        smsManager.sendTextMessage(cleanPhone, null, message, null, null)
                    }
                    prefs.addLog(
                        LogItem(
                            message = if (isReconnect) "تم إرسال SMS عودة الشبكة لـ $cleanPhone" else "تم إرسال SMS انقطاع لـ $cleanPhone",
                            details = message,
                            type = LogType.SMS_SENT
                        )
                    )
                }
            }
        } catch (e: Exception) {
            prefs.addLog(
                LogItem(
                    message = "فشل في إرسال SMS",
                    details = e.localizedMessage ?: "حدث خطأ أثناء الإرسال",
                    type = LogType.ERROR
                )
            )
        }
    }

    private fun sendTelegramAlert(message: String, isReconnect: Boolean) {
        val token = prefs.telegramToken
        val chatId = prefs.telegramChatId
        if (token.isBlank() || chatId.isBlank()) return

        serviceScope.launch(Dispatchers.IO) {
            try {
                val urlString = "https://api.telegram.org/bot$token/sendMessage"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                        "&text=" + URLEncoder.encode(message, "UTF-8")

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    withContext(Dispatchers.Main) {
                        prefs.addLog(
                            LogItem(
                                message = if (isReconnect) "تم إرسال تنبيه Telegram (عودة الاتصال)" else "تم إرسال تنبيه Telegram (انقطاع)",
                                details = "كود الاستجابة: $responseCode",
                                type = LogType.TELEGRAM_SENT
                            )
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        prefs.addLog(
                            LogItem(
                                message = "فشل إرسال Telegram",
                                details = "كود الخطأ: $responseCode",
                                type = LogType.ERROR
                            )
                        )
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    prefs.addLog(
                        LogItem(
                            message = "خطأ اتصال Telegram",
                            details = e.localizedMessage ?: "Timeout / No Route",
                            type = LogType.ERROR
                        )
                    )
                }
            }
        }
    }

    private fun triggerLocalAlarm() {
        try {
            // Vibrate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }

            // Play Alarm Ringtone
            var alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alertUri != null) {
                activeRingtone = RingtoneManager.getRingtone(applicationContext, alertUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activeRingtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                activeRingtone?.play()

                // Auto stop sound after 8 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    stopLocalAlarm()
                }, 8000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopLocalAlarm() {
        try {
            if (activeRingtone?.isPlaying == true) {
                activeRingtone?.stop()
            }
            activeRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun broadcastStatus(isConnected: Boolean) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_CONNECTED, isConnected)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildForegroundNotification(title, text))
    }
}
