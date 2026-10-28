# Development

## Prerequisites

The local `.toolchain/` directory is excluded from this repository. Provision the listed versions
there before using `./build.sh`; Gradle dependencies are resolved from Google Maven and Maven
Central.

| Tool | Version | Location |
|---|---|---|
| OpenJDK | 21 (Temurin) | `.toolchain/jdk` |
| Gradle | 8.13 | `.toolchain/gradle-8.13` |
| Android SDK | Platform 35, Build-Tools 35.0.0 | `.toolchain/android-sdk` |
| AGP | 8.10.1 | resolved by Gradle from Google Maven |
| Kotlin | 2.1.21 | resolved by Gradle from Maven Central |

`build.sh` exports `JAVA_HOME`, `ANDROID_HOME` and `GRADLE_USER_HOME` to those paths. Always use it
rather than calling `gradle` directly — plain `gradle` has no `JAVA_HOME` and will fail.

## Commands

```bash
./build.sh tasks                    # list every task
./build.sh compileDebugKotlin       # fastest correctness check
./build.sh testDebugUnitTest        # 170 unit tests
./build.sh detekt                   # static analysis
./build.sh spotlessCheck            # formatting gate
./build.sh spotlessApply            # reformat
./build.sh lintDebug                # Android lint
./build.sh lintVitalRelease         # release lint gate
./build.sh assembleDebug            # debug APK
./build.sh assembleRelease          # unsigned unless local signing files are configured
./build.sh build                    # everything
```

## The environment constraint you must know about

The platform `android.jar` available here is a **stripped** API surface, not the real one. It is
missing methods that exist on a real device. Verified absences include:

- `PrintManager.getPrinters()`, `getPrintServiceInfo()`, `isPrintJobSupported()`, `addPrintStateListener()`
- `PrintJob.getState()`
- `PrintAttributes.ScaleMode`
- `PrintDocumentAdapter.onLayoutFinished` 3-argument overload

Consequences already handled in the code:

- Printer discovery uses `MdnsDiscovery` and manual entries instead of `getPrinters()`.
- Job tracking polls `getPrintJobs()` and reads `PrintJobInfo.getState()`.
- `PrintJob.systemJobKey` is replaced by a derived `String` key.
- Copies, scaling and margins are handled by our own renderer.

**Before using any platform API, verify it exists:**

```bash
.toolchain/jdk/bin/javap -cp .toolchain/android-sdk/platforms/android-35/android.jar <fqcn>
```

Several overloads are also missing that you would reasonably reach for. Known ones:
`Canvas.drawBitmap(Bitmap, Matrix, RectF, Paint)` and `RectF.minDimension()`. `ColorMatrix` has no
`postTranslate` or `postScale` — build a `ColorMatrix` and use `postConcat`.

## Code rules

- No comments in production code. The only exceptions are the two `//noinspection WrongConstant`
  directives required because the lint database disagrees with the compile-time API in this
  environment.
- No `TODO` or `FIXME`.
- All warnings are errors: Kotlin `-Werror` and lint `warningsAsErrors`.
- Detekt must report zero findings. If a rule is genuinely wrong for this codebase, relax the
  threshold in `config/detekt/detekt.yml` with a reason — do not annotate code to silence it.
- No wildcard imports, no global mutable state, no `runBlocking` on the UI thread, no reflection.
- Sealed types for every UI action and every error path.
- ViewModels emit message **keys**; `UiMessages` resolves them to string resources at the UI edge.

## Compose gotchas in this environment

- `ColorMatrix` composed via `postConcat` — see the note above.
- Extensions taking a `Context` must be called as `receiver.fn(context)`.
- `OptionChips.optionLabel` is `@Composable (T) -> String`; prefer enum `.label` properties.
- `Icons.AutoMirrored.*` is mandatory for directional icons under `-Werror`.
- Composable functions returning `Unit` must start with an uppercase letter; those returning a value
  must start lowercase. Android Lint enforces both.
- A `private fun PrivateEnum.extension()` in the same file failed to resolve under the K2 frontend
  here. Use a plain top-level function instead.
- `MaterialTheme.colorScheme` reads inside a `Canvas` draw lambda are not allowed; hoist them into
  local `val`s outside the lambda.
- Prefer `LocalWindowInfo.current.containerSize` or `BoxWithConstraints` over
  `Configuration.screenWidthDp`; lint rejects the latter.
- `Scaffold`'s default `contentWindowInsets` is the **system bars** only, which in portrait are
  entirely vertical. Passing its inner padding to a screen gives zero left and right margin and
  every row sits flush against the display edge.
- Do not *discard* `Scaffold`'s inner padding either: its `bottom` is the height of the bottom bar,
  not just the navigation bar inset, so replacing it with window insets buries the last item under an
  80 dp navigation bar. The shell sets `contentWindowInsets = WindowInsets(0, 0, 0, 0)` and builds
  padding in one place, `rememberScreenPadding`, from the inner padding **plus** the design margin.
  Do not reintroduce a second padding source.
- `PdfDocument.PageInfo.Builder` takes **points** (1/72 inch), not pixels. Rasters are in pixels at
  a DPI, so a pixel count makes the page `dpi / 72` times too large; draw the raster into the
  resulting page rect rather than 1:1.
- Custom icons must be **stroked, never filled**. Filled compound paths whose subpaths wind the same
  way union under the `NonZero` fill rule, and the glyph collapses into a solid block that draws
  correctly but is completely unreadable. Declare icons as `IconShape` data so
  `IconLegibilityTest` can measure them; do not convert them back to lambdas.
- Hit-test handles against the item's own border, and scale handle geometry with the item. A fixed
  grab radius swallows small images entirely, and separate drawing and hit-testing constants drift
  apart so the visible handle is not the grabbable one.
- `Modifier.pointerInput` must not be keyed on anything a gesture mutates, or the detector is torn
  down and rebuilt every frame, which disables the gesture silently.

## Layout conventions

- One `*ViewModel`, one `*UiState` and one sealed `*Action` per screen.
- All spacing comes from `DosPrintDimens`. Do not write raw `dp` literals in screens.
- Sections use `DosPrintSection` (title, subtitle, icon, trailing) or `ListItemRow`. Do not compose
  raw `Card`s with ad-hoc padding.
- Action groups use `UniformButtonRow` / `UniformButtonCell`, which wrap rather than overflow. Never
  place several equal-weight `Button`s in a `Row`; long labels clip and the group never adapts.
- Repositories are constructed in `di/AppContainer.kt`.
- Persisted models live in `data/local/` as `@Serializable` types with explicit `toDomain`/`toStored`
  mappers, so the domain layer stays framework-free.
- Tests are JUnit 4, one class per unit under test.

## Testing approach

The geometry-heavy domain is deliberately pure so it can be tested without Android. That paid off:
the tests caught five real bugs during this iteration, including a corner-resize that moved the
pinned anchor when the dragged corner left the page, and a `fromAspect` that did not preserve the
image's aspect ratio.

When adding a gesture or a snapping rule, add the arithmetic test first — assert the geometric
invariant, not the exact float output, using a tolerance appropriate to the operation.

## Adding a layout preset

Append to `LayoutPresetCatalog.all`:

```kotlin
LayoutPreset(
    id = "my_preset",
    label = "My layout",
    description = "What it is for.",
    category = LayoutCategory.STANDARD,
    grid = LayoutGrid(2, 3, gutterFraction = 0.03f),
    paperId = PaperSize.A4.id,
    borderless = false,
)
```

`PaperSizeCatalogTest.every preset paper id resolves` will fail if the `paperId` is unknown.

## Versioning

`VERSION_NAME` and `VERSION_CODE` live in `dos-print/gradle.properties`. The release APK path is
derived from `VERSION_NAME` by the copy task at the end of `build.gradle.kts`.
