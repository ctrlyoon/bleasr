package com.tyeng.bleasr.gatthelper

import android.Manifest.permission.BLUETOOTH
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*

class CharacteristicReadRequestCallback(private val context: Context, private val gattServer: BluetoothGattServer) : BluetoothGattServerCallback() {

    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
    }
    private val sttUUID = UUID.fromString("0000dddd-0000-1000-8000-00805f9b34fb")

    override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCharacteristicReadRequest: device=$device, requestId=$requestId, offset=$offset, characteristic=$characteristic")
        when (characteristic.uuid) {
            sttUUID -> {
                // Convert the STT data to bytes and send it to the client
                val sttData = "Some STT data".toByteArray()
                val value = if (offset > sttData.size) {
                    ByteArray(0)
                } else {
                    sttData.sliceArray(offset until sttData.size)
                }
                if (ActivityCompat.checkSelfPermission(context,BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            else -> {
                Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "Unknown characteristic read request")
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }
    }
}
