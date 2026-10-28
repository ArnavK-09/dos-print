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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = HomeContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= WideHomeBreakpoint) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DosPrintDimens.space24),
                horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.space32),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space24),
                ) {
                    HomeIntro()
                    PrintSheetArtwork(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WideArtworkHeight),
                    )
                }
                FlowEntries(
                    onAction = onAction,
                    modifier = Modifier.weight(0.9f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DosPrintDimens.space4),
            ) {
                HomeIntro()
                Spacer(modifier = Modifier.weight(1f))
                PrintSheetArtwork(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CompactArtworkHeight),
                )
                Spacer(modifier = Modifier.weight(1f))
                FlowEntries(onAction = onAction)
                Spacer(modifier = Modifier.height(DosPrintDimens.space8))
            }
        }
    }
}

@Composable
private fun HomeIntro() {
    Column(
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space8),
        modifier = Modifier.semantics { heading() },
    ) {
        Text(
            text = stringResource(R.string.home_choose_flow),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlowEntries(
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
    ) {
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
            .heightIn(min = ActionTileHeight)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(DosPrintDimens.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(DosPrintDimens.listItemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
        ) {
            Box(
                modifier = Modifier
                    .size(ActionIconSize)
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PrintSheetArtwork(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = (-38).dp)
                .size(152.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 34.dp, y = 38.dp)
                .size(126.dp)
                .clip(CircleShape)
                .background(colors.tertiaryContainer),
        )
        ArtworkSheet(
            accent = colors.secondaryContainer,
            modifier = Modifier
                .offset(x = (-62).dp, y = 8.dp)
                .rotate(-11f)
                .size(86.dp, 116.dp),
        )
        ArtworkSheet(
            accent = colors.tertiaryContainer,
            modifier = Modifier
                .offset(x = 62.dp, y = 8.dp)
                .rotate(11f)
                .size(86.dp, 116.dp),
        )
        ArtworkSheet(
            accent = colors.primaryContainer,
            modifier = Modifier.size(96.dp, 132.dp),
        )
    }
}

@Composable
private fun ArtworkSheet(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(DosPrintDimens.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(DosPrintDimens.space8),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space6),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(accent),
            )
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 2) 0.68f else 1f)
                        .height(DosPrintDimens.space4)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
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
private val HomeContentMaxWidth = 1040.dp
private val WideHomeBreakpoint = 680.dp
private val CompactArtworkHeight = 164.dp
private val WideArtworkHeight = 236.dp
private val ActionTileHeight = 92.dp
private val ActionIconSize = 44.dp
