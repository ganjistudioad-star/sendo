package com.example.networkwatcher

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogType {
    DISCONNECT,
    RECONNECT,
    SMS_SENT,
    TELEGRAM_SENT,
    ERROR,
    INFO
}

data class LogItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val details: String = "",
    val type: LogType = LogType.INFO
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("timestamp", timestamp)
        json.put("message", message)
        json.put("details", details)
        json.put("type", type.name)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): LogItem {
            val typeStr = json.optString("type", LogType.INFO.name)
            val logType = try {
                LogType.valueOf(typeStr)
            } catch (e: Exception) {
                LogType.INFO
            }
            return LogItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                message = json.optString("message", ""),
                details = json.optString("details", ""),
                type = logType
            )
        }
    }
}
