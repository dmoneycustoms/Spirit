package com.spirit.scan.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput

@Composable
fun LiveHud(output: EntityOutput?) {
    if (output == null) {
        Text(
            text = "SPIRIT SCAN - Waiting for signal...",
            color = Color(0xFF7CFFB2),
            fontSize = 18.sp
        )
    } else {
        val score = (output.jonesScore * 100).toInt()
        val text = "SPIRIT SCAN - " + output.jonesLabel + " - " + score + "% - " + output.narrative
        Text(
            text = text,
            color = Color(0xFF7CFFB2),
            fontSize = 18.sp
        )
    }
}
