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
import androidx.documentfile.provider.DocumentFile
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
        val freeNow = storageStats(context).second
        val gained = if (cacheFreeBefore >= 0) (freeNow - cacheFreeBefore).coerceAtLeast(0) else 0L
        status = if (gained > 0) "После системного запроса свободного места стало больше на ${formatBytes(gained)}." else "Android вернул управление, но прирост свободного места не обнаружен. Система могла не выполнить очистку."
        cacheFreeBefore = -1L
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
        val stats = storageStats(context)
        InfoCard("Память телефона", "Всего: ${formatBytes(stats.first)}\nЗанято: ${formatBytes((stats.first - stats.second).coerceAtLeast(0))}\nСвободно: ${formatBytes(stats.second)}")
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
                cacheFreeBefore = storageStats(context).second
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

private fun storageStats(context: Context): Pair<Long, Long> = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val storageManager = context.getSystemService(StorageManager::class.java)
        val statsManager = context.getSystemService(StorageStatsManager::class.java)
        val uuid = storageManager.getUuidForPath(Environment.getExternalStorageDirectory())
        val total = statsManager.getTotalBytes(uuid)
        val free = statsManager.getFreeBytes(uuid).coerceIn(0L, total)
        total to free
    } else {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.totalBytes
        total to stat.availableBytes.coerceIn(0L, total)
    }
}.getOrElse {
    runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.totalBytes
        total to stat.availableBytes.coerceIn(0L, total)
    }.getOrElse { 0L to 0L }
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
