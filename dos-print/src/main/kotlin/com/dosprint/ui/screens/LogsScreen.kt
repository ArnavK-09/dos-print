package com.dosprint.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.dosprint.R
import com.dosprint.core.AppLog
import com.dosprint.core.ErrorRecord
import com.dosprint.core.ErrorReporter
import com.dosprint.core.FileLog
import com.dosprint.core.LogFormatter
import com.dosprint.core.LogLevel
import com.dosprint.core.UiMessages
import com.dosprint.ui.components.ChipFlow
import com.dosprint.ui.components.DosPrintIcons
import com.dosprint.ui.components.UniformButton
import com.dosprint.ui.components.UniformButtonRow
import com.dosprint.ui.theme.DosPrintDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogTab { SESSION, SAVED, ERRORS }

private enum class LogFilter(val minimum: LogLevel) {
    ALL(LogLevel.VERBOSE),
    INFO(LogLevel.INFO),
    WARN(LogLevel.WARN),
    ERROR(LogLevel.ERROR),
}

/**
 * Everything the app has recorded, readable on the device itself.
 *
 * Reading, copying and sharing happen here because the person hitting a problem usually has only
 * the phone. Nothing polls: the view is a snapshot taken when the screen opens or Refresh is
 * pressed, so having it open costs nothing.
 */
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(LogTab.SESSION) }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var revision by remember { mutableIntStateOf(0) }
    val clock = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val tabNames = LogTab.entries.associateWith { tabLabel(it) }
    val filterNames = LogFilter.entries.associateWith { filterLabel(it) }

    val session = remember(revision) { AppLog.snapshot() }
    val errors = remember(revision) { ErrorReporter.recent() }
    var saved by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(tab, revision) {
        if (tab == LogTab.SAVED) {
            saved = withContext(Dispatchers.IO) {
                FileLog.directory()?.let { FileLog.recentLines(it, SAVED_LINE_LIMIT) }.orEmpty()
            }
        }
    }

    val sessionLines = remember(session, filter) {
        session.filter { it.level >= filter.minimum }
            .map { entry -> LogLine(entry.level, LogFormatter.line(entry) { clock.format(Date(it)) }) }
    }
    val exportText = when (tab) {
        LogTab.SESSION -> LogFormatter.export(sessionLines.map { it.text })
        LogTab.SAVED -> LogFormatter.export(saved)
        LogTab.ERRORS -> LogFormatter.export(errors.map { it.asText() })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        ) {
            IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.logs_back),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.logs_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.logs_count,
                        when (tab) {
                            LogTab.SESSION -> sessionLines.size
                            LogTab.SAVED -> saved.size
                            LogTab.ERRORS -> errors.size
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { revision++ }) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.logs_refresh),
                )
            }
        }

        ChipFlow(
            options = LogTab.entries,
            selected = tab,
            label = { tabNames.getValue(it) },
            onSelect = { tab = it },
        )
        if (tab == LogTab.SESSION) {
            ChipFlow(
                options = LogFilter.entries,
                selected = filter,
                label = { filterNames.getValue(it) },
                onSelect = { filter = it },
            )
        }

        UniformButtonRow(
            buttons = listOf(
                UniformButton(
                    label = stringResource(R.string.action_copy),
                    icon = DosPrintIcons.ContentCopy,
                    enabled = exportText.isNotBlank(),
                    onClick = {
                        UiMessages.copyToClipboard(context, "Dos Print log", exportText)
                    },
                ),
                UniformButton(
                    label = stringResource(R.string.logs_share),
                    icon = DosPrintIcons.ArrowUpward,
                    emphasized = true,
                    enabled = exportText.isNotBlank(),
                    onClick = { shareText(context, exportText) },
                ),
                UniformButton(
                    label = stringResource(R.string.error_log_clear),
                    icon = Icons.Filled.Delete,
                    onClick = {
                        when (tab) {
                            LogTab.SESSION -> AppLog.clear()
                            LogTab.SAVED -> FileLog.directory()?.let { FileLog.deleteAll(it) }
                            LogTab.ERRORS -> ErrorReporter.clear()
                        }
                        saved = emptyList()
                        revision++
                    },
                ),
            ),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(DosPrintDimens.controlCorner),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PanelAlpha),
        ) {
            when (tab) {
                LogTab.SESSION -> LineList(lines = sessionLines.asReversed())
                LogTab.SAVED -> LineList(
                    lines = saved.asReversed().map { LogLine(levelOfSavedLine(it), it) },
                )
                LogTab.ERRORS -> ErrorList(errors = errors)
            }
        }
    }
}

private data class LogLine(val level: LogLevel, val text: String)

@Composable
private fun LineList(lines: List<LogLine>) {
    if (lines.isEmpty()) {
        EmptyNote()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(DosPrintDimens.space8),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space2),
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = LOG_FONT_SIZE.sp,
                lineHeight = LOG_LINE_HEIGHT.sp,
                color = colorFor(line.level),
            )
        }
    }
}

@Composable
private fun ErrorList(errors: List<ErrorRecord>) {
    if (errors.isEmpty()) {
        EmptyNote()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(DosPrintDimens.space12),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
    ) {
        items(errors, key = { "${it.timestampMillis}-${it.area}" }) { record ->
            ErrorEntry(record)
        }
    }
}

@Composable
private fun ErrorEntry(record: ErrorRecord) {
    var expanded by rememberSaveable(record.timestampMillis) { mutableStateOf(false) }
    val tone = if (record.severity == ErrorReporter.Severity.FATAL) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space4)) {
        Text(text = record.area, style = MaterialTheme.typography.labelLarge, color = tone)
        Text(
            text = record.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(if (expanded) R.string.error_log_hide else R.string.error_log_show),
            )
        }
        if (expanded) {
            Text(
                text = record.detail,
                fontFamily = FontFamily.Monospace,
                fontSize = LOG_FONT_SIZE.sp,
                lineHeight = LOG_LINE_HEIGHT.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyNote() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DosPrintDimens.space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.logs_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun colorFor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun tabLabel(tab: LogTab): String = stringResource(
    when (tab) {
        LogTab.SESSION -> R.string.logs_tab_session
        LogTab.SAVED -> R.string.logs_tab_saved
        LogTab.ERRORS -> R.string.logs_tab_errors
    },
)

@Composable
private fun filterLabel(filter: LogFilter): String = stringResource(
    when (filter) {
        LogFilter.ALL -> R.string.logs_filter_all
        LogFilter.INFO -> R.string.logs_filter_info
        LogFilter.WARN -> R.string.logs_filter_warn
        LogFilter.ERROR -> R.string.logs_filter_error
    },
)

/** Saved lines are `date time L message`; the level letter sits after the 23-character timestamp. */
private fun levelOfSavedLine(line: String): LogLevel = when (line.getOrNull(SAVED_LEVEL_INDEX)) {
    'E' -> LogLevel.ERROR
    'W' -> LogLevel.WARN
    'I' -> LogLevel.INFO
    'D' -> LogLevel.DEBUG
    else -> LogLevel.VERBOSE
}

private fun shareText(context: Context, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Dos Print log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, "Dos Print log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { ErrorReporter.report("shareLog", it) }
}

internal fun ErrorRecord.asText(): String = buildString {
    append(area)
    append(" [")
    append(severity)
    append("] ")
    append(summary)
    if (detail.isNotBlank()) {
        append('\n')
        append(detail)
    }
}

private const val PanelAlpha = 0.35f
private const val LOG_FONT_SIZE = 11
private const val LOG_LINE_HEIGHT = 15
private const val SAVED_LINE_LIMIT = 600
private const val SAVED_LEVEL_INDEX = 24
