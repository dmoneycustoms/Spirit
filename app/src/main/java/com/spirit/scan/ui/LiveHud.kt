package com.spirit.scan.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput

@Composable
fun LiveHud(output: EntityOutput?) {
    val text = if (output == null) {
        "SPIRIT SCAN\n\nWaiting for signal..."
    } else {
        "SPIRIT SCAN\n\n\( {output.jonesLabel.uppercase()}\n \){(output.jonesScore * 100).toInt()}%\n\n${output.narrative}"
    }
    Text(text = text, color = Color(0xFF7CFFB2), fontSize = 18.sp)
}
