package com.sortora.app.domain.history

import com.sortora.app.domain.model.OperationLogEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** CSV export of the rename log (RFC 4180 quoting, opens cleanly in any spreadsheet app). */
object LogCsv {

    private val header = listOf("timestamp", "action", "status", "original_name", "new_name", "folder", "note")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun format(entries: List<OperationLogEntry>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine(header.joinToString(","))
        for (entry in entries) {
            val row = listOf(
                formatter.format(Instant.ofEpochMilli(entry.timestamp).atZone(zone)),
                entry.mode.name.lowercase(),
                entry.status.name.lowercase(),
                entry.originalName,
                entry.newName,
                entry.targetFolder,
                entry.message.orEmpty(),
            )
            appendLine(row.joinToString(",") { escape(it) })
        }
    }

    private fun escape(value: String): String {
        // Leading =, +, -, @ would be evaluated as formulas by spreadsheet apps.
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
