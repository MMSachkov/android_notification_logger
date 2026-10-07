package com.example.notificationlog.ui

import android.content.ComponentName
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.notificationlog.data.*
import com.example.notificationlog.worker.BootReceiver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val dateTime = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
private val shortTime = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var notificationAccess by mutableStateOf(false)
    private val dao by lazy { AppDatabase.get(this).notificationDao() }
    private val prefs by lazy { Preferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { BootReceiver.schedule(this) }
        refreshNotificationAccess()
        setContent { NotificationLogTheme { AppScreen() } }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationAccess()
    }

    private fun refreshNotificationAccess() {
        notificationAccess = runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.isNotificationListenerAccessGranted(
                ComponentName(this, com.example.notificationlog.notification.NotificationLogListenerService::class.java)
            )
        }.getOrElse {
            val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return@getOrElse false
            enabled.split(":").mapNotNull { runCatching { ComponentName.unflattenFromString(it) }.getOrNull() }.any {
                it.packageName == packageName &&
                    it.className == "com.example.notificationlog.notification.NotificationLogListenerService"
            }
        }
    }

    private fun openListenerSettings() = startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    @Composable
    private fun AppScreen() {
        var selectedId by remember { mutableStateOf<String?>(null) }
        var screen by remember { mutableStateOf(0) }
        val access = notificationAccess
        val allSummaries by dao.observeThreadSummaries().collectAsState(initial = emptyList())
        val versionsCount by dao.observeVersionCount().collectAsState(initial = 0)
        val removedCount by dao.observeEventCount("REMOVED").collectAsState(initial = 0)
        val changedCount by dao.observeEventCount("UPDATED").collectAsState(initial = 0)
        var logFilter by remember { mutableStateOf(LogFilter()) }
        var showFilter by remember { mutableStateOf(false) }
        var exportFilter by remember { mutableStateOf(LogFilter()) }
        val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) lifecycleScope.launch {
                runCatching {
                    val rows = dao.exportRows(exportFilter.eventType, exportFilter.appQuery.trim(), exportFilter.startMillis(), exportFilter.endMillis(), exportFilter.startMinute(), exportFilter.endMinute(), exportFilter.crossesMidnight())
                    NotificationExporter.exportCsv(contentResolver, uri, rows)
                }.onSuccess {
                    Toast.makeText(this@MainActivity, "CSV экспортирован", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this@MainActivity, "Не удалось экспортировать: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
        val xlsxLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
            if (uri != null) lifecycleScope.launch {
                runCatching {
                    val rows = dao.exportRows(exportFilter.eventType, exportFilter.appQuery.trim(), exportFilter.startMillis(), exportFilter.endMillis(), exportFilter.startMinute(), exportFilter.endMinute(), exportFilter.crossesMidnight())
                    NotificationExporter.exportXlsx(contentResolver, uri, rows)
                }.onSuccess {
                    Toast.makeText(this@MainActivity, "XLSX экспортирован", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this@MainActivity, "Не удалось экспортировать: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
        val summaries by dao.observeThreadSummariesFiltered(
            logFilter.eventType, logFilter.appQuery.trim(), logFilter.startMillis(), logFilter.endMillis(),
            logFilter.startMinute(), logFilter.endMinute(), logFilter.crossesMidnight()
        ).collectAsState(initial = emptyList())

        if (selectedId != null) {
            val thread = summaries.firstOrNull { it.id == selectedId }
            if (thread != null) {
                HistoryScreen(thread, onBack = { selectedId = null })
                return
            }
            selectedId = null
        }

        Scaffold(
            modifier = if (screen != 0) Modifier.swipeBack { screen = 0 } else Modifier,
            topBar = {
                if (screen == 0) TopAppBar(
                    title = { Text("Лог уведомлений", fontWeight = FontWeight.SemiBold) },
                    actions = { IconButton(onClick = { screen = 1 }) { Icon(Icons.Default.Settings, "Настройки") } }
                ) else TopAppBar(
                    title = { Text("Настройки", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = { screen = 0 }) { Icon(Icons.Default.ArrowBack, "Назад") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = screen == 0, onClick = { screen = 0 }, icon = { Icon(Icons.Default.List, null) }, label = { Text("Журнал") })
                    NavigationBarItem(selected = screen == 1, onClick = { screen = 1 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
                }
            }
        ) { pad ->
            if (screen == 0) {
                HomeScreen(
                    summaries, versionsCount, changedCount, removedCount, access,
                    Modifier.padding(pad), logFilter,
                    onOpenAccess = { openListenerSettings() },
                    onSelect = { selectedId = it.id },
                    onFilterChange = { logFilter = it },
                    onShowFilter = { showFilter = true },
                    onExportCsv = { exportFilter = logFilter; csvLauncher.launch("notification_log.csv") },
                    onExportXlsx = { exportFilter = logFilter; xlsxLauncher.launch("notification_log.xlsx") }
                )
            } else {
                SettingsScreen(Modifier.padding(pad), allSummaries, onOpenAccess = { openListenerSettings() })
            }
        }
        if (showFilter) NotificationFilterDialog(logFilter, onDismiss = { showFilter = false }) { value -> logFilter = value; showFilter = false }
    }

    @Composable
    private fun HomeScreen(
        summaries: List<ThreadSummary>, versionsCount: Int, changedCount: Int, removedCount: Int,
        access: Boolean, modifier: Modifier, logFilter: LogFilter, onOpenAccess: () -> Unit,
        onSelect: (ThreadSummary) -> Unit, onFilterChange: (LogFilter) -> Unit, onShowFilter: () -> Unit,
        onExportCsv: () -> Unit, onExportXlsx: () -> Unit
    ) {
        val filtered = summaries

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!access) item { PermissionBanner(onOpenAccess) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Цепочки", summaries.size.toString(), Icons.Default.Forum, Modifier.weight(1f))
                    StatCard("Версии", versionsCount.toString(), Icons.Default.History, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Изменения", changedCount.toString(), Icons.Default.Edit, Modifier.weight(1f))
                    StatCard("Удаления", removedCount.toString(), Icons.Default.DeleteOutline, Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(
                    value = logFilter.appQuery, onValueChange = { onFilterChange(logFilter.copy(appQuery = it)) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (logFilter.appQuery.isNotEmpty()) IconButton(onClick = { onFilterChange(logFilter.copy(appQuery = "")) }) { Icon(Icons.Default.Clear, "Очистить") } },
                    placeholder = { Text("Поиск по приложению") }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(logFilter.eventType == "ALL", { onFilterChange(logFilter.copy(eventType = "ALL")) }, { Text("Все") })
                    FilterChip(logFilter.eventType == "POSTED", { onFilterChange(logFilter.copy(eventType = "POSTED")) }, { Text("Новые") })
                    FilterChip(logFilter.eventType == "UPDATED", { onFilterChange(logFilter.copy(eventType = "UPDATED")) }, { Text("Изменения") })
                    FilterChip(logFilter.eventType == "REMOVED", { onFilterChange(logFilter.copy(eventType = "REMOVED")) }, { Text("Удалённые") })
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowFilter, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Tune, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (logFilter.isActive) "Фильтры ✓" else "Фильтры")
                    }
                    OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) { Text("CSV") }
                    OutlinedButton(onClick = onExportXlsx, modifier = Modifier.weight(1f)) { Text("XLSX") }
                }
                if (logFilter.isActive) Text(logFilter.summary(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Text("Последние цепочки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (filtered.isEmpty()) {
                item { EmptyState(logFilter.isActive) }
            } else {
                items(filtered, key = { it.id }) { item -> ThreadCard(item, onClick = { onSelect(item) }) }
            }
        }
    }

    @Composable
    private fun PermissionBanner(onOpenAccess: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Доступ к уведомлениям не включён", fontWeight = FontWeight.SemiBold)
                    Text("Без него журнал не сможет получать новые события.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onOpenAccess) { Text("Включить") }
            }
        }
    }

    @Composable
    private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
        Card(modifier, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    @Composable
    private fun ThreadCard(item: ThreadSummary, onClick: () -> Unit) {
        val event = item.latestEventType ?: "POSTED"
        Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Text(item.appName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.appName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        EventPill(event)
                    }
                    item.latestTitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    item.latestText?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(6.dp))
                    Text(item.latestTimestamp?.let { dateTime.format(Date(it)) } ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun EventPill(event: String) {
        val label = when (event) { "UPDATED" -> "Изменение"; "REMOVED" -> "Удалено"; else -> "Новое" }
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }

    @Composable
    private fun EmptyState(searching: Boolean) {
        Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (searching) Icons.Default.SearchOff else Icons.Default.NotificationsNone, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(if (searching) "Ничего не найдено" else "История пока пуста", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (searching) "Измените запрос или фильтр." else "Новые уведомления появятся здесь автоматически.", style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun HistoryScreen(thread: ThreadSummary, onBack: () -> Unit) {
        val versions by dao.observeVersions(thread.id).collectAsState(initial = emptyList())
        Scaffold(modifier = Modifier.swipeBack(onBack), topBar = {
            TopAppBar(title = { Column { Text(thread.appName, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("История изменений", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } })
        }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(thread.latestTitle ?: "Без заголовка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            thread.latestText?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                            Text("${versions.size} версий · ${thread.notificationKey}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                items(versions, key = { it.id }) { version -> TimelineItem(version, isLast = version.id == versions.lastOrNull()?.id) }
            }
        }
    }

    @Composable
    private fun TimelineItem(version: NotificationVersion, isLast: Boolean) {
        Row(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                if (!isLast) Box(Modifier.width(2.dp).height(90.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Spacer(Modifier.width(8.dp))
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(eventTitle(version.eventType), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(shortTime.format(Date(version.timestamp)), style = MaterialTheme.typography.labelMedium)
                    }
                    version.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
                    version.text?.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.padding(top = 2.dp)) }
                    Text(dateTime.format(Date(version.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Text("extras · ${version.extrasJson.length} байт", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    private fun eventTitle(type: String) = when (type) { "UPDATED" -> "Изменение уведомления"; "REMOVED" -> "Уведомление удалено"; else -> "Новое уведомление" }

    @Composable
    private fun SettingsScreen(modifier: Modifier, summaries: List<ThreadSummary>, onOpenAccess: () -> Unit) {
        var days by remember { mutableStateOf(prefs.retentionDays.toString()) }
        var showClear by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        val access = notificationAccess
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text("Хранение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Срок хранения истории", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(days, { days = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), singleLine = true, suffix = { Text("дней") })
                        Text("Старые версии автоматически удаляются раз в сутки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { days.toIntOrNull()?.let {
                            prefs.retentionDays = it
                            runCatching { BootReceiver.schedule(this@MainActivity) }
                            saved = true
                        } }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Сохранено" else "Сохранить") }
                    }
                }
            }
            item {
                Text("Разрешения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (access) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (access) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Доступ к уведомлениям", fontWeight = FontWeight.SemiBold)
                            Text(if (access) "Разрешение включено" else "Требуется разрешение", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!access) TextButton(onClick = onOpenAccess) { Text("Открыть") }
                    }
                }
            }
            item {
                Text("Исключения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Не записывать уведомления от выбранных приложений", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        val excludedPackages = prefs.excludedPackages()
                        val summaryApps = summaries.associateBy { it.packageName }
                        val apps = (summaries.map { it.packageName } + excludedPackages)
                            .distinct()
                            .map { packageName ->
                                val summary = summaryApps[packageName]
                                val appName = summary?.appName ?: runCatching {
                                    packageManager.getApplicationLabel(
                                        packageManager.getApplicationInfo(packageName, 0)
                                    ).toString()
                                }.getOrElse { packageName }
                                packageName to appName
                            }
                            .sortedBy { it.second.lowercase(Locale.getDefault()) }
                        if (apps.isEmpty()) {
                            Text("Список появится после получения первого уведомления.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            apps.forEach { (packageName, appName) ->
                                var excluded by remember(packageName) { mutableStateOf(prefs.isExcluded(packageName)) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(appName, fontWeight = FontWeight.SemiBold)
                                        Text(packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = !excluded, onCheckedChange = {
                                        excluded = !it
                                        prefs.setExcluded(packageName, excluded)
                                    })
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Данные", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Очистить всю историю")
                }
                Spacer(Modifier.height(8.dp))
                Text("Все данные хранятся только на устройстве. Приложение не использует интернет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showClear) AlertDialog(
            onDismissRequest = { showClear = false },
            icon = { Icon(Icons.Default.DeleteForever, null) },
            title = { Text("Очистить всю историю?") },
            text = { Text("Все сохранённые уведомления и их версии будут удалены. Это действие нельзя отменить.") },
            confirmButton = { Button(onClick = { lifecycleScope.launch { dao.clearVersions(); dao.clearThreads(); showClear = false } }) { Text("Удалить всё") } },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Отмена") } }
        )
    }

}

private fun Modifier.swipeBack(onBack: () -> Unit): Modifier = pointerInput(Unit) {
    var totalX = 0f
    var totalY = 0f
    detectDragGestures(
        onDragStart = {
            totalX = 0f
            totalY = 0f
        },
        onDrag = { _, dragAmount ->
            totalX += dragAmount.x
            totalY += dragAmount.y
        },
        onDragEnd = {
            if (totalX < -120f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) {
                onBack()
            }
        },
        onDragCancel = {
            totalX = 0f
            totalY = 0f
        }
    )
}

@Composable
private fun NotificationLogTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF315B8C),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFD5E3FF),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001B3E),
        secondary = androidx.compose.ui.graphics.Color(0xFF555F71),
        secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD9E3F8),
        surface = androidx.compose.ui.graphics.Color(0xFFFAF8FF),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE1E2EC)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
