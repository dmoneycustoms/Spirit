package com.spirit.scan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(events: List<EntityOutput>) {
    Column {
        Text(
            text = "TIMELINE",
            color = Color(0xFF7CFFB2),
            fontSize = 18.sp
        )

        Text(text = " ", fontSize = 8.sp)

        if (events.isEmpty()) {
            Text(
                text = "No events yet",
                color = Color(0xFF666666),
                fontSize = 14.sp
            )
        } else {
            LazyColumn {
                items(events.reversed()) { e ->
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Date(e.timestamp))
                    Text(
                        text = time + "  " + e.jonesLabel,
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp
                    )
                    Text(
                        text = e.narrative,
                        color = Color(0xFFE0E0E0),
                        fontSize = 14.sp
                    )
                    Text(text = " ", fontSize = 8.sp)
                }
            }
        }
    }
}
