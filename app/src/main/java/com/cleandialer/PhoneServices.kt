package com.cleandialer

import android.telecom.Call
import android.telecom.InCallService
import android.content.Intent
import android.os.Bundle

class CleanInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ActiveCall.call = call
        val intent = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (ActiveCall.call == call) ActiveCall.call = null
    }
}

object ActiveCall { @Volatile var call: Call? = null }

class InCallActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.rgb(13, 16, 21))
        }
        val number = android.widget.TextView(this).apply {
            text = ActiveCall.call?.details?.handle?.schemeSpecificPart ?: "Call"
            textSize = 26f; setTextColor(android.graphics.Color.WHITE); gravity = android.view.Gravity.CENTER
        }
        val answer = android.widget.Button(this).apply {
            text = "Answer"
            setOnClickListener { ActiveCall.call?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY); finish() }
        }
        val end = android.widget.Button(this).apply {
            text = "End call"
            setOnClickListener { ActiveCall.call?.disconnect(); finish() }
        }
        layout.addView(number); layout.addView(answer); layout.addView(end)
        setContentView(layout)
    }
}

class RespondViaMessageService : android.app.Service() {
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra("android.telecom.extra.PHONE_NUMBER")
        val text = intent?.getStringExtra("android.telecom.extra.TEXT") ?: ""
        if (!number.isNullOrBlank() && text.isNotBlank()) {
            try { @Suppress("DEPRECATION") val sms = android.telephony.SmsManager.getDefault(); sms.sendTextMessage(number, null, text, null, null) } catch (_: Exception) { }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
