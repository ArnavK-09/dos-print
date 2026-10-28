# Architecture

Dos Print is a single Gradle module cut into three layers. Everything the user sees comes from
`ui/`, all rules and geometry live in `domain/`, and Android framework access is confined to `data/`.

```
┌───────────────────────────── UI (Compose + ViewModel)
│  Screens, navigation, state flows, Compose theme, Material 3
├───────────────────────────── Domain (interfaces + pure models)
│  Repository contracts, entities, settings validation, page planning, layout geometry
├───────────────────────────── Data (Android framework + coroutines)
   Stores, discovery, media decoding, print submission, job monitoring
```

## Components

| Layer | Package | Role |
|---|---|---|
| Core | `core/` | Sealed `AppError`, `Outcome`, `AppLog` + rotating `FileLog`, crash handler, `ErrorReporter`, dispatcher abstraction |
| Domain | `domain/model/` | Entities (`PrintJob`, `PrinterProfile`, `PrintSettings`, `PrinterCapabilities`, `PaperSize`, `PageRange`, `PrintDocument`) |
| Domain | `domain/model/LayoutCatalog.kt` | `LayoutGrid` geometry, `PageOrder`, `PrintSides`, `SheetsPerBooklet`, 20-entry `LayoutPresetCatalog` |
| Domain | `domain/model/ImagePage.kt` | `ImagePageSettings` and the photo options: scaling, per-page mode, gaps, position, grid |
| Domain | `domain/model/PlatformMediaSizes.kt` | The platform paper catalogue, and the bridge from a `PaperSize` to a real `MediaSize` |
| Domain | `domain/repository/` | Repository interfaces |
| Data | `data/local/` | Atomic JSON stores with corruption quarantine (`JsonFileStore`) |
| Data | `data/preferences/` | DataStore preferences + `PrintSettingsCodec` for last-used settings |
| Data | `data/media/` | `PageSource` implementations; `MediaImagePool` (LRU decode cache) |
| Data | `data/print/` | `PrintDocumentAdapter`, `PrintDocumentComposer`, `PrintPagePlanner`, `PdfExportManager`, `PrintRepositoryImpl`, `PrintJobMonitor` |
| Data | `data/printer/` | `SystemPrinterGateway`, `MdnsDiscovery`, `PrinterRepositoryImpl` |
| Data | `data/render/` | `SheetPreviewRenderer` for print preview |
| DI | `di/AppContainer.kt` | Hand-wired container, no reflection, no framework |
| UI | `ui/screens/` | One composable per screen |
| UI | `ui/components/` | `DosPrintIcons`, `UniformControls`, `LayoutPickers`, `ErrorLogCard`, `LivePreviewPanel`, `SettingsComponents` |
| UI | `ui/*ViewModel.kt` | `StateFlow` UiState per screen, unidirectional sealed `Action` types |

## The single-render-path guarantee

Preview, print and PDF all compose a sheet through the **same** code:

```
ui/components/LivePreviewPanel ─┐
data/print/PdfExportManager ────┼──►  PrintDocumentComposer.composeSheet(…)
data/print/PrintDocumentAdapter ┘
```

`SheetPreviewRenderer` and `PrintDocumentAdapter` share the composer, so there is no intermediate
representation of a sheet anywhere, and no chance of the preview diverging from the output. The page
size, grid, gutters, margins and scaling a user sees in the live preview are the values the printer
receives, by construction rather than by agreement.

## Performance

- **Bounded decode.** `MediaImagePool` is a byte-budgeted `LruCache` keyed on `uri@WxH`. Bounds and
  EXIF orientation are cached separately. Decode target size respects *both* dimensions so tall,
  narrow media does not over-decode. Supersampling is clamped to 1.5x.
- **Pixel budget.** `PrintQualityProfile` scales media down so a sheet never exceeds its pixel
  ceiling, with a `lowMemory()` profile for constrained devices.
- **Off the main thread.** All I/O on `Dispatchers.IO`; composition on `Dispatchers.Default`;
  printing on a dedicated single-thread write executor. The error log writes from its own low
  priority thread so a failure can never stall the flow that reported it.
- **Debounced previews.** The live preview redraws 220 ms after the last settings change rather than
  on every keystroke of a slider.
- **Zoom and pan are local UI state**, not ViewModel state, so gestures never trigger a
  ViewModel round trip.

## Print flows

Two flows, chosen before anything else happens, because they want different things:

| | Documents | Photos |
|---|---|---|
| Entry | Pick a document | Pick one or more images |
| Page composition | Pages, pages per sheet, order, duplex | Images per sheet, scaling, gaps, position, grid |
| Shared | paper, orientation, copies, colour, quality, duplex, printer | the same |

Everything shared is genuinely shared, so it lives in one `PrintSettings` and one screen. Only page
composition diverges, which is what `PrintMode` selects and what `ImagePageSettings` describes.

`ImagePageSettings` reproduces the reference app's option vocabulary and its documented defaults —
crop to fit, scale to fit, stretch to fit; single or multiple; four gap sizes; restrict to grid;
compact group; a 3x2 grid; nine positions centred on centre. Layout is always computed from those
settings rather than hand-placed, which is the same trade the reference app makes: no image handles,
so no way for a layout to drift from what the printer will do.

## Paper size

Paper only takes effect if the print framework recognises it, and it identifies sizes by id rather
than by dimensions alone. `PrintAttributes.MediaSize` ids are not free-form: passing
`DosPrint-iso_a4` produces a page the framework cannot match, so the printer falls back to its own
default and the user's choice silently does nothing.

`PlatformPaperCatalogue` keeps the app's catalogue ids equal to the platform's own
(`iso_a4`, `na_letter`, `na_tabloid`, `na_4x6`, ...), and `PlatformMediaSizes` submits the matching
platform constant rotated with `asPortrait`/`asLandscape`. Sizes the platform has no name for keep an
explicit `photo_` or `env_` id and are submitted with a synthesised id. The mapping is plain data
rather than reflection, so the catalogue can be checked against the platform in a unit test.

## Error reporting

Every failure, fatal or recovered, goes to `ErrorReporter`. Failures used to be held in memory, so
they were gone by the time the app reopened and a flow could misbehave leaving no trace at all.

Records are written to a bounded on-disk log from a background thread — an error path that blocks on
disk is an error path that can itself time out. `ErrorReporter.guard` wraps flow boundaries so the
failing area is known even when the exception carries no useful message. The log is readable from
Settings with the stack trace one tap away.

## Print pipeline

1. Documents and photos enter via the system picker or the share sheet, routed to the matching flow.
2. Printers are discovered (mDNS, USB attach, Bluetooth) or added manually; capabilities are
   learned from live `PrintJobInfo` attributes and cached.
3. Settings are mutated by sealed `SetupAction`s, validated by `SettingsValidator` against the
   printer's capabilities, and persisted through `PrintSettingsCodec`.
4. `PrintRepositoryImpl.submit` builds a `DosPrintDocumentAdapter` and goes through
   `PrintJobCoordinator` into `PrintManager.print`.
5. `PrintPagePlanner` converts logical pages to sheets, honouring the page range, copies, pages per
   sheet and reverse page order.
6. `PrintDocumentComposer` composes each sheet from `PrintSettings.layoutGrid` — the same
   `LayoutGrid` the Setup screen's visual pickers draw, so the gutter, page margin and cell border
   sliders are pixel-accurate in the output.
7. `PrintJobMonitor` polls `getPrintJobs()` and pushes state into `HistoryRepository`.

### Print to PDF

PDF is a first-class virtual destination (`destination_pdf`) rather than an export bolted onto the
printer list, so it can be selected anywhere a printer can and is the default until a real one is
chosen. `PdfExportManager` rasterises sheets through the same composer as the print path and writes
them with `PdfDocument` into a location chosen through the Storage Access Framework, so no storage
permission is required.

Page geometry deserves a note. `PdfDocument.PageInfo.Builder` takes **points**, one point being
1/72 inch, while rasters are in pixels at a chosen DPI. Passing a pixel count straight through makes
the page `dpi / 72` times too large — an A4 sheet becomes 34 x 48 inches — and the raster then
lands in one corner because it is drawn 1:1 into a far smaller canvas. `PdfPageGeometry` converts
mils to points, and rasters are drawn into the resulting page rect.

## Error handling

- Every failure crosses a repository boundary as a sealed `AppError` with a resource-backed message.
- `Outcome<T>` replaces exceptions at interfaces; `runCatchingOutcome` converts unexpected throws
  into `AppError.Unknown` and logs to `FileLog`.
- ViewModels emit message **keys**, never user text. `UiMessages` resolves them to string
  resources at the UI edge, so no internal identifier can leak onto the screen.
- Out-of-memory is caught explicitly at every bitmap site and reported as `AppError.OutOfMemory`.
- Corrupt JSON stores are quarantined to `*.corrupt` and rebuilt empty.
- A top-level `CrashHandler` records the stack trace and redirects to a shareable `CrashActivity`.

## State management

- One `StateFlow<UiState>` per screen, single-writer ViewModels, collected with
  `collectAsStateWithLifecycle`.
- Cross-screen session state (documents, printer, settings) lives in `SessionStore`.
- `ScreenStates`, `ScreenCallbacks` and `PdfExportLauncher` bundle screen data so
  `DosPrintNavHost` stays readable.

## Pointer gestures

`pointerInput` keys must never change for the duration of a gesture. Keying on a value the gesture
itself mutates tears the detector down and rebuilds it every frame, which disables the gesture with no
error at all. Keys stay limited to values that cannot change mid-drag, with live state read through
`rememberUpdatedState`.

## Responsiveness

- `WindowSizeClass` selects a bottom bar (compact) or a navigation rail (expanded).
- Option groups wrap into a `FlowRow` rather than overflowing, and every action group is a
  `UniformButtonRow` so buttons share a row evenly and wrap instead of clipping their labels.
- A single spacing scale in `DosPrintDimens` drives every gap, corner radius and control height, so
  padding is consistent across all screens.
- All touch targets are at least 48 dp; semantics labels and headings are set on every control.

## Screen padding

Screen padding is owned by exactly one function, `rememberScreenPadding`, and the shell disables
`Scaffold`'s own window insets so the two cannot stack.

Two failure modes sit either side of this, and both were hit here:

- `Scaffold`'s default `contentWindowInsets` is the *system bars* only, and in portrait those are
  entirely vertical. Handing its inner padding straight to a screen yields a zero left and right
  margin and every row sits flush against the display edge.
- Discarding its inner padding in favour of window insets is equally wrong, because
  `innerPadding.bottom` is the height of the bottom bar, not just the navigation bar inset. Content
  then scrolls underneath an 80 dp navigation bar and the last item cannot be reached.

So the shell zeroes the insets, passes the resulting inner padding as a base, and adds the design
margin on top. Cutouts still come from the window. Screens only ever apply what they are given.

## Icons

The icon set is **stroked outlines on a 24x24 viewport**, declared as data
(`IconRect`, `IconCircle`, `IconArc`, `IconLine`, `IconPolyline`) and rendered by a single factory.

They are stroked because they were once filled, and filled paths broke in a way that is easy to miss:
every subpath wound the same direction, and the `NonZero` fill rule unions those, so an icon built
from an outer outline plus inner detail collapsed into one solid 24x24 block. It drew perfectly and
was completely unreadable. Outlines cannot merge that way whatever the geometry.

Because the shapes are data rather than lambdas, `IconLegibilityTest` can rasterise exactly what
production renders, stroking each polyline the way the platform would. It rejects an icon that inks
too little (blank), too much (a block), or whose strokes overlap past a density threshold (fused
shapes), and it rejects two icons that render identically. It found three real defects on its first
run. `iconCatalogue()` forces every lazy vector so the test measures the real thing rather than a
hand-written copy that could drift.
