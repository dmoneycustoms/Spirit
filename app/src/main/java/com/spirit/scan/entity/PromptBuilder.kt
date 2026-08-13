package com.spirit.scan.entity

object PromptBuilder {
    fun build(event: AnomalyEvent, sde: SdeState, ctx: LocationContext): String {
        val j = event.jones
        val s = sde.result
        return """
            You are the narrative voice of SpiritScan.
            Jones: label=\( {j.label} residual= \){j.residual} envelope=\( {j.envelope} firewall= \){j.inFirewall}
            SDE: system_ok=\( {s.isSystemOk} composite= \){s.sComposite}
            Generate a short non-supernatural interpretation under 40 words.
        """.trimIndent()
    }
}
