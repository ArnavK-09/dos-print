# User Guide

Dos Print prints documents from your device and arranges photos and other media on a page exactly
the way you want them.

## Printing a document

1. **Add a printer** from the Printers tab. Dos Print discovers Wi-Fi, USB and Bluetooth printers
   automatically; you can also add one by hand.
2. **Choose files** with the pick-files button, or share a document into Dos Print from any app.
3. **Set up the job** on the Setup screen.
4. **Preview** the sheets, then **Print**.

## Print settings

| Setting | Where |
|---|---|
| Printer | Setup summary card, or the Printers tab |
| Paper size | Setup → Paper size (visual picker with real dimensions) |
| Orientation | Setup → Paper size → Orientation |
| Margins | Setup → Paper size → Margins |
| Copies | Setup → Paper size → Copies |
| Pages per sheet | Setup → Page layout → Pages per sheet |
| Page order | Setup → Page layout → Page order |
| Sides (duplex) | Setup → Page layout → Sides |
| Sheets per booklet | Setup → Page layout (book fold only) |
| Borderless | Setup → Page layout → Borderless |
| Gap between items | Setup → Page layout → Gap |
| Page margin | Setup → Page layout → Page margin |
| Item outline | Setup → Page layout → Item outline |
| Colour and quality | Setup → Colour and quality |
| Scaling | Setup → Colour and quality → Scaling |
| Page range | Setup → Advanced → Page range |
| Rotation | Setup → Advanced → Rotation |
| Watermark | Setup → Advanced → Watermark |
| Saved presets | Setup → Presets |

### Page layout presets

The **Layout presets** section offers ready-made grids, each showing a picture of the finished
page. They cover:

- **Page layout** — 1, 2, 2-wide, 4, 6, 9 and 16 pages per sheet
- **Booklets** — book fold, A5 pages, two-sided
- **Photo** — 4x6, 5x7, 10x15 cm borderless prints, 3x3 contact sheet, 2x2 photo
- **Labels and cards** — 10 business cards per sheet, 21 or 24 address labels, CD labels
- **Everyday documents** — DL envelopes, half-sheet and quarter-sheet flyers, 11x17 poster

Choosing a preset sets the page size, the grid and the borderless option together. You can still
fine-tune the gap, margin and outline afterwards. The live preview at the top of Setup shows the
real result before anything is sent to the printer.

## Layout studio

The Layout studio is for placing photos and other images on a page yourself.

1. Tap **Add media** and pick one or more images. Each appears immediately and settles to its real
   aspect ratio a moment later.
2. The **Print** button is at the top right, and the printer chip beside it is where the job will go.
   Tap the chip to switch destination.
3. **Drag** the middle of an item to move it. While dragging, pink guides appear when the item lines
   up with the page centre, thirds, quarters or another item's edges and centres.
4. **Pinch** with two fingers to scale, and **twist** to rotate. Rotation snaps to 15° steps.
5. **Drag a handle to resize.** The four corner handles change width and height together; the four
   smaller handles on the edges move that one border and pin the opposite one. Handles sit on the
   image's own border and scale with it, so a small thumbnail stays easy to grab. Switch on **Lock
   aspect ratio** in the panel to keep the image undistorted.
6. **Drag the round handle above the top edge** to rotate precisely.
7. **Double tap** an item to open its panel, or tap outside the page to deselect. **Undo** and
   **Redo** are at the bottom of the screen, and every drag is a single undo step.

### The item panel

With an item selected, the panel gives you:

- **Position and size** — align to any edge, centre, fill page, fit page, actual size, lock aspect
- **Appearance** — fill / fit / stretch, opacity, rotation, rotate both ways, flip both ways
- **Frame and border** — corner rounding, border width, drop shadow, six border colours
- **Adjustments** — brightness, contrast, saturation, warmth, sharpen, blur, black and white, sepia,
  fade and vignette, each with a reset
- **Arrange** — bring to front, send to back, duplicate, lock position, distribute horizontally or
  vertically, and delete

A locked item ignores drags, scaling and rotation but still accepts appearance changes.

### Page settings

- **Page size** switches between all the standard sizes, including photo sizes and envelopes. Items
  keep their relative positions when the page shape changes.
- **Trim lines** shows the centre cross used for cutting.
- **Grid guides** shows the page outline and enables snapping.
- **Layout presets** inside the studio can snap everything to a grid, or fill a grid with the media
  you just picked.

### Printing a layout

Pick a printer on the bar at the bottom, then tap **Print layout**. What you see in the editor is
what gets rendered — the same code produces both.

## Print preview

Setup shows a live render of the first sheet, redrawn shortly after every change, so you can see
the effect of a setting without leaving the screen. Tap it to open the full inspector, which
renders the real sheets at the real paper proportions.

- **Zoom** with the +/- controls, a pinch, or a double tap to reset.
- **Panning** is bounded so the sheet can never be dragged out of view.
- The **sheet strip** under the canvas jumps to any sheet; if only the first few are rendered it
  says so rather than pretending the job is smaller than it is.
- The bar at the bottom summarises the exact settings, and lets you go back and edit them before
  printing.

## Save as PDF

**Save as PDF** writes a real PDF file through Android's document picker, so no storage permission
is needed and you choose where it goes. The page size in the file is exactly the paper size you
selected — A4 gives a 210 x 297 mm page, Letter gives 8.5 x 11 in.

PDF is offered as a destination in its own right and is the default until you pick a real printer,
so you can export without configuring any hardware at all.

## History

The Jobs tab lists every submitted job with its printer, settings and live status. You can cancel
active jobs and reopen completed ones with the same settings.

## Troubleshooting

| Symptom | What to do |
|---|---|
| No printers listed | Open Printers and add one by hand, or check that the printer is on the same network |
| "Select a printer first" | Pick a printer on the Studio's bottom bar or in Setup |
| Preview looks blurry | Lower the Quality setting, or pick a smaller paper size |
| Exported PDF is the wrong shape | Check Paper size on Setup — the PDF uses that setting verbatim |
| Photo is cropped | Set Fit mode to **Fit inside** instead of **Fill frame** |
| Item is stuck | Check the lock toggle in the item panel |
| Job never finishes | The printer is paused — check the device, then cancel and retry from the Jobs tab |
| Out of memory errors | Turn on Low memory mode in Settings |
| Encrypted PDF | Remove the password; protected documents are rejected before submission |

## Notifications

Two channels: *Print progress* and *Print results*. Both can be silenced per-app in Android system
settings. On Android 13 and later, grant the notification permission to see them.
