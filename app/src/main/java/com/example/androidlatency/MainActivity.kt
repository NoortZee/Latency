package com.example.androidlatency

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.WindowManager
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
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var refreshRate = 60f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Получаем текущую частоту обновления дисплея
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshRate = windowManager.defaultDisplay.refreshRate
        
        enableEdgeToEdge()
        setContent {
            AndroidLatencyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LatencyTestScreen(refreshRate)
                }
            }
        }
    }
}

@Composable
fun LatencyTestScreen(refreshRate: Float) {
    var testActive by remember { mutableStateOf(false) }
    var touchTimestamp by remember { mutableStateOf(0L) }
    var frameTimestamp by remember { mutableStateOf(0L) }
    var vsyncTimestamp by remember { mutableStateOf(0L) }
    var displayTimestamp by remember { mutableStateOf(0L) }
    var latency by remember { mutableStateOf(0L) }
    var resultList by remember { mutableStateOf(listOf<Long>()) }
    var averageLatency by remember { mutableStateOf(0L) }
    var minLatency by remember { mutableStateOf(0L) }
    var maxLatency by remember { mutableStateOf(0L) }
    var frameTime by remember { mutableStateOf(0L) } // время одного кадра в мс
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val handler = Handler(Looper.getMainLooper())
    
    // Расчет времени одного кадра в миллисекундах
    LaunchedEffect(refreshRate) {
        frameTime = if (refreshRate > 0) (1000 / refreshRate).roundToInt().toLong() else 16
    }
    
    fun measureLatency() {
        if (testActive) return
        
        testActive = true
        // Измеряем время нажатия
        touchTimestamp = SystemClock.elapsedRealtimeNanos()
        
        val choreographer = Choreographer.getInstance()
        
        // Получаем время ближайшего vsync события
        choreographer.postFrameCallback { frameTimeNanos ->
            vsyncTimestamp = frameTimeNanos
            frameTimestamp = SystemClock.elapsedRealtimeNanos()
            
            // Добавляем еще одно измерение через два кадра для учета реального времени отображения на экране
            choreographer.postFrameCallback { _ ->
                choreographer.postFrameCallback { _ ->
                    displayTimestamp = SystemClock.elapsedRealtimeNanos()
                    
                    // Рассчитываем полную задержку от нажатия до отображения на экране
                    // Включая время ожидания следующего vsync + время рендеринга + время до физического обновления дисплея
                    latency = (displayTimestamp - touchTimestamp) / 1_000_000
                    
                    // Для большей точности вычитаем один кадр (т.к. отображение произошло в начале последнего кадра)
                    if (latency > frameTime) {
                        latency -= frameTime / 2
                    }
                    
                    resultList = resultList + latency
                    
                    if (resultList.isNotEmpty()) {
                        averageLatency = resultList.average().toLong()
                        minLatency = resultList.minOrNull() ?: 0
                        maxLatency = resultList.maxOrNull() ?: 0
                    }
                    
                    // Задержка перед сбросом флага активного теста
                    handler.postDelayed({
                        testActive = false
                    }, 500) // Задержка, чтобы предотвратить случайные двойные клики
                }
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
                    Text(text = "Частота обновления:", fontWeight = FontWeight.Medium)
                    Text(text = "$refreshRate Гц", fontWeight = FontWeight.Bold)
                }
                
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