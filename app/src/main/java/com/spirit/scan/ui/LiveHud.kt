package com.spirit.scan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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

    Column {
        Text(
            text = "SPIRIT SCAN",
            color = Color(0xFF7CFFB2),
            fontSize = 18.sp
        )

        Text(text = " ", fontSize = 8.sp)

        if (output != null) {
            val score = (output.jonesScore * 100).toInt()
            Text(
                text = output.jonesLabel.uppercase() + "  " + score + "%",
                color = Color(0xFF7CFFB2),
                fontSize = 20.sp
            )
            Text(
                text = if (output.systemOk) "SYSTEM OK" else "SYSTEM STRESSED",
                color = if (output.systemOk) Color(0xFF7CFFB2) else Color(0xFFFF6B6B),
                fontSize = 14.sp
            )
            Text(text = " ", fontSize = 6.sp)
            Text(
                text = output.narrative,
                color = Color(0xFFE0E0E0),
                fontSize = 16.sp
            )
            if (!output.userQuestion.isNullOrBlank()) {
                Text(
                    text = "Q: " + output.userQuestion,
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
        } else {
            Text(
                text = if (status.isNotEmpty()) status else "WAITING FOR SIGNAL...",
                color = Color(0xFF7CFFB2),
                fontSize = 16.sp
            )
        }

        Text(text = " ", fontSize = 10.sp)

        Text(
            text = "ASK THE FIELD",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp
        )

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            placeholder = {
                Text("What is influencing this reading?")
            },
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
