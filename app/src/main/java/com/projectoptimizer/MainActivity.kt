package com.projectoptimizer

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.StorageStatsManager
import android.content.Intent
import android.provider.Settings
import android.content.Context
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import android.os.BatteryManager
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Background = Color(0xFF111719)
private val Panel = Color(0xFF202B30)
private val Accent = Color(0xFF99B69E)
private val Peach = Color(0xFFFFCEAD)
private val Coral = Color(0xFFFF847C)
private val Rose = Color(0xFFE84A5F)
private val Muted = Color(0xFFB2C0BC)
private val CardGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF34464A), Color(0xFF26353A), Color(0xFF202B30))
)
private val AccentGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF99B69E), Color(0xFFFFCEAD), Color(0xFFFF847C))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Accent, onPrimary = Color(0xFF062016),
                    background = Background, surface = Panel,
                    onBackground = Color(0xFFF4F5F1), onSurface = Color(0xFFF4F5F1),
                    secondary = Peach, tertiary = Coral
                )
            ) { OptimizerApp(this) }
        }
    }
}

private data class TabItem(val title: String, val icon: ImageVector)
private enum class FileKind(val title: String) {
    PHOTO("Фото"), VIDEO("Видео"), AUDIO("Аудио"), GIF("GIF"), CACHE("Кэш и временные"), ARCHIVE("Архивы"), OTHER("Другие файлы")
}
private data class ScannedFile(
    val uri: Uri, val name: String, val size: Long, val modified: Long,
    val kind: FileKind, val location: String, val sha256: String? = null,
    val isDocument: Boolean = false
)

@Composable
private fun OptimizerApp(context: Context) {
    val tabs = listOf(
        TabItem("Главная", Icons.Default.Home),
        TabItem("Очистка", Icons.Default.CleaningServices),
        TabItem("Игры", Icons.Default.Gamepad),
        TabItem("Мониторинг", Icons.Default.MonitorHeart),
        TabItem("Настройки", Icons.Default.Settings)
    )
    var selected by remember { mutableIntStateOf(0) }
    var assistantOpen by remember { mutableStateOf(false) }
    var assistantDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(12000)
        if (!assistantDismissed) assistantOpen = true
    }
    val assistantTitle = when (selected) {
        0 -> "Начнём с состояния телефона?"
        1 -> "Небольшой совет по очистке"
        2 -> "Подготовим телефон к игре"
        3 -> "Разберём показатели"
        else -> "Нужна помощь с настройками?"
    }
    val assistantText = when (selected) {
        0 -> "Я помогу понять, что происходит с памятью и хранилищем. Если всё в норме — ничего лишнего делать не нужно."
        1 -> "Сначала сканируй файлы, посмотри список и отмечай только ненужное. Личные фото и видео не выбираются автоматически."
        2 -> "Перед игрой проверь нагрев и закрой тяжёлые загрузки. Android сам управляет памятью — агрессивно закрывать все приложения не нужно."
        3 -> "Свободная RAM меняется постоянно. Важнее смотреть на общую картину и не допускать сильного перегрева."
        else -> "Настрой автоочистку с безопасным интервалом. Android может немного задерживать фоновые задачи ради экономии батареи."
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Accent, onPrimary = Color(0xFF10221D),
            background = Background, surface = Panel,
            onBackground = Color(0xFFF4F5F1), onSurface = Color(0xFFF4F5F1),
            secondary = Peach, tertiary = Coral
        ),
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                androidx.compose.material3.Surface(color = Color(0xFF101A1E), tonalElevation = 0.dp, shadowElevation = 18.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        tabs.forEachIndexed { index, tab ->
                            val active = selected == index
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { selected = index; assistantOpen = false }.padding(vertical = 5.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) Accent else Color.Transparent).padding(horizontal = 18.dp, vertical = 6.dp)) {
                                    Icon(tab.icon, contentDescription = tab.title, tint = if (active) Color(0xFF14221D) else Muted.copy(alpha = .75f), modifier = Modifier.size(22.dp))
                                }
                                Text(tab.title, color = if (active) Accent else Muted.copy(alpha = .72f), fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
                }
                Text(
                    "Показываются приложения с ярлыком запуска, доступные Android для этого приложения.",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }

        ScreenSectionLabel("СОСТОЯНИЕ УСТРОЙСТВА")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("Свободная RAM", memoryInfo(context), Modifier.weight(1f))
            MetricCard("Нагрев", thermalInfo(context), Modifier.weight(1f))
        }
        InfoCard(
            "Что реально делает бустер",
            "Game Center запускает выбранные приложения и по твоему разрешению включает приоритетный режим уведомлений. Он не разгоняет процессор, не очищает память другой игры и не рисует выдуманный FPS."
        )
    }
}

@Composable
private fun GameStatPill(text: String) {
    androidx.compose.material3.Surface(
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(50)
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontSize = 9.sp, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun Monitoring(context: Context) {
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var monitoredStorage by remember { mutableStateOf<StorageSnapshot?>(null) }
    var memory by remember { mutableStateOf<Triple<Long, Long, Float>?>(null) }
    var battery by remember { mutableStateOf(readBatterySnapshot(context)) }
    var ramHistory by remember {
        mutableStateOf(
            prefs.getString("monitor_ram_history", "").orEmpty()
                .split(",").mapNotNull { it.toFloatOrNull() }.takeLast(36)
        )
    }
    var storageHistory by remember {
        mutableStateOf(
            prefs.getString("monitor_storage_history", "").orEmpty()
                .split(",").mapNotNull { it.toFloatOrNull() }.takeLast(36)
        )
    }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(context) {
        while (true) {
            val freshStorage = withContext(Dispatchers.IO) { readStorageStats(context) }
            val freshMemory = withContext(Dispatchers.IO) { readMemorySnapshot(context) }
            val freshBattery = readBatterySnapshot(context)
            monitoredStorage = freshStorage
            memory = freshMemory
            battery = freshBattery

            freshMemory?.let { current ->
                ramHistory = (ramHistory + current.third).takeLast(36)
                prefs.edit().putString("monitor_ram_history", ramHistory.joinToString(",")).apply()
            }
            freshStorage?.let { current ->
                val used = current.usedBytes.toFloat() / current.totalBytes.coerceAtLeast(1L).toFloat()
                storageHistory = (storageHistory + used.coerceIn(0f, 1f)).takeLast(36)
                prefs.edit().putString("monitor_storage_history", storageHistory.joinToString(",")).apply()
            }
            refreshTick++
            delay(15_000L)
        }
    }
    val stats = monitoredStorage
    val storageFraction = stats?.let {
        (it.usedBytes.toFloat() / it.totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
    } ?: 0f
    val memoryFraction = memory?.third ?: 0f
    val animatedStorage by animateFloatAsState(storageFraction, tween(850), label = "monitor_storage")
    val animatedMemory by animateFloatAsState(memoryFraction, tween(850), label = "monitor_memory")
    val thermal = thermalInfo(context)

    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ScreenHeading("LIVE DEVICE ANALYTICS", "Мониторинг", "Показатели обновляются, пока открыт этот экран.")
        MonitorMetricCard(
            title = "Хранилище",
            value = stats?.let { formatStorageBytes(context, it.freeBytes) } ?: "Проверка…",
            subtitle = stats?.let { "Свободно из " + formatStorageBytes(context, it.totalBytes) } ?: "Ожидаем данные Android",
            fraction = 1f - animatedStorage,
            tint = Accent,
            inverse = true
        )
        MonitorMetricCard(
            title = "Оперативная память",
            value = memory?.let { formatStorageBytes(context, it.first) } ?: "Проверка…",
            subtitle = memory?.let { "Свободно из " + formatStorageBytes(context, it.second) } ?: "Показатель доступной RAM",
            fraction = 1f - animatedMemory,
            tint = Peach
        )
        MonitorMetricCard(
            title = "Аккумулятор",
            value = battery?.let { "${it.percent}%" } ?: "Нет данных",
            subtitle = battery?.let {
                buildString {
                    append(if (it.charging) "Заряжается" else "Работает от батареи")
                    it.temperatureC?.let { temp -> append(" · батарея ${String.format(Locale.getDefault(), "%.1f", temp)} °C") }
                }
            } ?: "Системная информация недоступна",
            fraction = (battery?.percent ?: 0).coerceIn(0, 100) / 100f,
            tint = Coral
        )
        FeatureCard(
            "Тепловое состояние",
            thermal,
            when {
                thermal.contains("Критическое") || thermal.contains("Аварийное") -> Rose
                thermal.contains("Высокое") || thermal.contains("Умеренное") -> Coral
                else -> Accent
            }
        )
        ScreenSectionLabel("ИСТОРИЯ ПОКАЗАТЕЛЕЙ")
        TrendLineChart("Загрузка RAM", ramHistory, Peach)
        TrendLineChart("Заполнение хранилища", storageHistory, Accent)
        Text(
            "Графики собирают точки только пока открыт экран мониторинга. После закрытия приложения сбор останавливается.",
            color = Muted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}

private data class BatterySnapshot(
    val percent: Int,
    val charging: Boolean,
    val temperatureC: Float?
)

private fun readBatterySnapshot(context: Context): BatterySnapshot? = runCatching {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
    BatterySnapshot(
        percent = (level * 100f / scale).toInt().coerceIn(0, 100),
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        temperatureC = rawTemperature.takeIf { it > 0 }?.div(10f)
    )
}.getOrNull()

private fun readMemorySnapshot(context: Context): Triple<Long, Long, Float>? = runCatching {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    val total = info.totalMem.coerceAtLeast(1L)
    val free = info.availMem.coerceIn(0L, total)
    Triple(free, total, 1f - free.toFloat() / total.toFloat())
}.getOrNull()

@Composable
private fun TrendLineChart(title: String, values: List<Float>, tint: Color) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(if (values.isEmpty()) "Сбор данных" else "${values.size} точек", color = Muted, fontSize = 10.sp)
        }
        if (values.size < 2) {
            Box(Modifier.fillMaxWidth().height(74.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Нужно немного времени для графика", color = Muted, fontSize = 11.sp)
            }
        } else {
            Canvas(Modifier.fillMaxWidth().height(74.dp)) {
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = size.width * index / (values.size - 1).toFloat()
                    val y = size.height * (1f - value.coerceIn(0f, 1f))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.5f),
                    strokeWidth = 1.dp.toPx()
                )
                drawPath(path = path, color = tint, style = Stroke(width = 2.8.dp.toPx()))
            }
        }
        Text(
            values.lastOrNull()?.let { "${(it * 100).toInt()}%" } ?: "—",
            color = tint,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun Settings(context: Context) {
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("auto_clean_enabled", false)) }
    var cleanAppCache by remember { mutableStateOf(prefs.getBoolean("auto_clean_app_cache", true)) }
    var cleanTempFiles by remember { mutableStateOf(prefs.getBoolean("auto_clean_temp_files", true)) }
    var intervalMinutes by remember {
        mutableIntStateOf(
            prefs.getInt("auto_clean_interval_minutes", prefs.getInt("auto_clean_interval_days", 1) * 1440)
                .coerceIn(30, 10080)
        )
    }
    var showTips by remember { mutableStateOf(prefs.getBoolean("show_optimizer_tips", true)) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }

    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = prefs.getBoolean("auto_clean_enabled", false)
                cleanAppCache = prefs.getBoolean("auto_clean_app_cache", true)
                cleanTempFiles = prefs.getBoolean("auto_clean_temp_files", true)
                intervalMinutes = prefs.getInt(
                    "auto_clean_interval_minutes",
                    prefs.getInt("auto_clean_interval_days", 1) * 1440
                ).coerceIn(30, 10080)
                showTips = prefs.getBoolean("show_optimizer_tips", true)
                hasAccess = hasStorageAccess(context)
                refreshToken++
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val lastRun = remember(refreshToken) { prefs.getLong("auto_clean_last_run", 0L) }
    val lastFreed = remember(refreshToken) { prefs.getLong("auto_clean_last_freed_estimate", 0L) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeading("PERSONAL CONTROL", "Настройки", "Подстрой приложение под свой телефон — остальное оно сделает само.")
        InfoCard(
            "Автопилот устройства",
            "Включи расписание, чтобы ProjectOptimizer сам запускал безопасную очистку доступных временных файлов."
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAccess) {
            PrimaryActionButton("Выдать разрешение Android", onClick = { requestAllFilesAccess(context) }, modifier = Modifier.fillMaxWidth())
            InfoCard(
                "Нужно системное разрешение",
                "Android откроет собственный экран специального доступа. Включи «Разрешить доступ к управлению всеми файлами» и вернись сюда."
            )
        } else {
            FeatureCard("Доступ к файлам", "Разрешение предоставлено. Доступны только те файлы и каталоги, которые разрешает Android.", Accent)
        }

        SettingToggle(
            "Включить автоочистку",
            "Запуск в фоне по выбранному расписанию. Android может немного сдвигать время.",
            enabled
        ) { checked ->
            enabled = checked
            prefs.edit().putBoolean("auto_clean_enabled", checked).apply()
            if (checked && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
            else cancelAutoClean(context)
        }

        if (enabled) {
            ScreenSectionLabel("ИНТЕРВАЛ АВТООЧИСТКИ")
            Text(
                "Повторять: " + when (intervalMinutes) {
                    30 -> "каждые 30 минут"
                    60 -> "каждый час"
                    120 -> "каждые 2 часа"
                    360 -> "каждые 6 часов"
                    720 -> "каждые 12 часов"
                    1440 -> "раз в сутки"
                    4320 -> "раз в 3 дня"
                    10080 -> "раз в 7 дней"
                    else -> "каждые $intervalMinutes минут"
                },
                color = Color.White,
                fontSize = 17.sp
            )
            val intervalOptions = listOf(
                30 to "30 мин", 60 to "1 час", 120 to "2 часа", 360 to "6 часов",
                720 to "12 часов", 1440 to "24 часа", 4320 to "3 дня", 10080 to "7 дней"
            )
            intervalOptions.chunked(4).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    group.forEach { (minutes, label) ->
                        val selected = intervalMinutes == minutes
                        androidx.compose.material3.Surface(
                            onClick = {
                                intervalMinutes = minutes
                                prefs.edit().putInt("auto_clean_interval_minutes", minutes).apply()
                                if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
                            },
                            modifier = Modifier.weight(1f),
                            color = if (selected) Accent.copy(alpha = 0.18f) else Panel,
                            shape = RoundedCornerShape(15.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) Accent else Color.White.copy(alpha = 0.07f)
                            )
                        ) {
                            Text(
                                label,
                                Modifier.padding(vertical = 13.dp, horizontal = 3.dp),
                                color = if (selected) Accent else Color.White,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            InfoCard(
                "Как работает интервал",
                "После включения очистка будет запускаться с выбранной периодичностью. Android может задержать фоновую задачу из-за энергосбережения или нагрузки."
            )

            ScreenSectionLabel("ЧТО ОЧИЩАТЬ")
            SettingToggle("Кэш Project Optimizer", "Временные файлы самого оптимизатора.", cleanAppCache) { checked ->
                cleanAppCache = checked
                prefs.edit().putBoolean("auto_clean_app_cache", checked).apply()
                if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
                else if (enabled) cancelAutoClean(context)
            }
            SettingToggle(
                "Временные и мусорные файлы",
                "Старые .tmp, .temp, .part и файлы из папок temp/tmp/cache старше 14 дней.",
                cleanTempFiles
            ) { checked ->
                cleanTempFiles = checked
                prefs.edit().putBoolean("auto_clean_temp_files", checked).apply()
                if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
                else if (enabled) cancelAutoClean(context)
            }
            if (!cleanAppCache && !cleanTempFiles) {
                InfoCard("Нечего очищать", "Включи хотя бы один тип очистки, чтобы расписание могло работать.")
            }
        }

        ScreenSectionLabel("ОСНОВНЫЕ НАСТРОЙКИ")
        SettingToggle(
            "Подсказки на главной",
            "Показывать карточки с советами по уходу за телефоном.",
            showTips
        ) { checked ->
            showTips = checked
            prefs.edit().putBoolean("show_optimizer_tips", checked).apply()
        }

        ScreenSectionLabel("ИСТОРИЯ ОЧИСТКИ")
        InfoCard(
            "Последняя очистка",
            if (lastRun > 0L) {
                "Выполнена: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastRun)) + "\n" +
                    "Оценочно удалено: " + formatStorageBytes(context, lastFreed)
            } else "Автоматическая очистка ещё не запускалась."
        )
    }
}
@Composable
private fun ScreenHeading(eyebrow: String, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Accent))
            Text(eyebrow, color = Peach, fontSize = 10.sp, letterSpacing = 2.2.sp)
        }
        Text(title, color = Color(0xFFF4F5F1), fontSize = 30.sp, letterSpacing = (-0.5).sp)
        Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun ScreenSectionLabel(label: String) {
    Text(label, color = Peach, fontSize = 10.sp, letterSpacing = 1.8.sp)
}

@Composable
private fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(17.dp)
    ) {
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun FeatureCard(title: String, description: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardGradient, RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun MonitorMetricCard(
    title: String,
    value: String,
    subtitle: String,
    fraction: Float,
    tint: Color,
    inverse: Boolean = false
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = title)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (inverse) CardGradient else Brush.linearGradient(listOf(Panel, Panel)), RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(title, color = tint, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 25.sp)
        Text(subtitle, color = Muted, fontSize = 11.sp)
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.22f))
        ) {
            Box(
                Modifier.fillMaxWidth(animated.coerceAtLeast(0.015f)).height(8.dp)
                    .clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(tint, Coral)))
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(CardGradient, RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Text(title, color = Peach, fontSize = 12.sp, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(9.dp))
            Text(value, color = Color(0xFFF4F5F1), fontSize = 21.sp)
        }
    }
}
@Composable
private fun InfoCard(title: String, description: String) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(CardGradient, RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Text(title, color = Color(0xFFF4F5F1), fontSize = 16.sp)
            Spacer(Modifier.height(7.dp))
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}


private const val AUTO_CLEAN_WORK_NAME = "project_optimizer_auto_clean"

private fun scheduleAutoClean(context: Context) {
    val prefs = context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE)
    val intervalMinutes = prefs.getInt(
        "auto_clean_interval_minutes",
        prefs.getInt("auto_clean_interval_days", 1) * 1440
    ).coerceIn(30, 10080)
    val delay = intervalMinutes.toLong() * 60_000L
    val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
    val request = OneTimeWorkRequestBuilder<AutoCleanWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        AUTO_CLEAN_WORK_NAME, ExistingWorkPolicy.REPLACE, request
    )
}

private fun cancelAutoClean(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(AUTO_CLEAN_WORK_NAME)
}

class AutoCleanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_clean_enabled", false)) return@withContext Result.success()

        var estimatedRemoved = 0L
        if (prefs.getBoolean("auto_clean_app_cache", true)) {
            estimatedRemoved += clearOwnAppCache(applicationContext)
        }
        if (prefs.getBoolean("auto_clean_temp_files", true) && hasStorageAccess(applicationContext)) {
            val ageDays = prefs.getInt("auto_clean_temp_age_days", 14).coerceIn(7, 90)
            estimatedRemoved += cleanSafeTemporaryFiles(
                Environment.getExternalStorageDirectory(),
                ageDays
            )
        }

        prefs.edit()
            .putLong("auto_clean_last_run", System.currentTimeMillis())
            .putLong("auto_clean_last_freed_estimate", estimatedRemoved)
            .apply()
        if (prefs.getBoolean("auto_clean_enabled", false) &&
            (prefs.getBoolean("auto_clean_app_cache", true) || prefs.getBoolean("auto_clean_temp_files", true))) {
            scheduleAutoClean(applicationContext)
        }
        Result.success()
    }
}

/**
 * Only old temporary artifacts and explicitly named temporary/cache folders
 * are eligible. Personal media, documents, messenger data and backups are excluded.
 */
private fun cleanSafeTemporaryFiles(root: java.io.File, ageDays: Int): Long {
    val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return 0L
    val cutoff = System.currentTimeMillis() - ageDays.coerceIn(7, 90).toLong() * 86_400_000L
    val safeTempFolders = setOf("temp", "tmp", ".temp", ".tmp", ".cache", "cache", "caches", ".thumbnails", "thumbnails")
    val protectedFolders = setOf(
        "android", "dcim", "pictures", "movies", "music", "documents",
        "whatsapp", "telegram", "signal", "viber", "messenger",
        "backups", "backup", "recordings", "podcasts", "audiobooks"
    )
    val tempExtensions = setOf(".tmp", ".temp", ".part", ".crdownload", ".download", ".cache")
    val visited = HashSet<String>()
    var removedBytes = 0L
    var visitedFiles = 0

    fun walk(dir: java.io.File, depth: Int) {
        if (depth > 7 || visitedFiles >= 20_000) return
        val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: return
        if (!canonical.startsWith(rootPath + java.io.File.separator) && canonical != rootPath) return
        if (!visited.add(canonical)) return

        val relative = canonical.removePrefix(rootPath).trim('/')
        val segments = relative.split('/').filter { it.isNotBlank() }
        if (segments.any { it.lowercase(Locale.ROOT) in protectedFolders }) return

        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        children.forEach { file ->
            if (visitedFiles >= 20_000) return
            if (file.isDirectory) {
                walk(file, depth + 1)
            } else if (file.isFile) {
                visitedFiles++
                val modified = file.lastModified()
                if (modified <= 0L || modified >= cutoff) return@forEach

                val parentPath = file.parentFile?.let {
                    runCatching { it.canonicalPath.removePrefix(rootPath).trim('/') }.getOrDefault("")
                }.orEmpty()
                val inKnownTempFolder = parentPath.split('/').any {
                    it.lowercase(Locale.ROOT) in safeTempFolders
                }
                val hasTempExtension = tempExtensions.any {
                    file.name.lowercase(Locale.ROOT).endsWith(it)
                }

                if (inKnownTempFolder || hasTempExtension) {
                    val size = file.length()
                    if (runCatching { file.delete() }.getOrDefault(false)) removedBytes += size
                }
            }
        }
    }

    walk(root, 0)
    return removedBytes
}

private fun isCacheCandidate(parentPath: String, fileName: String): Boolean {
    val normalized = parentPath.replace('\\', '/').lowercase(Locale.ROOT)
    val cacheFolders = setOf("cache", ".cache", "caches", "tmp", "temp", ".thumbnails", "thumbnails")
    val inCacheFolder = normalized.split('/').any { it in cacheFolders }
    val temporaryFile = fileName.endsWith(".tmp") || fileName.endsWith(".temp") ||
        fileName.endsWith(".cache") || fileName.startsWith("thumb_")
    return inCacheFolder || temporaryFile
}

private fun clearOwnAppCache(context: Context): Long {
    fun directoryBytes(file: java.io.File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directoryBytes(it) } ?: 0L
    }
    val dirs = (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).distinctBy { it.absolutePath }
    return dirs.sumOf { dir ->
        val bytes = directoryBytes(dir)
        runCatching { dir.listFiles()?.forEach { it.deleteRecursively() } }
        bytes
    }
}

private data class StorageSnapshot(
    val totalBytes: Long,
    val freeBytes: Long,
    val source: String
) {
    val usedBytes: Long
        get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

/**
 * Reads storage for display, not for file-write decisions.
 *
 * Priority:
 * 1) Android's documented default UUID (primary user-visible storage).
 * 2) UUID resolved from the primary shared-storage path.
 * 3) UUID resolved from /data as a compatibility fallback.
 * 4) StatFs on the primary shared-storage mount.
 * 5) StatFs on /data as the last fallback.
 *
 * Every candidate keeps total/free values from the same volume. We never
 * combine the capacity of one partition with free space from another.
 */
private fun readStorageStats(context: Context): StorageSnapshot? {
    val storageManager = runCatching {
        context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    }.getOrNull()
    val statsManager = runCatching {
        context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
    }.getOrNull()

    val primaryPath = Environment.getExternalStorageDirectory()
    val dataPath = Environment.getDataDirectory()

    if (statsManager != null) {
        val uuids = linkedMapOf<java.util.UUID, String>()
        uuids[StorageManager.UUID_DEFAULT] = "Android · основной том"

        if (storageManager != null) {
            runCatching {
                val uuid = storageManager.getUuidForPath(primaryPath)
                if (!uuids.containsKey(uuid)) uuids[uuid] = "Android · общее хранилище"
            }
            runCatching {
                val uuid = storageManager.getUuidForPath(dataPath)
                if (!uuids.containsKey(uuid)) uuids[uuid] = "Android · резервный том"
            }
        }

        uuids.forEach { (uuid, source) ->
            val candidate = runCatching {
                val total = statsManager.getTotalBytes(uuid)
                val free = statsManager.getFreeBytes(uuid)
                if (total > 0L && free >= 0L && free <= total) {
                    StorageSnapshot(total, free, source)
                } else null
            }.getOrNull()

            if (candidate != null) return candidate
        }
    }

    fun statFsSnapshot(path: java.io.File, source: String): StorageSnapshot? =
        runCatching {
            val stat = StatFs(path.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            if (total > 0L && free >= 0L && free <= total) {
                StorageSnapshot(total, free, source)
            } else null
        }.getOrNull()

    fun fileSnapshot(path: java.io.File, source: String): StorageSnapshot? =
        runCatching {
            val total = path.totalSpace
            val free = path.usableSpace
            if (total > 0L && free >= 0L && free <= total) {
                StorageSnapshot(total, free, source)
            } else null
        }.getOrNull()

    return statFsSnapshot(primaryPath, "StatFs · общее хранилище")
        ?: fileSnapshot(primaryPath, "File API · общее хранилище")
        ?: statFsSnapshot(dataPath, "StatFs · резервный раздел")
        ?: fileSnapshot(dataPath, "File API · резервный раздел")
}

/**
 * StorageStatsManager calls can take seconds. Load them off the UI thread
 * and refresh whenever the app returns to the foreground.
 */
@Composable
private fun rememberStorageStats(context: Context): StorageSnapshot? {
    val state = remember(context) { mutableStateOf<StorageSnapshot?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context) {
        var refreshJob: Job? = null
        val refresh: () -> Unit = {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                state.value = withContext(Dispatchers.IO) { readStorageStats(context) }
            }
        }

        refresh()
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle?.addObserver(observer)

        onDispose {
            refreshJob?.cancel()
            lifecycle?.removeObserver(observer)
        }
    }

    return state.value
}

private fun hasStorageAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
} else {
    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    } else if (context is ComponentActivity) {
        context.requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 702)
    }
}
private fun availableMemoryLabel(context: Context): String = runCatching {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    formatStorageBytes(context, info.availMem)
}.getOrDefault("—")

private fun memoryInfo(context: Context): String = runCatching {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    "${formatBytes(info.availMem)} свободно из ${formatBytes(info.totalMem)}"
}.getOrDefault("Недоступно")
private fun thermalInfo(context: Context): String = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runCatching "Не поддерживается"
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    when (power.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "Норма"
        PowerManager.THERMAL_STATUS_LIGHT -> "Небольшой нагрев"
        PowerManager.THERMAL_STATUS_MODERATE -> "Умеренный нагрев"
        PowerManager.THERMAL_STATUS_SEVERE -> "Высокий нагрев"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Критический нагрев"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Аварийное состояние"
        else -> "Неизвестно"
    }
}.getOrDefault("Недоступно")
private fun formatStorageBytes(context: Context, bytes: Long): String =
    runCatching {
        android.text.format.Formatter.formatFileSize(context, bytes.coerceAtLeast(0L))
    }.getOrElse { formatBytes(bytes) }

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}
