package com.tyeng.bleasr.gatthelper

import android.Manifest.permission.BLUETOOTH
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.ActivityCompat
import com.tyeng.bleasr.CallService
import java.util.*

class CharacteristicWriteRequestCallback(private val context: Context, private val gattServer: BluetoothGattServer) : BluetoothGattServerCallback() {
    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
    }
    private val serviceUUID = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb")
    private val incomingCallUUID = UUID.fromString("0000bbbb-0000-1000-8000-00805f9b34fb")
    private val audioUUID = UUID.fromString("0000cccc-0000-1000-8000-00805f9b34fb")

    private val recordBufferSize = 16000 // bytes per buffer
    private val recordSampleRate = 16000 // Hz
    private val encodingAmrWb = AudioFormat.ENCODING_PCM_16BIT

    private var tts: TextToSpeech? = null
    private var audioRecorder: AudioRecord? = null
    private var audioData: ByteArray? = null
    private var audioThread: Thread? = null
    
    override fun onCharacteristicWriteRequest(device: BluetoothDevice,requestId: Int,characteristic: BluetoothGattCharacteristic,preparedWrite: Boolean,responseNeeded: Boolean,offset: Int,value: ByteArray) {
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"onCharacteristicWriteRequest: device=$device, requestId=$requestId, characteristic=$characteristic, preparedWrite=$preparedWrite, responseNeeded=$responseNeeded, offset=$offset, value=${String(value)}")
        when (characteristic.uuid) {
            serviceUUID -> {
                Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "Received Audio Gateway Control Point Command: ${value[0]}")
                if (value.isNotEmpty() && value[0] == '1'.code.toByte()) {
                    // Use the member variable tts instead of declaring a new one
                    tts = TextToSpeech(context) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            Log.d(
                                TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,
                                "Playing TTS"
                            )
                            tts?.speak(
                                "Hi, this is TYEng",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                null
                            )
                        }
                    }
                }
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(context,BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
            incomingCallUUID -> {
                Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "Received Response Code Command: ${value[0]}")
                // Handle response code commands here
            }
            audioUUID -> {
                Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "Received Audio Command: ${value.size} bytes")
                if (value.isNotEmpty() && value[0] == '1'.code.toByte()) {
                    // Create the AudioRecord object and start the recording thread
                    audioData = ByteArray(recordBufferSize)
                    audioRecorder =
                        AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,recordSampleRate,AudioFormat.CHANNEL_IN_MONO,encodingAmrWb,recordBufferSize)
                    audioThread = Thread {
                        audioRecorder?.startRecording()
                        while (!Thread.interrupted()) {
                            val bytesRead =
                                audioRecorder?.read(audioData!!, 0, audioData!!.size)
                            if (bytesRead != null && bytesRead > 0) {
                                Log.d(CallService.TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "Handle audio data here")                                
                            }
                        }
                        audioRecorder?.stop()
                        audioRecorder?.release()
                        audioRecorder = null
                        audioThread = null
                    }
                    audioThread?.start()
                } else if (value.isNotEmpty() && value[0] == '0'.code.toByte()) {
                    // Stop the recording thread
                    audioThread?.interrupt()
                }
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(context,BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
            else -> {
                Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "Unknown characteristic write request")
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(context,BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }
}