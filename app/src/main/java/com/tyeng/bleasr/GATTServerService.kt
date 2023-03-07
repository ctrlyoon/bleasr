package com.tyeng.bleasr

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.util.*

class GATTServerService : Service() {
    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
    }
    private val notificationId: Int
        get() {
            return 1001
        }

    private val binder = LocalBinder()
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var gattServer: BluetoothGattServer
    private lateinit var incomingCallCharacteristic: BluetoothGattCharacteristic
    private lateinit var tts: TextToSpeech

    private val serviceUUID = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb")
    private val incomingCallUUID = UUID.fromString("0000bbbb-0000-1000-8000-00805f9b34fb")

    private var connectedDevice: BluetoothDevice? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG,"onConnectionStateChange: device=$device, status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
            } else {
                connectedDevice = null
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG,"onCharacteristicReadRequest: device=$device, requestId=$requestId, offset=$offset, characteristic=$characteristic")
            when (characteristic.uuid) {
                incomingCallUUID -> {
                    if (offset == 0) {
                        val value = if (connectedDevice != null) {
                            "1".toByteArray()
                        } else {
                            "0".toByteArray()
                        }
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }
                        gattServer.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            value
                        )
                    } else {
                        gattServer.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_INVALID_OFFSET,
                            0,
                            null
                        )
                    }
                }
                else -> {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG,"onCharacteristicWriteRequest: device=$device, requestId=$requestId, characteristic=$characteristic, preparedWrite=$preparedWrite, responseNeeded=$responseNeeded, offset=$offset, value=${String(value)}")
            if (characteristic.uuid == incomingCallUUID) {
                if (value.isNotEmpty() && value[0] == '1'.toByte()) {

                }
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            } else {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }

    private val answerCallReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"GATTServerService onReceive ${intent?.action}")
            if (intent?.action == "com.tyeng.bleasr.ANSWER_CALL") {
                val notification = Notification.Builder(context,"gattNotificationId")
                    .setContentTitle("BLEASR Service")
                    .setContentText("Running")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()
                // Call startForeground() to indicate that the service is running in the foreground
                startForeground(notificationId, notification)
                MainActivity.log("음성안내 준비중")
                Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"GATTServerService onReceive")
                // Use the member variable tts instead of declaring a new one
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts.speak("Hi, this is TYEng", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCreate")
        registerReceiver(answerCallReceiver, IntentFilter("com.tyeng.bleasr.ANSWER_CALL"))
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Create the incoming call characteristic
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCreate incoming call characteristic")
        val incomingCallProperties =
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE
        val incomingCallPermissions =
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        incomingCallCharacteristic = BluetoothGattCharacteristic(
            incomingCallUUID,
            incomingCallProperties,
            incomingCallPermissions
        )

        // Create the service
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCreate the service")
        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(incomingCallCharacteristic)

        // Create the GATT server
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "no BLUETOOTH_CONNECT permission")
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        gattServer.addService(service)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel with unique ID and name
            val channel = NotificationChannel("gattNofificationId","Gatt Channel",NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "GATT Notification Channel"
            // Register the channel with the system
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCreate started gattServer")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onDestroy")
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "no BLUETOOTH_CONNECT permission")
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gattServer.close()
        unregisterReceiver(answerCallReceiver)    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): GATTServerService = this@GATTServerService
    }
}