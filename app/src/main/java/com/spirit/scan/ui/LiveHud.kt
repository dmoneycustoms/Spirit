package com.spirit.scan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput

@Composable
fun LiveHud(
    output: EntityOutput?,
    status: String = "",
    onAsk: ((String) -> Unit)? = null
) {
    var question by remember { mutableStateOf("") }

    val activity = when {
        output == null -> 0.05f
        output.jonesLabel == "firewall" -> 0.95f
        output.jonesLabel == "harmonic_break" -> 0.8f
        output.jonesLabel == "high_residual" -> (output.residual / 2f).coerceIn(0.4f, 0.9f)
        output.jonesLabel == "strong_envelope" -> output.envelope.coerceIn(0.5f, 0.95f)
        else -> (output.jonesScore * 0.35f).coerceIn(0.05f, 0.35f)
    }

    val labelColor = when (output?.jonesLabel) {
        "firewall" -> Color(0xFFFF6B6B)
        "harmonic_break" -> Color(0xFFFFB347)
        "high_residual" -> Color(0xFFFF8C42)
        "strong_envelope" -> Color(0xFF4ECDC4)
        else -> Color(0xFF7CFFB2)
    }

    Column {
        Text(
            text = "SPIRIT SCAN",
            color = Color(0xFF7CFFB2),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Text(text = " ", fontSize = 6.sp)

        Text(
            text = "ACTIVITY",
            color = Color(0xFF888888),
            fontSize = 11.sp
        )
        LinearProgressIndicator(
            progress = { activity },
            color = labelColor,
            trackColor = Color(0xFF222233)
        )

        Text(text = " ", fontSize = 8.sp)

        if (output != null) {
            val score = (output.jonesScore * 100).toInt()
            Text(
                text = output.jonesLabel.uppercase().replace('_', ' '),
                color = labelColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = score.toString() + "%   " +
                    (if (output.systemOk) "SYSTEM OK" else "SYSTEM STRESSED"),
                color = if (output.systemOk) Color(0xFF7CFFB2) else Color(0xFFFF6B6B),
                fontSize = 13.sp
            )
            Text(text = " ", fontSize = 6.sp)
            Text(
                text = output.narrative,
                color = Color(0xFFE8E8E8),
                fontSize = 15.sp
            )
            if (!output.userQuestion.isNullOrBlank()) {
                Text(
                    text = "Q: " + output.userQuestion,
                    color = Color(0xFF777777),
                    fontSize = 12.sp
                )
            }
        } else {
            Text(
                text = if (status.isNotEmpty()) status else "WAITING FOR SIGNAL...",
                color = Color(0xFF7CFFB2),
                fontSize = 15.sp
            )
        }

        Text(text = " ", fontSize = 6.sp)
        Text(
            text = status,
            color = Color(0xFF666666),
            fontSize = 11.sp
        )

        Text(text = " ", fontSize = 10.sp)
        Text(
            text = "ASK THE FIELD",
            color = Color(0xFF999999),
            fontSize = 11.sp
        )

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            placeholder = { Text("What is influencing this reading?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    val q = question.trim()
                    if (q.isNotEmpty()) {
                        onAsk?.invoke(q)
                        question = ""
                    }
                }
            )
        )

        Button(
            onClick = {
                val q = question.trim()
                if (q.isNotEmpty()) {
                    onAsk?.invoke(q)
                    question = ""
                }
            }
        ) {
            Text("ASK")
        }
    }
}
