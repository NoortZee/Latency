package com.example.androidlatency

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidlatency.ui.theme.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var refreshRate = 60f
    private var maxRefreshRate = 60f
    private var minRefreshRate = 60f
    private var deviceModel = ""
    private var androidVersion = ""
    private var touchRateInfo = TouchRateInfo(100f, "")
    private var isPowerSaveMode = false
    private var isGameMode = false
    private var systemPerformance = SystemPerformance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Получаем текущую и максимальную частоту обновления дисплея
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshRate = windowManager.defaultDisplay.refreshRate
        
        // На Android 11+ получаем дополнительную информацию о дисплее
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display
            display?.let {
                val mode = it.mode
                refreshRate = mode.refreshRate
                // Получаем максимальную поддерживаемую частоту обновления
                val supportedModes = it.supportedModes
                maxRefreshRate = supportedModes.maxOfOrNull { mode -> mode.refreshRate } ?: refreshRate
                minRefreshRate = supportedModes.minOfOrNull { mode -> mode.refreshRate } ?: refreshRate
            }
        }
        
        // Получаем информацию об устройстве
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        
        // Проверяем режим энергосбережения
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
        
        // Определяем возможное наличие игрового режима (косвенно)
        isGameMode = checkGameMode()
        
        // Собираем информацию о производительности системы
        systemPerformance = collectSystemPerformance()
        
        // Оценка частоты опроса сенсора на основе модели устройства и версии Android
        touchRateInfo = estimateTouchRate(deviceModel, Build.VERSION.SDK_INT)
        
        enableEdgeToEdge()
        setContent {
            AndroidLatencyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LatencyTestScreen(
                        refreshRate, 
                        maxRefreshRate,
                        deviceModel, 
                        androidVersion, 
                        touchRateInfo,
                        systemPerformance
                    )
                }
            }
        }
    }
    
    /**
     * Собирает информацию о производительности системы
     */
    private fun collectSystemPerformance(): SystemPerformance {
        // Проверяем уровень заряда батареи
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            this.registerReceiver(null, filter)
        }
        
        val batteryLevel = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale
        } ?: -1
        
        // Проверяем, подключено ли зарядное устройство
        val isCharging = batteryStatus?.let { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
        
        // Проверяем наличие режима пониженной производительности
        val isReducedPerformance = Settings.Global.getInt(
            contentResolver,
            "low_power",
            0
        ) != 0 || isPowerSaveMode
        
        // Оцениваем общую производительность системы
        val performanceLevel = when {
            isGameMode -> PerformanceLevel.HIGH
            isReducedPerformance -> PerformanceLevel.LOW
            batteryLevel < 15 && !isCharging -> PerformanceLevel.LOW
            batteryLevel < 50 && !isCharging -> PerformanceLevel.MEDIUM
            else -> PerformanceLevel.NORMAL
        }
        
        return SystemPerformance(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            isGameMode = isGameMode,
            performanceLevel = performanceLevel
        )
    }
    
    /**
     * Пытается определить, находится ли устройство в игровом режиме
     * Это приблизительная оценка, поскольку нет стандартного API
     */
    private fun checkGameMode(): Boolean {
        // Проверяем на Samsung Game Mode
        val isSamsungGameMode = try {
            val settingsGlobal = Settings.Global.getString(contentResolver, "game_home_enable")
            settingsGlobal == "1"
        } catch (e: Exception) {
            false
        }
        
        // Проверяем на MIUI Game Turbo
        val isMiuiGameTurbo = try {
            val settingsMiui = Settings.System.getString(contentResolver, "game_effect_enable")
            settingsMiui == "1"
        } catch (e: Exception) {
            false
        }
        
        // Проверяем наличие игрового режима OnePlus
        val isOnePlusGameMode = deviceModel.lowercase().contains("oneplus") &&
                Settings.System.getInt(contentResolver, "game_mode_enable", 0) != 0
        
        return isSamsungGameMode || isMiuiGameTurbo || isOnePlusGameMode
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
        val hasHighRefreshScreen = maxRefreshRate > 70f // Обычно устройства с >60 Гц имеют и более высокую частоту опроса сенсора
        
        // На основе этих факторов определяем приблизительную частоту опроса
        return when {
            sdkVersion >= Build.VERSION_CODES.S && isGamingDevice -> {
                // Игровые устройства на Android 12+
                // Некоторые устройства поддерживают до 480 Гц для сенсора
                if (maxRefreshRate >= 144f) { 
                    TouchRateInfo(360f, getString(R.string.gaming_device_144))
                } else {
                    TouchRateInfo(240f, getString(R.string.gaming_device))
                }
            }
            sdkVersion >= Build.VERSION_CODES.R && (isGamingDevice || (isFlagship && hasHighRefreshScreen)) -> {
                // Флагманы с высокой частотой обновления экрана на Android 11+
                if (maxRefreshRate >= 120f) {
                    TouchRateInfo(240f, getString(R.string.flagship_device_120))
                } else if (maxRefreshRate >= 90f) {
                    TouchRateInfo(180f, getString(R.string.flagship_device_90))
                } else {
                    TouchRateInfo(120f, getString(R.string.flagship_device))
                }
            }
            sdkVersion >= Build.VERSION_CODES.Q && (isFlagship || hasHighRefreshScreen) -> {
                // Флагманы на Android 10+ или устройства с высокой частотой обновления
                if (maxRefreshRate >= 90f) {
                    TouchRateInfo(120f, getString(R.string.modern_device_90))
                } else {
                    TouchRateInfo(90f, getString(R.string.modern_device))
                }
            }
            sdkVersion >= Build.VERSION_CODES.P && hasHighRefreshScreen -> {
                // Устройства на Android 9+ с повышенной частотой обновления
                TouchRateInfo(90f, getString(R.string.android9_90))
            }
            sdkVersion >= Build.VERSION_CODES.P -> {
                // Устройства на Android 9+
                TouchRateInfo(90f, getString(R.string.android9))
            }
            else -> {
                // Старые устройства
                TouchRateInfo(60f, getString(R.string.standard_touch))
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
    
    /**
     * Перечисление уровней производительности системы
     */
    enum class PerformanceLevel {
        LOW,     // Режим энергосбережения, низкий заряд батареи
        MEDIUM,  // Стандартный режим с некоторыми ограничениями
        NORMAL,  // Стандартный режим без ограничений
        HIGH     // Игровой режим, повышенная производительность
    }
    
    /**
     * Класс для хранения информации о производительности системы
     */
    data class SystemPerformance(
        val batteryLevel: Int = -1,
        val isCharging: Boolean = false,
        val isPowerSaveMode: Boolean = false,
        val isGameMode: Boolean = false,
        val performanceLevel: PerformanceLevel = PerformanceLevel.NORMAL
    ) {
        // Оценка влияния на производительность (множитель)
        fun getPerformanceMultiplier(): Float {
            return when (performanceLevel) {
                PerformanceLevel.LOW -> 0.85f      // Снижение производительности в режиме экономии
                PerformanceLevel.MEDIUM -> 0.95f   // Небольшое снижение при среднем режиме
                PerformanceLevel.NORMAL -> 1.0f    // Нормальная производительность
                PerformanceLevel.HIGH -> 1.05f     // Повышенная производительность в игровом режиме
            }
        }
        
        // Получение описания режима работы системы
        fun getDescription(): String {
            return when (performanceLevel) {
                PerformanceLevel.LOW -> appContext.getString(R.string.power_save_mode)
                PerformanceLevel.MEDIUM -> appContext.getString(R.string.standard_mode)
                PerformanceLevel.NORMAL -> appContext.getString(R.string.optimal_mode)
                PerformanceLevel.HIGH -> appContext.getString(R.string.high_performance_mode)
            }
        }
    }
    
    companion object {
        private lateinit var appContext: Context
    }
    
    init {
        appContext = this
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatencyTestScreen(
    refreshRate: Float, 
    maxRefreshRate: Float,
    deviceModel: String, 
    androidVersion: String, 
    touchRateInfo: MainActivity.TouchRateInfo,
    systemPerformance: MainActivity.SystemPerformance
) {
    var testActive by remember { mutableStateOf(false) }
    var touchTimestamp by remember { mutableStateOf(0L) }
    var frameTimestamp by remember { mutableStateOf(0L) }
    var vsyncTimestamp by remember { mutableStateOf(0L) }
    var displayTimestamp by remember { mutableStateOf(0L) }
    var latency by remember { mutableStateOf(0L) }
    
    // Компоненты задержки
    var touchDetectionTime by remember { mutableStateOf(0L) }
    var vsyncWaitTime by remember { mutableStateOf(0L) }
    var osProcessingTime by remember { mutableStateOf(0L) }
    var renderingTime by remember { mutableStateOf(0L) }
    var displayTime by remember { mutableStateOf(0L) }
    
    var resultList by remember { mutableStateOf(listOf<Long>()) }
    var averageLatency by remember { mutableStateOf(0L) }
    var minLatency by remember { mutableStateOf(0L) }
    var maxLatency by remember { mutableStateOf(0L) }
    var frameTime by remember { mutableStateOf(0L) }
    var performanceRating by remember { mutableStateOf("") }
    var currentRefreshRate by remember { mutableStateOf(refreshRate) }
    
    // Отслеживаем производительность
    var performanceMultiplier by remember { mutableStateOf(systemPerformance.getPerformanceMultiplier()) }
    var powerModeDescription by remember { mutableStateOf(systemPerformance.getDescription()) }
    
    // Состояние для анимации
    var showTestResults by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val handler = Handler(Looper.getMainLooper())
    
    // Расчет времени одного кадра в миллисекундах
    LaunchedEffect(refreshRate, systemPerformance.performanceLevel) {
        // Корректируем частоту обновления с учетом режима энергосбережения
        currentRefreshRate = if (systemPerformance.isPowerSaveMode && refreshRate > 60f) {
            // В режиме энергосбережения многие устройства снижают частоту обновления экрана до 60 Гц
            60f
        } else {
            refreshRate
        }
        
        // Время одного кадра с поправкой на реальную частоту обновления
        frameTime = if (currentRefreshRate > 0) {
            (1000 / currentRefreshRate).roundToInt().toLong()
        } else {
            16 // Значение по умолчанию для 60 Гц
        }
        
        // Обновляем множитель производительности при изменении режима
        performanceMultiplier = systemPerformance.getPerformanceMultiplier()
        powerModeDescription = systemPerformance.getDescription()
    }
    
    // Получение цвета по рейтингу производительности
    fun getPerformanceColor(latencyValue: Long): Color {
        return when {
            latencyValue < 20 -> Green
            latencyValue < 33 -> Yellow
            latencyValue < 50 -> MediumBlue
            latencyValue < 70 -> Orange
            else -> Red
        }
    }
    
    // Получение текстового рейтинга производительности
    fun getPerformanceRating(latencyValue: Long, context: Context): String {
        return when {
            latencyValue < 20 -> context.getString(R.string.excellent)
            latencyValue < 33 -> context.getString(R.string.very_good)
            latencyValue < 50 -> context.getString(R.string.good)
            latencyValue < 70 -> context.getString(R.string.average)
            latencyValue < 100 -> context.getString(R.string.below_average)
            else -> context.getString(R.string.high_latency)
        }
    }
    
    // Функция для запуска измерения задержки
    fun startLatencyTest() {
        if (testActive) return
        
        testActive = true
        showTestResults = false
        
        // Фиксируем время нажатия на кнопку
        touchTimestamp = SystemClock.elapsedRealtime()
        
        // Симулируем задержку сенсорного экрана
        // Это приблизительная оценка, так как мы не можем узнать точное время между физическим нажатием и регистрацией события системой
        val sensorDelay = touchRateInfo.getRealisticDelayMs()
        
        // Обрабатываем событие нажатия с учетом задержки сенсора
        handler.postDelayed({
            // Время регистрации нажатия системой
            frameTimestamp = SystemClock.elapsedRealtime()
            
            // Симулируем ожидание следующего VSYNC
            val vsyncDelay = Random.nextInt(1, frameTime.toInt()).toLong()
            
            handler.postDelayed({
                // Время наступления VSYNC
                vsyncTimestamp = SystemClock.elapsedRealtime()
                
                // Симулируем время обработки системой и рендеринг
                // Это зависит от производительности устройства
                val processingDelay = (frameTime * 0.5 * performanceMultiplier).toLong()
                
                handler.postDelayed({
                    // Время окончания рендеринга
                    val renderCompleteTimestamp = SystemClock.elapsedRealtime()
                    
                    // Осталось дождаться обновления экрана
                    // Это происходит при следующем VSYNC
                    val displayDelay = (frameTime - (renderCompleteTimestamp - vsyncTimestamp) % frameTime).toLong()
                    
                    handler.postDelayed({
                        // Время, когда изменения появились на экране
                        displayTimestamp = SystemClock.elapsedRealtime()
                        
                        // Общая задержка
                        latency = displayTimestamp - touchTimestamp
                        
                        // Разбиваем задержку на компоненты
                        touchDetectionTime = sensorDelay 
                        vsyncWaitTime = vsyncTimestamp - frameTimestamp
                        osProcessingTime = (processingDelay * 0.3).toLong()
                        renderingTime = (processingDelay * 0.7).toLong()
                        displayTime = displayDelay
                        
                        // Добавляем результат в историю
                        resultList = resultList + latency
                        
                        // Обновляем статистику
                        if (resultList.isNotEmpty()) {
                            averageLatency = resultList.sum() / resultList.size
                            minLatency = resultList.minOrNull() ?: latency
                            maxLatency = resultList.maxOrNull() ?: latency
                        }
                        
                        // Обновляем рейтинг
                        performanceRating = getPerformanceRating(latency, context)
                        
                        // Показываем результаты с небольшой задержкой
                        handler.postDelayed({
                            showTestResults = true
                            testActive = false
                        }, 300)
                        
                    }, displayDelay)
                }, processingDelay)
            }, vsyncDelay)
        }, sensorDelay)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.test_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { 
                        // Сбрасываем статистику
                        resultList = emptyList()
                        averageLatency = 0
                        minLatency = 0
                        maxLatency = 0
                        latency = 0
                        performanceRating = ""
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset_statistics))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Информационное окно с результатом измерения
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showTestResults) {
                            Text(
                                text = "${latency} ${stringResource(R.string.ms)}",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = getPerformanceColor(latency)
                            )
                            Text(
                                text = performanceRating,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            // Кнопка запуска теста
            Button(
                onClick = { startLatencyTest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(56.dp),
                enabled = !testActive
            ) {
                Text(
                    text = if (testActive) stringResource(R.string.measuring) else stringResource(R.string.tap_to_measure),
                    fontSize = 18.sp
                )
            }
            
            // Секция информации об устройстве
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.device_info),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    InfoRow(title = stringResource(R.string.model), value = deviceModel)
                    InfoRow(title = stringResource(R.string.version), value = androidVersion)
                    InfoRow(
                        title = stringResource(R.string.current_refresh_rate),
                        value = "$currentRefreshRate ${stringResource(R.string.hz)} (${frameTime} ${stringResource(R.string.ms_per_frame)})"
                    )
                    
                    if (maxRefreshRate > 0) {
                        InfoRow(
                            title = stringResource(R.string.max_refresh_rate),
                            value = "$maxRefreshRate ${stringResource(R.string.hz)}"
                        )
                    }
                    
                    InfoRow(
                        title = stringResource(R.string.touch_polling_rate),
                        value = "~${touchRateInfo.frequency.roundToInt()} ${stringResource(R.string.hz)} (~${touchRateInfo.pollTimeMs.roundToInt()} ${stringResource(R.string.ms)})"
                    )
                    
                    val sensorTypeSimplified = when {
                        touchRateInfo.source.contains(context.getString(R.string.gaming_device_144)) || 
                        touchRateInfo.source.contains(context.getString(R.string.gaming_device)) -> stringResource(R.string.gaming_sensor)
                        touchRateInfo.source.contains(context.getString(R.string.flagship_device_120)) || 
                        touchRateInfo.source.contains(context.getString(R.string.flagship_device_90)) || 
                        touchRateInfo.source.contains(context.getString(R.string.flagship_device)) -> stringResource(R.string.flagship_sensor)
                        touchRateInfo.source.contains(context.getString(R.string.modern_device_90)) || 
                        touchRateInfo.source.contains(context.getString(R.string.modern_device)) -> stringResource(R.string.modern_sensor)
                        touchRateInfo.source.contains(context.getString(R.string.standard_touch)) -> stringResource(R.string.standard_sensor)
                        touchRateInfo.source.contains(context.getString(R.string.android9_90)) || 
                        touchRateInfo.source.contains(context.getString(R.string.android9)) -> stringResource(R.string.modern_sensor)
                        else -> stringResource(R.string.standard_sensor)
                    }
                    
                    InfoRow(title = stringResource(R.string.sensor_type), value = sensorTypeSimplified)
                    InfoRow(title = stringResource(R.string.system_mode), value = powerModeDescription)
                }
            }
            
            // Если тест выполнен и результаты доступны, показываем детализацию
            if (showTestResults) {
                // Детализация задержки
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.latency_details),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        InfoRow(title = stringResource(R.string.touch_response), value = "$touchDetectionTime ${stringResource(R.string.ms)}")
                        InfoRow(title = stringResource(R.string.vsync_wait), value = "$vsyncWaitTime ${stringResource(R.string.ms)}")
                        InfoRow(title = stringResource(R.string.os_processing), value = "$osProcessingTime ${stringResource(R.string.ms)}")
                        InfoRow(title = stringResource(R.string.rendering), value = "$renderingTime ${stringResource(R.string.ms)}")
                        InfoRow(title = stringResource(R.string.display_update), value = "$displayTime ${stringResource(R.string.ms)}")
                        
                        // Разделитель
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.overall_latency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$latency ${stringResource(R.string.ms)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = getPerformanceColor(latency)
                            )
                        }
                    }
                }
                
                // Статистика по прошлым тестам
                if (resultList.size > 1) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.overall_statistics),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            InfoRow(title = stringResource(R.string.average_value), value = "${averageLatency} ${stringResource(R.string.ms)}")
                            InfoRow(title = stringResource(R.string.min_value), value = "${minLatency} ${stringResource(R.string.ms)}")
                            InfoRow(title = stringResource(R.string.max_value), value = "${maxLatency} ${stringResource(R.string.ms)}")
                            InfoRow(title = stringResource(R.string.test_count), value = "${resultList.size}")
                            
                            TextButton(
                                onClick = {
                                    resultList = emptyList()
                                    averageLatency = 0
                                    minLatency = 0
                                    maxLatency = 0
                                }, 
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(R.string.reset_statistics))
                            }
                        }
                    }
                }
                
                // Примечание
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                        )
                        Text(
                            text = stringResource(R.string.note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}