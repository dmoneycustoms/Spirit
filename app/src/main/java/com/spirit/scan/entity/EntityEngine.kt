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
    val timestamp: Long = System.currentTimeMillis()
)

class EntityEngine(
    private val jones: JonesRunner,
    private val sde: SdeRunner,
    private val llm: LlmClient
) {
    suspend fun process(buffer: CircularBuffer, ctx: LocationContext): EntityOutput {
        val jonesFeatures = buffer.toJonesFeatures()
        val jr = jones.run(jonesFeatures)
        val sdeFeatures = buffer.toSdeFeatures(jr)
        val sr = sde.run(sdeFeatures)
        val sdeState = SdeState(sr)
        val event = AnomalyEvent(jr, ctx)
        val prompt = PromptBuilder.build(event, sdeState, ctx)
        val text = llm.generate(prompt)
        return EntityOutput(
            jonesLabel = jr.label,
            jonesScore = jr.confidence,
            sdeState = sdeState,
            narrative = text,
            residual = jr.residual,
            envelope = jr.envelope,
            systemOk = sr.isSystemOk
        )
    }
}
