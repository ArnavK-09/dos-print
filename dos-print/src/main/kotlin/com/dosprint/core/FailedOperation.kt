package com.dosprint.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dosprint.R
import com.dosprint.domain.model.AddPrinterError
import com.dosprint.domain.model.AddressProblem
import com.dosprint.ui.components.DosPrintIcons
import com.dosprint.ui.theme.DosPrintDimens
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class FailedOperation(
    val operation: String,
    val detail: String,
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    fun asReport(): String = buildString {
        appendLine("Dos Print error report")
        appendLine("When: ${formatTimestamp(timestampMillis)}")
        appendLine("What: $operation")
        appendLine()
        appendLine(detail.trim())
    }

    companion object {
        private val timestampFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

        fun formatTimestamp(millis: Long): String = runCatching {
            timestampFormat.format(Instant.ofEpochMilli(millis))
        }.getOrDefault(millis.toString())
    }
}

object UiMessages {

    fun addPrinterErrorText(context: Context, error: AddPrinterError): String = when (error) {
        AddPrinterError.NameRequired -> context.getString(R.string.add_printer_name_required)
        is AddPrinterError.InvalidName -> context.getString(R.string.add_printer_name_invalid)
        is AddPrinterError.AddressRequired -> context.getString(
            R.string.add_printer_address_required,
            error.connectionType.label,
        )
        is AddPrinterError.InvalidAddress -> context.getString(
            when (error.reason) {
                AddressProblem.INVALID_PORT -> R.string.add_printer_port_invalid
                AddressProblem.OUT_OF_RANGE -> R.string.add_printer_port_range
                AddressProblem.NOT_A_HOSTNAME -> R.string.add_printer_host_invalid
            },
        )
        is AddPrinterError.DuplicateName -> context.getString(
            R.string.add_printer_duplicate,
            error.existing,
        )
    }

    fun failedOperation(operation: String, error: Throwable): FailedOperation = FailedOperation(
        operation = operation,
        detail = buildString {
            append(error.javaClass.name)
            append(": ")
            append(error.message ?: "no message")
            appendLine()
            append(stackTraceOf(error))
        },
    )

    fun stackTraceOf(error: Throwable): String = StringWriter().also { writer ->
        PrintWriter(writer).use { error.printStackTrace(it) }
    }.toString()

    fun copyToClipboard(context: Context, label: String, text: String): Boolean = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        clipboard != null
    }.getOrDefault(false)
}

@Composable
fun CopyToClipboardButton(
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val description = label ?: stringResource(R.string.action_copy_details)
    TextButton(
        onClick = { copied = UiMessages.copyToClipboard(context, description, text) },
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (copied) Icons.Filled.Check else DosPrintIcons.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(DosPrintDimens.iconSmall),
        )
        Text(
            text = if (copied) stringResource(R.string.action_copied) else description,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = DosPrintDimens.space6),
        )
    }
}

/**
 * An error, with every action that makes sense for it.
 *
 * The actions are independent of whether a [detail] was supplied. They used to sit inside the
 * `detail != null` branch, so an error raised without a detail rendered as a card with no way to
 * retry, dismiss or find out more — a dead end on screen. They also wrap rather than overflow, so
 * four buttons stay visible on a narrow phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    detail: FailedOperation? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onViewLogs: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DosPrintDimens.controlCorner),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(DosPrintDimens.listItemPadding),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(
                    text = context.getString(R.string.error_detail_available, detail.operation),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
                verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space2),
            ) {
                if (detail != null) {
                    CopyToClipboardButton(text = detail.asReport())
                }
                onViewLogs?.let { open ->
                    TextButton(onClick = open) {
                        Text(stringResource(R.string.error_view_logs))
                    }
                }
                onRetry?.let { retry ->
                    TextButton(onClick = retry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                onDismiss?.let { dismiss ->
                    TextButton(onClick = dismiss) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
            }
        }
    }
}
