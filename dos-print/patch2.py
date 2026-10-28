import pathlib

p = pathlib.Path('src/main/kotlin/com/dosprint/ui/components/StudioInspector.kt')
lines = p.read_text().split('\n')

start = 265
end = next(i for i in range(start, len(lines)) if lines[i].strip() == ')' and 'leadingIconContentColor' in lines[i - 1])

specs = [
    ('studio_arrange_distribute_x', 'DosPrintIcons.Grid', 'onAction(StudioAction.Distribute(StudioGeometryAxis.HORIZONTAL))', 'state.items.size > 2'),
    ('studio_arrange_distribute_y', 'DosPrintIcons.Grid', 'onAction(StudioAction.Distribute(StudioGeometryAxis.VERTICAL))', 'state.items.size > 2'),
    ('action_fit_page', 'DosPrintIcons.AspectRatio', 'onAction(StudioAction.Align(AlignmentAction.FIT_PAGE))', None),
    ('action_fill_page', 'DosPrintIcons.AlignMiddle', 'onAction(StudioAction.Align(AlignmentAction.FILL_PAGE))', None),
    ('action_actual_size', 'DosPrintIcons.AspectRatio', 'onAction(StudioAction.Align(AlignmentAction.ACTUAL_SIZE))', None),
    ('action_reset_frame', 'DosPrintIcons.Redo', 'onAction(StudioAction.SetFrameStyle(selected.frameStyle.reset()))', None),
    ('action_delete', 'Icons.Filled.Delete', 'onAction(StudioAction.RemoveItem(selected.id))', None),
]

block = ['        UniformButtonRow(', '            buttons = listOf(']
for label, icon, click, enable in specs:
    block.append('                UniformButton(')
    block.append('                    label = stringResource(R.string.%s),' % label)
    block.append('                    icon = %s,' % icon)
    if enable:
        block.append('                    enabled = %s,' % enable)
    block.append('                    onClick = { %s },' % click)
    block.append('                ),')
block.append('            ),')
block.append('        )')

lines[start:end + 1] = block
p.write_text('\n'.join(lines))
print("arrange block replaced at", start, "to", end)
