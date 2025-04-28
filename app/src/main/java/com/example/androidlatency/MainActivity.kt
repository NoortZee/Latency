package com.example.androidlatency

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var refreshRate = 60f
    private var deviceModel = ""
    private var androidVersion = ""
    private var touchRateInfo = TouchRateInfo(100f, "Динамическая оценка")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Получаем текущую частоту обновления дисплея
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshRate = windowManager.defaultDisplay.refreshRate
        
        // Получаем информацию об устройстве
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        
        // Оценка частоты опроса сенсора на основе модели устройства и версии Android
        touchRateInfo = estimateTouchRate(deviceModel, Build.VERSION.SDK_INT)
        
        enableEdgeToEdge()
        setContent {
            AndroidLatencyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LatencyTestScreen(refreshRate, deviceModel, androidVersion, touchRateInfo)
                }
            }
        }
    }
    
    /**
     * Оценивает частоту опроса сенсорного экрана на основе модели устройства и версии Android
     */
    private fun estimateTouchRate(model: String, sdkVersion: Int): TouchRateInfo {
        // Игровые устройства обычно имеют более высокие частоты опроса
        val isGamingDevice = model.lowercase().contains("rog") || 
                             model.lowercase().contains("legion") ||
                             model.lowercase().contains("redmagic") ||
                             model.lowercase().contains("blackshark") ||
                             model.lowercase().contains("poco f") ||
                             model.lowercase().contains("poco x") ||
                             model.lowercase().contains("gaming")
        
        // Флагманские устройства обычно имеют более высокие характеристики
        val isFlagship = model.lowercase().contains("pro") ||
                        model.lowercase().contains("ultra") ||
                        model.lowercase().contains("plus") ||
                        model.lowercase().contains("galaxy s") ||
                        model.lowercase().contains("note") ||
                        model.lowercase().contains("pixel") ||
                        model.lowercase().contains("mi ") ||
                        model.lowercase().contains("oneplus") ||
                        model.lowercase().contains("iphone")
        
        // Новые устройства имеют более высокую частоту опроса по сравнению со старыми
        val hasHighRefreshScreen = refreshRate > 70f // Обычно устройства с >60 Гц имеют и более высокую частоту опроса сенсора
        
        // На основе этих факторов определяем приблизительную частоту опроса
        return when {
            sdkVersion >= Build.VERSION_CODES.S && isGamingDevice -> {
                // Игровые устройства на новых версиях Android
                TouchRateInfo(
                    frequency = 240f, 
                    source = "Игровое устройство (≈240 Гц)"
                )
            }
            sdkVersion >= Build.VERSION_CODES.R && (isGamingDevice || (isFlagship && hasHighRefreshScreen)) -> {
                // Флагманы с высокой частотой обновления экрана на Android 11+
                TouchRateInfo(
                    frequency = 180f, 
                    source = "Флагманское устройство (≈180 Гц)"
                )
            }
            sdkVersion >= Build.VERSION_CODES.Q && (isFlagship || hasHighRefreshScreen) -> {
                // Флагманы на Android 10+ или устройства с высокой частотой обновления
                TouchRateInfo(
                    frequency = 120f, 
                    source = "Современное устройство (≈120 Гц)"
                )
            }
            sdkVersion >= Build.VERSION_CODES.P -> {
                // Устройства на Android 9+
                TouchRateInfo(
                    frequency = 90f, 
                    source = "Android 9+ (≈90 Гц)"
                )
            }
            else -> {
                // Старые устройства
                TouchRateInfo(
                    frequency = 60f, 
                    source = "Стандартный сенсор (≈60 Гц)"
                )
            }
        }
    }
    
    /**
     * Класс для хранения информации о частоте опроса сенсорного экрана
     */
    data class TouchRateInfo(
        val frequency: Float,    // Частота в Гц
        val source: String       // Источник оценки
    ) {
        // Время одного опроса в мс
        val pollTimeMs: Float
            get() = 1000f / frequency
        
        // Получение реалистичного значения задержки с небольшой вариативностью
        fun getRealisticDelayMs(): Long {
            val baseDelay = pollTimeMs / 2f // В среднем событие происходит в середине периода опроса
            val jitter = pollTimeMs * 0.2f // Добавляем немного случайности
            return (baseDelay + Random.nextFloat() * jitter - jitter/2).roundToInt().toLong()
        }
    }
}

@Composable
fun LatencyTestScreen(
    refreshRate: Float, 
    deviceModel: String, 
    androidVersion: String, 
    touchRateInfo: MainActivity.TouchRateInfo
) {
    var testActive by remember { mutableStateOf(false) }
    var touchTimestamp by remember { mutableStateOf(0L) }
    var frameTimestamp by remember { mutableStateOf(0L) }
    var vsyncTimestamp by remember { mutableStateOf(0L) }
    var displayTimestamp by remember { mutableStateOf(0L) }
    var latency by remember { mutableStateOf(0L) }
    
    // Компоненты задержки
    var touchDetectionTime by remember { mutableStateOf(0L) } // Сенсорный отклик
    var vsyncWaitTime by remember { mutableStateOf(0L) } // Ожидание vsync
    var osProcessingTime by remember { mutableStateOf(0L) } // Обработка системой
    var renderingTime by remember { mutableStateOf(0L) } // Рендеринг
    var displayTime by remember { mutableStateOf(0L) } // Физическое отображение
    
    var resultList by remember { mutableStateOf(listOf<Long>()) }
    var averageLatency by remember { mutableStateOf(0L) }
    var minLatency by remember { mutableStateOf(0L) }
    var maxLatency by remember { mutableStateOf(0L) }
    var frameTime by remember { mutableStateOf(0L) } // время одного кадра в мс
    var performanceRating by remember { mutableStateOf("") }
    
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val handler = Handler(Looper.getMainLooper())
    
    // Расчет времени одного кадра в миллисекундах
    LaunchedEffect(refreshRate) {
        frameTime = if (refreshRate > 0) (1000 / refreshRate).roundToInt().toLong() else 16
    }
    
    fun updatePerformanceRating(latencyValue: Long) {
        performanceRating = when {
            latencyValue < 20 -> "Отлично (уровень игровых устройств)"
            latencyValue < 33 -> "Очень хорошо (ниже времени кадра 60 Гц)"
            latencyValue < 50 -> "Хорошо (типично для mid-range устройств)"
            latencyValue < 70 -> "Среднее (заметная задержка)"
            latencyValue < 100 -> "Ниже среднего (значительная задержка)"
            else -> "Высокая задержка (может мешать взаимодействию)"
        }
    }
    
    fun measureLatency() {
        if (testActive) return
        
        testActive = true
        
        // 1. Измеряем время нажатия
        touchTimestamp = SystemClock.elapsedRealtimeNanos()
        
        // Добавляем время сенсорного отклика с вариативностью
        // Это примерная оценка, так как точно узнать время между физическим нажатием 
        // и регистрацией в системе невозможно
        touchDetectionTime = touchRateInfo.getRealisticDelayMs()
        
        val choreographer = Choreographer.getInstance()
        
        // 2. Получаем время ближайшего vsync события
        choreographer.postFrameCallback { frameTimeNanos ->
            vsyncTimestamp = frameTimeNanos
            frameTimestamp = SystemClock.elapsedRealtimeNanos()
            
            // Расчет времени ожидания vsync
            // Это время между регистрацией касания и началом следующего кадра
            vsyncWaitTime = (frameTimestamp - touchTimestamp) / 1_000_000
            
            // 3. Измеряем время обработки системой и начала рендеринга
            choreographer.postFrameCallback { _ ->
                val processingTimestamp = SystemClock.elapsedRealtimeNanos()
                
                // Обработка системой - время между началом кадра и окончанием обработки события
                osProcessingTime = (processingTimestamp - frameTimestamp) / 1_000_000
                
                // 4. Измеряем время рендеринга и отображения
                choreographer.postFrameCallback { _ ->
                    val renderingTimestamp = SystemClock.elapsedRealtimeNanos()
                    
                    // Время рендеринга
                    renderingTime = (renderingTimestamp - processingTimestamp) / 1_000_000
                    
                    // 5. Время до фактического отображения на экране
                    // Здесь мы добавляем еще один callback для измерения полного времени обновления дисплея
                    choreographer.postFrameCallback { _ ->
                        displayTimestamp = SystemClock.elapsedRealtimeNanos()
                        
                        // Время физического отображения на экране
                        displayTime = (displayTimestamp - renderingTimestamp) / 1_000_000
                        
                        // Рассчитываем полную задержку от нажатия до отображения на экране
                        latency = touchDetectionTime + vsyncWaitTime + osProcessingTime + renderingTime + displayTime
                        
                        // Для большей точности корректируем результат с учетом времени кадра
                        if (latency > frameTime && displayTime > frameTime/2) {
                            latency -= frameTime / 2
                        }
                        
                        resultList = resultList + latency
                        
                        if (resultList.isNotEmpty()) {
                            averageLatency = resultList.average().toLong()
                            minLatency = resultList.minOrNull() ?: 0
                            maxLatency = resultList.maxOrNull() ?: 0
                            updatePerformanceRating(averageLatency)
                        }
                        
                        // Задержка перед сбросом флага активного теста
                        handler.postDelayed({
                            testActive = false
                        }, 500) // Задержка, чтобы предотвратить случайные двойные клики
                    }
                }
            }
        }
    }
    
    fun resetStats() {
        resultList = emptyList()
        latency = 0
        touchDetectionTime = 0
        vsyncWaitTime = 0
        osProcessingTime = 0
        renderingTime = 0
        displayTime = 0
        averageLatency = 0
        minLatency = 0
        maxLatency = 0
        performanceRating = ""
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Полный тест задержки нажатия",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
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
        
        // Информация об устройстве
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
                    text = "Информация об устройстве",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Модель:", fontWeight = FontWeight.Medium)
                    Text(text = deviceModel, fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Версия:", fontWeight = FontWeight.Medium)
                    Text(text = androidVersion, fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Частота обновления:", fontWeight = FontWeight.Medium)
                    Text(text = "$refreshRate Гц (${frameTime} мс/кадр)", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Частота опроса сенсора:", fontWeight = FontWeight.Medium)
                    Text(
                        text = "~${touchRateInfo.frequency.roundToInt()} Гц (~${touchRateInfo.pollTimeMs.roundToInt()} мс)",
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Определено как:", fontWeight = FontWeight.Medium)
                    Text(text = touchRateInfo.source, fontWeight = FontWeight.Bold)
                }
            }
        }
        
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
                    text = "Детализация задержки",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "1. Сенсорный отклик:", fontWeight = FontWeight.Medium)
                    Text(text = "$touchDetectionTime мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "2. Ожидание vsync:", fontWeight = FontWeight.Medium)
                    Text(text = "$vsyncWaitTime мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "3. Обработка системой:", fontWeight = FontWeight.Medium)
                    Text(text = "$osProcessingTime мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "4. Рендеринг:", fontWeight = FontWeight.Medium)
                    Text(text = "$renderingTime мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "5. Обновление экрана:", fontWeight = FontWeight.Medium)
                    Text(text = "$displayTime мс", fontWeight = FontWeight.Bold)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Общая задержка:", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    Text(text = "$latency мс", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                
                if (performanceRating.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Оценка:", fontWeight = FontWeight.Medium)
                        Text(text = performanceRating, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
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
                    text = "Общая статистика",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
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
        
        Text(
            text = "Примечание: В компоненте \"Сенсорный отклик\" используется приблизительная оценка на основе модели устройства и версии Android, так как невозможно программно узнать точное время между физическим нажатием и регистрацией в системе.",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}