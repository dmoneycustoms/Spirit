package com.spirit.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spirit.scan.entity.EntityOutput
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TimelineScreen(events: List<EntityOutput>) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F)).padding(16.dp)) {
        Text("TIMELINE", color = Color(0xFF7CFFB2), fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
        Spacer(Modifier.height(16.dp))
        if (events.isEmpty()) {
            Text("No events yet", color = Color(0xFF666666))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events.reversed()) { e ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(e.jonesLabel, color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(e.timestamp)), color = Color(0xFF666666), fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(e.narrative, color = Color(0xFFE0E0E0), fontSize = 14.sp, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}
