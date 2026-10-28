package com.dosprint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dosprint.R
import com.dosprint.domain.model.PrintDocument
import com.dosprint.ui.HomeAction
import com.dosprint.ui.HomeUiState
import com.dosprint.ui.common.ChipTone
import com.dosprint.ui.common.ListItemRow
import com.dosprint.ui.common.ListSectionHeader
import com.dosprint.ui.common.StatusChip
import com.dosprint.ui.components.DosPrintIcons
import com.dosprint.ui.components.icon
import com.dosprint.ui.theme.DosPrintDimens

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Always the same shell, so both flows are reachable from a fresh install.
    HomeListBody(
        state = state,
        onAction = onAction,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun HomeListBody(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
    ) {
        item(key = "header") { HomeHeader(printerName = state.defaultPrinter?.displayName) }
        item(key = "flows") {
            FlowEntries(state = state, onAction = onAction)
        }
        if (!state.isPrintServiceAvailable) {
            item(key = "no-service") {
                StatusChip(
                    text = stringResource(R.string.error_no_print_service),
                    tone = ChipTone.ERROR,
                )
            }
        }
        if (state.favoriteDocuments.isNotEmpty()) {
            item(key = "favorites-header") {
                ListSectionHeader(title = stringResource(R.string.home_favorite_documents))
            }
            items(
                state.favoriteDocuments.take(MaxVisibleDocuments),
                key = { "fav-${it.uri}" },
            ) { document ->
                DocumentRow(
                    document = document,
                    onClick = { onAction(HomeAction.DocumentClicked(document)) },
                    onToggleFavorite = { onAction(HomeAction.ToggleFavorite(document.uri, it)) },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(printerName: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DosPrintDimens.space8, bottom = DosPrintDimens.space4)
            .semantics { heading() },
    ) {
        Text(
            text = stringResource(R.string.home_greeting_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(DosPrintDimens.space4))
        Text(
            text = printerName?.let { stringResource(R.string.home_destination_label, it) }
                ?: stringResource(R.string.home_no_printers_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The two print flows, given equal weight.
 *
 * Documents and photos want different things from the app: a document needs pages, a photo needs a
 * sheet layout. Splitting them at the entry point means neither flow inherits options that do not
 * apply to it, which is what happens when everything sits behind one generic picker.
 */
@Composable
private fun FlowEntries(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap)) {
        Text(
            text = stringResource(R.string.home_choose_flow),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowEntry(
            icon = DosPrintIcons.Image,
            title = stringResource(R.string.home_flow_photos),
            subtitle = stringResource(R.string.home_flow_photos_subtitle),
            onClick = { onAction(HomeAction.PickImages) },
        )
        FlowEntry(
            icon = DosPrintIcons.Document,
            title = stringResource(R.string.home_flow_documents),
            subtitle = stringResource(R.string.home_flow_documents_subtitle),
            onClick = { onAction(HomeAction.PickDocuments) },
        )
        FlowEntry(
            icon = DosPrintIcons.Printer,
            title = stringResource(R.string.nav_printers),
            subtitle = state.defaultPrinter?.displayName
                ?: stringResource(R.string.home_no_printers_body),
            onClick = { onAction(HomeAction.OpenPrinters) },
        )
    }
}

@Composable
private fun FlowEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DosPrintDimens.tileHeight)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(DosPrintDimens.listItemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
        ) {
            Box(
                modifier = Modifier
                    .size(DosPrintIcons.size40)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(DosPrintDimens.iconMedium),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DosPrintDimens.iconMedium),
            )
        }
    }
}

@Composable
private fun DocumentRow(
    document: PrintDocument,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
) {
    ListItemRow(modifier = Modifier.clickable(onClick = onClick)) {
        Icon(
            imageVector = document.format.kind.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(DosPrintDimens.iconMedium),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = document.summaryLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = { onToggleFavorite(!document.isFavorite) }) {
            Icon(
                imageVector = if (document.isFavorite) {
                    Icons.Filled.Star
                } else {
                    Icons.Outlined.FavoriteBorder
                },
                contentDescription = null,
                tint = if (document.isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private const val MaxVisibleDocuments = 6
