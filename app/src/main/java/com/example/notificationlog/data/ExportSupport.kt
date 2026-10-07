package com.example.notificationlog.data

import android.content.ContentResolver
import android.net.Uri
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LogFilter(
    val appQuery: String = "",
    val eventType: String = "ALL",
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val fromTime: LocalTime? = null,
    val toTime: LocalTime? = null
) {
    val isActive: Boolean
        get() = appQuery.isNotBlank() || eventType != "ALL" || fromDate != null || toDate != null || fromTime != null || toTime != null

    fun startMillis(): Long? = fromDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    fun endMillis(): Long? = toDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    fun startTimeValue(): String = fromTime?.format(TIME_FORMAT) ?: ""
    fun endTimeValue(): String = toTime?.format(TIME_FORMAT) ?: ""
    fun crossesMidnight(): Int = if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) 1 else 0

    fun summary(): String = buildList {
        if (appQuery.isNotBlank()) add("приложение: $appQuery")
        if (fromDate != null || toDate != null) add("дата: ${fromDate ?: "…"} — ${toDate ?: "…"}")
        if (fromTime != null || toTime != null) add("время: ${fromTime?.format(TIME_FORMAT) ?: "…"} — ${toTime?.format(TIME_FORMAT) ?: "…"}")
    }.joinToString(" · ")

    companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

object NotificationExporter {
    private val dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

    suspend fun exportCsv(resolver: ContentResolver, uri: Uri, rows: List<ExportRow>) {
        resolver.openOutputStream(uri)?.use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                writer.write(headers.joinToString(",") { csv(it) })
                writer.write("\n")
                rows.forEach { row ->
                    writer.write(row.toValues().joinToString(",") { csv(it) })
                    writer.write("\n")
                }
            }
        } ?: error("Не удалось открыть файл для записи")
    }

    suspend fun exportXlsx(resolver: ContentResolver, uri: Uri, rows: List<ExportRow>) {
        resolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                entry(zip, "[Content_Types].xml", contentTypes)
                entry(zip, "_rels/.rels", rootRels)
                entry(zip, "xl/workbook.xml", workbook)
                entry(zip, "xl/_rels/workbook.xml.rels", workbookRels)
                entry(zip, "xl/worksheets/sheet1.xml", sheet(rows))
            }
        } ?: error("Не удалось открыть файл для записи")
    }

    private val headers = listOf(
        "Дата и время", "Событие", "Приложение", "Пакет", "Ключ уведомления",
        "Заголовок", "Текст", "Подтекст", "BigText", "Summary", "Категория",
        "Channel ID", "Channel name", "Group key", "Group summary", "Importance",
        "Priority", "Notification number", "Flags", "State hash", "Extras JSON"
    )

    private fun ExportRow.toValues(): List<String> = listOf(
        dateTime.format(java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())),
        eventType, appName, packageName, threadId, title.orEmpty(), text.orEmpty(),
        subText.orEmpty(), bigText.orEmpty(), summary.orEmpty(), category.orEmpty(),
        channelId.orEmpty(), channelName.orEmpty(), groupKey.orEmpty(), isGroupSummary.toString(),
        importance.toString(), priority.toString(), notificationNumber.toString(), flags.toString(),
        stateHash, extrasJson
    )

    private fun csv(value: String): String =
        "\"${value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""

    private fun sheet(rows: List<ExportRow>): String {
        val all = sequenceOf(headers) + rows.asSequence().map { it.toValues() }
        val xml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        all.forEachIndexed { index, values ->
            val rowNumber = index + 1
            xml.append("<row r=\"").append(rowNumber).append("\">")
            values.forEachIndexed { col, value ->
                val ref = columnName(col + 1) + rowNumber
                xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xmlEscape(value)).append("</t></is></c>")
            }
            xml.append("</row>")
        }
        xml.append("</sheetData></worksheet>")
        return xml.toString()
    }

    private fun columnName(number: Int): String {
        var n = number
        val result = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            result.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
        .filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' }

    private fun entry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private const val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    private const val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private const val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Уведомления" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private const val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
}
