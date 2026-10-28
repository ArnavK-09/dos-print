package com.dosprint.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dosprint.R
import com.dosprint.domain.model.PaperChoices
import com.dosprint.domain.model.PaperSize
import com.dosprint.ui.theme.DosPrintDimens

@Composable
fun PaperChoicesDropdown(
    choices: PaperChoices,
    selectedId: String,
    onSelect: (PaperSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = choices.all.firstOrNull { it.id == selectedId } ?: choices.all.firstOrNull() ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showOther by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space4),
    ) {
        Text(
            text = if (choices.isReliable) {
                stringResource(R.string.paper_supported_summary, choices.supported.size)
            } else {
                stringResource(R.string.paper_unknown_summary)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DosPrintDimens.controlHeight)
                    .semantics { contentDescription = paperDescription(selected) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space2),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = selected.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = selected.dimensionsLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = dropdownMenuMaxHeight()),
            ) {
                choices.supported.forEach { paper ->
                    PaperSizeMenuItem(
                        paper = paper,
                        selected = paper.id == selectedId,
                        onClick = {
                            onSelect(paper)
                            expanded = false
                        },
                    )
                }
                if (choices.other.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = DosPrintDimens.space4))
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (showOther) R.string.paper_other_hide else R.string.paper_other_show,
                                    choices.other.size,
                                ),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showOther) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = { showOther = !showOther },
                    )
                    if (showOther) {
                        Text(
                            text = stringResource(R.string.paper_other_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = DosPrintDimens.sectionPadding,
                                vertical = DosPrintDimens.space4,
                            ),
                        )
                        choices.other.forEach { paper ->
                            PaperSizeMenuItem(
                                paper = paper,
                                selected = paper.id == selectedId,
                                onClick = {
                                    onSelect(paper)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperSizeMenuItem(
    paper: PaperSize,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.space2)) {
                Text(
                    text = paper.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = paper.dimensionsLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}

private fun PaperSize.dimensionsLabel(): String {
    val width = (widthMils / 1000f * 10f).toInt() / 10f
    val height = (heightMils / 1000f * 10f).toInt() / 10f
    return "$width x $height in"
}

private fun paperDescription(paper: PaperSize): String = "${paper.label}, ${paper.dimensionsLabel()}"
