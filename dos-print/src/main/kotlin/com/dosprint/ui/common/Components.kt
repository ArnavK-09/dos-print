package com.dosprint.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.dosprint.domain.model.SetupWarning
import com.dosprint.ui.theme.DosPrintDimens

@Composable
fun DosPrintCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(DosPrintDimens.sectionPadding),
    shape: Shape = RoundedCornerShape(DosPrintDimens.sectionCorner),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = DosPrintDimens.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.sectionContentGap),
            content = content,
        )
    }
}

@Composable
fun DosPrintSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(DosPrintDimens.sectionPadding),
    trailing: @Composable (() -> Unit)? = null,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val onHeaderClick = onExpandedChange
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onHeaderClick == null) Modifier else Modifier.animateContentSize()),
        shape = RoundedCornerShape(DosPrintDimens.sectionCorner),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = DosPrintDimens.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DosPrintDimens.minTouchTarget)
                    .then(
                        if (onHeaderClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable(role = Role.Button) { onHeaderClick(!expanded) }
                        },
                    )
                    .semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(DosPrintDimens.iconSmall + DosPrintDimens.space4),
                    )
                    Spacer(Modifier.width(DosPrintDimens.space10))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
                if (onHeaderClick != null) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded || onHeaderClick == null) {
                Spacer(Modifier.height(DosPrintDimens.fieldGap))
                Column(
                    verticalArrangement = Arrangement.spacedBy(DosPrintDimens.sectionContentGap),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun ListSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DosPrintDimens.groupGap, bottom = DosPrintDimens.space4)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DosPrintDimens.minTouchTarget)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        trailing?.invoke()
    }
}

@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = DosPrintDimens.dividerIndent),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun ListItemRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(DosPrintDimens.listItemPadding),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DosPrintDimens.minTouchTarget)
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        content = content,
    )
}

enum class ChipTone {
    NEUTRAL,
    POSITIVE,
    WARNING,
    ERROR,
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.NEUTRAL,
) {
    val container = when (tone) {
        ChipTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        ChipTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
        ChipTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        ChipTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        ChipTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        ChipTone.POSITIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        ChipTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        ChipTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DosPrintDimens.chipCorner),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = DosPrintDimens.space10, vertical = DosPrintDimens.space4),
        )
    }
}

@Composable
fun WarningBanner(
    warnings: List<SetupWarning>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = warnings.isNotEmpty(), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap)) {
            warnings.forEach { warning -> WarningRow(warning) }
        }
    }
}

@Composable
private fun WarningRow(warning: SetupWarning) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(DosPrintDimens.controlCorner),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(DosPrintDimens.listItemPadding),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier
                    .size(DosPrintDimens.iconSmall + DosPrintDimens.space2)
                    .clearAndSetSemantics { },
            )
            Column(
                modifier = Modifier.padding(start = DosPrintDimens.space10),
                verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space2),
            ) {
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                warning.suggestion?.let { suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    contentPadding: PaddingValues = PaddingValues(DosPrintDimens.space32),
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = AlphaSubtle),
            modifier = Modifier.size(DosPrintDimens.iconLarge * 2),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(DosPrintDimens.iconLarge),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = DosPrintDimens.space20),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = DosPrintDimens.space8)
                .widthIn(max = DosPrintDimens.maxReadingWidth),
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = DosPrintDimens.space24)) {
                action()
            }
        }
    }
}

@Composable
fun LoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DosPrintDimens.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space12),
    ) {
        CircularProgressIndicator(modifier = Modifier.sizeIn(minWidth = DosPrintDimens.space40, minHeight = DosPrintDimens.space40))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ProgressLabel(
    label: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    progressDescription: String? = null,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                progressDescription?.let { contentDescription = it }
            },
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space6),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { clamped },
            modifier = Modifier
                .fillMaxWidth()
                .height(DosPrintDimens.sliderTrackHeight),
        )
    }
}

@Composable
fun ResultBadge(
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
    successText: String,
    failureText: String,
) {
    val icon = if (isSuccess) Icons.Filled.CheckCircle else Icons.Outlined.Warning
    val tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val text = if (isSuccess) successText else failureText
    Row(
        modifier = modifier.semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(DosPrintDimens.iconSmall),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(start = DosPrintDimens.space6),
        )
    }
}

@Composable
fun SurfaceTint(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(DosPrintDimens.controlCorner),
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color, content = { content() })
}

@Composable
fun ExpandableBox(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.animateContentSize(),
    ) {
        content()
    }
}

@Composable
fun SubtleDividerLine(modifier: Modifier = Modifier, thickness: Dp = DosPrintDimens.hairline) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
fun ClippedBox(modifier: Modifier = Modifier, corner: Dp, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(corner))) {
        content()
    }
}

private const val AlphaSubtle = 0.35f
