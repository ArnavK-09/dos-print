package com.dosprint.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dosprint.R
import com.dosprint.ui.PreviewAction
import com.dosprint.ui.PreviewUiState
import com.dosprint.ui.common.EmptyState
import com.dosprint.ui.components.DosPrintIcons
import com.dosprint.ui.components.UniformButton
import com.dosprint.ui.components.UniformButtonRow
import com.dosprint.ui.theme.DosPrintDimens
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PreviewScreen(
    state: PreviewUiState,
    onAction: (PreviewAction) -> Unit,
    onSurfaceWidth: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableFloatStateOf(InitialZoom) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val swipeThresholdPx = with(LocalDensity.current) { PageSwipeThreshold.toPx() }
    LaunchedEffect(state.currentSheetIndex) {
        zoom = InitialZoom
        pan = Offset.Zero
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
    ) {
        PreviewToolbar(
            state = state,
            zoom = zoom,
            onZoomIn = { zoom = (zoom * ZoomStep).coerceIn(MinZoom, MaxZoom) },
            onZoomOut = { zoom = (zoom / ZoomStep).coerceIn(MinZoom, MaxZoom) },
            onReset = {
                zoom = InitialZoom
                pan = Offset.Zero
            },
        )
        if (state.isRendering) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { onSurfaceWidth(it.width) },
        ) {
            when {
                state.errorMessage != null -> EmptyState(
                    title = stringResource(R.string.preview_failed),
                    body = stringResource(R.string.error_generic),
                    action = {
                        TextButton(onClick = { onAction(PreviewAction.Render) }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                !state.hasSheets && state.isRendering -> PreviewLoading()
                !state.hasSheets -> EmptyState(
                    title = stringResource(R.string.preview_generating),
                    body = stringResource(R.string.preview_generating_hint),
                )
                else -> SheetViewer(
                    state = state,
                    zoom = zoom,
                    pan = pan,
                    onZoomPan = { nextZoom, nextPan ->
                        zoom = nextZoom
                        pan = nextPan
                    },
                    onPageSwipe = { delta ->
                        onAction(if (delta < 0) PreviewAction.NextSheet else PreviewAction.PreviousSheet)
                    },
                    swipeThresholdPx = swipeThresholdPx,
                    onReset = {
                        zoom = InitialZoom
                        pan = Offset.Zero
                    },
                )
            }
        }
        if (state.totalSheets > 1) {
            SheetNavigator(state = state, onAction = onAction)
        }
        PreviewSummaryBar(state = state, onAction = onAction)
    }
}

@Composable
private fun PreviewToolbar(
    state: PreviewUiState,
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DosPrintDimens.controlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.preview_page_of,
                    state.currentSheetIndex + 1,
                    state.totalSheets.coerceAtLeast(1),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ZoomControls(
            zoom = zoom,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onReset = onReset,
        )
    }
}

@Composable
private fun ZoomControls(
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onZoomOut,
            enabled = zoom > MinZoom + ZoomEpsilon,
            modifier = Modifier.semantics { contentDescription = ZoomOutLabel },
        ) {
            Icon(
                imageVector = DosPrintIcons.Minus,
                contentDescription = null,
                modifier = Modifier.size(DosPrintDimens.iconMedium),
            )
        }
        Text(
            text = "${(zoom * ZoomPercentBase).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier
                .width(ZoomLabelWidth)
                .clickable(onClick = onReset)
                .semantics { contentDescription = ResetZoomLabel },
        )
        IconButton(
            onClick = onZoomIn,
            enabled = zoom < MaxZoom - ZoomEpsilon,
            modifier = Modifier.semantics { contentDescription = ZoomInLabel },
        ) {
            Icon(
                imageVector = DosPrintIcons.Plus,
                contentDescription = null,
                modifier = Modifier.size(DosPrintDimens.iconMedium),
            )
        }
    }
}

@Composable
private fun PreviewLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(DosPrintDimens.space40))
            Text(
                text = stringResource(R.string.preview_generating),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = DosPrintDimens.fieldGap),
            )
        }
    }
}

@Composable
private fun SheetViewer(
    state: PreviewUiState,
    zoom: Float,
    pan: Offset,
    onZoomPan: (Float, Offset) -> Unit,
    onPageSwipe: (Int) -> Unit,
    swipeThresholdPx: Float,
    onReset: () -> Unit,
) {
    val sheet = state.currentSheet ?: run {
        PreviewLoading()
        return
    }
    val currentZoom = rememberUpdatedState(zoom)
    val currentPan = rememberUpdatedState(pan)
    val currentOnZoomPan = rememberUpdatedState(onZoomPan)
    val currentOnPageSwipe = rememberUpdatedState(onPageSwipe)
    val insetPx = with(LocalDensity.current) { DosPrintDimens.space16.toPx() }
    val backdrop = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(DosPrintDimens.controlCorner))
            .background(backdrop)
            .pointerInput(sheet.index, swipeThresholdPx, insetPx) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var swipeDelta = Offset.Zero
                    var navigated = false
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }
                        if (pointers.isEmpty()) {
                            continue
                        }
                        val centroid = Offset(
                            pointers.sumOf { it.position.x.toDouble() }.toFloat() / pointers.size,
                            pointers.sumOf { it.position.y.toDouble() }.toFloat() / pointers.size,
                        )
                        val previousCentroid = Offset(
                            pointers.sumOf { it.previousPosition.x.toDouble() }.toFloat() / pointers.size,
                            pointers.sumOf { it.previousPosition.y.toDouble() }.toFloat() / pointers.size,
                        )
                        val panChange = centroid - previousCentroid
                        val zoomChange = if (pointers.size > 1) {
                            val currentRadius = pointers.sumOf { (it.position - centroid).getDistance().toDouble() }
                                .toFloat() / pointers.size
                            val previousRadius = pointers.sumOf {
                                (it.previousPosition - previousCentroid).getDistance().toDouble()
                            }.toFloat() / pointers.size
                            if (previousRadius > 0f) currentRadius / previousRadius else 1f
                        } else {
                            1f
                        }
                        val oldZoom = currentZoom.value
                        val nextZoom = (oldZoom * zoomChange).coerceIn(MinZoom, MaxZoom)
                        if (pointers.size == 1 && oldZoom <= MinZoom + ZoomEpsilon && nextZoom <= MinZoom + ZoomEpsilon) {
                            swipeDelta += panChange
                            if (!navigated &&
                                abs(swipeDelta.x) >= swipeThresholdPx &&
                                abs(swipeDelta.x) > abs(swipeDelta.y) * SwipeAxisRatio
                            ) {
                                currentOnPageSwipe.value(if (swipeDelta.x < 0f) -1 else 1)
                                navigated = true
                            }
                            if (abs(panChange.x) > abs(panChange.y) * SwipeAxisRatio || navigated) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else {
                            val scaleFactor = nextZoom / oldZoom
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val focalOffset = centroid - center
                            val desiredPan = currentPan.value * scaleFactor + focalOffset * (1f - scaleFactor) + panChange
                            val contentWidth = (size.width - insetPx * 2f).coerceAtLeast(1f)
                            val contentHeight = (size.height - insetPx * 2f).coerceAtLeast(1f)
                            val fitScale = min(contentWidth / sheet.widthPx, contentHeight / sheet.heightPx)
                            val pageWidth = sheet.widthPx * fitScale * nextZoom
                            val pageHeight = sheet.heightPx * fitScale * nextZoom
                            val limitX = ((pageWidth - size.width) / 2f).coerceAtLeast(0f)
                            val limitY = ((pageHeight - size.height) / 2f).coerceAtLeast(0f)
                            currentOnZoomPan.value(
                                nextZoom,
                                Offset(
                                    desiredPan.x.coerceIn(-limitX, limitX),
                                    desiredPan.y.coerceIn(-limitY, limitY),
                                ),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(sheet.index) {
                detectTapGestures(onDoubleTap = { onReset() })
            }
            .semantics { contentDescription = PreviewCanvasLabel },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = sheet.image.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_document_thumbnail, SheetLabel),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(DosPrintDimens.space16)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
        )
    }
}

@Composable
private fun SheetNavigator(
    state: PreviewUiState,
    onAction: (PreviewAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
    ) {
        OutlinedButton(
            onClick = { onAction(PreviewAction.PreviousSheet) },
            enabled = state.currentSheetIndex > 0 && !state.isRendering,
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(DosPrintDimens.space4))
                Text(stringResource(R.string.preview_previous_sheet), maxLines = 1)
            }
        }
        Text(
            text = stringResource(
                R.string.preview_page_of,
                state.currentSheetIndex + 1,
                state.totalSheets,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        OutlinedButton(
            onClick = { onAction(PreviewAction.NextSheet) },
            enabled = state.currentSheetIndex + 1 < state.totalSheets && !state.isRendering,
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.preview_next_sheet), maxLines = 1)
                Spacer(Modifier.width(DosPrintDimens.space4))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun PreviewSummaryBar(
    state: PreviewUiState,
    onAction: (PreviewAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(DosPrintDimens.sectionCorner),
    ) {
        Column(
            modifier = Modifier.padding(DosPrintDimens.listItemPadding),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        ) {
            Text(
                text = state.settings.summaryLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            UniformButtonRow(
                buttons = listOf(
                    UniformButton(
                        label = stringResource(R.string.action_back),
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { onAction(PreviewAction.BackToSetup) },
                    ),
                    UniformButton(
                        label = stringResource(R.string.action_edit),
                        icon = Icons.Filled.Edit,
                        onClick = { onAction(PreviewAction.OpenSetup) },
                    ),
                    UniformButton(
                        label = stringResource(R.string.setup_print),
                        icon = DosPrintIcons.Printer,
                        onClick = { onAction(PreviewAction.Print) },
                        enabled = state.canPrint,
                        emphasized = true,
                    ),
                ),
            )
        }
    }
}

private const val ZoomPercentBase = 100
private const val ZoomEpsilon = 0.01f
private const val ZoomStep = 1.25f
private const val InitialZoom = 1f
private const val SheetLabel = "sheet"
private const val PreviewCanvasLabel = "Sheet preview canvas"
private const val ZoomInLabel = "Zoom in"
private const val ZoomOutLabel = "Zoom out"
private const val ResetZoomLabel = "Reset zoom"
private const val MinZoom = 1f
private const val MaxZoom = 4f
private val PageSwipeThreshold = 72.dp
private const val SwipeAxisRatio = 1.2f
private val ZoomLabelWidth = 56.dp
