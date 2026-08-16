package com.spirit.scan.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.spirit.scan.R
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

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
    @Volatile private var micOn = false
    @Volatile private var micLevel = 0f

    private var micJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private lateinit var content: FrameLayout
    private lateinit var tabLive: TextView
    private lateinit var tabTimeline: TextView
    private lateinit var livePanel: View
    private lateinit var timelinePanel: View

    private lateinit var cameraPreview: PreviewView
    private lateinit var activityBar: ProgressBar
    private lateinit var labelText: TextView
    private lateinit var metaText: TextView
    private lateinit var narrativeText: TextView
    private lateinit var questionText: TextView
    private lateinit var statusText: TextView
    private lateinit var askInput: EditText
    private lateinit var askButton: Button
    private lateinit var micToggle: Button
    private lateinit var micBadge: TextView
    private lateinit var camBadge: TextView
    private lateinit var timelineList: ListView

    private val history = mutableListOf<EntityOutput>()
    private lateinit var timelineAdapter: TimelineAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val coreOk =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.BODY_SENSORS] == true

        if (coreOk) startPipeline() else setStatus("permissions denied")

        if (grants[Manifest.permission.CAMERA] == true) startCamera()
        else {
            cameraStatus = "camera: permission denied"
            updateBadges()
            setStatus(cameraStatus)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content)
        tabLive = findViewById(R.id.tabLive)
        tabTimeline = findViewById(R.id.tabTimeline)

        livePanel = layoutInflater.inflate(R.layout.panel_live, content, false)
        timelinePanel = layoutInflater.inflate(R.layout.panel_timeline, content, false)
        content.addView(livePanel)
        content.addView(timelinePanel)
        timelinePanel.visibility = View.GONE

        cameraPreview = livePanel.findViewById(R.id.cameraPreview)
        activityBar = livePanel.findViewById(R.id.activityBar)
        labelText = livePanel.findViewById(R.id.labelText)
        metaText = livePanel.findViewById(R.id.metaText)
        narrativeText = livePanel.findViewById(R.id.narrativeText)
        questionText = livePanel.findViewById(R.id.questionText)
        statusText = livePanel.findViewById(R.id.statusText)
        askInput = livePanel.findViewById(R.id.askInput)
        askButton = livePanel.findViewById(R.id.askButton)
        micToggle = livePanel.findViewById(R.id.micToggle)
        micBadge = livePanel.findViewById(R.id.micBadge)
        camBadge = livePanel.findViewById(R.id.camBadge)
        timelineList = timelinePanel.findViewById(R.id.timelineList)

        timelineAdapter = TimelineAdapter()
        timelineList.adapter = timelineAdapter

        tabLive.setOnClickListener { showLive() }
        tabTimeline.setOnClickListener { showTimeline() }
        askButton.setOnClickListener { submitQuestion() }
        micToggle.setOnClickListener { toggleMic() }
        askInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitQuestion()
                true
            } else false
        }

        var modelsOk = false
        try {
            jones = JonesRunner()
            sde = SdeRunner()
            entityEngine = EntityEngine(jones, sde, LlmClient())
            modelsOk = true
            setStatus("models ok - waiting for sensors...")
        } catch (e: Exception) {
            Log.e(SpiritApp.TAG, "Failed to load models", e)
            setStatus("MODELS FAILED: " + (e.message ?: "unknown"))
        }

        updateBadges()
        if (modelsOk) requestPermissionsAndStart()
    }

    private fun showLive() {
        livePanel.visibility = View.VISIBLE
        timelinePanel.visibility = View.GONE
        tabLive.setTextColor(Color.parseColor("#7CFFB2"))
        tabTimeline.setTextColor(Color.parseColor("#666666"))
    }

    private fun showTimeline() {
        livePanel.visibility = View.GONE
        timelinePanel.visibility = View.VISIBLE
        tabLive.setTextColor(Color.parseColor("#666666"))
        tabTimeline.setTextColor(Color.parseColor("#7CFFB2"))
        timelineAdapter.notifyDataSetChanged()
    }

    private fun submitQuestion() {
        val q = askInput.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return
        if (::entityEngine.isInitialized) {
            entityEngine.setQuestion(q)
            setStatus("question set | " + cameraStatus)
        }
        askInput.setText("")
    }

    private fun setStatus(msg: String) {
        runOnUiThread { statusText.text = msg }
    }

    private fun updateBadges() {
        runOnUiThread {
            if (micOn) {
                micBadge.text = "MIC ON " + ((micLevel * 100).toInt()) + "%"
                micBadge.setTextColor(Color.parseColor("#7CFFB2"))
                micToggle.text = "MIC: ON"
            } else {
                micBadge.text = "MIC OFF"
                micBadge.setTextColor(Color.parseColor("#FF6B6B"))
                micToggle.text = "MIC: OFF"
            }

            val camRunning = cameraBridge?.isRunning == true
            if (camRunning) {
                camBadge.text = "CAM ON"
                camBadge.setTextColor(Color.parseColor("#7CFFB2"))
            } else {
                camBadge.text = "CAM OFF"
                camBadge.setTextColor(Color.parseColor("#FF6B6B"))
            }
        }
    }

    private fun renderOutput(output: EntityOutput) {
        val score = (output.jonesScore * 100).toInt()
        val activity = when (output.jonesLabel) {
            "firewall" -> 95
            "harmonic_break" -> 85
            "high_residual" -> (40 + output.residual * 20f).toInt().coerceIn(40, 90)
            "strong_envelope" -> (50 + output.envelope * 40f).toInt().coerceIn(50, 95)
            else -> (5 + output.jonesScore * 30f).toInt().coerceIn(5, 35)
        }
        val color = when (output.jonesLabel) {
            "firewall" -> "#FF6B6B"
            "harmonic_break" -> "#FFB347"
            "high_residual" -> "#FF8C42"
            "strong_envelope" -> "#4ECDC4"
            else -> "#7CFFB2"
        }

        activityBar.progress = activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activityBar.progressTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(color))
        }
        labelText.text = output.jonesLabel.uppercase().replace('_', ' ')
        labelText.setTextColor(Color.parseColor(color))
        metaText.text = score.toString() + "%   " +
            if (output.systemOk) "SYSTEM OK" else "SYSTEM STRESSED"
        metaText.setTextColor(
            Color.parseColor(if (output.systemOk) "#7CFFB2" else "#FF6B6B")
        )
        narrativeText.text = output.narrative
        questionText.text =
            if (!output.userQuestion.isNullOrBlank()) "Q: " + output.userQuestion else ""
        statusText.text = "live | " + cameraStatus +
            if (micOn) " | mic=" + ((micLevel * 100).toInt()) + "%" else " | mic=off"
        updateBadges()
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
            updateBadges()
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
            cameraBridge?.start(cameraPreview)
            cameraStatus = "camera: starting..."
            setStatus(cameraStatus)
            updateBadges()
        } catch (e: Exception) {
            Log.e(SpiritApp.TAG, "startCamera failed", e)
            cameraStatus = "camera: failed " + (e.message ?: e.javaClass.simpleName)
            setStatus(cameraStatus)
            updateBadges()
        }
    }

    private fun toggleMic() {
        if (micOn) stopMic() else startMic()
    }

    private fun startMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            setStatus("mic permission denied")
            return
        }
        if (micOn) return
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            setStatus("mic unavailable")
            return
        }
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            audioRecord?.startRecording()
            micOn = true
            updateBadges()
            micJob = lifecycleScope.launch(Dispatchers.Default) {
                val buf = ShortArray(minBuf)
                while (isActive && micOn) {
                    val n = audioRecord?.read(buf, 0, buf.size) ?: break
                    if (n > 0) {
                        var sum = 0.0
                        for (i in 0 until n) {
                            val v = buf[i].toDouble()
                            sum += v * v
                        }
                        val rms = sqrt(sum / n) / 32768.0
                        micLevel = rms.toFloat().coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) { updateBadges() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(SpiritApp.TAG, "mic start failed", e)
            micOn = false
            setStatus("mic failed: " + (e.message ?: e.javaClass.simpleName))
            updateBadges()
        }
    }

    private fun stopMic() {
        micOn = false
        micJob?.cancel()
        micJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        micLevel = 0f
        updateBadges()
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
                            renderOutput(output)
                            history.add(output)
                            if (history.size > 100) history.removeAt(0)
                            timelineAdapter.notifyDataSetChanged()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(SpiritApp.TAG, "Pipeline error", e)
                    withContext(Dispatchers.Main) {
                        setStatus(
                            "pipeline error: " +
                                (e.message ?: e.javaClass.simpleName) +
                                " | " + cameraStatus
                        )
                    }
                } finally {
                    processing.set(false)
                }
            }
        }
        sensorManager.start()
        setStatus("sensors started | " + cameraStatus)
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
        return ((v * 100f).toInt() / 100f).toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMic()
        if (::sensorManager.isInitialized) sensorManager.stop()
        cameraBridge?.stop()
        spiritAudio?.stop()
        if (::jones.isInitialized) jones.close()
        if (::sde.isInitialized) sde.close()
    }

    private inner class TimelineAdapter : BaseAdapter() {
        private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        override fun getCount(): Int = history.size
        override fun getItem(position: Int): EntityOutput =
            history[history.size - 1 - position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_timeline, parent, false)
            val item = getItem(position)
            view.findViewById<TextView>(R.id.itemTitle).text =
                fmt.format(Date(item.timestamp)) + "  " + item.jonesLabel
            view.findViewById<TextView>(R.id.itemBody).text = item.narrative
            return view
        }
    }
}
