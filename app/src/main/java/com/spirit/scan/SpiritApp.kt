package com.spirit.scan

import android.app.Application
import android.util.Log

class SpiritApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SpiritScan started")
    }

    companion object {
        const val TAG = "SpiritScan"
    }
}
