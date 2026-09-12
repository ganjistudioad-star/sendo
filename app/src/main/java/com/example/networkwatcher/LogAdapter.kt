package com.example.networkwatcher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter(private var items: List<LogItem>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        val logMessage: TextView = itemView.findViewById(R.id.logMessage)
        val logDetails: TextView = itemView.findViewById(R.id.logDetails)
        val logTimestamp: TextView = itemView.findViewById(R.id.logTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = items[position]
        holder.logMessage.text = item.message
        holder.logTimestamp.text = item.formattedTime

        if (item.details.isNotBlank()) {
            holder.logDetails.text = item.details
            holder.logDetails.visibility = View.VISIBLE
        } else {
            holder.logDetails.visibility = View.GONE
        }

        val indicatorColor = when (item.type) {
            LogType.DISCONNECT -> Color.parseColor("#D32F2F") // Red
            LogType.RECONNECT -> Color.parseColor("#388E3C")  // Green
            LogType.SMS_SENT -> Color.parseColor("#1E88E5")   // Blue
            LogType.TELEGRAM_SENT -> Color.parseColor("#0088cc") // Telegram Blue
            LogType.ERROR -> Color.parseColor("#E65100")       // Orange
            LogType.INFO -> Color.parseColor("#757575")        // Grey
        }
        holder.statusIndicator.setBackgroundColor(indicatorColor)
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<LogItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
