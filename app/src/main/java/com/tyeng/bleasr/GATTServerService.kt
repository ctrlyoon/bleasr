package com.tyeng.bleasr

import android.Manifest
import android.app.Notification
import android.app.Service
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.tyeng.bleasr.gatthelper.CharacteristicReadRequestCallback
import com.tyeng.bleasr.gatthelper.CharacteristicWriteRequestCallback
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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

    private lateinit var gattServer: BluetoothGattServer
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null
    private var connectedDevice: BluetoothGatt? = null

    private val sttUUID = UUID.fromString("0000dddd-0000-1000-8000-00805f9b34fb")
    private val serviceUUID = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb")
    private val incomingCallUUID = UUID.fromString("0000bbbb-0000-1000-8000-00805f9b34fb")
    private val audioUUID = UUID.fromString("0000cccc-0000-1000-8000-00805f9b34fb")
    private lateinit var tts: TextToSpeech

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCreate")
        val notification = Notification.Builder(applicationContext,"gattNotificationId")
            .setContentTitle("BLEASR Service")
            .setContentText("Running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        // Call startForeground() to indicate that the service is running in the foreground
        startForeground(notificationId, notification)
        initialize()
        registerReceiver(answerCallReceiver, IntentFilter("com.tyeng.bleasr.ANSWER_CALL"))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onDestroy")
        stopServer()
        unregisterReceiver(answerCallReceiver)
    }

    private fun initialize() {
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "initialize")
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gattServer = bluetoothManager!!.openGattServer(applicationContext, gattServerCallback)
        addServices()
    }

    private fun addServices() {
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "addServices")
        // Add the STT service
        val sttService = BluetoothGattService(sttUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val sttCharacteristic = BluetoothGattCharacteristic(
            sttUUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        sttService.addCharacteristic(sttCharacteristic)
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gattServer.addService(sttService)

        // Add the Audio Gateway service
        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Add the Incoming Call characteristic
        val incomingCallCharacteristic = BluetoothGattCharacteristic(
            incomingCallUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(incomingCallCharacteristic)

        // Add the Audio characteristic
        val audioCharacteristic = BluetoothGattCharacteristic(
            audioUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(audioCharacteristic)

        gattServer.addService(service)
    }

    private fun stopServer() {
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "stopServer")
        if (bluetoothAdapter?.isEnabled == true) {
            if (connectedDevice != null) {
                if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                connectedDevice?.disconnect()
                connectedDevice = null
            }
            gattServer.close()
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        private val connectedDevices = mutableListOf<BluetoothDevice>()
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG+ " " + Thread.currentThread().stackTrace[2].lineNumber, "GATT service added successfully")
            } else {
                Log.e(TAG+ " " + Thread.currentThread().stackTrace[2].lineNumber, "Failed to add GATT service with status $status")
            }
        }
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onConnectionStateChange: device=$device, status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices.add(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device)
                }
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice,requestId: Int,offset: Int,characteristic: BluetoothGattCharacteristic) {
            CharacteristicReadRequestCallback(applicationContext,gattServer).onCharacteristicReadRequest(device, requestId, offset, characteristic)
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice,requestId: Int,characteristic: BluetoothGattCharacteristic,preparedWrite: Boolean,responseNeeded: Boolean,offset: Int,value: ByteArray) {
            CharacteristicWriteRequestCallback(applicationContext,gattServer).onCharacteristicWriteRequest(device,requestId,characteristic,preparedWrite,responseNeeded,offset,value)
        }

    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onConnectionStateChange: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = gatt
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                }
            }
        }
    }

    private fun convertTtsStreamToByteArray(stream: InputStream): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        return outputStream.toByteArray()
    }

    private val answerCallReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(
                TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                "GATTServerService onReceive ${intent?.action}"
            )
            if (intent?.action == "com.tyeng.bleasr.ANSWER_CALL") {
                MainActivity.log("음성안내 준비중")
                Log.d(
                    TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                    "answerCallReceiver: "
                )
                // Get the mouthpiece characteristic
                val audioCharacteristic =
                    gattServer.getService(serviceUUID)?.getCharacteristic(audioUUID)
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        Log.d(
                            TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                            "TextToSpeech.SUCCESS"
                        )
                        MainActivity.log("음성안내 멘트 생성중")
                        val file = File(context?.cacheDir, "tts_audio.mp3")
                        tts.synthesizeToFile("Hello, world!", null, file, null)
                        val ttsStream = FileInputStream(file)
                        val ttsByteArray = convertTtsStreamToByteArray(ttsStream)
                        audioCharacteristic?.value = ttsByteArray
                        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                        if (bluetoothAdapter != null) {
                            Log.d(
                                TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                                "bluetoothAdapter"
                            )
                        }
                        if (bluetoothAdapter.isEnabled) {
                            Log.d(
                                TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                                "bluetoothAdapter.isEnabled"
                            )
                        }
                        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                            if (ActivityCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.BLUETOOTH
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                Log.d(
                                    TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                                    "No BLUETOOTH PERMISSION_GRANTED"
                                )
                                // TODO: Consider calling
                                //    ActivityCompat#requestPermissions
                                // here to request the missing permissions, and then overriding
                                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                //                                          int[] grantResults)
                                // to handle the case where the user grants the permission. See the documentation
                                // for ActivityCompat#requestPermissions for more details.
                            } else {
                                val device =
                                    bluetoothAdapter.getRemoteDevice(bluetoothAdapter.address)
                                if (device != null) {
                                    Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"device not null")
                                }
                                if (BluetoothProfile.STATE_CONNECTED == bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)) {
                                    Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"headset connected")
                                } else {
                                    Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"headset not connected")
                                }
                                if (device != null
//                                    && BluetoothProfile.STATE_CONNECTED == bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                                ) {
                                    Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"Bluetooth headset is connected")
                                    audioManager.mode = AudioManager.MODE_IN_CALL
                                    audioManager.isSpeakerphoneOn = false
                                    audioManager.startBluetoothSco()
                                    audioManager.isBluetoothScoOn = true
                                    MainActivity.log("음성안내 멘트 송출중")
                                    gattServer.notifyCharacteristicChanged(device,audioCharacteristic,false)
                                } else {
                                    Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"Bluetooth headset is not connected")
                                }
                            }
                        } else {
                            Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"Bluetooth is not enabled")
                        }
                    } else {
                        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"no TextToSpeech.SUCCESS")
                    }
                }
                Log.d(
                    TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                    "GATTServerService onReceive"
                )
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): GATTServerService = this@GATTServerService
    }
}
