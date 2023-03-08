package com.tyeng.bleasr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.annotation.RequiresApi

class CallService : InCallService() {
    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
    }

    private val notificationId: Int
        get() {
            return 1002
        }

    @RequiresApi(VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        val channelId = "callNotificationId"
        val channelName = "Call Channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Call Notification Channel"
        notificationManager.createNotificationChannel(channel)
        val notificationBuilder = Notification.Builder(this, channelId)
            .setContentTitle("BLEASR Service")
            .setContentText("Running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
        startForeground(notificationId, notificationBuilder.build())
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "CallService.onDestroy")
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "onCallAdded " + call.state)
        if (call.state == Call.STATE_RINGING) {
            val notification = Notification.Builder(this, "callNotificationId")
                .setContentTitle("BLEASR Service")
                .setContentText("Running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
            startForeground(notificationId, notification)
            Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber, "Incoming Call")
            MainActivity.log("전화옴")
            val extras = Bundle()
            extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
            Handler().postDelayed({
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                call.putExtras(extras)
                MainActivity.log("전화받음")
            }, 2000)
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "onStateChanged: state=${state}")
                when (state) {
                    Call.STATE_RINGING -> {
                        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"STATE_RINGING")
                    }
                    Call.STATE_DIALING -> {
                        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"STATE_DIALING")
                    }
                    Call.STATE_ACTIVE -> {
                        val intent = Intent("com.tyeng.bleasr.ANSWER_CALL")
                        intent.putExtra("callNumber", call.details.handle.schemeSpecificPart)
                        sendBroadcast(intent)
                        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"STATE_ACTIVE broadcasting Answer callNumber: ${call.details.handle.schemeSpecificPart}")
                    }
                    Call.STATE_DISCONNECTED -> {
                        Log.d(TAG + " " + Thread.currentThread().stackTrace[2].lineNumber,"STATE_DISCONNECTED")
                    }
                }
            }
        }
//        Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "registering callback for call=$call")
        call.registerCallback(callback)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG + Throwable().getStackTrace()[0].lineNumber, "onCallRemoved")
    }
}
