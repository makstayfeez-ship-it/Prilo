package com.projectoptimizer

import android.app.ActivityManager
import android.app.TimePickerDialog
import android.app.usage.StorageStatsManager
import android.content.Intent
import android.provider.Settings
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF182226)) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected == index, { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 10.sp) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF182226),
                            selectedTextColor = Accent,
                            indicatorColor = Accent,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF172125), Background, Color(0xFF101517)))).padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(10.dp))
                Text("PROJECT", color = Peach, fontSize = 11.sp, letterSpacing = 3.sp)
                Text("OPTIMIZER", color = Color(0xFFF4F5F1), fontSize = 29.sp, letterSpacing = 1.5.sp)
                Text("Контроль состояния устройства", color = Muted, fontSize = 13.sp)
            }
            item {
                when (selected) {
                    0 -> Dashboard(context)
                    1 -> Cleaning(context)
                    2 -> Games()
                    3 -> Monitoring(context)
                    else -> Settings(context)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun Dashboard(context: Context) {
    val storage = rememberStorageStats(context)
    val usedFraction = (storage?.let { it.usedBytes.toFloat() / it.totalBytes.coerceAtLeast(1L).toFloat() } ?: 0f).coerceIn(0f, 1f)
    val animatedUsage by animateFloatAsState(
        targetValue = usedFraction,
        animationSpec = tween(durationMillis = 950),
        label = "storage_usage"
    )
    val freeLabel = storage?.let { formatStorageBytes(context, it.freeBytes) } ?: "Проверка…"
    val totalLabel = storage?.let { formatStorageBytes(context, it.totalBytes) } ?: "Получаю данные…"
    val usedLabel = storage?.let { formatStorageBytes(context, it.usedBytes) } ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ТВОЁ УСТРОЙСТВО", color = Peach, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("Всё под контролем", color = Color(0xFFF4F5F1), fontSize = 21.sp)
            }
            Text("● ONLINE", color = Accent, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        StorageHeroCard(
            free = freeLabel,
            total = totalLabel,
            used = usedLabel,
            progress = animatedUsage,
            source = storage?.source ?: "Считываем хранилище Android"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Оперативная память", memoryInfo(context), Modifier.weight(1f))
            MetricCard("Нагрев устройства", thermalInfo(context), Modifier.weight(1f))
        }
        val storageAdvice = when {
            storage == null -> "Пока не удалось получить данные хранилища. Попробуй обновить экран позже."
            usedFraction >= 0.9f -> "Свободного места мало. Рекомендуем проверить крупные и временные файлы."
            usedFraction >= 0.75f -> "Хранилище постепенно заполняется. Можно проверить, что занимает больше всего места."
            else -> "С хранилищем всё в порядке. Продолжай пользоваться телефоном — мы поможем следить за состоянием."
        }
        InfoCard("Подсказка Optimizer", storageAdvice)
        InfoCard(
            "Доступ к файлам",
            if (hasStorageAccess(context)) "Доступ к общему хранилищу предоставлен."
            else "Открой «Очистка» и выдай разрешение «Доступ ко всем файлам»."
        )
    }
}

@Composable
private fun StorageHeroCard(
    free: String,
    total: String,
    used: String,
    progress: Float,
    source: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF526E68), Color(0xFF354B4D), Color(0xFF26353A))
                ),
                RoundedCornerShape(28.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ХРАНИЛИЩЕ", color = Peach, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("●", color = Accent, fontSize = 15.sp)
        }
        Text(free, color = Color(0xFFF8F5EF), fontSize = 35.sp)
        Text("свободно из $total", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.22f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0.015f, 1f))
                    .height(11.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentGradient)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ЗАНЯТО", color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, letterSpacing = 1.sp)
                Text(used, color = Color.White, fontSize = 14.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ИСТОЧНИК", color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, letterSpacing = 1.sp)
                Text(source, color = Color.White, fontSize = 10.sp)
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val stats = rememberStorageStats(context)
        InfoCard(
            "Память телефона",
            stats?.let {
                "Всего: ${formatStorageBytes(context, it.totalBytes)}\nЗанято: ${formatStorageBytes(context, it.usedBytes)}\nСвободно: ${formatStorageBytes(context, it.freeBytes)}\nИсточник: ${it.source}"
            } ?: "Получаю системные данные хранилища…"
        )
        if (!hasAccess) {
            Button(onClick = { requestAllFilesAccess(context) }, modifier = Modifier.fillMaxWidth()) { Text("Разрешить доступ ко всем файлам") }
            InfoCard("Доступ Android", "Включи специальное разрешение в системных настройках и вернись в приложение.")
        } else InfoCard("Доступ предоставлен", "Проверяется доступное общее хранилище. Закрытые системные каталоги Android могут быть недоступны.")
        Button(enabled = !scanning && !deleting, onClick = { folderPicker.launch(extraFolder?.let(Uri::parse)) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (extraFolder == null) "Добавить папку для полного сканирования" else "Выбрать другую дополнительную папку")
        }
        if (extraFolder != null) {
            InfoCard("Дополнительная папка", "Папка подключена. Она будет включена в следующий поиск.")
            TextButton(onClick = { extraFolder = null; prefs.edit().remove("extra_folder_uri").apply(); status = "Дополнительная папка отключена." }) { Text("Отключить дополнительную папку") }
        }
        Button(enabled = hasAccess && !scanning && !deleting, onClick = {
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
                status = "Поиск завершён. Найдено файлов: ${result.size}. Проверь список и отметь нужное."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Идёт поиск…" else "Найти файлы") }
        if (scanning || deleting) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (scanning) "Поиск файлов · найдено: $foundCount" else "Удаление выбранных файлов…", color = Accent) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Button(enabled = !scanning && !deleting, onClick = {
                cacheFreeBefore = stats?.freeBytes ?: -1L
                val ownCacheBytes = clearOwnAppCache(context)
                status = "Кэш Project Optimizer очищен: ${formatBytes(ownCacheBytes)}. Запрашиваю системную очистку других приложений…"
                runCatching { cacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE)) }.onFailure {
                    status = "Кэш Project Optimizer очищен: ${formatBytes(ownCacheBytes)}. Android не запустил общую очистку."
                    cacheFreeBefore = -1L
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Очистить кэш") }
            InfoCard("Кэш приложений", "Закрытый кэш других приложений может очищать только Android. После возврата приложение сравнит свободное место и покажет фактический прирост.")
        }
        InfoCard("Состояние", status)
        Text(lastDeleted, color = Accent, fontSize = 13.sp)
        if (scanned) {
            Button(enabled = eligible.isNotEmpty() && !deleting, onClick = {
                selected = eligible.map { it.uri.toString() }.toSet()
                showDialog = true
            }, modifier = Modifier.fillMaxWidth()) { Text("Полная очистка · ${eligible.size} файлов") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showLocked = false }, modifier = Modifier.weight(1f)) { Text("Найденные (${files.size})") }
                Button(onClick = { showLocked = true }, modifier = Modifier.weight(1f)) { Text("Под замком (${locked.size})") }
            }
            if (!showLocked) {
                Text("Выбрано: ${selectedFiles.size} · ${formatBytes(selectedBytes)}", color = Accent)
                Button(enabled = selectedFiles.isNotEmpty() && !deleting, onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Очистить выбранное") }
            } else Text("Эти файлы исключены из очистки. Сними замок, чтобы вернуть их в общий список.", color = Muted, fontSize = 12.sp)
            visible.take(limit).forEach { file ->
                val key = file.uri.toString(); val isLocked = key in locked; val isProtected = file.kind in protected
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, color = Color.White, fontSize = 13.sp)
                                Text("${formatBytes(file.size)} · ${file.kind.title}", color = Muted, fontSize = 11.sp)
                                Text(file.location, color = Muted, fontSize = 10.sp)
                            }
                            if (!showLocked && !isProtected && !isLocked) Checkbox(checked = key in selected, onCheckedChange = { yes -> selected = if (yes) selected + key else selected - key })
                        }
                        TextButton(onClick = { locked = if (isLocked) locked - key else locked + key; prefs.edit().putStringSet("locked_uris", locked).apply(); if (!isLocked) selected = selected - key }) { Text(if (isLocked) "Снять замок" else "Поставить под замок") }
                    }
                }
            }
            if (visible.size > limit) Button(onClick = { limit += 100 }, modifier = Modifier.fillMaxWidth()) { Text("Показать ещё (${visible.size - limit})") }
            if (visible.isEmpty()) InfoCard("Пусто", if (showLocked) "Нет файлов под замком." else "Файлы не найдены.")
        }
        Text("Правила поиска", color = Color.White, fontSize = 16.sp)
        SettingToggle("GIF-файлы", "Автоматически отмечать GIF", allowGif) { allowGif = it; prefs.edit().putBoolean("allow_gif", it).apply() }
        SettingToggle("Архивы и установочные файлы", "ZIP, RAR, 7Z, TAR, GZ, APK и другие", allowArchives) { allowArchives = it; prefs.edit().putBoolean("allow_archives", it).apply() }
        SettingToggle("Повторяющиеся файлы", "Точные копии по SHA-256", allowDuplicates) { allowDuplicates = it; prefs.edit().putBoolean("allow_duplicates", it).apply() }
        SettingToggle("Старые файлы", "Автоматически отмечать файлы старше срока", allowOld) { allowOld = it; prefs.edit().putBoolean("allow_old", it).apply() }
        Text("Считать старыми через: $oldDays дней", color = Muted, fontSize = 13.sp)
        androidx.compose.material3.Slider(value = oldDays.toFloat(), onValueChange = { oldDays = it.toInt().coerceIn(7, 730) }, onValueChangeFinished = { prefs.edit().putInt("old_days", oldDays).apply() }, valueRange = 7f..730f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("7 дней", color = Muted, fontSize = 11.sp); Text("730 дней", color = Muted, fontSize = 11.sp) }
        InfoCard("Ограничения", "Сканируются доступные общие файлы. Android закрывает приватные данные приложений и часть Android/data и Android/obb. Фото, видео и аудио не отмечаются автоматически.")
    }
}
@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White)
                Text(description, color = Muted, fontSize = 11.sp)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked)
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

@Composable
private fun Games() {
    InfoCard("Игровой режим", "Здесь будут игровые профили, список игр и быстрые настройки перед запуском.")
    InfoCard("Без ложных обещаний", "Обычное приложение не может разгонять процессор или видеочип. Доступность системных игровых функций зависит от телефона и версии Android.")
}
@Composable
private fun Monitoring(context: Context) {
    MetricCard("Свободная оперативная память", memoryInfo(context), Modifier.fillMaxWidth())
    val stats = rememberStorageStats(context)
    MetricCard("Свободное хранилище", stats?.let { formatStorageBytes(context, it.freeBytes) } ?: "Проверка…", Modifier.fillMaxWidth())
    if (stats != null) {
        InfoCard("Сведения о накопителе", "Всего: ${formatStorageBytes(context, stats.totalBytes)}\nЗанято: ${formatStorageBytes(context, stats.usedBytes)}\nИсточник: ${stats.source}")
    }
    MetricCard("Тепловое состояние", thermalInfo(context), Modifier.fillMaxWidth())
}
@Composable
private fun Settings(context: Context) {
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("auto_clean_enabled", false)) }
    var cleanAppCache by remember { mutableStateOf(prefs.getBoolean("auto_clean_app_cache", true)) }
    var cleanTempFiles by remember { mutableStateOf(prefs.getBoolean("auto_clean_temp_files", true)) }
    var intervalDays by remember { mutableIntStateOf(prefs.getInt("auto_clean_interval_days", 1).coerceIn(1, 7)) }
    var cleanHour by remember { mutableIntStateOf(prefs.getInt("auto_clean_hour", 3).coerceIn(0, 23)) }
    var cleanMinute by remember { mutableIntStateOf(prefs.getInt("auto_clean_minute", 0).coerceIn(0, 59)) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }

    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = prefs.getBoolean("auto_clean_enabled", false)
                cleanAppCache = prefs.getBoolean("auto_clean_app_cache", true)
                cleanTempFiles = prefs.getBoolean("auto_clean_temp_files", true)
                intervalDays = prefs.getInt("auto_clean_interval_days", 1).coerceIn(1, 7)
                cleanHour = prefs.getInt("auto_clean_hour", 3).coerceIn(0, 23)
                cleanMinute = prefs.getInt("auto_clean_minute", 0).coerceIn(0, 59)
                hasAccess = hasStorageAccess(context)
                refreshToken++
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val lastRun = remember(refreshToken) { prefs.getLong("auto_clean_last_run", 0L) }
    val lastFreed = remember(refreshToken) { prefs.getLong("auto_clean_last_freed_estimate", 0L) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard("Автоматическая очистка", "Выдай разрешение Android один раз — и оптимизатор сможет по расписанию удалять доступные временные файлы общего хранилища и свой кэш.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAccess) {
            Button(onClick = { requestAllFilesAccess(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Выдать разрешение Android · все файлы")
            }
            InfoCard(
                "Нужно системное разрешение",
                "Android откроет собственный экран специального доступа. Включи «Разрешить доступ к управлению всеми файлами» для Project Optimizer и вернись сюда — статус обновится автоматически."
            )
        } else {
            InfoCard("Доступ к файлам", "Разрешение предоставлено. Оптимизатор может очищать доступные временные файлы общего хранилища.")
        }

        SettingToggle(
            "Включить автоочистку",
            "Очистка запускается в фоне, когда Android разрешит фоновую работу.",
            enabled
        ) { checked ->
            enabled = checked
            prefs.edit().putBoolean("auto_clean_enabled", checked).apply()
            if (checked && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
            else cancelAutoClean(context)
        }

        if (enabled) {
            Text("Время автоочистки", color = Color.White, fontSize = 14.sp)
            Button(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    cleanHour = hour
                    cleanMinute = minute
                    prefs.edit().putInt("auto_clean_hour", hour).putInt("auto_clean_minute", minute).apply()
                    if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
                }, cleanHour, cleanMinute, true).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать время · %02d:%02d".format(cleanHour, cleanMinute))
            }
        }
        Text("Как часто запускать", color = Color.White, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 7).forEach { days ->
                Button(
                    onClick = {
                        intervalDays = days
                        prefs.edit().putInt("auto_clean_interval_days", days).apply()
                        if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(when (days) { 1 -> "Каждый день"; 3 -> "3 дня"; else -> "7 дней" }, fontSize = 11.sp)
                }
            }
        }

        Text("Что очищать", color = Color.White, fontSize = 14.sp)
        SettingToggle("Кэш Project Optimizer", "Удаляет временные файлы самого оптимизатора.", cleanAppCache) { checked ->
            cleanAppCache = checked
            prefs.edit().putBoolean("auto_clean_app_cache", checked).apply()
            if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
            else if (enabled) cancelAutoClean(context)
        }
        SettingToggle(
            "Временные и мусорные файлы",
            "Удаляет старые .tmp, .temp, .part и файлы из папок temp/tmp/cache старше 14 дней.",
            cleanTempFiles
        ) { checked ->
            cleanTempFiles = checked
            prefs.edit().putBoolean("auto_clean_temp_files", checked).apply()
            if (enabled && (cleanAppCache || cleanTempFiles)) scheduleAutoClean(context)
            else if (enabled) cancelAutoClean(context)
        }

        InfoCard(
            "Последняя очистка",
            if (lastRun > 0L) {
                "Выполнена: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastRun))}\n" +
                    "Оценочно удалено: ${formatStorageBytes(context, lastFreed)}"
            } else "Автоматическая очистка ещё не запускалась."
        )
        InfoCard(
            "Что Android всё равно закрывает",
            "Даже с этим разрешением Android 11+ не открывает сторонним приложениям приватные каталоги других приложений и часть Android/data и Android/obb. Их закрытый кэш нельзя безопасно обойти обычным приложением без root или системных привилегий. Фото, видео, музыка, документы, мессенджеры и резервные копии защищены от автоудаления."
        )
        InfoCard("Конфиденциальность", "Настройки и расписание хранятся на телефоне. Список личных файлов никуда не отправляется.")
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
    val intervalDays = prefs.getInt("auto_clean_interval_days", 1).coerceIn(1, 7)
    val hour = prefs.getInt("auto_clean_hour", 3).coerceIn(0, 23)
    val minute = prefs.getInt("auto_clean_minute", 0).coerceIn(0, 59)
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        while (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, intervalDays)
    }
    val delay = (next.timeInMillis - now.timeInMillis).coerceAtLeast(1L)
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
