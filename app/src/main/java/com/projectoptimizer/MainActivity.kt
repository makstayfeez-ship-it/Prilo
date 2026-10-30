package com.projectoptimizer

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
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
import androidx.documentfile.provider.DocumentFile
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
    PHOTO("Фото"), VIDEO("Видео"), AUDIO("Аудио"), GIF("GIF"), OTHER("Другие файлы")
}
private data class ScannedFile(
    val uri: Uri, val name: String, val size: Long, val modified: Long,
    val kind: FileKind, val location: String
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
    val storage = storageStats()
    InfoCard("Хранилище телефона", "Занято: ${formatBytes(storage.first - storage.second)} из ${formatBytes(storage.first)}")
    MetricCard("Свободно", formatBytes(storage.second), Modifier.fillMaxWidth())
    MetricCard("Свободная RAM", memoryInfo(context), Modifier.fillMaxWidth())
    MetricCard("Тепловое состояние", thermalInfo(context), Modifier.fillMaxWidth())
    InfoCard("Следующий шаг", "Открой «Очистка» и выбери папку, которую приложение сможет анализировать.")
}

@Composable
private fun Cleaning(context: Context) {
    val prefs = remember { context.getSharedPreferences("optimizer_storage", Context.MODE_PRIVATE) }
    var treeUri by remember { mutableStateOf(prefs.getString("tree_uri", null)?.let(Uri::parse)) }
    var files by remember { mutableStateOf<List<ScannedFile>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Выбери папку, которую нужно проверить.") }
    var allowGif by remember { mutableStateOf(prefs.getBoolean("allow_gif", true)) }
    var allowDuplicates by remember { mutableStateOf(prefs.getBoolean("allow_duplicates", true)) }
    var allowOld by remember { mutableStateOf(prefs.getBoolean("allow_old", false)) }
    var oldDays by remember { mutableIntStateOf(prefs.getInt("old_days", 180)) }
    val scope = rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            treeUri = uri
            prefs.edit().putString("tree_uri", uri.toString()).apply()
            files = emptyList()
            scanned = false
            status = "Папка подключена. Нажми «Проверить файлы»."
        }
    }

    val protectedKinds = setOf(FileKind.PHOTO, FileKind.VIDEO, FileKind.AUDIO)
    val duplicateUris = remember(files) {
        files.filter { it.size > 0 }.groupBy { it.size }.values
            .filter { it.size > 1 }.flatten().map { it.uri }.toSet()
    }
    val oldCutoff = System.currentTimeMillis() / 1000 - oldDays.toLong() * 24 * 60 * 60
    val candidates = files.filter { file ->
        file.kind !in protectedKinds && (
            (allowGif && file.kind == FileKind.GIF) ||
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
                                DocumentFile.fromSingleUri(context, it.uri)?.delete() == true
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
        val stats = storageStats()
        InfoCard("Внутренняя память", "Всего: ${formatBytes(stats.first)}\nСвободно: ${formatBytes(stats.second)}\nЗанято: ${formatBytes(stats.first - stats.second)}")
        Button(onClick = { folderPicker.launch(treeUri) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (treeUri == null) "Выбрать папку для анализа" else "Изменить выбранную папку")
        }
        Text(treeUri?.let { "Подключено: ${DocumentFile.fromTreeUri(context, it)?.name ?: "папка"}" }
            ?: "Доступ ещё не предоставлен", color = Muted, fontSize = 12.sp)
        Button(
            enabled = treeUri != null && !scanning,
            onClick = {
                val uri = treeUri ?: return@Button
                scanning = true
                status = "Проверяю файлы…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) { scanTree(context, uri) }
                    files = result
                    scanned = true
                    scanning = false
                    status = "Проверка завершена. Найдено файлов: ${result.size}."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (scanning) "Идёт проверка…" else "Проверить файлы") }
        InfoCard("Результат анализа", status)
        if (scanned) {
            MetricCard("Можно освободить по текущим правилам", formatBytes(reclaimable), Modifier.fillMaxWidth())
            Text("Найдено кандидатов: ${candidates.size}", color = Color.White)
            Button(
                enabled = candidates.isNotEmpty(),
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Удалить безопасно отобранное") }
        }
        InfoCard("Защита по умолчанию", "Фото, видео и аудио всегда исключены из удаления. Даже если они попали в выбранную папку, очистка их не тронет.")
        Text("Что разрешено искать и удалять", color = Color.White, fontSize = 16.sp)
        SettingToggle("GIF-файлы", "Разрешить включать GIF в очистку", allowGif) {
            allowGif = it; prefs.edit().putBoolean("allow_gif", it).apply()
        }
        SettingToggle("Повторяющиеся файлы", "Одинаковый размер — предварительный признак; перед удалением нужно проверить копии", allowDuplicates) {
            allowDuplicates = it; prefs.edit().putBoolean("allow_duplicates", it).apply()
        }
        SettingToggle("Старые файлы", "Файлы, к которым давно не обращались", allowOld) {
            allowOld = it; prefs.edit().putBoolean("allow_old", it).apply()
        }
        Text("Считать старыми через: $oldDays дней", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 90, 180, 365).forEach { days ->
                TextButton(onClick = {
                    oldDays = days; prefs.edit().putInt("old_days", days).apply()
                }) { Text("$days") }
            }
        }
        InfoCard("Важные ограничения", "Android выдаёт доступ только к выбранной папке и её содержимому. Чтобы проверить другую папку, подключи её отдельно. Приватные данные и кэш других приложений недоступны обычному приложению.")
        InfoCard("Важно о дубликатах", "Сейчас одинаковый размер используется только для предварительного поиска. Это ещё не доказательство, что содержимое файлов совпадает. Не удаляй такие файлы, пока не добавим точную проверку содержимого.")
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

private fun scanTree(context: Context, treeUri: Uri): List<ScannedFile> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val result = mutableListOf<ScannedFile>()
    fun walk(dir: DocumentFile, path: String, depth: Int) {
        if (depth > 12 || result.size >= 5000) return
        dir.listFiles().forEach { file ->
            if (file.isDirectory) walk(file, "$path/${file.name ?: "folder"}", depth + 1)
            else if (file.isFile) {
                val name = file.name ?: "Без названия"
                val mime = file.type.orEmpty().lowercase(Locale.ROOT)
                val lower = name.lowercase(Locale.ROOT)
                val kind = when {
                    lower.endsWith(".gif") || mime == "image/gif" -> FileKind.GIF
                    mime.startsWith("image/") -> FileKind.PHOTO
                    mime.startsWith("video/") -> FileKind.VIDEO
                    mime.startsWith("audio/") -> FileKind.AUDIO
                    else -> FileKind.OTHER
                }
                result.add(ScannedFile(file.uri, name, file.length(), file.lastModified() / 1000, kind, path))
            }
        }
    }
    walk(root, root.name ?: "Выбранная папка", 0)
    return result
}

@Composable
private fun Games() {
    InfoCard("Игровой режим", "Здесь будут игровые профили, список игр и быстрые настройки перед запуском.")
    InfoCard("Без ложных обещаний", "Обычное приложение не может разгонять процессор или видеочип. Доступность системных игровых функций зависит от телефона и версии Android.")
}
@Composable
private fun Monitoring(context: Context) {
    MetricCard("Свободная оперативная память", memoryInfo(context), Modifier.fillMaxWidth())
    val stats = storageStats()
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
private fun storageStats(): Pair<Long, Long> = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    stat.totalBytes to stat.availableBytes
}.getOrDefault(0L to 0L)
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
