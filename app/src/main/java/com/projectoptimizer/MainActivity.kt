package com.projectoptimizer

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    primary = Accent,
                    onPrimary = Color(0xFF062016),
                    background = Background,
                    surface = Panel,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                OptimizerApp(this)
            }
        }
    }
}

private data class TabItem(val title: String, val icon: ImageVector)

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
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 9.sp) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
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
                    1 -> Cleaning()
                    2 -> Games()
                    3 -> Monitoring(context)
                    else -> Settings()
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun Dashboard(context: Context) {
    InfoCard("Состояние устройства", "Показатели считываются из системных API Android.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Свободное место", storageInfo(), Modifier.weight(1f))
        MetricCard("Свободная RAM", memoryInfo(context), Modifier.weight(1f))
    }
    MetricCard("Тепловое состояние", thermalInfo(context), Modifier.fillMaxWidth())
    InfoCard(
        "Быстрые действия",
        "Открой «Очистка», чтобы посмотреть состояние хранилища, или «Мониторинг» для диагностики."
    )
    InfoCard(
        "Честно о возможностях",
        "Приложение не закрывает чужие процессы и не обещает искусственное увеличение FPS. Доступны только функции, разрешённые Android."
    )
}

@Composable
private fun Cleaning() {
    InfoCard("Анализ хранилища", "Свободное место: ${storageInfo()}")
    InfoCard(
        "Безопасная очистка",
        "Это стартовая версия: она показывает доступное место, но пока не удаляет файлы автоматически. Удаление добавим через системный выбор файлов Android с подтверждением пользователя."
    )
    InfoCard(
        "Ограничения Android",
        "Обычное приложение не может очищать приватный кэш других приложений. Для этого нужны системные привилегии, которых у обычного приложения нет."
    )
}

@Composable
private fun Games() {
    InfoCard(
        "Игровой режим",
        "Здесь будут игровые профили, список игр и быстрые настройки перед запуском."
    )
    InfoCard(
        "Без ложных обещаний",
        "Обычное приложение не может разгонять процессор или видеочип. Доступность системных игровых функций зависит от телефона и версии Android."
    )
    InfoCard(
        "Следующий этап",
        "Добавим обнаружение установленных игр и пользовательские профили."
    )
}

@Composable
private fun Monitoring(context: Context) {
    MetricCard("Свободная оперативная память", memoryInfo(context), Modifier.fillMaxWidth())
    MetricCard("Свободное хранилище", storageInfo(), Modifier.fillMaxWidth())
    MetricCard("Тепловое состояние", thermalInfo(context), Modifier.fillMaxWidth())
    InfoCard(
        "Загрузка CPU",
        "Пока не показывается: универсального стабильного API для точного процента загрузки CPU у сторонних приложений нет."
    )
}

@Composable
private fun Settings() {
    InfoCard("Настройки", "Уведомления, расписание обслуживания и оформление появятся в следующих версиях.")
    InfoCard("Версия", "Project Optimizer 0.1.0 · ранняя сборка")
    InfoCard("Конфиденциальность", "На этом этапе приложение не отправляет данные устройства на сервер.")
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Accent, fontSize = 20.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

private fun storageInfo(): String = runCatching {
    val stat = StatFs(android.os.Environment.getDataDirectory().path)
    formatBytes(stat.availableBytes)
}.getOrDefault("Недоступно")

private fun memoryInfo(context: Context): String = runCatching {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    "${formatBytes(info.availMem)} свободно"
}.getOrDefault("Недоступно")

private fun thermalInfo(context: Context): String = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return@runCatching "Не поддерживается этой версией Android"
    }
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
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f ГБ", gib)
}
