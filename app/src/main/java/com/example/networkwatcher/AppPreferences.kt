package com.example.networkwatcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "network_watcher_preferences"
        const val KEY_PHONE_NUMBERS = "phone_numbers"
        const val KEY_DISCONNECT_MSG = "disconnect_message"
        const val KEY_RECONNECT_MSG = "reconnect_message"
        const val KEY_RECONNECT_SMS_ENABLED = "reconnect_sms_enabled"
        const val KEY_DEBOUNCE_DELAY_SEC = "debounce_delay_seconds"
        const val KEY_LOCAL_ALARM_ENABLED = "local_alarm_enabled"
        const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        const val KEY_TELEGRAM_TOKEN = "telegram_token"
        const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        const val KEY_SERVICE_ACTIVE = "service_active"
        const val KEY_LAST_STATE_CONNECTED = "last_state_connected"
        const val KEY_LOGS_JSON = "logs_json"

        const val DEFAULT_DISCONNECT_MSG = "⚠️ تنبيه: انقطع اتصال الواي فاي عن الهاتف المراقَب."
        const val DEFAULT_RECONNECT_MSG = "✅ تنبيه: تمت استعادة اتصال الواي فاي بنجاح."
    }

    var phoneNumbers: String
        get() = prefs.getString(KEY_PHONE_NUMBERS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PHONE_NUMBERS, value.trim()).apply()

    var disconnectMessage: String
        get() = prefs.getString(KEY_DISCONNECT_MSG, DEFAULT_DISCONNECT_MSG) ?: DEFAULT_DISCONNECT_MSG
        set(value) = prefs.edit().putString(KEY_DISCONNECT_MSG, value.trim()).apply()

    var reconnectMessage: String
        get() = prefs.getString(KEY_RECONNECT_MSG, DEFAULT_RECONNECT_MSG) ?: DEFAULT_RECONNECT_MSG
        set(value) = prefs.edit().putString(KEY_RECONNECT_MSG, value.trim()).apply()

    var isReconnectSmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECONNECT_SMS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_RECONNECT_SMS_ENABLED, value).apply()

    var debounceDelaySeconds: Int
        get() = prefs.getInt(KEY_DEBOUNCE_DELAY_SEC, 10)
        set(value) = prefs.edit().putInt(KEY_DEBOUNCE_DELAY_SEC, if (value < 1) 1 else value).apply()

    var isLocalAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_ALARM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCAL_ALARM_ENABLED, value).apply()

    var isTelegramEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TELEGRAM_ENABLED, value).apply()

    var telegramToken: String
        get() = prefs.getString(KEY_TELEGRAM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_TOKEN, value.trim()).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, value.trim()).apply()

    var isServiceActive: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, value).apply()

    var lastStateConnected: Boolean
        get() = prefs.getBoolean(KEY_LAST_STATE_CONNECTED, true)
        set(value) = prefs.edit().putBoolean(KEY_LAST_STATE_CONNECTED, value).apply()

    fun getPhoneList(): List<String> {
        val raw = phoneNumbers
        if (raw.isBlank()) return emptyList()
        return raw.split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    @Synchronized
    fun addLog(log: LogItem) {
        val list = getLogs().toMutableList()
        list.add(0, log)
        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_LOGS_JSON, jsonArray.toString()).apply()
    }

    @Synchronized
    fun getLogs(): List<LogItem> {
        val raw = prefs.getString(KEY_LOGS_JSON, null) ?: return emptyList()
        val list = mutableListOf<LogItem>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val itemObj = array.getJSONObject(i)
                list.add(LogItem.fromJson(itemObj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @Synchronized
    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS_JSON).apply()
    }
}
