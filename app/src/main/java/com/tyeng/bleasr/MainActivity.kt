package com.tyeng.bleasr

import android.Manifest.permission.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
        lateinit var logTextView: TextView
        var logLineCount = 0
        fun log(message: String) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date())
            val formattedMessage = "$formattedDate $message\n"
            logTextView.append(formattedMessage)

            if (logTextView.lineCount > 100) {
                val indexOfNewLine = logTextView.text.indexOf("\n")
                logTextView.text = logTextView.text.substring(indexOfNewLine + 1)
            }
        }
        const val CHANNEL_ID = "asrChannel"
        const val NOTIFICATION_ID = 1
    }

    private val roleManager: RoleManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(ROLE_SERVICE) as RoleManager
        } else null
    }
    private val telecomManager: TelecomManager by lazy { getSystemService(TELECOM_SERVICE) as TelecomManager }


    private val changeDefaultDialerIntent
        get() = if (packageName.equals(telecomManager.defaultDialerPackage)) {
            Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "Build.VERSION_CODES.Q")
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "Build.VERSION_CODES.Q")
                roleManager!!.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "no Build.VERSION_CODES.Q")
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
            }
        }
    private val changeDefaultDialerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "changeDefaultDialerLauncher")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logTextView = findViewById(R.id.log_text_view)
        val permissions = arrayOf(READ_PHONE_STATE, CALL_PHONE, READ_CALL_LOG, BLUETOOTH,BLUETOOTH_ADMIN)
        var allPermissionsGranted = true
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "permission $permission")
                allPermissionsGranted = false
                break
            }
        }
        Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "allPermissionsGranted $allPermissionsGranted")
        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        } else {
            findViewById<ImageView>(R.id.change_default_dialer).setOnClickListener {
                Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "setOnClickListener")
                changeDefaultDialerLauncher.launch(changeDefaultDialerIntent)
            }
            log("앱 시작됨")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // start the service as a foreground service for devices running Android Oreo or higher
            Log.d(TAG, "Starting foreground service")
            startForegroundService(Intent(this, CallService::class.java))
            startForegroundService(Intent(this, GATTServerService::class.java))
        } else {
            // start the service as a background service for devices running Android Nougat or lower
            Log.d(TAG, "Starting background service")
            startService(Intent(this, GATTServerService::class.java))
        }

    }
}
