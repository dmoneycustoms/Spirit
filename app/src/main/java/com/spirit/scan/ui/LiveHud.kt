package com.spirit.scan.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput

@Composable
fun LiveHud(output: EntityOutput?, status: String = "") {
    val text = when {
        output != null -> {
            val score = (output.jonesScore * 100).toInt()
            "SPIRIT SCAN - " + output.jonesLabel + " - " + score + "% - " + output.narrative
        }
        status.isNotEmpty() -> "SPIRIT SCAN\n\n" + status
        else -> "SPIRIT SCAN - Waiting for signal..."
    }
    Text(
        text = text,
        color = Color(0xFF7CFFB2),
        fontSize = 16.sp
    )
}
