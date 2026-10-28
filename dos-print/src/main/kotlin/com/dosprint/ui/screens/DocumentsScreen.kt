package com.dosprint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dosprint.R
import com.dosprint.core.ErrorCard
import com.dosprint.core.FailedOperation
import com.dosprint.domain.model.PrintDocument
import com.dosprint.ui.DocumentFilter
import com.dosprint.ui.DocumentsAction
import com.dosprint.ui.DocumentsUiState
import com.dosprint.ui.common.EmptyState
import com.dosprint.ui.common.LoadingState
import com.dosprint.ui.components.DocumentIcon
import com.dosprint.ui.theme.DosPrintDimens

@Composable
fun DocumentsScreen(
    state: DocumentsUiState,
    onAction: (DocumentsAction) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
    ) {
        if (state.errorMessage != null) {
            ErrorCard(
                message = stringResource(R.string.import_failed_title),
                detail = FailedOperation(
                    operation = stringResource(R.string.import_failed_title),
                    detail = stringResource(R.string.import_failed_body, state.failedUris.size) +
                        state.failedUris.take(MAX_LISTED_FAILURES).joinToString("\n") { " - $it" },
                ),
                onDismiss = { onAction(DocumentsAction.ClearError) },
                onViewLogs = { onAction(DocumentsAction.OpenLogs) },
            )
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(DocumentsAction.Search(it)) },
            label = { Text(stringResource(R.string.files_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = SEARCH_LABEL },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentFilter.entries.forEach { filter ->
                FilterPill(
                    label = filter.label(),
                    selected = state.filter == filter,
                    onClick = { onAction(DocumentsAction.Filter(filter)) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
        ) {
            OutlinedButton(
                onClick = { onAction(DocumentsAction.BrowseFiles) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(DosPrintDimens.iconSmall),
                )
                Text(
                    text = stringResource(R.string.home_pick_files),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(
                onClick = { onAction(DocumentsAction.SelectAllVisible) },
                enabled = state.visibleDocuments.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        if (state.allVisibleSelected) R.string.files_clear_selection else R.string.files_select_all,
                    ),
                )
            }
        }

        if (state.isLoading) {
            LoadingState(label = stringResource(R.string.cd_job_progress))
        }

        if (state.visibleDocuments.isEmpty() && !state.isLoading) {
            EmptyState(
                title = stringResource(R.string.files_empty),
                body = stringResource(R.string.home_no_documents_hint),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
        ) {
            items(state.visibleDocuments, key = { it.uri }) { document ->
                DocumentSelectRow(
                    document = document,
                    isSelected = document.uri in state.selectedUris,
                    onToggleSelection = { onAction(DocumentsAction.ToggleSelection(document.uri)) },
                    onToggleFavorite = { onAction(DocumentsAction.ToggleFavorite(document.uri)) },
                )
            }
        }

        if (state.canContinue) {
            Button(
                onClick = { onAction(DocumentsAction.Continue) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = CONTINUE_LABEL },
            ) {
                Text(
                    text = stringResource(
                        R.string.files_selected_count,
                        state.selectedUris.size,
                    ),
                )
                Text(
                    text = stringResource(R.string.files_continue),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DocumentSelectRow(
    document: PrintDocument,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = document.displayName },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
            modifier = Modifier.semantics {
                contentDescription = if (isSelected) SELECTED_LABEL else SELECT_LABEL
            },
        )
        DocumentIcon(
            kind = document.format.kind,
            description = null,
            modifier = Modifier.size(DosPrintDimens.iconMedium),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = document.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
            Text(
                text = document.detailLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(DosPrintDimens.minTouchTarget),
        ) {
            Icon(
                imageVector = if (document.isFavorite) Icons.Filled.Star else Icons.Outlined.FavoriteBorder,
                contentDescription = if (document.isFavorite) {
                    REMOVE_FAVORITE_LABEL
                } else {
                    ADD_FAVORITE_LABEL
                },
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = if (selected) 6.dp else 0.dp),
        )
    }
}

@Composable
private fun DocumentFilter.label(): String = when (this) {
    DocumentFilter.ALL -> stringResource(R.string.files_filter_all)
    DocumentFilter.IMAGE -> stringResource(R.string.files_filter_image)
    DocumentFilter.DOCUMENT -> stringResource(R.string.files_filter_document)
    DocumentFilter.TEXT -> stringResource(R.string.files_filter_text)
}

@Composable
private fun PrintDocument.detailLabel(): String {
    val extension = format.extension.uppercase()
    val size = com.dosprint.core.ByteFormatter.format(sizeBytes.coerceAtLeast(0))
    val pages = detectedPageCount
    return if (pages != null) {
        "$extension · $size · $pages"
    } else {
        "$extension · $size"
    }
}

private const val SEARCH_LABEL = "Search documents"
private const val CONTINUE_LABEL = "Continue to print setup"
private const val SELECTED_LABEL = "Selected"
private const val SELECT_LABEL = "Select"
private const val ADD_FAVORITE_LABEL = "Add to favorites"
private const val REMOVE_FAVORITE_LABEL = "Remove from favorites"

private const val MAX_LISTED_FAILURES = 8
