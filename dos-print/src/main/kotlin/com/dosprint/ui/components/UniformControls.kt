package com.dosprint.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dosprint.ui.theme.DosPrintDimens

@Composable
fun UniformButtonRow(
    buttons: List<UniformButton>,
    modifier: Modifier = Modifier,
    minHeight: Dp = DosPrintDimens.controlHeight,
) {
    if (buttons.isEmpty()) {
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        maxItemsInEachRow = MaxButtonsPerRow,
    ) {
        buttons.forEach { button ->
            UniformButtonCell(button = button, minHeight = minHeight, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun UniformButtonCell(
    button: UniformButton,
    minHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            button.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(DosPrintDimens.iconSmall),
                )
            }
            Text(
                text = button.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (button.icon != null) {
                    Modifier.padding(start = DosPrintDimens.space6)
                } else {
                    Modifier
                },
            )
        }
    }
    val baseModifier = modifier
        .heightIn(min = minHeight)
        .widthIn(min = MinButtonWidth)
        .semantics { contentDescription = button.description ?: button.label }
    if (button.emphasized) {
        Button(
            onClick = button.onClick,
            enabled = button.enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(),
            modifier = baseModifier,
        ) {
            content()
        }
    } else {
        OutlinedButton(
            onClick = button.onClick,
            enabled = button.enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(),
            modifier = baseModifier,
        ) {
            content()
        }
    }
}

@Composable
fun <T> ChipFlow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (T) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.rowGap),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        text = label(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = when {
                    leadingIcon != null -> ({ leadingIcon(option) })
                    isSelected -> {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(DosPrintDimens.iconSmall),
                            )
                        }
                    }
                    else -> null
                },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.semantics { contentDescription = label(option) },
            )
        }
    }
}

@Composable
fun UniformToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DosPrintDimens.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DosPrintDimens.fieldGap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
fun AccentBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = DosPrintDimens.space8,
                vertical = DosPrintDimens.space2,
            ),
        )
    }
}

private const val MaxButtonsPerRow = 2
private val MinButtonWidth = 108.dp
