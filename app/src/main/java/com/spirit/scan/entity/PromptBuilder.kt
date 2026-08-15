package com.spirit.scan.entity

object PromptBuilder {

    fun build(
        event: AnomalyEvent,
        sde: SdeState,
        ctx: LocationContext,
        question: String? = null
    ): String {
        val j = event.jones
        val s = sde.result
        val q = if (question.isNullOrBlank()) "No question" else question.trim()

        return """
System: You interpret magnetometer and accelerometer anomalies symbolically.
You never claim proof of ghosts, spirits, or entities as fact.
Stay atmospheric, grounded, and honest about uncertainty.

User question: "$q"

Anomaly (Jones field):
- label: ${j.label}
- confidence: ${j.confidence}
- residual: ${j.residual}
- envelope: ${j.envelope}
- firewall: ${j.inFirewall}
- harmonic_ok: ${j.harmonicOk}
- route_weight: ${j.routeWeight}
- forcing: ${j.forcing}

Dynamics (SDE):
- system_ok: ${s.isSystemOk}
- sigma_sde: ${s.sigmaSde}
- composite: ${s.sComposite}
- temporal: ${s.temporalScore}
- harmonic: ${s.harmonicScore}
- signature: ${sde.signature}

Context:
- lat: ${ctx.lat}
- lon: ${ctx.lon}
- accuracy_m: ${ctx.accuracy}
- location: ${ctx.locationLabel}
- time: ${ctx.timeLabel}

Task:
1. Answer the user question using the anomaly and dynamics.
2. If there is no question, give a short field reading.
3. Keep it symbolic and concise (2-4 sentences).
""".trimIndent()
    }
}
