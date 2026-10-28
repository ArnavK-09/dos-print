package com.dosprint.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dosprint.R
import com.dosprint.core.ErrorRecord
import com.dosprint.core.UiMessages
import com.dosprint.ui.theme.DosPrintDimens

/**
 * Tells the user how the previous run ended.
 *
 * A crash or ANR used to be followed by the app quietly reopening on Home, with nothing to say that
 * anything had gone wrong. The crash handler cannot show UI safely while the process is dying, so
 * the report is saved and shown here on the next launch instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviousCrashDialog(
    record: ErrorRecord,
    onViewLogs: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val report = remember(record) { buildReport(record) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_previous_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap)) {
                Text(
                    text = stringResource(R.string.crash_previous_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = record.area,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = record.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap)) {
                TextButton(onClick = onViewLogs) {
                    Text(stringResource(R.string.crash_previous_view))
                }
                TextButton(
                    onClick = {
                        UiMessages.copyToClipboard(context, record.area, report)
                    },
                ) {
                    Text(stringResource(R.string.crash_previous_copy))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.crash_previous_dismiss))
                }
            }
        },
    )
}

private fun buildReport(record: ErrorRecord): String = buildString {
    append(record.area)
    append('\n')
    append(record.summary)
    if (record.detail.isNotBlank()) {
        append("\n\n")
        append(record.detail)
    }
}
