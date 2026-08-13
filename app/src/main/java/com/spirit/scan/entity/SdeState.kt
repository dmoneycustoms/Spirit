package com.spirit.scan.entity

import com.spirit.scan.ml.SdeResult

data class SdeState(val result: SdeResult) {
    val latent: FloatArray get() = result.toLatent()
    val isSystemOk: Boolean get() = result.isSystemOk
}
