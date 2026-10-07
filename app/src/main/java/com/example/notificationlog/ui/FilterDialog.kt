package com.example.notificationlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.notificationlog.data.LogFilter
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun NotificationFilterDialog(
    initial: LogFilter,
    onDismiss: () -> Unit,
    onApply: (LogFilter) -> Unit
) {
    var app by remember { mutableStateOf(initial.appQuery) }
    var event by remember { mutableStateOf(initial.eventType) }
    var fromDate by remember { mutableStateOf(initial.fromDate?.format(LogFilter.DATE_FORMAT).orEmpty()) }
    var toDate by remember { mutableStateOf(initial.toDate?.format(LogFilter.DATE_FORMAT).orEmpty()) }
    var fromTime by remember { mutableStateOf(initial.fromTime?.format(LogFilter.TIME_FORMAT).orEmpty()) }
    var toTime by remember { mutableStateOf(initial.toTime?.format(LogFilter.TIME_FORMAT).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parse(): LogFilter? {
        return runCatching {
            val fd = fromDate.trim().takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it, LogFilter.DATE_FORMAT) }
            val td = toDate.trim().takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it, LogFilter.DATE_FORMAT) }
            val ft = fromTime.trim().takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it, LogFilter.TIME_FORMAT) }
            val tt = toTime.trim().takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it, LogFilter.TIME_FORMAT) }
            require(fd == null || td == null || !td.isBefore(fd)) { "Дата «по» не может быть раньше даты «с»." }
            LogFilter(app.trim(), event, fd, td, ft, tt)
        }.onFailure { error = "Проверьте формат даты (дд.ММ.гггг) и времени (ЧЧ:мм)." }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтр журнала") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(app, { app = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Приложение или пакет") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "Все", "POSTED" to "Новые", "UPDATED" to "Изменения", "REMOVED" to "Удалённые").forEach { (value, label) ->
                        TextButton(onClick = { event = value }) { Text(if (event == value) "✓ $label" else label) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(fromDate, { fromDate = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Дата с") }, placeholder = { Text("дд.ММ.гггг") })
                    OutlinedTextField(toDate, { toDate = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Дата по") }, placeholder = { Text("дд.ММ.гггг") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(fromTime, { fromTime = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Время с") }, placeholder = { Text("ЧЧ:мм") })
                    OutlinedTextField(toTime, { toTime = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Время по") }, placeholder = { Text("ЧЧ:мм") })
                }
                error?.let { Text(it, modifier = Modifier.padding(top = 2.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { parse()?.let(onApply) }) { Text("Применить") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(LogFilter()) }) { Text("Сбросить") }
        }
    )
}
