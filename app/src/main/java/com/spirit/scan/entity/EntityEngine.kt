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
    var lastQuestion: String? = null
        private set

    private var lastLabel: String? = null
    private var lastNarrative: String = "Listening..."
    private var lastAnsweredQuestion: String? = null
    private var questionPending: Boolean = false

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

        val labelChanged = jr.label != lastLabel
        val needNewNarrative = labelChanged || questionPending

        if (needNewNarrative) {
            val prompt = PromptBuilder.build(event, sdeState, ctx, lastQuestion)
            lastNarrative = llm.generate(prompt)
            lastLabel = jr.label
            lastAnsweredQuestion = lastQuestion
            questionPending = false
        }

        return EntityOutput(
            jonesLabel = jr.label,
            jonesScore = jr.confidence,
            sdeState = sdeState,
            narrative = lastNarrative,
            residual = jr.residual,
            envelope = jr.envelope,
            systemOk = sr.isSystemOk,
            userQuestion = lastAnsweredQuestion
        )
    }

    fun recentEvents(): List<AnomalyEvent> = history.toList()
}
