package com.spirit.scan.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.spirit.scan.SpiritApp
import java.util.concurrent.Executors

class CameraBridge(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFeatures: (CameraFeatures) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null

    @Volatile
    var lastFeatures: CameraFeatures = CameraFeatures()
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(previewView: PreviewView?) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                cameraProvider.unbindAll()

                val preview = Preview.Builder().build()
                if (previewView != null) {
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor, CameraAnalyzer { features ->
                    lastFeatures = features
                    onFeatures(features)
                })

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                isRunning = true
                Log.i(SpiritApp.TAG, "CameraBridge started with preview")
            } catch (e: Exception) {
                isRunning = false
                Log.e(SpiritApp.TAG, "CameraBridge failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }
        isRunning = false
        Log.i(SpiritApp.TAG, "CameraBridge stopped")
    }
}
