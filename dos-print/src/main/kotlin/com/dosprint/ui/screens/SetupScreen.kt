package com.dosprint.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dosprint.R
import com.dosprint.core.ErrorCard
import com.dosprint.domain.model.ColorMode
import com.dosprint.domain.model.DuplexMode
import com.dosprint.domain.model.ImageGap
import com.dosprint.domain.model.ImageGridOrientation
import com.dosprint.domain.model.ImagePageSettings
import com.dosprint.domain.model.ImageScaling
import com.dosprint.domain.model.ManualDuplex
import com.dosprint.domain.model.MarginPreset
import com.dosprint.domain.model.PageOrder
import com.dosprint.domain.model.PagePosition
import com.dosprint.domain.model.PageRotation
import com.dosprint.domain.model.PageSelectionMode
import com.dosprint.domain.model.PaperSize
import com.dosprint.domain.model.PhotoBorder
import com.dosprint.domain.model.PhotoSizePreset
import com.dosprint.domain.model.PrintResolution
import com.dosprint.domain.model.ScalingMode
import com.dosprint.domain.model.WatermarkStyle
import com.dosprint.ui.SetupAction
import com.dosprint.ui.SetupUiState
import com.dosprint.ui.common.ChipTone
import com.dosprint.ui.common.DosPrintSection
import com.dosprint.ui.common.StatusChip
import com.dosprint.ui.common.WarningBanner
import com.dosprint.ui.components.CountStepper
import com.dosprint.ui.components.DosPrintIcons
import com.dosprint.ui.components.LivePreviewPanel
import com.dosprint.ui.components.PaperChoicesDropdown
import com.dosprint.ui.components.SettingRow
import com.dosprint.ui.components.UniformButton
import com.dosprint.ui.components.UniformButtonRow
import com.dosprint.ui.components.UniformToggleRow
import com.dosprint.ui.components.ValueDropdown
import com.dosprint.ui.setupMessage
import com.dosprint.ui.theme.DosPrintDimens
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SetupScreen(
    state: SetupUiState,
    onAction: (SetupAction) -> Unit,
    onPreviewSurface: (Int) -> Unit,
    onImagePage: (com.dosprint.domain.model.ImagePageSettings) -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var paperExpanded by rememberSaveable { mutableStateOf(false) }
    val imageDocumentsKey = state.documents.map { it.key }
    var imageLayoutExpanded by remember(state.isImageMode, imageDocumentsKey) {
        mutableStateOf(state.isImageMode)
    }
    var sizeLayoutExpanded by rememberSaveable { mutableStateOf(false) }
    var pagesExpanded by rememberSaveable { mutableStateOf(false) }
    var qualityExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val columns = if (maxWidth >= DosPrintDimens.maxReadingWidth) 2 else 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .widthIn(max = DosPrintDimens.maxReadingWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
        ) {
            item(
                key = "summary",
                span = { GridItemSpan(if (columns == 1) maxLineSpan else 1) },
            ) { JobSummaryCard(state) }
            item(
                key = "live-preview",
                span = { GridItemSpan(if (columns == 1) maxLineSpan else 1) },
            ) {
                LivePreviewPanel(
                    state = state.livePreview,
                    onPreviewSurface = onPreviewSurface,
                    onOpenPreview = { onAction(SetupAction.OpenPreview) },
                )
            }
            state.previewError?.let { key ->
                item(key = "preview-error", span = { GridItemSpan(maxLineSpan) }) {
                    ErrorCard(
                        message = setupMessage(key) ?: key,
                        onRetry = { onAction(SetupAction.RetryPreview) },
                        onDismiss = { onAction(SetupAction.ClearPreviewError) },
                        onViewLogs = { onAction(SetupAction.OpenLogs) },
                    )
                }
            }
            item(key = "warnings", span = { GridItemSpan(maxLineSpan) }) {
                WarningBanner(warnings = state.warnings)
            }
            if (state.isImageMode) {
                item(key = "image-layout") {
                    ImageLayoutCard(
                        state = state,
                        onChange = onImagePage,
                        onMarginsChange = { onAction(SetupAction.SelectMarginPreset(it)) },
                        expanded = imageLayoutExpanded,
                        onExpandedChange = { imageLayoutExpanded = it },
                    )
                }
            } else {
                item(key = "size-layout") {
                    SizeAndLayoutCard(
                        state = state,
                        onAction = onAction,
                        expanded = sizeLayoutExpanded,
                        onExpandedChange = { sizeLayoutExpanded = it },
                    )
                }
                item(key = "pages") {
                    PageSelectionCard(
                        state = state,
                        onAction = onAction,
                        expanded = pagesExpanded,
                        onExpandedChange = { pagesExpanded = it },
                    )
                }
            }
            item(key = "paper") {
                PaperCard(
                    state = state,
                    onAction = onAction,
                    expanded = paperExpanded,
                    onExpandedChange = { paperExpanded = it },
                )
            }
            item(key = "quality") {
                QualityCard(
                    state = state,
                    onAction = onAction,
                    expanded = qualityExpanded,
                    onExpandedChange = { qualityExpanded = it },
                )
            }
            item(key = "advanced") {
                AdvancedCard(
                    state = state,
                    onAction = onAction,
                    expanded = advancedExpanded,
                    onExpandedChange = { advancedExpanded = it },
                )
            }
            item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
                ActionsCard(state, onAction)
            }
        }
    }
}

@Composable
private fun JobSummaryCard(state: SetupUiState) {
    DosPrintSection(
        title = stringResource(R.string.setup_title),
        subtitle = summaryLine(state),
        icon = DosPrintIcons.Printer,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap)) {
            StatusChip(
                text = stringResource(R.string.setup_estimate, state.estimatedSheets),
                tone = ChipTone.POSITIVE,
            )
            StatusChip(
                text = stringResource(
                    R.string.setup_pages_selected,
                    state.selectedPages,
                    state.totalPages,
                ),
                tone = ChipTone.NEUTRAL,
            )
        }
        Text(
            text = state.settings.summaryLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaperCard(
    state: SetupUiState,
    onAction: (SetupAction) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    DosPrintSection(
        title = stringResource(R.string.setup_paper_size),
        subtitle = "${state.settings.effectivePaper.label} · ${if (state.settings.landscape) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT}",
        icon = DosPrintIcons.Document,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        PaperChoicesDropdown(
            choices = state.paperChoices,
            selectedId = state.settings.paperSize.id,
            onSelect = { onAction(SetupAction.SelectPaperSize(it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.setup_orientation),
            options = listOf(false, true),
            selected = state.settings.landscape,
            optionLabel = { if (it) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT },
            onSelect = { onAction(SetupAction.ToggleLandscape(it)) },
        )
        SettingRow(
            label = stringResource(if (state.isImageMode) R.string.image_copies else R.string.setup_copies),
        ) {
            CountStepper(
                value = state.settings.copies,
                onValueChange = { onAction(SetupAction.SetCopies(it)) },
                label = COPIES_LABEL,
            )
        }
    }
}

@Composable
private fun ImageLayoutCard(
    state: SetupUiState,
    onChange: (com.dosprint.domain.model.ImagePageSettings) -> Unit,
    onMarginsChange: (MarginPreset) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val page = state.imagePage
    val manualPhotoSize = page.photoSize == PhotoSizePreset.CUSTOM
    val photoScalingLabel = if (manualPhotoSize) ImageScaling.STRETCH_TO_FIT.label else page.scaling.label
    val context = LocalContext.current
    val paper = state.settings.effectivePaper
    val printGrid = state.settings.layoutGrid
    val maxPhotoWidthInches = paper.widthInches * (1f - 2f * printGrid.marginFraction)
    val maxPhotoHeightInches = paper.heightInches * (1f - 2f * printGrid.marginFraction)
    val grid = page.layoutGridFor(
        pageAspect = state.settings.sheetAspect,
        pageWidthInches = paper.widthInches,
        pageHeightInches = paper.heightInches,
        marginFraction = printGrid.marginFraction,
        cellBorderFraction = state.settings.cellBorderFraction,
    )
    val availableImageCounts = supportedImageCounts(
        page = page,
        pageAspect = state.settings.sheetAspect,
        pageWidthInches = paper.widthInches,
        pageHeightInches = paper.heightInches,
        marginFraction = printGrid.marginFraction,
        cellBorderFraction = state.settings.cellBorderFraction,
    )
    val borderSummary = stringResource(R.string.image_border_status, page.border.label)
    val imageCountLabel: (Int) -> String = { count ->
        val optionPage = page.withImagesPerPage(count, state.settings.sheetAspect)
        val optionGrid = optionPage.layoutGridFor(
            pageAspect = state.settings.sheetAspect,
            pageWidthInches = paper.widthInches,
            pageHeightInches = paper.heightInches,
            marginFraction = printGrid.marginFraction,
            cellBorderFraction = state.settings.cellBorderFraction,
        ).copy(
            marginFraction = printGrid.marginFraction,
            cellBorderFraction = state.settings.cellBorderFraction,
        )
        if (page.photoSize == PhotoSizePreset.AUTO) {
            val cell = optionGrid.slotRect(0, paper.widthMils.toFloat(), paper.heightMils.toFloat())
            val cellWidthInches = (cell[2] - cell[0]) / PaperSize.MILLIS_PER_INCH
            val cellHeightInches = (cell[3] - cell[1]) / PaperSize.MILLIS_PER_INCH
            context.getString(
                R.string.image_count_option,
                count,
                optionGrid.columns,
                optionGrid.rows,
                String.format(Locale.getDefault(), "%.1f", cellWidthInches),
                String.format(Locale.getDefault(), "%.1f", cellHeightInches),
            )
        } else {
            context.getString(
                R.string.image_count_option_fixed,
                count,
                optionGrid.columns,
                optionGrid.rows,
                formatInches(page.photoWidthInches),
                formatInches(page.photoHeightInches),
            )
        }
    }
    DosPrintSection(
        title = stringResource(R.string.image_section_title),
        subtitle = if (page.usesGrid) {
            "$borderSummary · ${state.settings.marginPreset.label} · ${stringResource(R.string.image_grid_size_summary, page.imagesPerPage)} · ${grid.columns} × ${grid.rows} · $photoScalingLabel"
        } else {
            "$borderSummary · ${state.settings.marginPreset.label} · 1 per sheet · $photoScalingLabel"
        },
        icon = DosPrintIcons.Image,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SettingDropdown(
            label = stringResource(R.string.setup_margins),
            options = MarginPreset.entries,
            selected = state.settings.marginPreset,
            optionLabel = { it.label },
            onSelect = onMarginsChange,
        )
        SettingDropdown(
            label = stringResource(R.string.image_border),
            options = PhotoBorder.entries,
            selected = page.border,
            optionLabel = { it.label },
            onSelect = { onChange(page.copy(border = it)) },
        )
        if (page.border != PhotoBorder.NONE) {
            Text(
                text = stringResource(R.string.image_border_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ManualPhotoSizeToggle(
            checked = manualPhotoSize,
            onCheckedChange = { enabled ->
                onChange(
                    page.copy(
                        photoSize = if (enabled) PhotoSizePreset.CUSTOM else PhotoSizePreset.AUTO,
                    ).withSupportedPhotoCount(
                        pageAspect = state.settings.sheetAspect,
                        pageWidthInches = paper.widthInches,
                        pageHeightInches = paper.heightInches,
                        marginFraction = printGrid.marginFraction,
                        cellBorderFraction = state.settings.cellBorderFraction,
                    ),
                )
            },
        )
        if (manualPhotoSize) {
            CustomPhotoSizeFields(
                widthInches = page.customPhotoWidthInches,
                heightInches = page.customPhotoHeightInches,
                maxWidthInches = maxPhotoWidthInches,
                maxHeightInches = maxPhotoHeightInches,
                onSizeChange = { width, height ->
                    onChange(
                        page.copy(
                            photoSize = PhotoSizePreset.CUSTOM,
                            customPhotoWidthInches = width,
                            customPhotoHeightInches = height,
                        ).withSupportedPhotoCount(
                            pageAspect = state.settings.sheetAspect,
                            pageWidthInches = paper.widthInches,
                            pageHeightInches = paper.heightInches,
                            marginFraction = printGrid.marginFraction,
                            cellBorderFraction = state.settings.cellBorderFraction,
                        ),
                    )
                },
            )
        }
        if (availableImageCounts.isEmpty() && page.photoSize != PhotoSizePreset.AUTO) {
            Text(
                text = stringResource(R.string.image_photo_size_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (availableImageCounts.isNotEmpty()) {
            ValueDropdown(
                label = stringResource(R.string.image_per_page),
                value = imageCountLabel(page.imagesPerPage),
                options = availableImageCounts,
                optionLabel = imageCountLabel,
                onSelect = { onChange(page.withImagesPerPage(it, state.settings.sheetAspect)) },
            )
            Text(
                text = stringResource(R.string.image_per_page_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.image_grid_direction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
        ) {
            ImageGridOrientation.entries.forEach { orientation ->
                ImageGridOrientationOption(
                    orientation = orientation,
                    selected = page.gridOrientation == orientation,
                    label = when (orientation) {
                        ImageGridOrientation.AUTO -> stringResource(R.string.image_arrangement_auto)
                        ImageGridOrientation.SIDE_BY_SIDE -> stringResource(R.string.image_arrangement_side_by_side)
                        ImageGridOrientation.STACKED -> stringResource(R.string.image_arrangement_stacked)
                    },
                    onClick = { onChange(page.copy(gridOrientation = orientation)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.image_grid_direction_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (manualPhotoSize) {
            Text(
                text = stringResource(R.string.image_custom_size_exact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SettingDropdown(
                label = stringResource(R.string.image_scaling),
                options = ImageScaling.entries,
                selected = page.scaling,
                optionLabel = { it.label },
                onSelect = { onChange(page.copy(scaling = it)) },
            )
        }
        SettingDropdown(
            label = stringResource(R.string.image_rotation),
            options = PageRotation.entries,
            selected = page.rotation,
            optionLabel = { it.label },
            onSelect = { onChange(page.copy(rotation = it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.image_position),
            options = PagePosition.entries,
            selected = page.position,
            optionLabel = { it.label },
            onSelect = { onChange(page.copy(position = it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.image_gaps),
            options = ImageGap.entries,
            selected = page.gap,
            optionLabel = { it.label },
            onSelect = { onChange(page.copy(gap = it)) },
        )
        if (page.usesGrid) {
            UniformToggleRow(
                label = stringResource(R.string.image_compact_group),
                summary = stringResource(R.string.image_compact_group_summary),
                checked = page.compactGroup,
                onCheckedChange = { onChange(page.copy(compactGroup = it)) },
            )
        }
    }
}

@Composable
private fun ManualPhotoSizeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DosPrintDimens.controlCorner))
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = DosPrintDimens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.image_manual_size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.image_manual_size_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomPhotoSizeFields(
    widthInches: Float,
    heightInches: Float,
    maxWidthInches: Float,
    maxHeightInches: Float,
    onSizeChange: (Float, Float) -> Unit,
) {
    var widthText by rememberSaveable(maxWidthInches, maxHeightInches) { mutableStateOf(formatInches(widthInches)) }
    var heightText by rememberSaveable(maxWidthInches, maxHeightInches) { mutableStateOf(formatInches(heightInches)) }
    val parsedWidth = widthText.toPhotoDimensionOrNull(maxWidthInches)
    val parsedHeight = heightText.toPhotoDimensionOrNull(maxHeightInches)
    val validSize = parsedWidth != null && parsedHeight != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
    ) {
        OutlinedTextField(
            value = widthText,
            onValueChange = { value ->
                widthText = value.take(8)
                val nextWidth = widthText.toPhotoDimensionOrNull(maxWidthInches)
                val nextHeight = heightText.toPhotoDimensionOrNull(maxHeightInches)
                if (nextWidth != null && nextHeight != null) {
                    onSizeChange(nextWidth, nextHeight)
                }
            },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.image_custom_width)) },
            isError = !validSize,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true,
        )
        OutlinedTextField(
            value = heightText,
            onValueChange = { value ->
                heightText = value.take(8)
                val nextWidth = widthText.toPhotoDimensionOrNull(maxWidthInches)
                val nextHeight = heightText.toPhotoDimensionOrNull(maxHeightInches)
                if (nextWidth != null && nextHeight != null) {
                    onSizeChange(nextWidth, nextHeight)
                }
            },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.image_custom_height)) },
            isError = !validSize,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
        )
    }
    Text(
        text = if (validSize) {
            stringResource(R.string.image_custom_size_hint)
        } else {
            stringResource(
                R.string.image_custom_size_invalid,
                formatInches(maxWidthInches),
                formatInches(maxHeightInches),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (validSize) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

private fun supportedImageCounts(
    page: ImagePageSettings,
    pageAspect: Float,
    pageWidthInches: Float,
    pageHeightInches: Float,
    marginFraction: Float,
    cellBorderFraction: Float,
): List<Int> = if (page.photoSize == PhotoSizePreset.AUTO) {
    ImageCountOptions
} else {
    ImageCountOptions.filter { count ->
        page.fitsPhotoCount(
            count = count,
            pageAspect = pageAspect,
            pageWidthInches = pageWidthInches,
            pageHeightInches = pageHeightInches,
            marginFraction = marginFraction,
            cellBorderFraction = cellBorderFraction,
        )
    }
}

private fun ImagePageSettings.withSupportedPhotoCount(
    pageAspect: Float,
    pageWidthInches: Float,
    pageHeightInches: Float,
    marginFraction: Float,
    cellBorderFraction: Float,
): ImagePageSettings {
    val supportedCounts = supportedImageCounts(
        page = this,
        pageAspect = pageAspect,
        pageWidthInches = pageWidthInches,
        pageHeightInches = pageHeightInches,
        marginFraction = marginFraction,
        cellBorderFraction = cellBorderFraction,
    )
    val selectedCount = when {
        supportedCounts.isEmpty() -> ImagePageSettings.MIN_IMAGES_PER_PAGE
        imagesPerPage in supportedCounts -> imagesPerPage
        else -> supportedCounts.lastOrNull { it <= imagesPerPage } ?: supportedCounts.last()
    }
    return withImagesPerPage(selectedCount, pageAspect)
}

private fun String.toPhotoDimensionOrNull(maxInches: Float): Float? =
    trim()
        .replace(',', '.')
        .toFloatOrNull()
        ?.takeIf {
            it.isFinite() &&
                it in ImagePageSettings.MIN_CUSTOM_PHOTO_DIMENSION_INCHES..maxInches
        }

private fun formatInches(value: Float): String = NumberFormat.getNumberInstance(Locale.getDefault())
    .apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        isGroupingUsed = false
    }
    .format(value.toDouble())

@Composable
private fun ImageGridOrientationOption(
    orientation: ImageGridOrientation,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = DosPrintDimens.controlHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(
            width = if (selected) DosPrintDimens.hairline * 2 else DosPrintDimens.hairline,
            color = if (selected) colors.primary else colors.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DosPrintDimens.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space6),
        ) {
            ImageGridOrientationDiagram(orientation = orientation, selected = selected)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
        }
    }
}

@Composable
private fun ImageGridOrientationDiagram(
    orientation: ImageGridOrientation,
    selected: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(width = 48.dp, height = 34.dp),
        shape = RoundedCornerShape(DosPrintDimens.space4),
        color = colors.surface,
        border = BorderStroke(DosPrintDimens.hairline, colors.outlineVariant),
    ) {
        val cellColor = if (selected) colors.primary else colors.surfaceVariant
        val (columns, rows) = when (orientation) {
            ImageGridOrientation.AUTO -> 2 to 2
            ImageGridOrientation.SIDE_BY_SIDE -> 2 to 1
            ImageGridOrientation.STACKED -> 1 to 2
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DosPrintDimens.space4),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space2),
        ) {
            repeat(rows) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.space2),
                ) {
                    repeat(columns) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(DosPrintDimens.space2))
                                .background(cellColor),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SettingDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    optionEnabled: (T) -> Boolean = { true },
) {
    ValueDropdown(
        label = label,
        value = optionLabel(selected),
        options = options,
        optionLabel = optionLabel,
        onSelect = onSelect,
        optionEnabled = optionEnabled,
    )
}

@Composable
private fun SizeAndLayoutCard(
    state: SetupUiState,
    onAction: (SetupAction) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val settings = state.settings
    val summary = buildList {
        add(settings.scaling.label)
        add(settings.marginPreset.label)
        if (settings.pagesPerSheet > 1) add("${settings.pagesPerSheet} per sheet")
    }.joinToString(" · ")
    DosPrintSection(
        title = stringResource(R.string.size_and_layout_title),
        subtitle = summary,
        icon = DosPrintIcons.AlignCenter,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SettingDropdown(
            label = stringResource(R.string.setup_scaling),
            options = ScalingMode.entries,
            selected = settings.scaling,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SelectScaling(it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.setup_margins),
            options = MarginPreset.entries,
            selected = settings.marginPreset,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SelectMarginPreset(it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.layout_pages_per_sheet),
            options = PagesPerSheetOptions,
            selected = settings.pagesPerSheet,
            optionLabel = { it.toString() },
            onSelect = { onAction(SetupAction.SelectPagesPerSheet(it)) },
        )
        if (settings.pagesPerSheet > 1) {
            SettingDropdown(
                label = stringResource(R.string.layout_page_order),
                options = PageOrder.entries,
                selected = settings.pageOrder,
                optionLabel = { it.label },
                onSelect = { onAction(SetupAction.SelectPageOrder(it)) },
            )
            UniformToggleRow(
                label = stringResource(R.string.layout_cell_border),
                checked = settings.cellBorderFraction > 0f,
                onCheckedChange = {
                    onAction(SetupAction.SetCellBorder(if (it) CellBorderOn else 0f))
                },
            )
        }
    }
}

/**
 * Which pages to print.
 *
 * Odd and Even are hidden for a single page document because they cannot mean anything there, and
 * the range box only appears when Range is the selected mode.
 */
@Composable
private fun PageSelectionCard(
    state: SetupUiState,
    onAction: (SetupAction) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val settings = state.settings
    val usable = state.totalPages > 1
    DosPrintSection(
        title = stringResource(R.string.pages_label),
        subtitle = "${settings.pageSelection.label} · ${stringResource(R.string.pages_summary, state.totalPages)}",
        icon = DosPrintIcons.Document,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SettingDropdown(
            label = stringResource(R.string.pages_which),
            options = PageSelectionMode.entries.filter { usable || !it.needsMultiplePages(state.totalPages) },
            selected = settings.pageSelection,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SelectPageSelection(it)) },
        )
        if (settings.pageSelection.isRange) {
            OutlinedTextField(
                value = state.pageRangeInput,
                onValueChange = { onAction(SetupAction.PageRangeInputChanged(it)) },
                label = { Text(stringResource(R.string.setup_page_range_custom)) },
                supportingText = { Text(stringResource(R.string.setup_page_range_hint)) },
                isError = state.pageRangeError != null,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            state.pageRangeError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.isPageRangeDirty) {
                UniformButtonRow(
                    buttons = listOf(
                        UniformButton(
                            label = stringResource(R.string.action_confirm),
                            icon = Icons.Filled.Check,
                            emphasized = true,
                            onClick = { onAction(SetupAction.ApplyPageRange) },
                        ),
                    ),
                )
            }
        }
        UniformToggleRow(
            label = stringResource(R.string.pages_reverse),
            checked = settings.reversePageOrder,
            onCheckedChange = { onAction(SetupAction.ToggleReversePageOrder(it)) },
        )
    }
}

@Composable
private fun QualityCard(
    state: SetupUiState,
    onAction: (SetupAction) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    DosPrintSection(
        title = stringResource(R.string.setup_quality),
        subtitle = "${state.settings.colorMode.label} · ${state.settings.resolution.label}",
        icon = DosPrintIcons.Tune,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SettingDropdown(
            label = stringResource(R.string.setup_color_mode),
            options = ColorMode.entries,
            selected = state.settings.colorMode,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SelectColorMode(it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.setup_quality),
            options = PrintResolution.entries,
            selected = state.settings.resolution,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SelectResolution(it)) },
        )
    }
}

@Composable
private fun AdvancedCard(
    state: SetupUiState,
    onAction: (SetupAction) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val summary = buildList {
        if (!state.isImageMode) add(state.settings.rotation.label)
        add(state.settings.duplex.label)
        add(state.settings.watermark.label)
    }.joinToString(" · ")
    DosPrintSection(
        title = stringResource(R.string.setup_advanced),
        subtitle = summary,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        if (!state.isImageMode) {
            SettingDropdown(
                label = stringResource(R.string.setup_rotation),
                options = PageRotation.entries,
                selected = state.settings.rotation,
                optionLabel = { it.label },
                onSelect = { onAction(SetupAction.Rotate(it)) },
            )
        }
        SettingDropdown(
            label = stringResource(R.string.duplex_label),
            options = DuplexMode.entries,
            selected = state.settings.duplex,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SelectDuplex(it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.manual_duplex_label),
            options = ManualDuplex.entries,
            selected = state.settings.manualDuplex,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SelectManualDuplex(it)) },
        )
        SettingDropdown(
            label = stringResource(R.string.setup_watermark),
            options = WatermarkStyle.entries,
            selected = state.settings.watermark,
            optionLabel = { it.label },
            onSelect = { onAction(SetupAction.SetWatermark(it)) },
        )
        if (state.settings.watermark == WatermarkStyle.TEXT) {
            OutlinedTextField(
                value = state.settings.watermarkText,
                onValueChange = { onAction(SetupAction.SetWatermarkText(it)) },
                label = { Text(stringResource(R.string.setup_watermark_text_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActionsCard(
    state: SetupUiState,
    onAction: (SetupAction) -> Unit,
) {
    DosPrintSection(title = stringResource(R.string.setup_actions)) {
        state.submitError?.let { key ->
            SurfaceErrorStrip(setupMessage(key) ?: key)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        ) {
            Button(
                onClick = { onAction(SetupAction.SavePdf) },
                enabled = state.canPrint,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.weight(1f).heightIn(min = DosPrintDimens.controlHeight),
            ) {
                Icon(DosPrintIcons.Pdf, contentDescription = null)
                Text(stringResource(R.string.preview_save_pdf), modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = { onAction(SetupAction.Print) },
                enabled = state.canPrint,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.weight(1f).heightIn(min = DosPrintDimens.controlHeight),
            ) {
                Icon(DosPrintIcons.Printer, contentDescription = null)
                Text(stringResource(R.string.output_android_print), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SurfaceErrorStrip(message: String) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(DosPrintDimens.controlCorner),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(DosPrintDimens.fieldGap),
        )
    }
}

@Composable
private fun summaryLine(state: SetupUiState): String {
    val document = if (state.documents.size == 1) {
        state.documents.first().displayName
    } else {
        stringResource(R.string.setup_document_count, state.documents.size)
    }
    return document
}

private val PagesPerSheetOptions = listOf(1, 2, 4, 6, 9, 16)
private val ImageCountOptions = (1..com.dosprint.domain.model.ImagePageSettings.MAX_IMAGES_PER_PAGE).toList()
private const val CellBorderOn = 0.01f

private const val COPIES_LABEL = "Copies"
private const val ORIENTATION_PORTRAIT = "Portrait"
private const val ORIENTATION_LANDSCAPE = "Landscape"
