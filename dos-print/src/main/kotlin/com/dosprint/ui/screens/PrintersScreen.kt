package com.dosprint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dosprint.R
import com.dosprint.core.ErrorCard
import com.dosprint.core.UiMessages
import com.dosprint.domain.model.PrinterAvailability
import com.dosprint.domain.model.PrinterConnectionType
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.ui.AddPrinterRequest
import com.dosprint.ui.PrintersAction
import com.dosprint.ui.PrintersUiState
import com.dosprint.ui.common.ChipTone
import com.dosprint.ui.common.EmptyState
import com.dosprint.ui.common.LoadingState
import com.dosprint.ui.common.StatusChip
import com.dosprint.ui.components.DosPrintIcons
import com.dosprint.ui.components.UniformButton
import com.dosprint.ui.components.UniformButtonRow
import com.dosprint.ui.components.icon
import com.dosprint.ui.theme.DosPrintDimens

@Composable
fun PrintersScreen(
    state: PrintersUiState,
    onAction: (PrintersAction) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (!state.hasPrinters && !state.isRefreshing) {
        EmptyState(
            title = stringResource(R.string.empty_no_printers_title),
            body = stringResource(R.string.empty_no_printers_body),
            modifier = modifier.padding(contentPadding),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
            ) {
                Button(onClick = { onAction(PrintersAction.OpenAddDialog) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(DosPrintDimens.iconSmall),
                    )
                    Text(
                        text = stringResource(R.string.printers_add),
                        modifier = Modifier.padding(start = DosPrintDimens.rowGap),
                    )
                }
                OutlinedButton(onClick = { onAction(PrintersAction.OpenSystemSettings) }) {
                    Text(stringResource(R.string.action_open_system_print))
                }
            }
        }
        return
    }

    PrintersListBody(
        state = state,
        onAction = onAction,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun PrintersListBody(
    state: PrintersUiState,
    onAction: (PrintersAction) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
    ) {
        item(key = "title") {
            ScreenTitle(
                title = stringResource(R.string.nav_printers),
                subtitle = printersSubtitle(context, state),
            )
        }

        item(key = "toolbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(PrintersAction.Search(it)) },
                    label = { Text(stringResource(R.string.printers_search)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = SEARCH_LABEL },
                )
                IconButton(
                    onClick = { onAction(PrintersAction.Refresh) },
                    modifier = Modifier
                        .size(DosPrintDimens.minTouchTarget)
                        .semantics { contentDescription = REFRESH_LABEL },
                ) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                }
            }
        }

        item(key = "add") {
            UniformButtonRow(
                buttons = listOf(
                    UniformButton(
                        label = stringResource(R.string.printers_add),
                        icon = Icons.Filled.Add,
                        emphasized = true,
                        onClick = { onAction(PrintersAction.OpenAddDialog) },
                        description = ADD_LABEL,
                    ),
                    UniformButton(
                        label = stringResource(R.string.printers_refresh),
                        icon = Icons.Filled.Refresh,
                        onClick = { onAction(PrintersAction.Refresh) },
                        enabled = !state.isRefreshing,
                    ),
                    UniformButton(
                        label = stringResource(R.string.action_open_system_print),
                        icon = DosPrintIcons.Printer,
                        onClick = { onAction(PrintersAction.OpenSystemSettings) },
                    ),
                ),
            )
        }

        if (state.isRefreshing) {
            item(key = "scanning") {
                LoadingState(label = stringResource(R.string.printers_searching))
            }
        }

        state.errorMessage?.let { key ->
            item(key = "error") {
                ErrorCard(
                    message = printersMessage(context, key) ?: key,
                    detail = state.failedOperation,
                    onRetry = { onAction(PrintersAction.Refresh) },
                    onDismiss = { onAction(PrintersAction.DismissError) },
                )
            }
        }

        if (!state.isPrintServiceAvailable) {
            item(key = "no-service") {
                StatusChip(
                    text = stringResource(R.string.error_no_print_service),
                    tone = ChipTone.ERROR,
                )
            }
        }

        if (state.visiblePrinters.isEmpty() && !state.isRefreshing) {
            item(key = "no-match") {
                EmptyState(
                    title = stringResource(R.string.empty_no_printers_title),
                    body = stringResource(R.string.empty_no_printers_body),
                )
            }
        }

        items(state.visiblePrinters, key = { it.id }) { printer ->
            PrinterCard(
                printer = printer,
                isDefault = printer.id == state.defaultPrinterId || printer.isDefault,
                onSelect = { onAction(PrintersAction.Select(printer)) },
                onSetDefault = { onAction(PrintersAction.SetDefault(printer.id)) },
                onRename = { onAction(PrintersAction.StartRename(printer)) },
                onRemove = { onAction(PrintersAction.Remove(printer.id)) },
            )
        }
    }

    state.addPrinterRequest?.let { request ->
        AddPrinterDialog(
            request = request,
            errorText = state.addPrinterError?.let { UiMessages.addPrinterErrorText(context, it) },
            isSubmitting = state.isSubmitting,
            onNameChange = { onAction(PrintersAction.UpdateAddName(it)) },
            onConnectionChange = { onAction(PrintersAction.UpdateAddConnection(it)) },
            onAddressChange = { onAction(PrintersAction.UpdateAddAddress(it)) },
            onConfirm = { onAction(PrintersAction.ConfirmAdd) },
            onDismiss = { onAction(PrintersAction.DismissAddDialog) },
        )
    }

    state.renameTarget?.let { _ ->
        AlertDialog(
            onDismissRequest = { onAction(PrintersAction.DismissRename) },
            title = { Text(stringResource(R.string.printers_rename)) },
            text = {
                OutlinedTextField(
                    value = state.renameText,
                    onValueChange = { onAction(PrintersAction.UpdateRenameText(it)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(PrintersAction.ConfirmRename) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(PrintersAction.DismissRename) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DosPrintDimens.space4)
            .semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space2),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun printersSubtitle(context: android.content.Context, state: PrintersUiState): String {
    val count = state.printers.size
    if (count == 0) {
        return context.getString(R.string.printers_subtitle_none)
    }
    return context.resources.getQuantityString(R.plurals.printers_subtitle, count, count)
}

@Composable
private fun PrinterCard(
    printer: PrinterProfile,
    isDefault: Boolean,
    onSelect: () -> Unit,
    onSetDefault: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${printer.displayName}, ${printer.description}"
            },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(DosPrintDimens.sectionCorner),
    ) {
        Column(
            modifier = Modifier.padding(DosPrintDimens.listItemPadding),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
            ) {
                Box(
                    modifier = Modifier
                        .size(DosPrintDimens.space40)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(DosPrintDimens.controlCorner),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = printer.connectionType.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(DosPrintDimens.iconMedium),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = printer.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = printer.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (printer.isVirtual) {
                    StatusChip(text = stringResource(R.string.save_pdf), tone = ChipTone.POSITIVE)
                } else {
                    PrinterAvailabilityChip(printer.availability)
                }
            }
            UniformButtonRow(
                buttons = buildList {
                    add(
                        UniformButton(
                            label = if (isDefault) {
                                stringResource(R.string.printers_default)
                            } else {
                                stringResource(R.string.printers_select)
                            },
                            icon = Icons.Filled.Check,
                            emphasized = !isDefault,
                            enabled = !isDefault,
                            onClick = onSelect,
                        ),
                    )
                    if (!printer.isVirtual) {
                        add(
                            UniformButton(
                                label = stringResource(R.string.printers_set_default),
                                icon = Icons.Filled.Star,
                                enabled = !isDefault,
                                onClick = onSetDefault,
                            ),
                        )
                        add(
                            UniformButton(
                                label = stringResource(R.string.printers_rename),
                                icon = Icons.Filled.Edit,
                                onClick = onRename,
                            ),
                        )
                        add(
                            UniformButton(
                                label = stringResource(R.string.action_remove),
                                icon = Icons.Filled.Delete,
                                onClick = onRemove,
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AddPrinterDialog(
    request: AddPrinterRequest,
    errorText: String?,
    isSubmitting: Boolean,
    onNameChange: (String) -> Unit,
    onConnectionChange: (PrinterConnectionType) -> Unit,
    onAddressChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nameProblem = request.name.isNotBlank() &&
        com.dosprint.domain.model.PrinterValidator.validateName(request.name) != null
    val errorTargetsName = errorText != null &&
        (
            request.name.isBlank() ||
                errorText == stringResource(R.string.add_printer_name_invalid)
            )
    val errorTargetsAddress = errorText != null && request.name.isNotBlank() && !errorTargetsName
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.printers_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap)) {
                Text(
                    text = stringResource(R.string.printers_manual_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = request.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.printers_add_name_label)) },
                    singleLine = true,
                    isError = nameProblem || errorTargetsName,
                    supportingText = if (errorTargetsName) {
                        { Text(errorText) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.printers_add_connection),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
                    verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
                ) {
                    addableConnectionTypes().forEach { type ->
                        FilterChip(
                            selected = request.connectionType == type,
                            onClick = { onConnectionChange(type) },
                            label = { Text(type.label, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    imageVector = type.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(DosPrintDimens.iconSmall),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(),
                            modifier = Modifier.semantics { contentDescription = type.label },
                        )
                    }
                }
                if (request.connectionType != PrinterConnectionType.CLOUD) {
                    OutlinedTextField(
                        value = request.address,
                        onValueChange = onAddressChange,
                        label = { Text(stringResource(R.string.printers_add_address_label)) },
                        placeholder = { Text(stringResource(R.string.printers_add_address_hint)) },
                        singleLine = true,
                        isError = errorTargetsAddress,
                        supportingText = if (errorTargetsAddress) {
                            { Text(errorText.orEmpty()) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                modifier = Modifier.heightIn(min = DosPrintDimens.controlHeight),
            ) {
                Text(
                    text = stringResource(
                        if (isSubmitting) R.string.printers_add_saving else R.string.printers_add_save,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun addableConnectionTypes(): List<PrinterConnectionType> = PrinterConnectionType.entries
    .filterNot { it == PrinterConnectionType.PDF || it == PrinterConnectionType.SYSTEM }

@Composable
private fun printersMessage(context: android.content.Context, key: String): String? =
    when (key) {
        "discovery_failed" -> context.getString(R.string.printers_refresh_failed)
        "printer_add_failed" -> context.getString(R.string.printer_add_failed)
        else -> null
    }

private const val SEARCH_LABEL = "Search printers"
private const val REFRESH_LABEL = "Refresh printer list"
private const val ADD_LABEL = "Add a printer"

@Composable
private fun PrinterAvailabilityChip(availability: PrinterAvailability) {
    val (label, tone) = when (availability) {
        PrinterAvailability.ONLINE -> stringResource(R.string.printer_online) to ChipTone.POSITIVE
        PrinterAvailability.BUSY -> stringResource(R.string.printer_busy) to ChipTone.NEUTRAL
        PrinterAvailability.OFFLINE -> stringResource(R.string.printer_offline) to ChipTone.ERROR
        PrinterAvailability.NO_PAPER -> stringResource(R.string.printer_no_paper) to ChipTone.NEUTRAL
        PrinterAvailability.ERROR -> stringResource(R.string.printer_error) to ChipTone.ERROR
        PrinterAvailability.UNKNOWN -> stringResource(R.string.printer_unknown) to ChipTone.NEUTRAL
    }
    StatusChip(text = label, tone = tone)
}
