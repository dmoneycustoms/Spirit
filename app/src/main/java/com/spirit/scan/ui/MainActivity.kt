package com.spirit.scan.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.spirit.scan.SpiritApp
import com.spirit.scan.audio.SpiritBoxAudio
import com.spirit.scan.camera.CameraBridge
import com.spirit.scan.camera.CameraFeatures
import com.spirit.scan.entity.EntityEngine
import com.spirit.scan.entity.EntityOutput
import com.spirit.scan.entity.LocationContext
import com.spirit.scan.ml.JonesRunner
import com.spirit.scan.ml.LlmClient
import com.spirit.scan.ml.SdeRunner
import com.spirit.scan.sensor.SensorStreamManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorStreamManager
    private lateinit var entityEngine: EntityEngine
    private lateinit var jones: JonesRunner
    private lateinit var sde: SdeRunner
    private var cameraBridge: CameraBridge? = null
    private var spiritAudio: SpiritBoxAudio? = null

    private val fusedLocation by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val pipelineMutex = Mutex()
    private val processing = AtomicBoolean(false)
    private var lastHapticLabel: String? = null

    @Volatile private var currentLocation = LocationContext()
    @Volatile private var lastCamera = CameraFeatures()
    @Volatile private var cameraStatus = "camera: off"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val coreOk =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.BODY_SENSORS] == true

        if (coreOk) startPipeline()
        else {
            Log.w(SpiritApp.TAG, "Core permissions denied")
            uiStateHolder?.setStatus?.invoke("permissions denied")
        }

        if (grants[Manifest.permission.CAMERA] == true) startCamera()
        else {
            cameraStatus = "camera: permission denied"
            uiStateHolder?.setStatus?.invoke(cameraStatus)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var modelsOk = false
        var modelError = ""
        try {
            jones = JonesRunner()
            sde = SdeRunner()
            entityEngine = EntityEngine(jones, sde, LlmClient())
            modelsOk = true
        } catch (e: Exception) {
            Log.e(SpiritApp.TAG, "Failed to load models", e)
            modelError = e.message ?: "unknown model error"
        }

        setContent {
            var current by remember { mutableStateOf<EntityOutput?>(null) }
            val history = remember { mutableStateListOf<EntityOutput>() }
            var selectedTab by remember { mutableIntStateOf(0) }
            var status by remember {
                mutableStateOf(
                    if (modelsOk) "models ok - waiting for sensors..."
                    else "MODELS FAILED: $modelError"
                )
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF7CFFB2),
                    background = Color(0xFF0A0A0F),
                    surface = Color(0xFF16161E)
                )
            ) {
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF111118)) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                                label = { Text("Live") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text("Timeline") }
                            )
                        }
                    }
                ) {
                    Box {
                        when (selectedTab) {
                            0 -> LiveHud(
                                output = current,
                                status = status,
                                onAsk = { q ->
                                    if (::entityEngine.isInitialized) {
                                        entityEngine.setQuestion(q)
                                        status = "question set | " + cameraStatus
                                    }
                                }
                            )
                            1 -> TimelineScreen(history)
                        }
                    }
                }
            }

            uiStateHolder = UiStateHolder(
                setCurrent = { current = it },
                addHistory = {
                    history.add(it)
                    if (history.size > 100) history.removeAt(0)
                },
                setStatus = { status = it }
            )
        }

        if (modelsOk) requestPermissionsAndStart()
    }

    private fun requestPermissionsAndStart() {
        val needed = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startPipeline()
            startCamera()
            startAudio()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startAudio() {
        if (spiritAudio != null) return
        spiritAudio = SpiritBoxAudio().also { it.start() }
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraStatus = "camera: permission denied"
            return
        }
        if (cameraBridge != null) return
        try {
            cameraBridge = CameraBridge(this, this) { features ->
                lastCamera = features
                cameraStatus =
                    "camera: on n=" + format2(features.noiseVariance) +
                        " m=" + format2(features.motionMagnitude) +
                        " b=" + format2(features.brightness)
            }
            cameraBridge?.start()
            cameraStatus = "camera: starting..."
            uiStateHolder?.setStatus?.invoke(cameraStatus)
        } catch (e: Exception) {
            Log.e(SpiritApp.TAG, "startCamera failed", e)
            cameraStatus = "camera: failed " + (e.message ?: e.javaClass.simpleName)
            uiStateHolder?.setStatus?.invoke(cameraStatus)
        }
    }

    private fun startPipeline() {
        if (!::entityEngine.isInitialized) return
        startAudio()

        lifecycleScope.launch {
            while (true) {
                try {
                    val loc = withContext(Dispatchers.IO) {
                        fusedLocation.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            CancellationTokenSource().token
                        ).result
                    }
                    val cam = lastCamera
                    if (loc != null) {
                        currentLocation = LocationContext(
                            lat = loc.latitude,
                            lon = loc.longitude,
                            accuracy = loc.accuracy,
                            cameraNoise = cam.noiseVariance,
                            cameraMotion = cam.motionMagnitude,
                            cameraBrightness = cam.brightness
                        )
                    } else {
                        currentLocation = currentLocation.copy(
                            cameraNoise = cam.noiseVariance,
                            cameraMotion = cam.motionMagnitude,
                            cameraBrightness = cam.brightness
                        )
                    }
                } catch (_: Exception) {
                }
                kotlinx.coroutines.delay(10_000)
            }
        }

        sensorManager = SensorStreamManager(this) {
            if (!processing.compareAndSet(false, true)) return@SensorStreamManager
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    pipelineMutex.withLock {
                        if (!sensorManager.buffer.ready()) return@withLock
                        val cam = lastCamera
                        val ctx = currentLocation.copy(
                            cameraNoise = cam.noiseVariance,
                            cameraMotion = cam.motionMagnitude,
                            cameraBrightness = cam.brightness
                        )
                        val output = entityEngine.process(sensorManager.buffer, ctx)
                        applyAudioAndHaptic(output)
                        withContext(Dispatchers.Main) {
                            uiStateHolder?.setCurrent?.invoke(output)
                            uiStateHolder?.addHistory?.invoke(output)
                            uiStateHolder?.setStatus?.invoke("live | " + cameraStatus)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(SpiritApp.TAG, "Pipeline error", e)
                    withContext(Dispatchers.Main) {
                        val msg = e.message ?: e.javaClass.simpleName
                        uiStateHolder?.setStatus?.invoke(
                            "pipeline error: " + msg + " | " + cameraStatus
                        )
                    }
                } finally {
                    processing.set(false)
                }
            }
        }
        sensorManager.start()
        uiStateHolder?.setStatus?.invoke("sensors started | " + cameraStatus)
    }

    private fun applyAudioAndHaptic(output: EntityOutput) {
        val audio = spiritAudio ?: return
        val level = when (output.jonesLabel) {
            "firewall" -> 0.95f
            "harmonic_break" -> 0.85f
            "high_residual" -> (0.45f + output.residual * 0.2f).coerceIn(0.4f, 0.9f)
            "strong_envelope" -> (0.5f + output.envelope * 0.4f).coerceIn(0.5f, 0.95f)
            else -> 0.12f + output.jonesScore * 0.15f
        }
        audio.setIntensity(level)

        if (output.jonesLabel != lastHapticLabel) {
            if (output.jonesLabel != "stable") {
                audio.pulseBurst()
                vibratePulse()
            }
            lastHapticLabel = output.jonesLabel
        }
    }

    private fun vibratePulse() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60)
            }
        } catch (_: Exception) {
        }
    }

    private fun format2(v: Float): String {
        val s = (v * 100f).toInt() / 100f
        return s.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorManager.isInitialized) sensorManager.stop()
        cameraBridge?.stop()
        spiritAudio?.stop()
        if (::jones.isInitialized) jones.close()
        if (::sde.isInitialized) sde.close()
    }

    private data class UiStateHolder(
        val setCurrent: ((EntityOutput?) -> Unit)?,
        val addHistory: ((EntityOutput) -> Unit)?,
        val setStatus: ((String) -> Unit)?
    )

    private var uiStateHolder: UiStateHolder? = null
}
