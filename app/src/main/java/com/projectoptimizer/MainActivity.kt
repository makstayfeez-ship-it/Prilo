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
        ) { padding ->
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF182529), Background, Color(0xFF0D1316))))
                    .padding(padding)
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                        .padding(top = 18.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            (fadeIn(tween(230)) + slideInHorizontally(tween(260)) { it / 12 }) togetherWith
                                (fadeOut(tween(170)) + slideOutHorizontally(tween(210)) { -it / 12 })
                        },
                        label = "premium_screen_transition"
                    ) { page ->
                        when (page) {
                            0 -> Dashboard(context)
                            1 -> Cleaning(context)
                            2 -> Games(context)
                            3 -> Monitoring(context)
                            else -> Settings(context)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = assistantOpen,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
                    enter = fadeIn(tween(260)),
                    exit = fadeOut(tween(180))
                ) {
                    Column(
                        Modifier.fillMaxWidth(0.88f)
                            .background(Brush.linearGradient(listOf(Color(0xFF405B54), Color(0xFF26383C))), RoundedCornerShape(24.dp))
                            .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("OPTIMIZER · ПОМОЩНИК", color = Accent, fontSize = 10.sp, letterSpacing = 1.5.sp)
                            Text("×", color = Color.White, fontSize = 22.sp,
                                modifier = Modifier.clickable { assistantOpen = false; assistantDismissed = true })
                        }
                        Text(assistantTitle, color = Color.White, fontSize = 17.sp)
                        Text(assistantText, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("ПОНЯТНО", color = Accent, fontSize = 11.sp, letterSpacing = 1.2.sp,
                            modifier = Modifier.align(androidx.compose.ui.Alignment.End).clickable { assistantOpen = false })
                    }
                }
                androidx.compose.material3.Surface(
                    onClick = { assistantOpen = !assistantOpen },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp).size(54.dp),
                    shape = RoundedCornerShape(19.dp),
                    color = Accent,
                    shadowElevation = 10.dp
                ) {
                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("✦", color = Color(0xFF14221D), fontSize = 27.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(context: Context) {
    val storage = rememberStorageStats(context)
    val usedFraction = (storage?.let { it.usedBytes.toFloat() / it.totalBytes.coerceAtLeast(1L).toFloat() } ?: 0f).coerceIn(0f, 1f)
    val animatedUsage by animateFloatAsState(usedFraction, tween(1000), label = "dashboard_storage_ring")
    val battery = readBatterySnapshot(context)
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var showTips by remember { mutableStateOf(prefs.getBoolean("show_optimizer_tips", true)) }
    val tips = listOf(
        "Не закрывай всё подряд" to "Android сам распределяет оперативную память. Постоянное закрытие всех приложений часто только заставляет их запускаться заново.",
        "Следи за температурой" to "Если телефон сильно нагрелся, сделай паузу и дай ему остыть. Не используй сомнительные приложения для принудительного охлаждения.",
        "Оставляй запас накопителя" to "Когда свободного места мало, обновлениям и приложениям сложнее работать. Проверяй большие файлы перед удалением.",
        "Играй с умом" to "Перед долгой игровой сессией проверь нагрев и заряд. Игровой фокус поможет убрать отвлекающие уведомления."
    )
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PROJECT OPTIMIZER", color = Accent, fontSize = 10.sp, letterSpacing = 2.2.sp)
                Text("Твой телефон.\nПод контролем.", color = Color.White, fontSize = 27.sp, lineHeight = 31.sp)
            }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Accent, Peach, Coral))), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("✦", color = Background, fontSize = 24.sp)
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF496B61), Color(0xFF2D4848), Color(0xFF202F35))))
                .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(30.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("СОСТОЯНИЕ УСТРОЙСТВА", color = Peach, fontSize = 10.sp, letterSpacing = 1.8.sp)
                Text("● LIVE", color = Accent, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(126.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(Color.White.copy(alpha = 0.12f), -90f, 360f, false, style = Stroke(width = 12.dp.toPx()))
                        drawArc(Brush.sweepGradient(listOf(Accent, Peach, Coral, Accent)), -90f, animatedUsage * 360f, false, style = Stroke(width = 12.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text((animatedUsage * 100).toInt().toString() + "%", color = Color.White, fontSize = 27.sp)
                        Text("ЗАНЯТО", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(storage?.let { formatStorageBytes(context, it.freeBytes) } ?: "—", color = Color.White, fontSize = 30.sp)
                    Text("свободно на накопителе", color = Muted, fontSize = 12.sp)
                    Text("из " + (storage?.let { formatStorageBytes(context, it.totalBytes) } ?: "—"), color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                    Text(if (usedFraction >= .9f) "Мало свободного места" else if (usedFraction >= .75f) "Накопитель заполнен" else "Хранилище в порядке", color = if (usedFraction >= .9f) Coral else Accent, fontSize = 11.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("ЗАНЯТО", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp); Text(storage?.let { formatStorageBytes(context, it.usedBytes) } ?: "—", color = Color.White, fontSize = 14.sp) }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) { Text("ИСТОЧНИК", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp); Text("Android", color = Color.White, fontSize = 14.sp) }
            }
        }
        Text("БЫСТРЫЙ ОБЗОР", color = Peach, fontSize = 10.sp, letterSpacing = 2.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Оперативная память", availableMemoryLabel(context), "ДОСТУПНО", Accent, Modifier.weight(1f))
            DashboardMetric("Температура", thermalInfo(context), "ТЕПЛОВОЙ СТАТУС", Peach, Modifier.weight(1f))
        }
        DashboardMetric("Аккумулятор", battery?.let { it.percent.toString() + "% · " + if (it.charging) "заряжается" else "не заряжается" } ?: "Нет данных", "СОСТОЯНИЕ БАТАРЕИ", Coral, Modifier.fillMaxWidth())
        if (showTips) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("OPTIMIZER ПОДСКАЗЫВАЕТ", color = Peach, fontSize = 10.sp, letterSpacing = 1.6.sp)
                    Text("Листай карточки →", color = Muted, fontSize = 10.sp)
                }
                val pagerState = rememberPagerState(pageCount = { tips.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF354A48), Color(0xFF202D32)))).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("0" + (page + 1) + " / 0" + tips.size, color = Accent, fontSize = 10.sp, letterSpacing = 1.4.sp)
                        Text(tips[page].first, color = Color.White, fontSize = 18.sp)
                        Text(tips[page].second, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(tips.size) { i -> Box(Modifier.padding(horizontal = 3.dp).size(if (pagerState.currentPage == i) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(20.dp)).background(if (pagerState.currentPage == i) Accent else Muted.copy(alpha = .35f))) }
                }
            }
        }
        InfoCard("Умная забота о телефоне", "Project Optimizer показывает реальные системные показатели, помогает находить безопасные для удаления файлы и подготавливает удобные инструменты для игр. Он не обещает невозможного разгона процессора.")
    }
}

@Composable
private fun DashboardMetric(title: String, value: String, caption: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(23.dp)).background(Brush.linearGradient(listOf(Color(0xFF2C3C40), Color(0xFF1D292D)))).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(23.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(caption, color = tint, fontSize = 9.sp, letterSpacing = 1.2.sp)
        Text(title, color = Muted, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 19.sp, lineHeight = 23.sp)
    }
}

@Composable
private fun Cleaning(context: Context) {
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hasAccess = hasStorageAccess(context) }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    var files by remember { mutableStateOf<List<ScannedFile>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var showLocked by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Нажми «Найти файлы», чтобы проверить хранилище.") }
    var foundCount by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(100) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var locked by remember { mutableStateOf(prefs.getStringSet("locked_uris", emptySet())?.toSet() ?: emptySet()) }
    var allowGif by remember { mutableStateOf(prefs.getBoolean("allow_gif", true)) }
    var allowArchives by remember { mutableStateOf(prefs.getBoolean("allow_archives", false)) }
    var allowOld by remember { mutableStateOf(prefs.getBoolean("allow_old", false)) }
    var allowDuplicates by remember { mutableStateOf(prefs.getBoolean("allow_duplicates", true)) }
    var oldDays by remember { mutableIntStateOf(prefs.getInt("old_days", 180).coerceIn(7, 730)) }
    var cacheFreeBefore by remember { mutableLongStateOf(-1L) }
    var lastDeleted by remember { mutableStateOf("Пока ничего не удалено.") }
    val scope = rememberCoroutineScope()
    var extraFolder by remember { mutableStateOf(prefs.getString("extra_folder_uri", null)) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            extraFolder = uri.toString()
            prefs.edit().putString("extra_folder_uri", uri.toString()).apply()
            status = "Дополнительная папка подключена. Запусти поиск ещё раз."
        }
    }
    val cacheLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val freeBefore = cacheFreeBefore
        scope.launch {
            val freshStats = withContext(Dispatchers.IO) { readStorageStats(context) }
            val gained = if (freeBefore >= 0 && freshStats != null) {
                (freshStats.freeBytes - freeBefore).coerceAtLeast(0L)
            } else null
            status = when {
                gained != null && gained > 0L ->
                    "После системного запроса свободного места стало больше на ${formatStorageBytes(context, gained)}."
                freshStats == null ->
                    "Android вернул управление, но не удалось повторно прочитать память."
                else ->
                    "Android вернул управление, но прирост свободного места не обнаружен. Система могла не выполнить очистку."
            }
            cacheFreeBefore = -1L
        }
    }
    val protected = setOf(FileKind.PHOTO, FileKind.VIDEO, FileKind.AUDIO)
    val duplicates = remember(files) { files.filter { it.sha256 != null && it.size > 0 }.groupBy { it.sha256 }.values.filter { it.size > 1 }.flatMap { it.drop(1) }.map { it.uri }.toSet() }
    val cutoff = System.currentTimeMillis() / 1000 - oldDays.toLong() * 86400L
    val eligible = files.filter { file -> file.kind !in protected && file.uri.toString() !in locked && ((file.kind == FileKind.CACHE) || (allowGif && file.kind == FileKind.GIF) || (allowArchives && file.kind == FileKind.ARCHIVE) || (allowDuplicates && file.uri in duplicates) || (allowOld && file.modified > 0 && file.modified < cutoff)) }
    val selectedFiles = files.filter { it.uri.toString() in selected && it.uri.toString() !in locked && it.kind !in protected }
    val selectedBytes = selectedFiles.sumOf { it.size }
    val visible = if (showLocked) files.filter { it.uri.toString() in locked } else files
    if (showDialog) AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("Подтвердить очистку") },
        text = { Text("Выбрано файлов: ${selectedFiles.size}. Освободится примерно ${formatBytes(selectedBytes)}. Фото, видео, аудио и файлы под замком защищены.") },
        confirmButton = { TextButton(onClick = {
            showDialog = false
            deleting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    var count = 0; var bytes = 0L; val removed = mutableSetOf<String>()
                    selectedFiles.forEach { file ->
                        val deleted = runCatching {
                            if (file.isDocument) android.provider.DocumentsContract.deleteDocument(context.contentResolver, file.uri)
                            else file.uri.path?.let { java.io.File(it).delete() } ?: false
                        }.getOrDefault(false)
                        if (deleted) { count++; bytes += file.size; removed.add(file.uri.toString()) }
                    }
                    Triple(count, bytes, removed)
                }
                files = files.filterNot { it.uri.toString() in result.third }
                selected = selected - result.third
                lastDeleted = "Удалено ${result.first} файлов · освобождено ${formatBytes(result.second)}"
                status = "Очистка завершена. ${lastDeleted}"
                deleting = false
            }
        }) { Text("Удалить выбранное") } },
        dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Отмена") } }
    )
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        val stats = rememberStorageStats(context)
        ScreenHeading("SMART CLEANUP / 01", "Очистка", "Порядок в хранилище — без риска удалить важные файлы.")

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF345B52), Color(0xFF203B3B), Color(0xFF1A292E))))
                .border(1.dp, Color(0xFF9FD9B8).copy(alpha = .22f), RoundedCornerShape(30.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("АНАЛИЗ ХРАНИЛИЩА", color = Color(0xFFBDE8CB), fontSize = 10.sp, letterSpacing = 1.8.sp)
                Text(if (scanned) "● ГОТОВО" else "● ОЖИДАНИЕ", color = if (scanned) Accent else Peach, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(112.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(Color.White.copy(alpha = .12f), -90f, 360f, false, style = Stroke(width = 10.dp.toPx()))
                        val fraction = stats?.let { it.usedBytes.toFloat() / it.totalBytes.coerceAtLeast(1L).toFloat() } ?: 0f
                        drawArc(Brush.sweepGradient(listOf(Accent, Peach, Coral, Accent)), -90f, fraction.coerceIn(0f, 1f) * 360f, false, style = Stroke(width = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(stats?.let { ((it.usedBytes.toFloat() / it.totalBytes.coerceAtLeast(1L)) * 100).toInt().toString() + "%" } ?: "—", color = Color.White, fontSize = 25.sp)
                        Text("ЗАНЯТО", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Свободно", color = Color(0xFFBDE8CB), fontSize = 11.sp)
                    Text(stats?.let { formatStorageBytes(context, it.freeBytes) } ?: "Проверка…", color = Color.White, fontSize = 27.sp)
                    Text("из " + (stats?.let { formatStorageBytes(context, it.totalBytes) } ?: "—"), color = Muted, fontSize = 12.sp)
                    Text(stats?.let { "Занято " + formatStorageBytes(context, it.usedBytes) } ?: "Данные Android", color = Muted, fontSize = 10.sp)
                }
            }
            androidx.compose.material3.Surface(
                onClick = {
                    scanning = true; scanned = false; showLocked = false; foundCount = 0; selected = emptySet(); status = "Идёт поиск файлов…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val shared = scanAllFiles { n -> if (n % 100 == 0) scope.launch(Dispatchers.Main) { foundCount = n } }
                            val extra = extraFolder?.let { scanDocumentTree(context, Uri.parse(it)) }.orEmpty()
                            (shared + extra).distinctBy { it.uri }
                        }
                        files = result
                        selected = result.filter { file -> file.kind !in protected && ((allowGif && file.kind == FileKind.GIF) || (allowArchives && file.kind == FileKind.ARCHIVE) || (allowOld && file.modified > 0 && file.modified < cutoff)) }.map { it.uri.toString() }.toSet()
                        limit = 100; scanned = true; scanning = false
                        status = "Поиск завершён. Найдено файлов: " + result.size + ". Проверь список и отметь нужное."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasAccess && !scanning && !deleting,
                shape = RoundedCornerShape(17.dp),
                color = Accent
            ) {
                Row(Modifier.padding(vertical = 15.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (scanning) "СКАНИРОВАНИЕ…" else "⌕  НАЙТИ ЛИШНИЕ ФАЙЛЫ", color = Color(0xFF14241F), fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Color.White.copy(alpha = .12f))
                Text("Просматриваю хранилище · найдено: " + foundCount, color = Color.White.copy(alpha = .78f), fontSize = 11.sp)
            }
        }

        if (!hasAccess) {
            FeatureCard("Нужен доступ к файлам", "Разрешение Android позволяет проверить общие папки. Закрытые системные данные останутся недоступны.", Coral)
            PrimaryActionButton("Открыть разрешения Android", onClick = { requestAllFilesAccess(context) }, modifier = Modifier.fillMaxWidth())
        } else {
            FeatureCard("Доступ к хранилищу активен", "Личные фото, видео и аудио защищены от автоматического выбора. Перед удалением всегда можно проверить каждый файл.", Accent)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
                .background(Panel).border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(23.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Дополнительная папка", color = Color.White, fontSize = 15.sp)
                    Text(if (extraFolder == null) "Подключи папку для проверки" else "Папка подключена к сканированию", color = Muted, fontSize = 11.sp)
                }
                Text("＋", color = Accent, fontSize = 25.sp, modifier = Modifier.clickable { folderPicker.launch(null) }.padding(5.dp))
            }
            PrimaryActionButton(if (extraFolder == null) "Выбрать папку" else "Изменить папку", onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth())
            if (extraFolder != null) {
                TextButton(onClick = { extraFolder = null; prefs.edit().remove("extra_folder_uri").apply(); status = "Дополнительная папка отключена." }) { Text("Отключить папку", color = Coral) }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Column(Modifier.fillMaxWidth().background(Color(0xFF242F35), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("КЭШ ПРИЛОЖЕНИЙ", color = Peach, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text("Очистка кэша", color = Color.White, fontSize = 16.sp)
                Text("Сначала очищается кэш самого оптимизатора, затем Android может показать системный запрос на очистку кэша других приложений.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                PrimaryActionButton(
                    "Запустить очистку кэша",
                    onClick = {
                        cacheFreeBefore = stats?.freeBytes ?: -1L
                        deleting = true
                        scope.launch {
                            val ownCacheBytes = withContext(Dispatchers.IO) { clearOwnAppCache(context) }
                            deleting = false
                            status = "Кэш Project Optimizer очищен: " + formatBytes(ownCacheBytes) + ". Запрашиваю системную очистку других приложений…"
                            runCatching { cacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE)) }
                                .onFailure { status = "Кэш Project Optimizer очищен: " + formatBytes(ownCacheBytes) + ". Android не запустил общую очистку."; cacheFreeBefore = -1L }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(Modifier.fillMaxWidth().background(Color(0xFF1B292D), RoundedCornerShape(22.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ПОСЛЕДНЕЕ ДЕЙСТВИЕ", color = Peach, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Text(status, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
            Text(lastDeleted, color = Accent, fontSize = 11.sp)
        }

        if (scanned) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ScreenSectionLabel("РЕЗУЛЬТАТ СКАНИРОВАНИЯ")
                        Text(files.size.toString() + " файлов найдено", color = Color.White, fontSize = 19.sp)
                    }
                    Text("↻", color = Accent, fontSize = 23.sp, modifier = Modifier.clickable { scanned = false }.padding(5.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DashboardMetric("Выбрано", selectedFiles.size.toString(), formatBytes(selectedBytes), Accent, Modifier.weight(1f))
                    DashboardMetric("Можно проверить", eligible.size.toString(), "кандидатов", Peach, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton("Все файлы", onClick = { showLocked = false }, modifier = Modifier.weight(1f))
                    PrimaryActionButton("Под замком", onClick = { showLocked = true }, modifier = Modifier.weight(1f))
                }
                if (!showLocked) {
                    PrimaryActionButton("Очистить выбранное · " + selectedFiles.size, onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth())
                    PrimaryActionButton("Выбрать безопасные файлы · " + eligible.size, onClick = { selected = eligible.map { it.uri.toString() }.toSet(); showDialog = true }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Файлы под замком исключены из очистки.", color = Muted, fontSize = 11.sp)
                }
                visible.take(limit).forEach { file ->
                    val key = file.uri.toString()
                    val isLocked = key in locked
                    val isProtected = file.kind in protected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF202D32))
                            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp))
                            .padding(13.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF34464A)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(when (file.kind) { FileKind.PHOTO -> "▧"; FileKind.VIDEO -> "▶"; FileKind.AUDIO -> "♫"; FileKind.GIF -> "GIF"; FileKind.CACHE -> "⌁"; FileKind.ARCHIVE -> "ZIP"; else -> "▤" }, color = Accent, fontSize = 13.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(file.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
                            Text(formatBytes(file.size) + " · " + file.kind.title, color = Muted, fontSize = 10.sp)
                            Text(if (isProtected) "Личный тип файла · защищён" else file.location, color = Muted, fontSize = 9.sp, maxLines = 1)
                            Text(if (isLocked) "Под замком" else "Нажми замок, чтобы защитить", color = if (isLocked) Peach else Muted, fontSize = 9.sp, modifier = Modifier.clickable { locked = if (isLocked) locked - key else locked + key; prefs.edit().putStringSet("locked_uris", locked).apply(); if (!isLocked) selected = selected - key })
                        }
                        if (!showLocked && !isProtected && !isLocked) {
                            Checkbox(checked = key in selected, onCheckedChange = { yes -> selected = if (yes) selected + key else selected - key })
                        } else if (showLocked) {
                            TextButton(onClick = { locked = locked - key; prefs.edit().putStringSet("locked_uris", locked).apply() }) { Text("Снять", color = Accent) }
                        }
                    }
                }
                if (visible.size > limit) PrimaryActionButton("Показать ещё " + (visible.size - limit), onClick = { limit += 100 }, modifier = Modifier.fillMaxWidth())
                if (visible.isEmpty()) InfoCard("Список пуст", if (showLocked) "Нет файлов под замком." else "Файлы не найдены.")
            }
        }

        Column(Modifier.fillMaxWidth().background(Color(0xFF1C292E), RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenSectionLabel("УМНЫЕ ПРАВИЛА ОТБОРА")
            Text("Что считать лишним", color = Color.White, fontSize = 17.sp)
            SettingToggle("GIF-файлы", "Автоматически отмечать GIF", allowGif) { allowGif = it; prefs.edit().putBoolean("allow_gif", it).apply() }
            SettingToggle("Архивы и APK", "ZIP, RAR, 7Z, TAR, GZ, APK и другие", allowArchives) { allowArchives = it; prefs.edit().putBoolean("allow_archives", it).apply() }
            SettingToggle("Повторяющиеся файлы", "Точные копии по SHA-256", allowDuplicates) { allowDuplicates = it; prefs.edit().putBoolean("allow_duplicates", it).apply() }
            SettingToggle("Старые файлы", "Отмечать файлы старше выбранного срока", allowOld) { allowOld = it; prefs.edit().putBoolean("allow_old", it).apply() }
            Text("Считать старыми через: " + oldDays + " дней", color = Muted, fontSize = 12.sp)
            androidx.compose.material3.Slider(value = oldDays.toFloat(), onValueChange = { oldDays = it.toInt().coerceIn(7, 730) }, onValueChangeFinished = { prefs.edit().putInt("old_days", oldDays).apply() }, valueRange = 7f..730f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("7 дней", color = Muted, fontSize = 10.sp); Text("730 дней", color = Muted, fontSize = 10.sp) }
        }
        InfoCard("Защита данных Android", "Фото, видео и аудио не отмечаются автоматически. Закрытые папки приложений и часть системных каталогов недоступны из-за ограничений Android.")
    }
}
@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontSize = 14.sp)
                Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF182226),
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Color(0xFF344146),
                    uncheckedBorderColor = Color(0xFF506066)
                )
            )
        }
    }
}

private fun scanAllFiles(onProgress: (Int) -> Unit = {}): List<ScannedFile> {
    val root = Environment.getExternalStorageDirectory()
    val result = mutableListOf<ScannedFile>()
    val maxFiles = 100000
    val visited = HashSet<String>()
    val blocked = setOf("Android/data", "Android/obb")
    fun walk(dir: java.io.File, depth: Int) {
        if (depth > 40 || result.size >= maxFiles) return
        val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: return
        if (!visited.add(canonical)) return
        val relative = runCatching { dir.relativeTo(root).invariantSeparatorsPath }.getOrDefault("")
        if (blocked.any { relative == it || relative.startsWith("$it/") }) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        children.forEach { file ->
            if (result.size >= maxFiles) return
            if (file.isDirectory) walk(file, depth + 1)
            else if (file.isFile && file.canRead()) {
                val name = file.name
                val lower = name.lowercase(Locale.ROOT)
                val mime = java.net.URLConnection.guessContentTypeFromName(name).orEmpty().lowercase(Locale.ROOT)
                val kind = when {
                    isCacheCandidate(file.parent ?: "", lower) -> FileKind.CACHE
                    lower.endsWith(".gif") -> FileKind.GIF
                    lower.matches(Regex(".*\\.(zip|rar|7z|tar|gz|bz2|xz|apk|xapk|apks)$")) -> FileKind.ARCHIVE
                    mime.startsWith("image/") || lower.matches(Regex(".*\\.(jpg|jpeg|png|webp|heic|bmp)$")) -> FileKind.PHOTO
                    mime.startsWith("video/") || lower.matches(Regex(".*\\.(mp4|mkv|mov|avi|webm|3gp)$")) -> FileKind.VIDEO
                    mime.startsWith("audio/") || lower.matches(Regex(".*\\.(mp3|m4a|aac|wav|flac|ogg|opus)$")) -> FileKind.AUDIO
                    else -> FileKind.OTHER
                }
                result.add(ScannedFile(Uri.fromFile(file), name, file.length(), file.lastModified() / 1000, kind, file.parent ?: root.absolutePath))
                if (result.size % 100 == 0) onProgress(result.size)
            }
        }
    }
    walk(root, 0)
    return result.groupBy { it.size }.values.flatMap { sameSize ->
        if (sameSize.size < 2 || sameSize.first().size <= 0) sameSize
        else sameSize.map { file -> file.copy(sha256 = hashFileFromFile(file.uri.path)) }
    }
}
private fun hashFileFromFile(path: String?): String? = runCatching {
    if (path == null) return null
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    java.io.FileInputStream(path).buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}.getOrNull()

private fun scanDocumentTree(context: Context, treeUri: Uri): List<ScannedFile> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val result = mutableListOf<ScannedFile>()
    val visited = HashSet<String>()
    fun walk(folder: DocumentFile, depth: Int) {
        if (depth > 30 || result.size >= 50000 || !visited.add(folder.uri.toString())) return
        val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray())
        children.forEach { item ->
            if (result.size >= 50000) return
            if (item.isDirectory) walk(item, depth + 1)
            else if (item.isFile && item.canRead()) {
                val name = item.name ?: "Без имени"
                val lower = name.lowercase(Locale.ROOT)
                val mime = item.type.orEmpty().lowercase(Locale.ROOT)
                val kind = when {
                    isCacheCandidate(item.parentFile?.name.orEmpty(), lower) -> FileKind.CACHE
                    lower.endsWith(".gif") -> FileKind.GIF
                    lower.matches(Regex(".*\\\\.(zip|rar|7z|tar|gz|bz2|xz|apk|xapk|apks)$")) -> FileKind.ARCHIVE
                    mime.startsWith("image/") || lower.matches(Regex(".*\\\\.(jpg|jpeg|png|webp|heic|bmp)$")) -> FileKind.PHOTO
                    mime.startsWith("video/") || lower.matches(Regex(".*\\\\.(mp4|mkv|mov|avi|webm|3gp)$")) -> FileKind.VIDEO
                    mime.startsWith("audio/") || lower.matches(Regex(".*\\\\.(mp3|m4a|aac|wav|flac|ogg|opus)$")) -> FileKind.AUDIO
                    else -> FileKind.OTHER
                }
                result.add(ScannedFile(item.uri, name, item.length(), item.lastModified() / 1000, kind, item.parentFile?.name ?: "Выбранная папка", isDocument = true))
            }
        }
    }
    walk(root, 0)
    return result
}

private fun hashFile(context: Context, uri: Uri): String? = runCatching {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    } ?: return null
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}.getOrNull()

private data class LaunchableGame(
    val label: String,
    val packageName: String,
    val isGameCategory: Boolean
)

private fun loadLaunchableApps(context: Context): List<LaunchableGame> = runCatching {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { resolve ->
            val activity = resolve.activityInfo ?: return@mapNotNull null
            val packageName = activity.packageName
            if (packageName == context.packageName) return@mapNotNull null
            LaunchableGame(
                label = activity.loadLabel(pm)?.toString() ?: packageName,
                packageName = packageName,
                isGameCategory = activity.applicationInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareByDescending<LaunchableGame> { it.isGameCategory }.thenBy { it.label.lowercase(Locale.ROOT) })
}.getOrDefault(emptyList())

private fun hasNotificationPolicyAccess(context: Context): Boolean =
    runCatching {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

private fun setGameFocus(context: Context, enabled: Boolean): Boolean = runCatching {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    if (!manager.isNotificationPolicyAccessGranted) return false
    val prefs = context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE)
    val active = prefs.getBoolean("game_focus_active", false)
    if (enabled) {
        if (!active) {
            prefs.edit()
                .putInt("game_focus_previous_filter", manager.currentInterruptionFilter)
                .putBoolean("game_focus_active", true)
                .apply()
        }
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    } else if (active) {
        val previous = prefs.getInt(
            "game_focus_previous_filter",
            NotificationManager.INTERRUPTION_FILTER_ALL
        )
        manager.setInterruptionFilter(previous)
        prefs.edit().putBoolean("game_focus_active", false).apply()
    }
    true
}.getOrDefault(false)

private fun launchGame(context: Context, packageName: String, focusBeforeLaunch: Boolean) {
    if (focusBeforeLaunch && !hasNotificationPolicyAccess(context)) {
        Toast.makeText(context, "Сначала разреши доступ к режиму «Не беспокоить».", Toast.LENGTH_LONG).show()
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        return
    }
    if (focusBeforeLaunch && !setGameFocus(context, true)) {
        Toast.makeText(context, "Не удалось включить игровой фокус.", Toast.LENGTH_LONG).show()
        return
    }
    val launchIntent = runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
    if (launchIntent == null) {
        Toast.makeText(context, "Не удалось открыть это приложение.", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(launchIntent) }
        .onFailure { Toast.makeText(context, "Не получилось запустить приложение.", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun GameAppRow(
    app: LaunchableGame,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onLaunch: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(20.dp))
            .padding(13.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Accent, Peach, Coral))),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(app.label.take(1).uppercase(Locale.ROOT), color = Color(0xFF1B292B), fontSize = 19.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(app.label, color = Color.White, fontSize = 14.sp, maxLines = 1)
            Text(
                if (app.isGameCategory) "Игра · Android" else "Приложение",
                color = Muted,
                fontSize = 10.sp
            )
        }
        if (onLaunch != null) {
            Text(
                "ИГРАТЬ",
                color = Accent,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable { onLaunch() }
                    .padding(horizontal = 7.dp, vertical = 10.dp)
            )
        } else if (onRemove != null) {
            Text(
                "УБРАТЬ",
                color = Coral,
                fontSize = 10.sp,
                modifier = Modifier.clickable { onRemove() }.padding(7.dp)
            )
        } else {
            Text(
                if (isAdded) "ДОБАВЛЕНО" else "+ ДОБАВИТЬ",
                color = if (isAdded) Muted else Accent,
                fontSize = 9.sp,
                modifier = Modifier.clickable { onAdd() }.padding(6.dp)
            )
        }
    }
}

@Composable
private fun Games(context: Context) {
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var installedApps by remember { mutableStateOf(loadLaunchableApps(context)) }
    var selectedPackages by remember {
        mutableStateOf(prefs.getStringSet("game_packages", emptySet())?.toSet() ?: emptySet())
    }
    var focusBeforeLaunch by remember { mutableStateOf(prefs.getBoolean("game_focus_before_launch", false)) }
    var focusActive by remember { mutableStateOf(prefs.getBoolean("game_focus_active", false)) }
    var hasDndAccess by remember { mutableStateOf(hasNotificationPolicyAccess(context)) }
    var showPicker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installedApps = loadLaunchableApps(context)
                selectedPackages = prefs.getStringSet("game_packages", emptySet())?.toSet() ?: emptySet()
                focusBeforeLaunch = prefs.getBoolean("game_focus_before_launch", false)
                focusActive = prefs.getBoolean("game_focus_active", false)
                hasDndAccess = hasNotificationPolicyAccess(context)
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val chosenGames = installedApps.filter { it.packageName in selectedPackages }
    val filteredApps = installedApps.filter {
        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }.take(80)

    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        ScreenHeading("GAME CENTER / 02", "Игровой центр", "Игры, быстрый запуск и спокойный режим на время матча.")

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF51417B), Color(0xFF302F50), Color(0xFF1D292F))))
                .border(1.dp, Color(0xFFB69AFF).copy(alpha = .26f), RoundedCornerShape(30.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("OPTIMIZER / PLAY", color = Color(0xFFD2C3FF), fontSize = 10.sp, letterSpacing = 2.sp)
                    Text("Твоя игровая зона", color = Color.White, fontSize = 23.sp)
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .10f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(Icons.Default.Gamepad, contentDescription = "Игры", tint = Color(0xFFD2C3FF), modifier = Modifier.size(27.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.size(108.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(Color.White.copy(alpha = .13f), -90f, 360f, false, style = Stroke(width = 10.dp.toPx()))
                        drawArc(Brush.sweepGradient(listOf(Color(0xFFB69AFF), Color(0xFF70DDC3), Color(0xFFB69AFF))), -90f, if (focusActive) 315f else 235f, false, style = Stroke(width = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(if (focusActive) "ON" else "READY", color = Color.White, fontSize = 18.sp)
                        Text("FOCUS", color = Color(0xFFD2C3FF), fontSize = 8.sp, letterSpacing = 1.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (focusActive) "Фокус активен" else "Готов к запуску", color = Color.White, fontSize = 18.sp)
                    Text("Библиотека игр и инструменты Android для игровых сессий.", color = Color.White.copy(alpha = .68f), fontSize = 11.sp, lineHeight = 17.sp)
                    Text(selectedPackages.size.toString() + " В БИБЛИОТЕКЕ", color = Color(0xFFB6F0D8), fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
            androidx.compose.material3.Surface(
                onClick = {
                    val first = chosenGames.firstOrNull()
                    if (first != null) launchGame(context, first.packageName, focusBeforeLaunch) else showPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                color = Color(0xFFB69AFF)
            ) {
                Row(Modifier.padding(vertical = 15.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.Gamepad, contentDescription = null, tint = Color(0xFF211A36))
                    Spacer(Modifier.width(9.dp))
                    Text(if (chosenGames.isEmpty()) "ДОБАВИТЬ ПЕРВУЮ ИГРУ" else "БЫСТРЫЙ ЗАПУСК", color = Color(0xFF211A36), fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GameStatPill("Библиотека · " + chosenGames.size)
                GameStatPill("Установлено · " + installedApps.size)
                GameStatPill(if (hasDndAccess) "Фокус доступен" else "Нужен доступ")
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF202B35)).border(1.dp, Color(0xFFB69AFF).copy(alpha = .13f), RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ИГРОВОЙ ФОКУС", color = Color(0xFFD2C3FF), fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Text("Меньше отвлечений", color = Color.White, fontSize = 17.sp)
                }
                Text(if (focusBeforeLaunch) "ON" else "OFF", color = if (focusBeforeLaunch) Accent else Muted, fontSize = 11.sp)
            }
            Text("Перед запуском игры Android может включить режим «Не беспокоить». Разрешение и фильтр уведомлений контролируются системой.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            SettingToggle(
                "Включать перед запуском",
                "Сохраняет настройку и использует системный режим при запуске выбранной игры.",
                focusBeforeLaunch
            ) { checked ->
                if (checked && !hasNotificationPolicyAccess(context)) {
                    Toast.makeText(context, "Разреши доступ к режиму «Не беспокоить» в настройках Android.", Toast.LENGTH_LONG).show()
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                } else {
                    focusBeforeLaunch = checked
                    prefs.edit().putBoolean("game_focus_before_launch", checked).apply()
                }
            }
            if (!hasDndAccess && focusBeforeLaunch) {
                PrimaryActionButton("Выдать системное разрешение", onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } }, modifier = Modifier.fillMaxWidth())
            }
            if (focusActive) {
                Column(Modifier.fillMaxWidth().background(Color(0xFF263D39), RoundedCornerShape(17.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Игровой фокус сейчас включён", color = Accent, fontSize = 13.sp)
                    Text("После завершения игры верни предыдущий режим уведомлений.", color = Muted, fontSize = 11.sp)
                    PrimaryActionButton(
                        "Восстановить обычный режим",
                        onClick = {
                            if (setGameFocus(context, false)) {
                                focusActive = false
                                Toast.makeText(context, "Обычный режим уведомлений восстановлен.", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(context, "Не удалось восстановить режим. Проверь доступ Android.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ScreenSectionLabel("ТВОЯ КОЛЛЕКЦИЯ")
                Text("Мои игры", color = Color.White, fontSize = 20.sp)
            }
            androidx.compose.material3.Surface(
                onClick = { showPicker = !showPicker; query = "" },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF3C3159)
            ) { Text(if (showPicker) "ЗАКРЫТЬ" else "+ ДОБАВИТЬ", Modifier.padding(horizontal = 13.dp, vertical = 11.dp), color = Color(0xFFD2C3FF), fontSize = 10.sp, letterSpacing = .8.sp) }
        }
        if (chosenGames.isEmpty()) {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎮", fontSize = 28.sp)
                Text("Здесь пока тихо", color = Color.White, fontSize = 17.sp)
                Text("Добавь установленные игры — появятся быстрые кнопки запуска и отдельная библиотека.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                PrimaryActionButton("Выбрать игры", onClick = { showPicker = true; query = "" }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            chosenGames.forEachIndexed { index, app ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF202B35))
                        .border(1.dp, Color.White.copy(alpha = .055f), RoundedCornerShape(20.dp))
                        .padding(13.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF9B79E8), Color(0xFF54C5AF)))), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(app.label.take(1).uppercase(Locale.ROOT), color = Color(0xFF1D2030), fontSize = 20.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(app.label, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text(if (app.isGameCategory) "ИГРА · " + (index + 1).toString().padStart(2, '0') else "ПРИЛОЖЕНИЕ · " + (index + 1).toString().padStart(2, '0'), color = Muted, fontSize = 9.sp, letterSpacing = .8.sp)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ИГРАТЬ", color = Color(0xFFCDBEFF), fontSize = 10.sp, modifier = Modifier.clickable { launchGame(context, app.packageName, focusBeforeLaunch) }.padding(4.dp))
                        Text("УБРАТЬ", color = Coral, fontSize = 9.sp, modifier = Modifier.clickable { selectedPackages = selectedPackages - app.packageName; prefs.edit().putStringSet("game_packages", selectedPackages).apply() }.padding(4.dp))
                    }
                }
            }
        }

        if (showPicker) {
            Column(Modifier.fillMaxWidth().background(Color(0xFF1A252C), RoundedCornerShape(23.dp)).border(1.dp, Color(0xFFB69AFF).copy(alpha = .18f), RoundedCornerShape(23.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ДОБАВИТЬ В БИБЛИОТЕКУ", color = Color(0xFFD2C3FF), fontSize = 10.sp, letterSpacing = 1.4.sp)
                Text("Выбери приложение", color = Color.White, fontSize = 18.sp)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск по названию", color = Muted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFB69AFF),
                        unfocusedBorderColor = Muted.copy(alpha = .3f),
                        cursorColor = Color(0xFFB69AFF)
                    )
                )
                if (filteredApps.isEmpty()) Text("Ничего не найдено.", color = Muted, fontSize = 12.sp)
                else filteredApps.forEach { app ->
                    GameAppRow(
                        app = app,
                        isAdded = app.packageName in selectedPackages,
                        onAdd = {
                            selectedPackages = selectedPackages + app.packageName
                            prefs.edit().putStringSet("game_packages", selectedPackages).apply()
                        }
                    )
                }
                Text("Список включает приложения с доступным ярлыком запуска Android.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }

        ScreenSectionLabel("ПЕРЕД МАТЧЕМ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("Доступная RAM", memoryInfo(context), Modifier.weight(1f))
            MetricCard("Температура", thermalInfo(context), Modifier.weight(1f))
        }
        InfoCard("Честный игровой режим", "Game Center помогает запускать игры и по твоему разрешению включает системный режим уведомлений. Android сам управляет производительностью; приложение не подделывает FPS и не обещает разгон процессора.")
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

    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        ScreenHeading("DEVICE ANALYTICS / 03", "Мониторинг", "Живые показатели телефона и динамика изменений.")

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(29.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF315C64), Color(0xFF213D48), Color(0xFF1A2A33))))
                .border(1.dp, Color(0xFF8EDCE0).copy(alpha = .20f), RoundedCornerShape(29.dp))
                .padding(19.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("СИСТЕМНЫЙ ПУЛЬС", color = Color(0xFFB7E7E6), fontSize = 10.sp, letterSpacing = 1.8.sp)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(Accent))
                    Text("LIVE", color = Accent, fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
            Text("Телефон под наблюдением", color = Color.White, fontSize = 22.sp)
            Text("Собираем системные значения, пока открыт этот экран.", color = Color.White.copy(alpha = .65f), fontSize = 11.sp, lineHeight = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("RAM", color = Color(0xFFF1D3A8), fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(memory?.let { formatStorageBytes(context, it.first) } ?: "—", color = Color.White, fontSize = 17.sp)
                    Text("доступно", color = Muted, fontSize = 9.sp)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("BATTERY", color = Color(0xFFFFAFA5), fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(battery?.let { it.percent.toString() + "%" } ?: "—", color = Color.White, fontSize = 17.sp)
                    Text(battery?.let { if (it.charging) "заряжается" else "без зарядки" } ?: "нет данных", color = Muted, fontSize = 9.sp)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("THERMAL", color = Color(0xFFB5E5D1), fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(thermal, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp)
                    Text("система", color = Muted, fontSize = 9.sp)
                }
            }
            Text("Обновлений экрана: " + refreshTick + " · интервал 15 сек", color = Color.White.copy(alpha = .48f), fontSize = 9.sp)
        }

        ScreenSectionLabel("ТЕКУЩИЕ РЕСУРСЫ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF24363A))
                    .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(22.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ХРАНИЛИЩЕ", color = Accent, fontSize = 9.sp, letterSpacing = 1.1.sp)
                Text(stats?.let { formatStorageBytes(context, it.freeBytes) } ?: "—", color = Color.White, fontSize = 21.sp)
                Text("свободно из " + (stats?.let { formatStorageBytes(context, it.totalBytes) } ?: "—"), color = Muted, fontSize = 10.sp)
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxWidth((1f - animatedStorage).coerceIn(.015f, 1f)).height(7.dp).clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Accent, Color(0xFF83D6BE)))))
                }
                Text((animatedStorage * 100).toInt().toString() + "% занято", color = Muted, fontSize = 9.sp)
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF2D3039))
                    .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(22.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ОПЕРАТИВНАЯ ПАМЯТЬ", color = Peach, fontSize = 9.sp, letterSpacing = .7.sp)
                Text(memory?.let { formatStorageBytes(context, it.first) } ?: "—", color = Color.White, fontSize = 21.sp)
                Text("доступно из " + (memory?.let { formatStorageBytes(context, it.second) } ?: "—"), color = Muted, fontSize = 10.sp)
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .22f))) {
                    Box(Modifier.fillMaxWidth((1f - animatedMemory).coerceIn(.015f, 1f)).height(7.dp).clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Peach, Coral))))
                }
                Text((animatedMemory * 100).toInt().toString() + "% используется", color = Muted, fontSize = 9.sp)
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF302B31)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("АККУМУЛЯТОР", color = Coral, fontSize = 9.sp, letterSpacing = 1.2.sp)
                Text(battery?.let { it.percent.toString() + "%" } ?: "—", color = Color.White, fontSize = 18.sp)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black.copy(alpha = .22f))) {
                Box(Modifier.fillMaxWidth(((battery?.percent ?: 0).coerceIn(0, 100) / 100f).coerceAtLeast(.01f)).height(8.dp).clip(RoundedCornerShape(30.dp)).background(Brush.horizontalGradient(listOf(Coral, Peach))))
            }
            Text(battery?.let {
                (if (it.charging) "Заряжается" else "Работает от батареи") +
                    (it.temperatureC?.let { temp -> " · " + String.format(Locale.getDefault(), "%.1f", temp) + " °C" } ?: "")
            } ?: "Системная информация недоступна", color = Muted, fontSize = 11.sp)
        }

        FeatureCard(
            "Тепловое состояние",
            thermal,
            when {
                thermal.contains("Критическое") || thermal.contains("Аварийное") -> Rose
                thermal.contains("Высокое") || thermal.contains("Умеренное") -> Coral
                else -> Accent
            }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ScreenSectionLabel("ИСТОРИЯ ИЗМЕНЕНИЙ")
                Text("Динамика системы", color = Color.White, fontSize = 20.sp)
            }
            Text("● REC", color = Accent, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        TrendLineChart("Использование оперативной памяти", ramHistory, Peach)
        TrendLineChart("Заполнение хранилища", storageHistory, Accent)
        InfoCard("Как читаются графики", "История собирает точки каждые 15 секунд только пока открыт экран мониторинга. После выхода сбор приостанавливается; значения берутся из Android, а не моделируются.")
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
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        ScreenHeading("CONTROL CENTER / 04", "Настройки", "Настрой автоматический уход и поведение приложения.")

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(29.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF485F50), Color(0xFF2B413D), Color(0xFF1D2D32))))
                .border(1.dp, Color(0xFFB3D6B7).copy(alpha = .20f), RoundedCornerShape(29.dp))
                .padding(19.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("OPTIMIZER / CONTROL", color = Color(0xFFCBE8C9), fontSize = 10.sp, letterSpacing = 1.8.sp)
                Box(Modifier.clip(RoundedCornerShape(30.dp)).background(if (enabled) Accent.copy(alpha = .18f) else Color.White.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (enabled) "АВТОПИЛОТ ON" else "АВТОПИЛОТ OFF", color = if (enabled) Accent else Muted, fontSize = 9.sp, letterSpacing = .8.sp)
                }
            }
            Text("Твой телефон.\nТвои правила.", color = Color.White, fontSize = 25.sp, lineHeight = 29.sp)
            Text("Безопасная автоочистка работает по расписанию, а ты выбираешь, что именно можно удалять.", color = Color.White.copy(alpha = .68f), fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ИНТЕРВАЛ", color = Color(0xFFCBE8C9), fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(if (enabled) when (intervalMinutes) { 30 -> "30 мин"; 60 -> "1 час"; 120 -> "2 часа"; 360 -> "6 часов"; 720 -> "12 часов"; 1440 -> "1 день"; 4320 -> "3 дня"; 10080 -> "7 дней"; else -> intervalMinutes.toString() + " мин" } else "Выключен", color = Color.White, fontSize = 16.sp)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ПОСЛЕДНИЙ ЗАПУСК", color = Color(0xFFCBE8C9), fontSize = 9.sp, letterSpacing = .8.sp)
                    Text(if (lastRun > 0L) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastRun)) else "Пока нет", color = Color.White, fontSize = 12.sp, maxLines = 2)
                }
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            ScreenSectionLabel("РАЗРЕШЕНИЯ")
            Text("Связь с Android", color = Color.White, fontSize = 17.sp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAccess) {
                FeatureCard("Доступ к файлам не выдан", "Для поиска временных файлов Android требует специальное разрешение.", Coral)
                PrimaryActionButton("Открыть системные разрешения", onClick = { requestAllFilesAccess(context) }, modifier = Modifier.fillMaxWidth())
            } else {
                FeatureCard("Доступ к файлам активен", "Приложение видит только те общие файлы и каталоги, которые разрешает Android.", Accent)
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Color(0xFF202D32)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenSectionLabel("АВТОПИЛОТ")
            Text("Автоматический уход", color = Color.White, fontSize = 18.sp)
            Text("Планировщик очищает только разрешённые временные файлы и кэш самого Project Optimizer. Android может немного сдвигать время запуска.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            SettingToggle(
                "Включить автоочистку",
                "Работа в фоне по выбранному расписанию.",
                enabled
            ) { checked ->
                enabled = checked
                prefs.edit().putBoolean("auto_clean_enabled", checked).apply()
                if (checked && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context) else cancelAutoClean(context)
            }
            if (enabled) {
                ScreenSectionLabel("ЧАСТОТА ЗАПУСКА")
                Text("Повторять: " + when (intervalMinutes) {
                    30 -> "каждые 30 минут"; 60 -> "каждый час"; 120 -> "каждые 2 часа"; 360 -> "каждые 6 часов"
                    720 -> "каждые 12 часов"; 1440 -> "раз в сутки"; 4320 -> "раз в 3 дня"; 10080 -> "раз в 7 дней"
                    else -> "каждые " + intervalMinutes + " минут"
                }, color = Color.White, fontSize = 16.sp)
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
                                color = if (selected) Accent.copy(alpha = .18f) else Color(0xFF2B3A3F),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent else Color.White.copy(alpha = .07f))
                            ) {
                                Text(label, Modifier.padding(vertical = 13.dp, horizontal = 3.dp), color = if (selected) Accent else Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                ScreenSectionLabel("ЧТО МОЖНО ОЧИЩАТЬ")
                SettingToggle("Кэш Project Optimizer", "Временные файлы самого оптимизатора.", cleanAppCache) { checked ->
                    cleanAppCache = checked
                    prefs.edit().putBoolean("auto_clean_app_cache", checked).apply()
                    if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context) else if (enabled) cancelAutoClean(context)
                }
                SettingToggle("Временные и мусорные файлы", "Старые .tmp, .temp, .part и известные temp/tmp/cache папки старше 14 дней.", cleanTempFiles) { checked ->
                    cleanTempFiles = checked
                    prefs.edit().putBoolean("auto_clean_temp_files", checked).apply()
                    if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context) else if (enabled) cancelAutoClean(context)
                }
                if (!cleanAppCache && !cleanTempFiles) FeatureCard("Очистка не настроена", "Включи хотя бы один тип файлов, чтобы расписание выполняло полезную работу.", Coral)
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Color(0xFF242D35)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            ScreenSectionLabel("ПЕРСОНАЛИЗАЦИЯ")
            Text("Как ведёт себя приложение", color = Color.White, fontSize = 17.sp)
            SettingToggle(
                "Подсказки на главной",
                "Показывать карточки с короткими советами по уходу за телефоном.",
                showTips
            ) { checked ->
                showTips = checked
                prefs.edit().putBoolean("show_optimizer_tips", checked).apply()
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Color(0xFF1E2B30)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenSectionLabel("ЖУРНАЛ ОБСЛУЖИВАНИЯ")
            Text("История очистки", color = Color.White, fontSize = 18.sp)
            if (lastRun > 0L) {
                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastRun)), color = Accent, fontSize = 13.sp)
                Text("Оценочно освобождено: " + formatStorageBytes(context, lastFreed), color = Color.White, fontSize = 13.sp)
            } else {
                Text("Автоматическая очистка ещё не запускалась.", color = Muted, fontSize = 12.sp)
            }
        }
        InfoCard("Никаких скрытых обещаний", "Приложение не управляет закрытыми системными процессами и не удаляет личные данные автоматически. Все правила очистки можно изменить здесь.")
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
