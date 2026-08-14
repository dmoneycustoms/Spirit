package com.spirit.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput

@Composable
fun LiveHud(output: EntityOutput?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(20.dp)
    ) {
        Text(
            text = "SPIRIT SCAN",
            color = Color(0xFF7CFFB2),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FIELD STATE",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (output != null) {
                    Text(
                        text = output.jonesLabel.uppercase().replace('_', ' '),
                        color = labelColor(output.jonesLabel),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(output.jonesScore * 100).toInt()}%  residual ${"%.2f".format(output.residual)}",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (output.systemOk) "SYSTEM OK" else "SYSTEM STRESSED",
                        color = if (output.systemOk) Color(0xFF7CFFB2) else Color(0xFFFF6B6B),
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "WAITING FOR SIGNAL...",
                        color = Color(0xFF666666),
                        fontSize = 16.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ENTITY CHANNEL",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = output?.narrative ?: "Listening...",
                    color = Color(0xFFE0E0E0),
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

private fun labelColor(label: String): Color {
    return when (label) {
        "firewall" -> Color(0xFFFF6B6B)
        "harmonic_break" -> Color(0xFFFFB347)
        "high_residual" -> Color(0xFFFF8C42)
        "strong_envelope" -> Color(0xFF4ECDC4)
        else -> Color(0xFF7CFFB2)
    }
}
