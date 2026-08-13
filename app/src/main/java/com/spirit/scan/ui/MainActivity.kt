package com.spirit.scan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier.modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.spirit.scan.SpiritApp
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

    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }
    @Volatile private var currentLocation = LocationContext()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startPipeline()
        else Log.w(SpiritApp.TAG, "Permissions denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            jones = JonesRunner(this)
            sde = SdeRunner(this)
            entityEngine = EntityEngine(jones, sde, LlmClient(this))
        } catch (e: Exception) {
            Log.e(SpiritApp.TAG, "Failed to load models", e)
        }

        setContent {
            var current by remember { mutableStateOf<EntityOutput?>(null) }
            val history = remember { mutableStateListOf<EntityOutput>() }
            var selectedTab by remember { mutableIntStateOf(0) }

            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7CFFB2), background = Color(0xFF0A0A0F), surface = Color(0xFF16161E))) {
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF111118)) {
                            NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Sensors, null) }, label = { Text("Live") })
                            NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Timeline") })
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> LiveHud(current)
                            1 -> TimelineScreen(history)
                        }
                    }
                }
            }

            uiStateHolder = UiStateHolder(
                setCurrent = { current = it },
                addHistory = { history.add(it); if (history.size > 200) history.removeAt(0) }
            )
        }
        requestPermissionsAndStart()
    }

    private fun requestPermissionsAndStart() {
        val needed = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BODY_SENSORS, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startPipeline() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startPipeline() {
        lifecycleScope.launch {
            while (true) {
                try {
                    val loc = withContext(Dispatchers.IO) {
                        fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).result
                    }
                    if (loc != null) currentLocation = LocationContext(lat = loc.latitude, lon = loc.longitude, accuracy = loc.accuracy)
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(10_000)
            }
        }

        sensorManager = SensorStreamManager(this) {
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    val output = entityEngine.process(sensorManager.buffer, currentLocation)
                    withContext(Dispatchers.Main) {
                        uiStateHolder?.setCurrent?.invoke(output)
                        uiStateHolder?.addHistory?.invoke(output)
                    }
                } catch (e: Exception) {
                    Log.e(SpiritApp.TAG, "Pipeline error", e)
                }
            }
        }
        sensorManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorManager.isInitialized) sensorManager.stop()
        if (::jones.isInitialized) jones.close()
        if (::sde.isInitialized) sde.close()
    }

    private data class UiStateHolder(val setCurrent: ((EntityOutput?) -> Unit)?, val addHistory: ((EntityOutput) -> Unit)?)
    private var uiStateHolder: UiStateHolder? = null
}
