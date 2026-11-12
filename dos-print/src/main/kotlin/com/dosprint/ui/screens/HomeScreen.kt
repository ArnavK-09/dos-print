package com.dosprint.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosprint.R
import com.dosprint.domain.model.PrintDocument
import com.dosprint.ui.HomeAction
import com.dosprint.ui.HomeUiState
import com.dosprint.ui.common.ListItemRow
import com.dosprint.ui.common.ListSectionHeader
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
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = HomeContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "landing") {
                HomeLanding(
                    onAction = onAction,
                    modifier = Modifier.fillParentMaxHeight(),
                )
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
}

@Composable
private fun HomeLanding(
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DosPrintDimens.space24),
    ) {
        val compactHeight = maxHeight < CompactHeightBreakpoint
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = HomeColumnMaxWidth)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactHeight) DosPrintDimens.space20 else DosPrintDimens.space32),
            ) {
                HomeIntro(showBody = !compactHeight)
                FlowEntries(
                    onAction = onAction,
                    compact = compactHeight,
                )
            }
        }
    }
}

@Composable
private fun HomeIntro(showBody: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space8),
        modifier = Modifier.semantics { heading() },
    ) {
        Text(
            text = stringResource(R.string.home_greeting_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_choose_flow),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (showBody) {
            Text(
                text = stringResource(R.string.home_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = HomeBodyMaxWidth),
            )
        }
    }
}

@Composable
private fun FlowEntries(
    onAction: (HomeAction) -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.space16),
    ) {
        FlowEntry(
            icon = DosPrintIcons.Image,
            title = stringResource(R.string.home_flow_photos),
            onClick = { onAction(HomeAction.PickImages) },
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        FlowEntry(
            icon = DosPrintIcons.Document,
            title = stringResource(R.string.home_flow_documents),
            onClick = { onAction(HomeAction.PickDocuments) },
            compact = compact,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FlowEntry(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(if (compact) CompactActionTileHeight else ActionTileHeight)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(DosPrintDimens.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(DosPrintDimens.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) CompactIconSize else ActionIconSize)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(if (compact) DosPrintDimens.iconMedium else DosPrintDimens.iconLarge),
                )
            }
            Spacer(modifier = Modifier.height(if (compact) DosPrintDimens.space8 else DosPrintDimens.space16))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
private val HomeContentMaxWidth = 560.dp
private val HomeColumnMaxWidth = 440.dp
private val HomeBodyMaxWidth = 340.dp
private val CompactHeightBreakpoint = 460.dp
private val ActionTileHeight = 168.dp
private val CompactActionTileHeight = 124.dp
private val ActionIconSize = 60.dp
private val CompactIconSize = 44.dp
