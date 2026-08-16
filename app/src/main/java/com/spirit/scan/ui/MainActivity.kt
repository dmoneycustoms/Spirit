package com.spirit.scan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorStreamManager
    private lateinit var entityEngine: EntityEngine
    private lateinit var jones: JonesRunner
    private lateinit var sde: SdeRunner
    private var cameraBridge: CameraBridge? = null

    private val fusedLocation by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    @Volatile
    private var currentLocation = LocationContext()

    @Volatile
    private var lastCamera = CameraFeatures()

    @Volatile
    private var cameraStatus = "camera: off"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val coreOk =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.BODY_SENSORS] == true

        if (coreOk) {
            startPipeline()
        } else {
            Log.w(SpiritApp.TAG, "Core permissions denied")
            uiStateHolder?.setStatus?.invoke("permissions denied")
        }

        if (grants[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
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
                                        status = "question set - next window..."
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

        if (modelsOk) {
            requestPermissionsAndStart()
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startPipeline()
            startCamera()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
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
                    "camera: on  noise=" +
                        "%.2f".format(features.noiseVariance) +
                        " motion=" +
                        "%.2f".format(features.motionMagnitude)
            }
            cameraBridge?.start()
            cameraStatus = "camera: starting..."
            uiStateHolder?.setStatus?.invoke(cameraStatus)
        } catch (e: Exception) {
            Log.e(SpiritApp.TAG, "startCamera failed", e)
            cameraStatus = "camera: failed " + (e.message ?: "")
            uiStateHolder?.setStatus?.invoke(cameraStatus)
        }
    }

    private fun startPipeline() {
        if (!::entityEngine.isInitialized) return

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
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    if (!sensorManager.buffer.ready()) return@launch
                    val cam = lastCamera
                    val ctx = currentLocation.copy(
                        cameraNoise = cam.noiseVariance,
                        cameraMotion = cam.motionMagnitude,
                        cameraBrightness = cam.brightness
                    )
                    val output = entityEngine.process(sensorManager.buffer, ctx)
                    withContext(Dispatchers.Main) {
                        uiStateHolder?.setCurrent?.invoke(output)
                        uiStateHolder?.addHistory?.invoke(output)
                        val camLine = cameraStatus
                        uiStateHolder?.setStatus?.invoke("live | " + camLine)
                    }
                } catch (e: Exception) {
                    Log.e(SpiritApp.TAG, "Pipeline error", e)
                    withContext(Dispatchers.Main) {
                        uiStateHolder?.setStatus?.invoke(
                            "pipeline error: " + (e.message ?: "unknown")
                        )
                    }
                }
            }
        }
        sensorManager.start()
        uiStateHolder?.setStatus?.invoke("sensors started | " + cameraStatus)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorManager.isInitialized) sensorManager.stop()
        cameraBridge?.stop()
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
