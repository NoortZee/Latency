package com.example.androidlatency

import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidlatency.ui.theme.AndroidLatencyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidLatencyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LatencyTestScreen()
                }
            }
        }
    }
}

@Composable
fun LatencyTestScreen() {
    var testActive by remember { mutableStateOf(false) }
    var touchTimestamp by remember { mutableStateOf(0L) }
    var frameTimestamp by remember { mutableStateOf(0L) }
    var latency by remember { mutableStateOf(0L) }
    var resultList by remember { mutableStateOf(listOf<Long>()) }
    var averageLatency by remember { mutableStateOf(0L) }
    var minLatency by remember { mutableStateOf(0L) }
    var maxLatency by remember { mutableStateOf(0L) }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    fun measureLatency() {
        if (testActive) return
        
        testActive = true
        touchTimestamp = SystemClock.elapsedRealtimeNanos()
        
        Choreographer.getInstance().postFrameCallback { 
            frameTimestamp = SystemClock.elapsedRealtimeNanos()
            latency = (frameTimestamp - touchTimestamp) / 1_000_000 // Convert to milliseconds
            
            resultList = resultList + latency
            
            if (resultList.isNotEmpty()) {
                averageLatency = resultList.average().toLong()
                minLatency = resultList.minOrNull() ?: 0
                maxLatency = resultList.maxOrNull() ?: 0
            }
            
            coroutineScope.launch {
                delay(500) // Delay to prevent accidental double-taps
                testActive = false
            }
        }
    }
    
    fun resetStats() {
        resultList = emptyList()
        latency = 0
        averageLatency = 0
        minLatency = 0
        maxLatency = 0
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Тест задержки нажатия",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Button(
                onClick = { measureLatency() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (testActive) Color.Gray else MaterialTheme.colorScheme.primary
                    ),
                enabled = !testActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.Gray
                )
            ) {
                Text(
                    text = if (testActive) "Измерение..." else "Нажмите для измерения задержки",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Результаты",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Последнее измерение:", fontWeight = FontWeight.Medium)
                    Text(text = "${latency} мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Среднее значение:", fontWeight = FontWeight.Medium)
                    Text(text = "${averageLatency} мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Минимальное значение:", fontWeight = FontWeight.Medium)
                    Text(text = "${minLatency} мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Максимальное значение:", fontWeight = FontWeight.Medium)
                    Text(text = "${maxLatency} мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Количество тестов:", fontWeight = FontWeight.Medium)
                    Text(text = "${resultList.size}", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { resetStats() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сбросить статистику")
        }
    }
}