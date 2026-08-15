package com.spirit.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(events: List<EntityOutput>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(16.dp)
    ) {
        Text(
            text = "TIMELINE",
            color = Color(0xFF7CFFB2),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (events.isEmpty()) {
            Text(text = "No events yet", color = Color(0xFF666666))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events.reversed()) { e ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = e.jonesLabel,
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                        .format(Date(e.timestamp)),
                                    color = Color(0xFF666666),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = e.narrative,
                                color = Color(0xFFE0E0E0),
                                fontSize = 14.sp,
                                maxLines = 4
                            )
                        }
                    }
                }
            }
        }
    }
}
