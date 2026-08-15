package com.spirit.scan.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput

@Composable
fun TimelineScreen(events: List<EntityOutput>) {
    val text = if (events.isEmpty()) {
        "No events yet"
    } else {
        events.reversed().joinToString("\n\n") { e ->
            e.jonesLabel + ": " + e.narrative
        }
    }
    Text(
        text = text,
        color = Color(0xFFCCCCCC),
        fontSize = 14.sp
    )
}
