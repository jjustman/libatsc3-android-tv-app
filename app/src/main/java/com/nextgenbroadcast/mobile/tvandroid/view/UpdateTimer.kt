package com.nextgenbroadcast.mobile.tvandroid.view

import android.os.Handler
import android.os.Looper

class UpdateTimer(
    private val delayMillis: Long,
    private val onTime: () -> Unit
) {
    private val updateMediaTimeHandler = Handler(Looper.getMainLooper())

    private val updateMediaTimeRunnable = object : Runnable {
        override fun run() {
            onTime()

            updateMediaTimeHandler.postDelayed(this, delayMillis)
        }
    }

    fun start() {
        updateMediaTimeHandler.removeCallbacks(updateMediaTimeRunnable)
        updateMediaTimeHandler.postDelayed(updateMediaTimeRunnable, delayMillis)
    }

    fun stop() {
        updateMediaTimeHandler.removeCallbacks(updateMediaTimeRunnable)
    }
}