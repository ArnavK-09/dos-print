package com.dosprint.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dosprint.R
import com.dosprint.ui.LivePreviewState
import com.dosprint.ui.theme.DosPrintDimens
import com.dosprint.ui.theme.isDarkTheme
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
    val paperBackdrop = if (MaterialTheme.colorScheme.isDarkTheme()) {
        Color.Black.copy(alpha = PreviewBackdropDarkAlpha)
    } else {
        Color.Black.copy(alpha = PreviewBackdropLightAlpha)
    }
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
                    state.isRendering -> {
                        val transition = rememberInfiniteTransition(label = "livePreviewSkeleton")
                        val alpha by transition.animateFloat(
                            initialValue = SkeletonAlphaMin,
                            targetValue = SkeletonAlphaMax,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = SkeletonPulseMillis, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "livePreviewSkeletonAlpha",
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
                        ) {
                            Surface(
                                modifier = Modifier.size(SkeletonPageWidth, SkeletonPageHeight),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    modifier = Modifier.padding(DosPrintDimens.space8),
                                    verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space6),
                                ) {
                                    LivePreviewBar(widthFraction = 0.62f, height = 10.dp, alpha = alpha)
                                    LivePreviewBar(widthFraction = 1f, height = 6.dp, alpha = alpha * SkeletonLineAlphaScale)
                                    LivePreviewBar(widthFraction = 0.84f, height = 6.dp, alpha = alpha * SkeletonLineAlphaScale)
                                    LivePreviewBar(widthFraction = 1f, height = 6.dp, alpha = alpha * SkeletonLineAlphaScale)
                                    LivePreviewBar(widthFraction = 0.72f, height = 6.dp, alpha = alpha * SkeletonLineAlphaScale)
                                    LivePreviewBar(widthFraction = 1f, height = 6.dp, alpha = alpha * SkeletonLineAlphaScale)
                                }
                            }
                            Text(
                                text = stringResource(R.string.preview_generating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
            state.errorMessage?.let {
                Text(
                    text = stringResource(R.string.preview_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val PreviewBackdropDarkAlpha = 0.62f
private const val PreviewBackdropLightAlpha = 0.05f
private val PreviewMinHeight = 190.dp
private val PreviewMaxHeight = 320.dp
private val SkeletonPageWidth = 96.dp
private val SkeletonPageHeight = 136.dp
private const val SkeletonAlphaMin = 0.12f
private const val SkeletonAlphaMax = 0.3f
private const val SkeletonLineAlphaScale = 0.75f
private const val SkeletonPulseMillis = 900

@Composable
private fun LivePreviewBar(widthFraction: Float, height: Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
    )
}
