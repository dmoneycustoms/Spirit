package com.spirit.scan

import android.app.Application
import android.util.Log

class SpiritApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SpiritScan Application started")
    }

    companion object {
        const val TAG = "SpiritScan"
    }
}
