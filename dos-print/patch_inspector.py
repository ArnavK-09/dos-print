import pathlib

p = pathlib.Path('src/main/kotlin/com/dosprint/ui/components/StudioInspector.kt')
s = p.read_text()

old_appearance = """        OptionRow(
            options = FrameFit.entries,
            selected = selected.fitMode,
            label = { it.label },
            onSelect = { onAction(StudioAction.SelectFitMode(it)) },
        )"""
new_appearance = """        ChipFlow(
            options = FrameFit.entries,
            selected = selected.fitMode,
            label = { it.label },
            onSelect = { onAction(StudioAction.SelectFitMode(it)) },
        )"""
assert old_appearance in s
s = s.replace(old_appearance, new_appearance)

old_transform = """        IconActionRow(
            actions = listOf(
                IconCommand(DosPrintIcons.RotateLeft, stringResource(R.string.action_rotate_left)) {
                    onAction(StudioAction.SetRotation(selected.rotationDegrees - QuarterTurn))
                },
                IconCommand(DosPrintIcons.RotateRight, stringResource(R.string.action_rotate_right)) {
                    onAction(StudioAction.SetRotation(selected.rotationDegrees + QuarterTurn))
                },
                IconCommand(DosPrintIcons.Flip, stringResource(R.string.action_flip_horizontal)) {
                    onAction(StudioAction.Flip(horizontal = true))
                },
                IconCommand(DosPrintIcons.Flip, stringResource(R.string.action_flip_vertical)) {
                    onAction(StudioAction.Flip(horizontal = false))
                },
            ),
        )"""
new_transform = """        UniformButtonRow(
            buttons = listOf(
                UniformButton(
                    label = stringResource(R.string.action_rotate_left),
                    icon = DosPrintIcons.RotateLeft,
                    onClick = { onAction(StudioAction.SetRotation(selected.rotationDegrees - QuarterTurn)) },
                ),
                UniformButton(
                    label = stringResource(R.string.action_rotate_right),
                    icon = DosPrintIcons.RotateRight,
                    onClick = { onAction(StudioAction.SetRotation(selected.rotationDegrees + QuarterTurn)) },
                ),
                UniformButton(
                    label = stringResource(R.string.action_flip_horizontal),
                    icon = DosPrintIcons.Flip,
                    onClick = { onAction(StudioAction.Flip(horizontal = true)) },
                ),
                UniformButton(
                    label = stringResource(R.string.action_flip_vertical),
                    icon = DosPrintIcons.Flip,
                    onClick = { onAction(StudioAction.Flip(horizontal = false)) },
                ),
            ),
        )"""
assert old_transform in s
s = s.replace(old_transform, new_transform)

old_arrange = """        IconActionRow(
            actions = listOf(
                IconCommand(DosPrintIcons.ArrowUpward, stringResource(R.string.action_bring_to_front)) {
                    onAction(StudioAction.Align(AlignmentAction.BRING_TO_FRONT))
                },
                IconCommand(DosPrintIcons.ArrowDownward, stringResource(R.string.action_send_to_back)) {
                    onAction(StudioAction.Align(AlignmentAction.SEND_TO_BACK))
                },
                IconCommand(DosPrintIcons.ContentCopy, stringResource(R.string.action_duplicate)) {
                    onAction(StudioAction.DuplicateItem(selected.id))
                },
                IconCommand(
                    if (selected.locked) DosPrintIcons.Lock else DosPrintIcons.LockOpen,
                    if (selected.locked) {
                        stringResource(R.string.action_unlock)
                    } else {
                        stringResource(R.string.action_lock)
                    },
                ) {
                    onAction(StudioAction.SetLocked(!selected.locked))
                },
            ),
        )"""
new_arrange = """        UniformButtonRow(
            buttons = listOf(
                UniformButton(
                    label = stringResource(R.string.action_bring_to_front),
                    icon = DosPrintIcons.ArrowUpward,
                    onClick = { onAction(StudioAction.Align(AlignmentAction.BRING_TO_FRONT)) },
                ),
                UniformButton(
                    label = stringResource(R.string.action_send_to_back),
                    icon = DosPrintIcons.ArrowDownward,
                    onClick = { onAction(StudioAction.Align(AlignmentAction.SEND_TO_BACK)) },
                ),
                UniformButton(
                    label = stringResource(R.string.action_duplicate),
                    icon = DosPrintIcons.ContentCopy,
                    onClick = { onAction(StudioAction.DuplicateItem(selected.id)) },
                ),
                UniformButton(
                    label = if (selected.locked) {
                        stringResource(R.string.action_unlock)
                    } else {
                        stringResource(R.string.action_lock)
                    },
                    icon = if (selected.locked) DosPrintIcons.Lock else DosPrintIcons.LockOpen,
                    onClick = { onAction(StudioAction.SetLocked(!selected.locked)) },
                ),
            ),
        )"""
assert old_arrange in s
s = s.replace(old_arrange, new_arrange)

chips = [
    ('AssistChip(\n            onClick = { onAction(StudioAction.Distribute(StudioGeometryAxis.HORIZONTAL)) },\n            enabled = state.items.size > 2,\n            label = { Text(stringResource(R.string.studio_arrange_distribute_x)) },\n            colors = AssistChipDefaults.assistChipColors(),\n        )',
     'UniformButton(\n                    label = stringResource(R.string.studio_arrange_distribute_x),\n                    onClick = { onAction(StudioAction.Distribute(StudioGeometryAxis.HORIZONTAL)) },\n                    enabled = state.items.size > 2,\n                )'),
    ('AssistChip(\n            onClick = { onAction(StudioAction.Distribute(StudioGeometryAxis.VERTICAL)) },\n            enabled = state.items.size > 2,\n            label = { Text(stringResource(R.string.studio_arrange_distribute_y)) },\n            colors = AssistChipDefaults.assistChipColors(),\n        )',
     'UniformButton(\n                    label = stringResource(R.string.studio_arrange_distribute_y),\n                    onClick = { onAction(StudioAction.Distribute(StudioGeometryAxis.VERTICAL)) },\n                    enabled = state.items.size > 2,\n                )'),
    ('AssistChip(\n            onClick = { onAction(StudioAction.Align(AlignmentAction.FIT_PAGE)) },\n            label = { Text(stringResource(R.string.action_fit_page)) },\n            colors = AssistChipDefaults.assistChipColors(),\n        )',
     'UniformButton(\n                    label = stringResource(R.string.action_fit_page),\n                    onClick = { onAction(StudioAction.Align(AlignmentAction.FIT_PAGE)) },\n                )'),
    ('AssistChip(\n            onClick = { onAction(StudioAction.Align(AlignmentAction.FILL_PAGE)) },\n            label = { Text(stringResource(R.string.action_fill_page)) },\n            colors = AssistChipDefaults.assistChipColors(),\n        )',
     'UniformButton(\n                    label = stringResource(R.string.action_fill_page),\n                    onClick = { onAction(StudioAction.Align(AlignmentAction.FILL_PAGE)) },\n                )'),
    ('AssistChip(\n            onClick = { onAction(StudioAction.Align(AlignmentAction.ACTUAL_SIZE)) },\n            label = { Text(stringResource(R.string.action_actual_size)) },\n            colors = AssistChipDefaults.assistChipColors(),\n        )',
     'UniformButton(\n                    label = stringResource(R.string.action_actual_size),\n                    onClick = { onAction(StudioAction.Align(AlignmentAction.ACTUAL_SIZE)) },\n                )'),
    ('AssistChip(\n            onClick = { onAction(StudioAction.SetFrameStyle(selected.frameStyle.reset())) },\n            label = { Text(stringResource(R.string.action_reset_frame)) },\n            colors = AssistChipDefaults.assistChipColors(),\n        )',
     'UniformButton(\n                    label = stringResource(R.string.action_reset_frame),\n                    onClick = { onAction(StudioAction.SetFrameStyle(selected.frameStyle.reset())) },\n                )'),
    ('AssistChip(\n            onClick = { onAction(StudioAction.RemoveItem(selected.id)) },\n            label = { Text(stringResource(R.string.action_delete)) },\n            leadingIcon = {\n                Icon(\n                    imageVector = Icons.Filled.Delete,\n                    contentDescription = null,\n                    modifier = Modifier.size(DosPrintDimens.iconSmall),\n                )\n            },\n            colors = AssistChipDefaults.assistChipColors(\n                labelColor = MaterialTheme.colorScheme.error,\n                leadingIconContentColor = MaterialTheme.colorScheme.error,\n            ),\n        )',
     'UniformButton(\n                    label = stringResource(R.string.action_delete),\n                    icon = Icons.Filled.Delete,\n                    onClick = { onAction(StudioAction.RemoveItem(selected.id)) },\n                )'),
]
for old, new in chips:
    assert old in s, old[:60]
    s = s.replace(old, new)

s = s.replace("""            ),
        )
    }
}

@Composable
private fun InspectorHeader(""", """            ),
        )
    }
}

@Composable
private fun InspectorHeader(""")

# Wrap the distribute / arrange chips in a UniformButtonRow
old_tail = """        )
    }
}

@Composable
private fun InspectorHeader("""
new_tail = """        )
    }
}

@Composable
private fun InspectorHeader("""
assert old_tail in s
s = s.replace(old_tail, new_tail)

# Convert the arrange chip block into a UniformButtonRow call
start = s.index("        AssistChip(\n            onClick = { onAction(StudioAction.Distribute(StudioGeometryAxis.HORIZONTAL)) },")
end = s.index("    }\n}\n\n@Composable\nprivate fun FrameStyleBlock", start)
block = s[start:end]
closing = block.rindex("        )\n")
inner = block[:closing]
inner = inner.replace("        AssistChip(", "                UniformButton(")
inner = "\n".join(line[4:] if line.startswith("    ") else line for line in inner.split("\n"))
inner = inner.rstrip() + "\n        )\n"
new_block = "        UniformButtonRow(\n            buttons = listOf(\n" + inner + "            ),\n        )\n"
s = s[:start] + new_block + s[end:]

p.write_text(s)
print("inspector patched")
