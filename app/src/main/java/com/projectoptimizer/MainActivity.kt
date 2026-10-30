package com.projectoptimizer

import android.app.ActivityManager
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Background = Color(0xFF0B0F0E)
private val Panel = Color(0xFF151D1A)
private val Accent = Color(0xFF35D49A)
private val Muted = Color(0xFF9AA9A2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Accent, onPrimary = Color(0xFF062016),
                    background = Background, surface = Panel,
                    onBackground = Color.White, onSurface = Color.White
                )
            ) { OptimizerApp(this) }
        }
    }
}

private data class TabItem(val title: String, val icon: ImageVector)
private enum class FileKind(val title: String) {
    PHOTO("Фото"), VIDEO("Видео"), AUDIO("Аудио"), GIF("GIF"), ARCHIVE("Архивы"), OTHER("Другие файлы")
}
private data class ScannedFile(
    val uri: Uri, val name: String, val size: Long, val modified: Long,
    val kind: FileKind, val location: String, val sha256: String? = null
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
            NavigationBar(containerColor = Panel) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(selected == index, { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 9.sp) })
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(10.dp))
                Text("PROJECT", color = Muted, fontSize = 12.sp)
                Text("OPTIMIZER", color = Accent, fontSize = 27.sp)
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
    val storage = storageStats(context)
    InfoCard("Хранилище телефона", "Занято: ${formatBytes(storage.first - storage.second)} из ${formatBytes(storage.first)}")
    MetricCard("Свободно", formatBytes(storage.second), Modifier.fillMaxWidth())
    MetricCard("Свободная RAM", memoryInfo(context), Modifier.fillMaxWidth())
    MetricCard("Тепловое состояние", thermalInfo(context), Modifier.fillMaxWidth())
    InfoCard("Доступ к файлам", if (hasStorageAccess(context)) "Доступ к общему хранилищу предоставлен." else "Открой «Очистка» и выдай разрешение «Доступ ко всем файлам».")
}

@Composable
private fun Cleaning(context: Context) {
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var hasAllFilesAccess by remember { mutableStateOf(hasStorageAccess(context)) }
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAllFilesAccess = hasStorageAccess(context)
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    var files by remember { mutableStateOf<List<ScannedFile>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Выбери папку, которую нужно проверить.") }
    var allowGif by remember { mutableStateOf(prefs.getBoolean("allow_gif", true)) }
    var allowDuplicates by remember { mutableStateOf(prefs.getBoolean("allow_duplicates", true)) }
    var allowOld by remember { mutableStateOf(prefs.getBoolean("allow_old", false)) }
    var allowArchives by remember { mutableStateOf(prefs.getBoolean("allow_archives", false)) }
    var lockedUris by remember { mutableStateOf(prefs.getStringSet("locked_uris", emptySet())?.toSet() ?: emptySet()) }
    var oldDays by remember { mutableIntStateOf(prefs.getInt("old_days", 180).coerceIn(7, 730)) }
    val scope = rememberCoroutineScope()
    val cacheClearLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        status = if (result.resultCode == android.app.Activity.RESULT_OK)
            "Системная очистка кэша завершена."
        else "Системная очистка кэша отменена или не выполнена."
    }
    val protectedKinds = setOf(FileKind.PHOTO, FileKind.VIDEO, FileKind.AUDIO)
    val duplicateUris = remember(files) {
        files.filter { it.size > 0 && it.sha256 != null }.groupBy { it.sha256 }.values
            .filter { it.size > 1 }.flatMap { it.drop(1) }.map { it.uri }.toSet()
    }
    val oldCutoff = System.currentTimeMillis() / 1000 - oldDays.toLong() * 24 * 60 * 60
    val candidates = files.filter { file ->
        file.kind !in protectedKinds && file.uri.toString() !in lockedUris && (
            (allowGif && file.kind == FileKind.GIF) ||
            (allowArchives && file.kind == FileKind.ARCHIVE) ||
            (allowDuplicates && file.uri in duplicateUris) ||
            (allowOld && file.modified > 0 && file.modified < oldCutoff)
        )
    }
    val reclaimable = candidates.sumOf { it.size }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить выбранные файлы?") },
            text = { Text("Будет удалено файлов: ${candidates.size}. Освободится примерно ${formatBytes(reclaimable)}. Фото, видео и аудио защищены. Отменить удаление после подтверждения может быть невозможно.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            candidates.count { runCatching {
                                it.uri.path?.let { path -> java.io.File(path).delete() } == true
                            }.getOrDefault(false) }
                        }
                        status = "Удалено файлов: $deleted. Запусти проверку ещё раз для обновления."
                        scanned = false
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val stats = storageStats(context)
        InfoCard("Память телефона", "Всего: ${formatBytes(stats.first)}\nСвободно: ${formatBytes(stats.second)}\nЗанято: ${formatBytes((stats.first - stats.second).coerceAtLeast(0))}")
        if (!hasAllFilesAccess) {
            Button(onClick = { requestAllFilesAccess(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Разрешить доступ ко всем файлам")
            }
            InfoCard("Разрешение Android", "Откроются системные настройки специального доступа. Включи «Разрешить доступ для управления всеми файлами», затем вернись сюда.")
        } else {
            InfoCard("Доступ предоставлен", "Сканируется общее пользовательское хранилище без ручного выбора папки.")
        }
        Button(
            enabled = hasAllFilesAccess && !scanning,
            onClick = {
                scanning = true
                status = "Проверяю доступные файлы общего хранилища…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) { scanAllFiles() }
                    files = result
                    scanned = true
                    scanning = false
                    status = "Проверка завершена. Найдено файлов: ${result.size}. Некоторые защищённые каталоги недоступны Android."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (scanning) "Идёт проверка…" else "Проверить всё хранилище") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Button(
                enabled = hasAllFilesAccess,
                onClick = { cacheClearLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Очистить кэш приложений через Android") }
            InfoCard("Очистка кэша", "Android покажет системное подтверждение. Кэш очищается средствами системы, закрытые данные приложений не затрагиваются.")
        } else {
            Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_STORAGE)) } },
                modifier = Modifier.fillMaxWidth()) { Text("Открыть управление хранилищем") }
        }
        InfoCard("Результат анализа", status)
        if (scanned) {
            MetricCard("Можно освободить по текущим правилам", formatBytes(reclaimable), Modifier.fillMaxWidth())
            Text("Найдено кандидатов: ${candidates.size}", color = Color.White)
            Button(
                enabled = candidates.isNotEmpty(),
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Удалить безопасно отобранное") }
            Text("Найденные файлы — можно поставить под замок", color = Color.White, fontSize = 15.sp)
            candidates.take(40).forEach { file ->
                SettingToggle(
                    file.name,
                    formatBytes(file.size) + " · " + file.location,
                    file.uri.toString() in lockedUris
                ) { locked ->
                    lockedUris = if (locked) lockedUris + file.uri.toString()
                        else lockedUris - file.uri.toString()
                    prefs.edit().putStringSet("locked_uris", lockedUris).apply()
                }
            }
            if (candidates.size > 40) {
                Text("Показаны первые 40 из " + candidates.size + ". Остальные также учитываются в расчёте.", color = Muted, fontSize = 12.sp)
            }
        }
        InfoCard("Защита по умолчанию", "Фото, видео и аудио всегда исключены из удаления. Даже если они попали в выбранную папку, очистка их не тронет.")
        Text("Что разрешено искать и удалять", color = Color.White, fontSize = 16.sp)
        SettingToggle("GIF-файлы", "Разрешить включать GIF в очистку", allowGif) {
            allowGif = it; prefs.edit().putBoolean("allow_gif", it).apply()
        }
        SettingToggle("Архивы и установочные файлы", "ZIP, RAR, 7Z, TAR, GZ и установочные пакеты", allowArchives) {
            allowArchives = it; prefs.edit().putBoolean("allow_archives", it).apply()
        }
        SettingToggle("Повторяющиеся файлы", "Одинаковый размер — предварительный признак; перед удалением нужно проверить копии", allowDuplicates) {
            allowDuplicates = it; prefs.edit().putBoolean("allow_duplicates", it).apply()
        }
        SettingToggle("Старые файлы", "Файлы, к которым давно не обращались", allowOld) {
            allowOld = it; prefs.edit().putBoolean("allow_old", it).apply()
        }
        Text("Считать старыми через: $oldDays дней", color = Muted, fontSize = 13.sp)
        androidx.compose.material3.Slider(
            value = oldDays.toFloat(),
            onValueChange = { oldDays = it.toInt().coerceIn(7, 730) },
            onValueChangeFinished = { prefs.edit().putInt("old_days", oldDays).apply() },
            valueRange = 7f..730f
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("7 дней", color = Muted, fontSize = 11.sp)
            Text("730 дней", color = Muted, fontSize = 11.sp)
        }
        InfoCard("Ограничения Android", "Даже с этим разрешением Android закрывает часть системных каталогов, включая приватные данные приложений и некоторые папки Android/data и Android/obb.")
        InfoCard("Проверка дубликатов", "Дубликаты сравниваются по SHA-256 содержимого, а не только по размеру. Для защиты оставляется возможность вручную проверить список перед удалением.")
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

private fun scanAllFiles(): List<ScannedFile> {
    val root = Environment.getExternalStorageDirectory()
    val result = mutableListOf<ScannedFile>()
    val maxFiles = 20000
    val blocked = setOf("Android/data", "Android/obb")
    fun walk(dir: java.io.File, depth: Int) {
        if (depth > 20 || result.size >= maxFiles) return
        val relative = dir.relativeToOrNull(root)?.invariantSeparatorsPath.orEmpty()
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
                    lower.endsWith(".gif") -> FileKind.GIF
                    lower.matches(Regex(".*\\\\.(zip|rar|7z|tar|gz|bz2|xz|apk|xapk|apks)$")) -> FileKind.ARCHIVE
                    mime.startsWith("image/") || lower.matches(Regex(".*\\.(jpg|jpeg|png|webp|heic|bmp)$")) -> FileKind.PHOTO
                    mime.startsWith("video/") || lower.matches(Regex(".*\\.(mp4|mkv|mov|avi|webm|3gp)$")) -> FileKind.VIDEO
                    mime.startsWith("audio/") || lower.matches(Regex(".*\\.(mp3|m4a|aac|wav|flac|ogg|opus)$")) -> FileKind.AUDIO
                    else -> FileKind.OTHER
                }
                result.add(ScannedFile(Uri.fromFile(file), name, file.length(), file.lastModified() / 1000, kind, file.parent ?: root.absolutePath))
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
    val stats = storageStats(context)
    MetricCard("Свободное хранилище", formatBytes(stats.second), Modifier.fillMaxWidth())
    MetricCard("Тепловое состояние", thermalInfo(context), Modifier.fillMaxWidth())
}
@Composable
private fun Settings(context: Context) {
    InfoCard("Настройки", "Настройки безопасной очистки сохраняются на устройстве.")
    InfoCard("Автоматическая очистка", "Расписание добавим следующим этапом. Сначала проверим, что анализ папок и защита файлов работают корректно; автоматическое удаление без проверки включать опасно.")
    InfoCard("Конфиденциальность", "Данные анализа остаются на телефоне и не отправляются на сервер.")
}
@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Accent, fontSize = 20.sp)
        }
    }
}
@Composable
private fun InfoCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
private fun storageStats(context: Context): Pair<Long, Long> = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val uuid = storageManager.getUuidForPath(Environment.getExternalStorageDirectory())
        statsManager.getTotalBytes(uuid) to statsManager.getFreeBytes(uuid)
    } else {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        stat.totalBytes to stat.availableBytes
    }
}.getOrElse {
    runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        stat.totalBytes to stat.availableBytes
    }.getOrDefault(0L to 0L)
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
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}
