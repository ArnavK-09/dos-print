package com.dosprint.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dosprint.R
import com.dosprint.ui.LivePreviewState
import com.dosprint.ui.theme.DosPrintDimens
import kotlin.math.max

/**
 * A live sheet render beside the settings, so the result of every change is visible immediately
 * rather than behind a navigation step. The panel reports its own width so the view model can size
 * the raster, and it never claims to be rendering when it has nothing to show.
 */
@Composable
fun LivePreviewPanel(
    state: LivePreviewState,
    onPreviewSurface: (Int) -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewLabel = stringResource(R.string.setup_live_preview)
    val inspectLabel = stringResource(R.string.setup_live_preview_inspect)
    val paperBackdrop = MaterialTheme.colorScheme.onSurface.copy(alpha = PreviewBackdropAlpha)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DosPrintDimens.sectionCorner),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(DosPrintDimens.listItemPadding),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = previewLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.sheetCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.setup_preview_sheet_of,
                            max(1, state.sheetIndex + 1),
                            state.sheetCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PreviewMinHeight, max = PreviewMaxHeight)
                    .clip(RoundedCornerShape(DosPrintDimens.controlCorner))
                    .background(paperBackdrop)
                    .onSizeChanged { onPreviewSurface(it.width) }
                    .clickable(
                        enabled = state.hasImage,
                        role = Role.Button,
                        onClickLabel = inspectLabel,
                        onClick = onOpenPreview,
                    )
                    .semantics {
                        contentDescription = previewLabel
                        if (state.hasImage) {
                            role = Role.Button
                            onClick(label = inspectLabel, action = null)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val image = state.image
                when {
                    image != null && !image.isRecycled -> Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = previewLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = PreviewMaxHeight)
                            .padding(DosPrintDimens.space8)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                    state.isRendering -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(DosPrintDimens.space24),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.preview_generating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
                        modifier = Modifier.padding(DosPrintDimens.space16),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(DosPrintDimens.iconMedium),
                        )
                        Text(
                            text = stringResource(R.string.setup_live_preview_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val PreviewBackdropAlpha = 0.18f
private val PreviewMinHeight = 190.dp
private val PreviewMaxHeight = 320.dp
