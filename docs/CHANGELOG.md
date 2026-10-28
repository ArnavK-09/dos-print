# Changelog

## 0.0.1 — initial prototype (2026-10-05)

### Page size actually takes effect now
- Page selection appeared to do nothing because the app submitted a made-up media size id such as
  `DosPrint-iso_a4`. `PrintAttributes.MediaSize` ids are not free-form: the print framework and print
  services identify paper by matching the id against the platform catalogue, so an unrecognised id
  falls back to whatever the printer defaults to.
- Standard sizes are now submitted as the platform's own constant and rotated with
  `asPortrait`/`asLandscape`, so the printer sees a real paper id. Only genuinely non-standard sizes
  get a synthesised id, which is honest about the fact that they cannot be matched by name.
- The catalogue was the deeper cause: it used arbitrary ids — `tabloid`, `ledger`, `photo_4x6`,
  `env_dl` — so **nothing** matched the platform. They are now the platform's own ids (`na_tabloid`,
  `na_ledger`, `na_4x6`, `na_fanfold_us_germany`), and sizes the platform does not name keep an
  explicit `photo_` or `env_` prefix.
- Removed sizes that duplicated another entry under a different name (`11 x 17` was `na_tabloid`,
  `6 x 4` was `4 x 6`) or that no printer makes (`quarto`, `octavo`). 35 sizes, no duplicates.
- The mapping is plain data rather than reflection, so `PlatformMediaSizesTest` can assert the
  catalogue lines up with the platform without an Android runtime.

### Layout Studio removed
- Deleted the Studio outright: its models, geometry, renderer, repository, persistence, document
  adapter, three composables, its ViewModel, state, undo history and navigation entry. Roughly
  4,000 lines and every string it owned.
- The reference app has no image transform handles at all — it solves photo geometry with an
  automatic grid, three scaling modes and a nine-way position picker. Keeping a bespoke editor meant
  shipping a second, competing way to do the same job.

### Two flows, chosen up front
- Home now asks what you are printing before anything else, the way the reference app does:
  **Photos and images**, **Documents**, **Printers**. Each is a full-width row with a large icon,
  title and explanation rather than a generic file button.
- Documents open the document layout options; photos open the photo options. The two share paper,
  orientation, copies, colour, quality, duplex and the printer, because those genuinely apply to
  both, and diverge only where page composition differs.
- Picking a file navigates straight to Setup in the matching mode.

### Photo layout options
- Reproduces the reference app's option vocabulary and its documented defaults: crop to fit (default),
  scale to fit, stretch to fit; single or multiple per page (multiple by default); no, narrow, normal
  or wide gaps (normal by default); restrict to grid (off); compact group (on); a 3x2 grid; and all
  nine positions with centre as the default.
- Layout stays fully automatic, matching the reference app. There are no handles to get wrong.
- The options only appear in the photo flow, because a document has no notion of images per sheet.

### Error log you can actually read
- Failures were held in memory, so they were gone by the time the app reopened and there was no way to
  see what had already happened. A flow could misbehave and leave no trace at all.
- `ErrorReporter` records every failure, fatal or recovered, to a bounded on-disk log, written off the
  calling thread so the reporter can never itself stall a flow. Readable from Settings, with the
  stack trace one tap away and copy-to-clipboard for the whole log.
- `ErrorReporter.guard` wraps flow boundaries so the failing area is known even when the exception
  carries no useful message.

### Icons
- The custom icon set rendered as solid black squares. The icons were filled paths whose subpaths all
  wound the same way, and the `NonZero` fill rule unions those, so a glyph built from an outer shape
  plus inner detail collapsed into one solid block. It drew correctly and was unreadable.
- The whole set is now stroked outlines, which cannot merge into a blob whatever the geometry.
- Icons are declared as data rather than lambdas, and `IconLegibilityTest` rasterises exactly that
  geometry the way a device strokes it. It fails an icon that inks too little (blank), too much (a
  block), or whose strokes overlap past a density threshold (fused shapes). It found three real
  defects on its first run.

- The entire custom icon set rendered as solid black squares. The icons were filled paths whose
  subpaths all wound the same way, and the `NonZero` fill rule unions those, so a glyph built from
  an outer shape plus inner detail collapsed into one solid block. It drew correctly and looked like
  nothing at all.
- The whole set is now **stroked outlines**, which cannot merge into a blob whatever the geometry,
  at a uniform 1.8 stroke with round caps and joins on the standard 24x24 viewport.
- Icons are declared as data rather than lambdas, and `IconLegibilityTest` rasterises exactly that
  geometry the way a device strokes it. It fails an icon that inks less than 1.5% of the viewport
  (blank), more than 45% (a block), or whose strokes overlap so much the density exceeds 1.3 (fused
  shapes). It also fails two icons that render identically, and checks that every icon stays inside
  the viewport. All 34 icons sit between 5% and 43% coverage at density 1.23 or below.
- That test found three real defects on its first run: the Wifi arcs swept the wrong way and ran off
  the bottom of the viewport, and two assertions were wrong about what a valid icon looks like.

### Chose files, then nothing happened
- Picking documents now moves straight to Setup, where the job is configured and the live preview
  waits. Previously the import was silent and the user was left on an empty Home screen.
- Navigates only from Home and Documents, so importing while already configuring does not yank the
  screen out from under them.

### Screen padding and layout
- Fixed a shell-wide defect where every screen rendered flush against the display edges. Material's
  `Scaffold` contributes only system-bar insets, which in portrait are entirely vertical, so its
  inner padding carried a zero left and right margin. The shell now owns padding in one place,
  combining display-cutout insets on the horizontal edges, status and navigation bar insets
  vertically, and a comfortable reading margin.
- Fixed content sliding under the bottom bar on several screens. The first attempt discarded
  `Scaffold`'s inner padding entirely in favour of window insets, but that value is the height of
  the bottom bar, not just the navigation bar inset — so an 80 dp navigation bar sat on top of the
  last item. Padding is now the inner padding **plus** the design margin, never one or the other.
- Printers gained a proper screen title with a pluralised printer count, a dedicated toolbar card
  for search, refresh and the system print settings, and per-printer cards whose actions are a
  uniform wrapping button group instead of a full-width bar.

### Document printing
- PDF, image, plain text, Markdown, office (OOXML/OpenDocument/RTF) page sources.
- Print settings: paper size, orientation, colour mode, resolution, copies, page range, scaling,
  margins, duplex, collation, rotation, watermark, borderless.
- Validation surfaces every conflict as the offending setting plus a suggested fix, and blocks
  invalid jobs before submission.
- Durable job history with live status, cancel, retry and reprint.
- Printer discovery over mDNS, USB hot-plug and Bluetooth, plus manual entries. Capabilities are
  learned from live `PrintJobInfo` attributes and cached.

### Print preview
- Setup now shows a live sheet render inline, beside the settings, so the result of every change is
  visible without navigating away. Tapping the render opens the full zoomable sheet inspector.
- The preview button was removed from Setup; the inline render replaced it rather than duplicating it.
- Sheets render at the real paper proportions rather than the first page's aspect ratio.
- Bounded pinch-zoom and pan, double-tap to reset, and a thumbnail strip with an honest
  "showing N of M" indicator.
- A summary bar restates the exact settings and links back to Setup to change them.

### Print to PDF
- Page dimensions are declared in points rather than pixels. `PdfDocument.PageInfo.Builder` expects
  1/72 inch, so passing a 300 DPI raster made an A4 sheet a 34 x 48 inch page and left the bitmap
  in one corner. Rasters are now scaled into a correctly sized page.
- The same corrected geometry is covered by tests for A4, Letter, photo sizes, landscape and every
  catalogue entry.

### Page layout presets
- A 20-entry preset catalog modelled on a desktop print dialog, grouped as Page layout, Photo,
  Labels and cards, Booklets, and Everyday documents.
- Every preset renders a picture of the finished page, so the grid and paper shape are visible
  before it is applied.
- Visual pickers for paper size, pages per sheet (1 to 16), page order, sides, sheets per booklet,
  borderless, gap, page margin and item outline.

### Engineering
- Single spacing scale (`DosPrintDimens`) drives every gap, radius and control height across all
  screens, replacing the ad-hoc padding that made the UI feel inconsistent.
- Byte-budgeted LRU decode cache with separate bounds and EXIF caches; decode targets respect both
  dimensions and supersampling is capped at 1.5x.
- Zoom and pan are local UI state, keeping gestures off the ViewModel.
- 156 unit tests covering the platform paper catalogue, the photo layout options and their defaults,
  grid geometry, error-record round trips, fitted-page geometry, icon legibility, PDF page geometry,
  layout presets and settings clamping.
- Detekt, Spotless, Android Lint and `-Werror` Kotlin compilation all clean; zero TODOs and no
  comments in production sources.

### Known limitations
- Book fold sets reverse order, a 2-up grid and duplex, but does not yet perform the full
  saddle-stitch sheet imposition with mirrored flaps.
- Office documents print as extracted text; high-fidelity rendering would need a heavyweight
  engine that the NokoPrint reference downloads at runtime.
- Live job state comes from polling `getPrintJobs()` in-process; the foreground
  `PrintMonitorService` is a placeholder.
- mDNS discovery and Bluetooth printing have not been verified against physical hardware in this
  environment.
- No cloud print or raw IPP submission: jobs go through the Android print framework, which handles
  those transports internally.
- The photo layout options are modelled and their defaults match the reference app, but the composer
  has not yet been wired to lay images out from `ImagePageSettings`. The document path is unaffected;
  images currently flow through the document grid.
- The reference app's web-page printing and manual duplex ("front sides" / "back sides in reverse
  order") are not implemented.
- mDNS discovery, Bluetooth printing and the resulting paper negotiation remain unverified on
  physical hardware, so a chosen size has been verified against the framework's catalogue but not
  against a real printer.
