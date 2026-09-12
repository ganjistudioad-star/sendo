package com.example.networkwatcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var logAdapter: LogAdapter

    // Views
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvWifiName: TextView
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    private lateinit var etPhoneNumbers: EditText
    private lateinit var etDisconnectMessage: EditText
    private lateinit var etReconnectMessage: EditText
    private lateinit var switchReconnectSms: MaterialSwitch

    private lateinit var etDebounceDelay: EditText
    private lateinit var switchLocalAlarm: MaterialSwitch
    private lateinit var btnBatteryOptimization: Button

    private lateinit var switchTelegram: MaterialSwitch
    private lateinit var layoutTelegramSettings: LinearLayout
    private lateinit var etTelegramToken: EditText
    private lateinit var etTelegramChatId: EditText
    private lateinit var btnTestTelegram: Button

    private lateinit var btnSaveSettings: Button
    private lateinit var rvLogs: RecyclerView
    private lateinit var tvEmptyLogs: TextView
    private lateinit var btnClearLogs: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateWifiStatusUI()
            updateServiceStatusUI()
            refreshLogs()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.SEND_SMS] == true
        if (smsGranted) {
            startMonitoringService()
        } else {
            Toast.makeText(
                this,
                "صلاحية إرسال SMS ضرورية لتشغيل خدمة التنبيه!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = AppPreferences(this)
        initViews()
        setupListeners()
        loadPreferencesToUI()
        setupLogsRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        updateWifiStatusUI()
        updateServiceStatusUI()
        refreshLogs()

        val filter = IntentFilter(NetworkMonitorService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initViews() {
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvWifiName = findViewById(R.id.tvWifiName)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)

        etPhoneNumbers = findViewById(R.id.etPhoneNumbers)
        etDisconnectMessage = findViewById(R.id.etDisconnectMessage)
        etReconnectMessage = findViewById(R.id.etReconnectMessage)
        switchReconnectSms = findViewById(R.id.switchReconnectSms)

        etDebounceDelay = findViewById(R.id.etDebounceDelay)
        switchLocalAlarm = findViewById(R.id.switchLocalAlarm)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)

        switchTelegram = findViewById(R.id.switchTelegram)
        layoutTelegramSettings = findViewById(R.id.layoutTelegramSettings)
        etTelegramToken = findViewById(R.id.etTelegramToken)
        etTelegramChatId = findViewById(R.id.etTelegramChatId)
        btnTestTelegram = findViewById(R.id.btnTestTelegram)

        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        rvLogs = findViewById(R.id.rvLogs)
        tvEmptyLogs = findViewById(R.id.tvEmptyLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)
    }

    private fun loadPreferencesToUI() {
        etPhoneNumbers.setText(prefs.phoneNumbers)
        etDisconnectMessage.setText(prefs.disconnectMessage)
        etReconnectMessage.setText(prefs.reconnectMessage)
        switchReconnectSms.isChecked = prefs.isReconnectSmsEnabled

        etDebounceDelay.setText(prefs.debounceDelaySeconds.toString())
        switchLocalAlarm.isChecked = prefs.isLocalAlarmEnabled

        switchTelegram.isChecked = prefs.isTelegramEnabled
        layoutTelegramSettings.visibility = if (prefs.isTelegramEnabled) View.VISIBLE else View.GONE
        etTelegramToken.setText(prefs.telegramToken)
        etTelegramChatId.setText(prefs.telegramChatId)
    }

    private fun savePreferencesFromUI(): Boolean {
        val phones = etPhoneNumbers.text.toString().trim()
        val disconnectMsg = etDisconnectMessage.text.toString().trim()
        val reconnectMsg = etReconnectMessage.text.toString().trim()
        val debounceStr = etDebounceDelay.text.toString().trim()
        val debounceVal = debounceStr.toIntOrNull() ?: 10

        prefs.phoneNumbers = phones
        prefs.disconnectMessage = if (disconnectMsg.isNotEmpty()) disconnectMsg else AppPreferences.DEFAULT_DISCONNECT_MSG
        prefs.reconnectMessage = if (reconnectMsg.isNotEmpty()) reconnectMsg else AppPreferences.DEFAULT_RECONNECT_MSG
        prefs.isReconnectSmsEnabled = switchReconnectSms.isChecked
        prefs.debounceDelaySeconds = debounceVal
        prefs.isLocalAlarmEnabled = switchLocalAlarm.isChecked

        prefs.isTelegramEnabled = switchTelegram.isChecked
        prefs.telegramToken = etTelegramToken.text.toString().trim()
        prefs.telegramChatId = etTelegramChatId.text.toString().trim()

        return true
    }

    private fun setupListeners() {
        switchTelegram.setOnCheckedChangeListener { _, isChecked ->
            layoutTelegramSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnSaveSettings.setOnClickListener {
            savePreferencesFromUI()
            Toast.makeText(this, "تم حفظ الإعدادات بنجاح ✅", Toast.LENGTH_SHORT).show()
        }

        btnStartService.setOnClickListener {
            savePreferencesFromUI()
            if (prefs.getPhoneList().isEmpty() && !prefs.isTelegramEnabled) {
                Toast.makeText(this, "يرجى إدخال رقم هاتف واحد على الأقل أو تفعيل Telegram!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            checkPermissionsAndStart()
        }

        btnStopService.setOnClickListener {
            val serviceIntent = Intent(this, NetworkMonitorService::class.java)
            stopService(serviceIntent)
            prefs.isServiceActive = false
            updateServiceStatusUI()
            prefs.addLog(LogItem(message = "تم إيقاف المراقبة يدوياً", type = LogType.INFO))
            refreshLogs()
            Toast.makeText(this, "تم إيقاف المراقبة", Toast.LENGTH_SHORT).show()
        }

        btnBatteryOptimization.setOnClickListener {
            checkAndRequestBatteryOptimization()
        }

        btnTestTelegram.setOnClickListener {
            savePreferencesFromUI()
            val token = prefs.telegramToken
            val chatId = prefs.telegramChatId
            if (token.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(this, "أدخل Bot Token و Chat ID أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testTelegramMessage(token, chatId)
        }

        btnClearLogs.setOnClickListener {
            prefs.clearLogs()
            refreshLogs()
            Toast.makeText(this, "تم مسح سجل الأحداث", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needed.isEmpty()) {
            startMonitoringService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, NetworkMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        prefs.isServiceActive = true
        prefs.addLog(LogItem(message = "بدء تشغيل خدمة مراقبة الواي فاي", type = LogType.INFO))
        updateServiceStatusUI()
        refreshLogs()
        Toast.makeText(this, "بدأت المراقبة بنجاح ✅", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatusUI() {
        if (prefs.isServiceActive) {
            tvServiceStatus.text = "تعمل بنشاط ✅"
            tvServiceStatus.setTextColor(Color.parseColor("#388E3C")) // Green
            btnStartService.isEnabled = false
            btnStopService.isEnabled = true
        } else {
            tvServiceStatus.text = "متوقفة ❌"
            tvServiceStatus.setTextColor(Color.parseColor("#D32F2F")) // Red
            btnStartService.isEnabled = true
            btnStopService.isEnabled = false
        }
    }

    private fun updateWifiStatusUI() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var isWifiConnected = false
        var wifiSsid = "غير متصل"

        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                isWifiConnected = true
                wifiSsid = getWifiSsid()
            }
        }

        if (isWifiConnected) {
            tvWifiName.text = "متصل: $wifiSsid"
            tvWifiName.setTextColor(Color.parseColor("#388E3C"))
        } else {
            tvWifiName.text = "غير متصل بالواي فاي ❌"
            tvWifiName.setTextColor(Color.parseColor("#D32F2F"))
        }
    }

    private fun getWifiSsid(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info: WifiInfo? = wifiManager?.connectionInfo
            val rawSsid = info?.ssid
            if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                rawSsid.replace("\"", "")
            } else {
                "شبكة واي فاي نشطة"
            }
        } catch (e: Exception) {
            "شبكة واي فاي نشطة"
        }
    }

    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(fallbackIntent)
                }
            } else {
                Toast.makeText(this, "التطبيق مستثنى بالفعل من توفير البطارية! ✅", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "إصدار أندرويد لا يحتاج لاستثناء البطارية", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testTelegramMessage(token: String, chatId: String) {
        Toast.makeText(this, "جاري إرسال رسالة تجريبية...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var responseInfo = ""
            try {
                val url = URL("https://api.telegram.org/bot$token/sendMessage")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                        "&text=" + URLEncoder.encode("✅ هذه رسالة تجريبية من تطبيق NetworkWatcher!", "UTF-8")

                OutputStreamWriter(conn.outputStream).use { it.write(postData) }
                val code = conn.responseCode
                success = code in 200..299
                responseInfo = "كود الاستجابة: $code"
                conn.disconnect()
            } catch (e: Exception) {
                responseInfo = e.localizedMessage ?: "فشل الاتصال"
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@MainActivity, "تم استلام رسالة Telegram بنجاح! ✅", Toast.LENGTH_LONG).show()
                    prefs.addLog(LogItem(message = "اختبار Telegram ناجح", details = responseInfo, type = LogType.TELEGRAM_SENT))
                } else {
                    Toast.makeText(this@MainActivity, "فشل الاختبار: $responseInfo", Toast.LENGTH_LONG).show()
                    prefs.addLog(LogItem(message = "فشل اختبار Telegram", details = responseInfo, type = LogType.ERROR))
                }
                refreshLogs()
            }
        }
    }

    private fun setupLogsRecyclerView() {
        logAdapter = LogAdapter(emptyList())
        rvLogs.layoutManager = LinearLayoutManager(this)
        rvLogs.adapter = logAdapter
        refreshLogs()
    }

    private fun refreshLogs() {
        val logs = prefs.getLogs()
        logAdapter.updateList(logs)
        if (logs.isEmpty()) {
            tvEmptyLogs.visibility = View.VISIBLE
            rvLogs.visibility = View.GONE
        } else {
            tvEmptyLogs.visibility = View.GONE
            rvLogs.visibility = View.VISIBLE
        }
    }
}
