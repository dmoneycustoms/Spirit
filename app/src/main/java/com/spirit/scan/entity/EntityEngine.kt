package com.spirit.scan.entity

import com.spirit.scan.ml.JonesRunner
import com.spirit.scan.ml.LlmClient
import com.spirit.scan.ml.SdeRunner
import com.spirit.scan.sensor.CircularBuffer

data class EntityOutput(
    val jonesLabel: String,
    val jonesScore: Float,
    val sdeState: SdeState,
    val narrative: String,
    val residual: Float = 0f,
    val envelope: Float = 0f,
    val systemOk: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val userQuestion: String? = null
)

class EntityEngine(
    private val jones: JonesRunner,
    private val sde: SdeRunner,
    private val llm: LlmClient
) {
    private val history = mutableListOf<AnomalyEvent>()

    @Volatile
    private var lastQuestion: String? = null

    // Sticky display label (only changes after confirmation)
    private var stableLabel: String? = null
    private var candidateLabel: String? = null
    private var candidateCount: Int = 0
    private val confirmCount: Int = 2

    private var lastNarrative: String = "Listening..."
    private var lastAnsweredQuestion: String? = null
    private var questionPending: Boolean = false

    // Live metrics always update; narrative is sticky
    private var lastScore: Float = 0f
    private var lastResidual: Float = 0f
    private var lastEnvelope: Float = 0f
    private var lastSystemOk: Boolean = true
    private var lastSdeState: SdeState? = null

    fun setQuestion(q: String?) {
        val cleaned = q?.trim()?.ifEmpty { null }
        lastQuestion = cleaned
        questionPending = cleaned != null
    }

    suspend fun process(
        buffer: CircularBuffer,
        ctx: LocationContext
    ): EntityOutput {
        val jonesFeatures = buffer.toJonesFeatures()
        val jr = jones.run(jonesFeatures)

        val event = AnomalyEvent(jr, ctx)
        history.add(event)
        if (history.size > 40) history.removeAt(0)

        val sdeFeatures = buffer.toSdeFeatures(jr)
        val sr = sde.run(sdeFeatures)
        val sdeState = SdeState(sr)

        // Always keep latest metrics
        lastScore = jr.confidence
        lastResidual = jr.residual
        lastEnvelope = jr.envelope
        lastSystemOk = sr.isSystemOk
        lastSdeState = sdeState

        // Label hysteresis: require 2 matching windows to switch
        var labelChanged = false
        if (stableLabel == null) {
            stableLabel = jr.label
            labelChanged = true
            candidateLabel = jr.label
            candidateCount = confirmCount
        } else if (jr.label == stableLabel) {
            candidateLabel = jr.label
            candidateCount = confirmCount
        } else if (jr.label == candidateLabel) {
            candidateCount += 1
            if (candidateCount >= confirmCount) {
                stableLabel = jr.label
                labelChanged = true
                candidateCount = 0
            }
        } else {
            candidateLabel = jr.label
            candidateCount = 1
        }

        val displayLabel = stableLabel ?: jr.label

        // Narrative only on confirmed label change or user Ask
        if (labelChanged || questionPending) {
            val promptEvent = event.copy(
                // keep real jones result; prompt uses labels from jr
            )
            val prompt = PromptBuilder.build(
                event = promptEvent,
                sde = sdeState,
                ctx = ctx,
                question = lastQuestion
            )
            // Force prompt label context to stable/display label for consistency
            lastNarrative = llm.generate(prompt)
            lastAnsweredQuestion = lastQuestion
            questionPending = false
        }

        return EntityOutput(
            jonesLabel = displayLabel,
            jonesScore = lastScore,
            sdeState = lastSdeState ?: sdeState,
            narrative = lastNarrative,
            residual = lastResidual,
            envelope = lastEnvelope,
            systemOk = lastSystemOk,
            userQuestion = lastAnsweredQuestion
        )
    }

    fun recentEvents(): List<AnomalyEvent> = history.toList()
}
