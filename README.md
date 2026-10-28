# Dos Print

A production-grade Android printing app in Kotlin and Jetpack Compose (Material 3). It prints
documents from your device, gives you a real sheet-accurate print preview, and includes a layout
studio where you drag, scale, rotate and restyle photos and other media on a page before printing.

## Build

Everything is vendored. From the repository root:

```bash
./build.sh assembleDebug          # debug APK
./build.sh assembleRelease        # release APK; unsigned unless local signing files are configured
./build.sh testDebugUnitTest      # 170 unit tests
./build.sh detekt                 # static analysis, zero findings
./build.sh spotlessCheck          # formatting gate
./build.sh lintVitalRelease       # release lint gate
```

The workspace toolchain under `.toolchain/` is machine-local and is not included in this repository.
See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) and
[docs/BUILD_INSTRUCTIONS.md](docs/BUILD_INSTRUCTIONS.md) for required versions and setup. Release
signing credentials and keystores are also local-only; without them, the release APK is unsigned.

## Features

**Printing**
- PDF, image, text, Markdown and office documents.
- Paper size, orientation, margins, copies, colour mode, resolution, scaling, page range,
  duplex, collation, rotation, watermark, borderless.
- Every settings conflict is reported with the offending value and a suggested fix.
- Printer discovery over mDNS, USB and Bluetooth, plus manual entries. Capabilities are learned
  from live print-job attributes rather than a hardcoded table.

**Print preview**
- Sheets render at the real paper proportions, not the document's.
- Bounded pinch-zoom, pan, double-tap reset, and a thumbnail strip that reports how many sheets were
  actually rendered.

**Layout studio**
- Drag media onto a page with live snapping to centre lines, thirds, quarters and sibling edges.
- Pinch to scale, twist to rotate, corner handles to resize with the opposite corner pinned,
  and an aspect lock so images never distort.
- Per item: fit mode, opacity, flip, rounded corners, border, shadow, and ten adjustments
  (brightness, contrast, saturation, warmth, sharpen, blur, monochrome, sepia, fade, vignette).
- Distribute along either axis, duplicate, lock, reorder.
- 60-step undo and redo, debounced autosave.
- The editor and the print output share one rasteriser, so what you see is what prints.

**Page layout presets**
- 20 presets modelled on a desktop print dialog, grouped as page layout, photo, labels and cards,
  booklets, and everyday documents — 1 to 16 pages per sheet, contact sheets, business cards,
  address labels, CD labels, envelopes, flyers, posters, book fold.
- Each preset shows a picture of the finished page. Paper size, grid, gap, page margin and item
  outline are all adjustable with visual pickers.

## Quality bar

- Zero detekt findings, zero lint warnings, clean Spotless, `-Werror` Kotlin.
- 170 unit tests over grid geometry, normalised coordinates, Studio interaction maths, paper
  catalogue, presets and settings clamping.
- No TODOs, no comments in production sources.
- No ad or analytics SDKs. No network access beyond printer discovery and job status.

## Documentation

| Document | Contents |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | Layers, the single-render-path design, interaction model, performance, error strategy |
| [Development](docs/DEVELOPMENT.md) | Toolchain, commands, code rules, environment constraints, Compose gotchas |
| [User guide](docs/USER_GUIDE.md) | End-user walkthrough of printing, preview and the studio |
| [Build instructions](docs/BUILD_INSTRUCTIONS.md) | Prerequisites, build steps, install |
| [Changelog](docs/CHANGELOG.md) | Release notes and known limitations |
| [Agent notes](docs/AGENTS.md) | Repository map and build entry point |
| [Reference analysis](docs/REFERENCE_ANALYSIS.md) | Findings used to inform the printer and rendering design |

## Layout

```
dos-print/                       Application module
  build.gradle.kts               Build config: AGP 8.10, Kotlin 2.1, Compose BOM
  gradle/libs.versions.toml      Pinned dependency catalog
  config/detekt/detekt.yml       Static analysis rules
  config/signing/                Local-only release signing files
  src/main/kotlin/com/dosprint/
    core/                        Errors, Outcome, logging, crash handling
    domain/model/                Entities, settings, layout catalog, studio geometry
    domain/repository/           Repository interfaces
    data/media/                  Page sources, image pool, image effects
    data/studio/                 Shared studio rasteriser, studio repository
    data/print/                  Print adapters, composer, page planner, job monitor
    data/printer/                mDNS discovery, printer repository
    data/render/                 Print preview renderer
    ui/                          Screens, ViewModels, components, theme, navigation
  src/test/kotlin/               170 unit tests
docs/                            Project guides and agent notes
```

The local toolchain, signing files, NokoPrint reference archive and generated build outputs are
excluded from version control.
The library that makes drag-and-drop viable is `MediaImagePool` plus the normalised-coordinate
model described in [the architecture guide](docs/ARCHITECTURE.md).
