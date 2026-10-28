# NokoPrint Reference Analysis — Executive Summary

Source artifact: `com.nokoprint-5.28.4.apk` (57 MB, 8 dex files, 1900 entries, no native libs)
Decompiled with JADX 1.5.6 into `jadx/` (44,086 Java files) and raw-extracted into `apk-contents/`.

Detailed findings are split across three documents:

| Document | Covers |
|---|---|
| [`01-core-print-engine.md`](reference/analysis/01-core-print-engine.md) | Print pipeline, pagination, layout math, job state, settings persistence, document parsing, 24 ranked defects, threading |
| [`02-printer-discovery-networking.md`](reference/analysis/02-printer-discovery-networking.md) | mDNS/WS-Discovery/BJNP/SNMP/SMB/BT/USB discovery, IPP implementation, capability model, connection lifecycle, 23 ranked defects |
| [`03-ui-resources-manifest.md`](reference/analysis/03-ui-resources-manifest.md) | Manifest inventory, layout analysis, user flows, settings UI, localisation audit, ad footprint, static defects |

---

## 1. Decompiled structure

```
nokoprint-reference/
├── com.nokoprint-5.28.4.apk        (input)
├── apk-contents/                   raw APK extraction (res, assets, resources.arsc, 8 dex)
├── jadx/
│   ├── sources/                    44,086 .java files
│   │   ├── com/nokoprint/          403 files, 227,903 LOC — the app
│   │   │   ├── bjnp/  bt/  core/  discovery/  ipp/  mdns/  smb/  snmp/
│   │   ├── com/{facebook,applovin,bytedance,chartboost,google,unity3d,…}  (ad SDKs)
│   │   └── androidx/  kotlin/  kotlinx/  okhttp3/  …
│   └── resources/                  decoded AndroidManifest.xml (1759 lines) + res/ + assets/
├── analysis/
│   ├── 01-core-print-engine.md
│   ├── 02-printer-discovery-networking.md
│   └── 03-ui-resources-manifest.md
└── REFERENCE_ANALYSIS.md           (this file)
```

Package layout in the app's own code:

| Package | LOC scale | Responsibility |
|---|---|---|
| `com.nokoprint.core` | largest | `u` = print worker (23k lines), `h` = `PrinterDiscoverySession`, `NativePrintService`, `PdfRender`/`DocsRender` JNI bridges, `DrvWrapper` |
| `com.nokoprint.discovery` | large | `f` (mDNS+BJNP), `l` (WSD+TPL), `i` (SNMP), `b` (Bluetooth), `o` (USB), `j` (WSD resolver) |
| `com.nokoprint.ipp` | medium | `d` = IPP message, `b` = attribute, `c` = chunked decoder, `a` = base64 |
| `com.nokoprint.mdns` | medium | `b` = DNS decoder, `c` = DNS encoder, `d` = query builder |
| `com.nokoprint.smb` | large | stripped jCIFS fork: SMB1 + NTLM + DCE/RPC + NetBIOS |
| `com.nokoprint.snmp` | small | BER encode/decode |
| `com.nokoprint.bt` | small | reflective classic-Bluetooth RFCOMM |

---

## 2. The single most important architectural finding

**NokoPrint is a `PrintService`, not a `PrintDocumentAdapter` producer.** There is no
`PrintManager.print(...)`, no `PrintJobInfo.Builder`, no `onLayout`/`onWrite`/`onFinish` anywhere
in the APK. The app registers `NativePrintService` (102 lines) with `BIND_PRINT_SERVICE`,
publishes exactly one synthetic `PrinterInfo` whose capabilities are derived from a hardcoded
driver table, receives `PrintJobInfo` from the system print dialog, copies the spooled PDF to a
fixed cache path, launches an Activity, and calls `printJob.complete()` **unconditionally**.

Everything outbound is hand-rolled: raw sockets speaking LPD, PDL/JetDirect, BJNP, IPP (correct
RFC 8010 encoding, only `Print-Job` and `Validate-Job`), WSD SOAP, SMB via a forked jCIFS, USB via
a `Socket` subclass, and ~20 vendor rasterisers plus four native `DrvWrapper` `.so` libraries
downloaded at runtime.

**Consequence for Dos Print:** the reference is authoritative on *user-visible feature surface*
and *failure modes*, and largely irrelevant as a *code* model. Dos Print deliberately uses the
opposite submission architecture (see §6).

---

## 3. How NokoPrint achieves each capability

### 3.1 Multi-printer support and discovery

Seven parallel scanner threads, all Activity-scoped, all cancelled in `onDestroy()`:

| Transport | Mechanism | Ports |
|---|---|---|
| mDNS/DNS-SD | custom DNS encoder/decoder, PTR → SRV → A/TXT walk | 5353 |
| WS-Discovery | SOAP Probe over UDP multicast, then WS-Transfer Get | 3702 |
| BJNP (Canon) | proprietary UDP broadcast + binary blob parse | 8611 |
| "TPL"/KC300 | `"WHERE ARE YOU KC300"` probe, 34 bound sockets, TCP 20005 handshake | 9303, 30202, 7410–7440, 7472 |
| SNMP | SNMPv1 GET, community `public` | 161 |
| SMB | `__MSBROWSE__` + `srvsvc` share enum, then `spoolss` `EnumPrinters` | 137/139/445 |
| Bluetooth | classic RFCOMM, `Major.IMAGING` class filter | – |
| USB | `UsbManager` + `controlTransfer` descriptor read | – |

IPv4 directed-broadcast addresses are computed from interface netmasks rather than relying on
`255.255.255.255`, which many APs drop.

### 3.2 File type compatibility

PDF and Office documents are rendered by **two native libraries downloaded at runtime**
(`lib_pdfrender` = pdfium, `lib_docsrender` = a custom engine) into `s2`/`w2` page objects.
Images use `BitmapFactory` + EXIF matrices. HTML uses `WebView.capturePicture()`. Supported MIME
types are enumerated in `App.u()`.

### 3.3 Print job configuration

Fourteen modal dialogs dispatched from one 14-case switch in `b1.java`: paper size/type/tray,
orientation, output mode, output colour, margins (5 levels), 9-way position, copies, collate,
page selection (all/odd/even/selected/range/reverse), multipage 2/4/6/9-up, poster 2×2/3×3/4×4 +
cut marks, booklet binding + sets, duplex (8 modes), image scaling/size/grid.

Attributes resolve by "last match wins → printer default → hard-coded fallback". `PrintJobInfo`
from the system dialog is consumed for copies, media id and duplex only — **page ranges, colour
mode and resolution are ignored**.

### 3.4 Print queue management

There is no queue. Job state is a private 5-value `Message.what` protocol (`r1.java`) plus a
`boolean[1]` cancel sentinel polled 30+ times inside the worker. No history, no resume, no retry.

### 3.5 UI patterns for print settings

Views/XML, dual-inflate activity shell (`ActivityRoot.t(bar, main)`), 16 `dialog_*` layouts
driven by a single switch, a per-content-type preference namespace derived from the class name
(`activity_print_docs#paper`), and a single `ActivityRoot.setContentView` override for window insets.

### 3.6 Error handling and crash recovery

`e2.printStackTrace(); App.w(null, e2);` — `App.w` forwards to Crashlytics. There is no
user-facing error taxonomy, no retry policy, and in several places no user-facing error at all
(render failures print blank pages silently). There is **no** `printJob.fail()` in the APK.

### 3.7 Performance optimisation

Band-wise rasterisation with an OOM ladder (halve the band, rethrow below 64 px),
`inSampleSize` power-of-two doubling for images, a byte-counting `OutputStream` for the PDF
writer, `Debug.setMinimumHeapSize`, `largeHeap="true"`, and `App.c()` (3 forced GCs + 200 ms of
sleep) before every memory-sensitive stage — including on the UI thread.

---

## 4. Critical areas — what actually breaks

Ranked across all three analyses; the CRITICAL items are reproduced in full detail in the
per-topic documents.

| # | Severity | Area | Defect |
|---|---|---|---|
| 1 | CRITICAL | Job lifecycle | `printJob.complete()` on every path including failure; no `printJob.fail()` exists |
| 2 | CRITICAL | Job lifecycle | `ParcelFileDescriptor` from `getDocument().getData()` never closed → 2–3 FDs leaked per job |
| 3 | CRITICAL | Concurrency | Fixed temp filenames (`nokoprint_temp_print.pdf`, `nokoprint_out.tmp`, `nokoprint.tmp`) plus **static single-slot** USB socket arrays — concurrent jobs corrupt each other |
| 4 | CRITICAL | Blocking | `"NOKOPRINT_USB_PERMISSION".wait()` with no timeout on an interned `String` monitor → print thread hangs forever, uncancellable |
| 5 | CRITICAL | Exceptions | `throw null` in 20+ places (`a0.java`, `x.java`) destroys the stack trace; a `Socket` subclass constructor can return with null `final` fields |
| 6 | CRITICAL | Discovery | Unbounded busy-spin in the SNMP GET retry loop: `do { ... } while (result == null)` where `result` is only assigned inside a `try` → 100 % CPU forever after cancellation |
| 7 | CRITICAL | Discovery | `while (true) { try { read() } catch (SocketTimeoutException ignored) {} }` — loops escapable only by EOF; on socket close it becomes a hot spin |
| 8 | HIGH | Job config | System-dialog copies unclamped; `pages × copies` pre-allocated into a `Vector` → OOM/ANR class |
| 9 | HIGH | Job config | System-dialog duplex handled only for constants 2 and 4; short-edge (3) and flip-long-edge (5) silently become single-sided |
| 10 | HIGH | Page ranges | `i0()` collapses to an empty selection if *any* token is bad; no open-ended ranges, no dedup, no overflow guard on `"1-2000000000"`; the bad value is applied anyway after showing an error |
| 11 | HIGH | Rendering | `getPageSize` return values discarded → `{0,0}` page sizes → zero-area bitmaps and divide-by-zero |
| 12 | HIGH | Rendering | PDF renderer catches `Throwable` and prints a blank page; indistinguishable from a legitimately blank page |
| 13 | HIGH | Memory | Recycled preview bitmaps without an `isAttachedToWindow` guard → `Canvas: trying to use a recycled bitmap` |
| 14 | HIGH | Download | ELF `DT_NEEDED` recursion with the visited-set written *after* recursion → `StackOverflowError`; `System.load` + `Class.forName` on a filename-derived string makes a download executable |
| 15 | HIGH | Threading | ~30 `Thread.yield()` spin loops on `isAlive()` that never check the interrupt flag — uncancellable 100 % CPU |
| 16 | HIGH | Threading | 200 ms of `Thread.sleep` + 3 forced GCs **on the UI thread** every time the user taps Print |
| 17 | HIGH | Discovery | mDNS listeners never `joinGroup()` → all unsolicited advertisements silently dropped |
| 18 | HIGH | Discovery | `BroadcastReceiver` leaked on the Bluetooth error path; USB handles leaked on exception paths behind a single-static-slot watchdog |
| 19 | MEDIUM | Security | Process-wide trust-all `SSLSocketFactory` + wildcard hostname verifier installed in `Application.onCreate` |
| 20 | MEDIUM | Security | Hidden-API reflection into `URI.AUTHORITY_ENCODER` to make `user:pass@host` URIs parse |
| 21 | MEDIUM | Security | `printer_login`/`printer_password`/`smb_auth` in cleartext `SharedPreferences` |
| 22 | MEDIUM | Parsing | `Integer.parseInt` on provider-supplied strings at the top of 9 renderers; values include `"iso_a4_210x297mm"` and `null` |
| 23 | MEDIUM | Parsing | Unchecked `split("\\|")[3]` on discovery-derived driver spec → AIOOBE before any output |
| 24 | MEDIUM | State | Shared capability objects mutated in place (`lVar2.f45482e = lVar2.f`) leaking into every other consumer |

---

## 5. What Dos Print adopts, and what it replaces

### 5.1 Adopted conceptually

| Reference pattern | Dos Print treatment |
|---|---|
| Content pages vs. sheets separation | **Kept.** `PrintPagePlan` / `PrintSheet` / `PageRef` mirror the two-stage model, but as immutable data with pure transforms and no shared `Vector`. |
| Page-range grammar + hint | **Kept.** The `"1, 3–5, 8"` hint style is used verbatim in `setup_page_range_hint`. |
| Per-printer settings keys | **Improved.** Settings are keyed per printer id in a versioned codec, not per activity class name. |
| Print-settings option surface | **Kept and extended.** The reference's 14 dialogs are unified into one basic/advanced sheet; watermark and borderless are added (absent in the reference). |
| "last match wins → default → fallback" attribute resolution | **Kept** in `PrinterCapabilities.closestPaperSize` / `closestColorMode` / `closestDuplex`, but each fallback is *reported* to the user as a warning instead of applied silently. |
| Band-wise rasterisation with an OOM ladder | **Improved.** Enforced by an explicit `PrintQualityProfile` pixel budget with a bitmap-pixels ceiling, rather than by catching `OutOfMemoryError`. |
| Consuming `PrintJobInfo` from the system dialog | **Kept** — `DosPrintDocumentAdapter.onLayout` honours the platform-provided media size, duplex, colour mode, copies and page ranges. |

### 5.2 Replaced

| Reference | Dos Print |
|---|---|
| Own `PrintService` publishing one fake `PrinterInfo` with a hardcoded driver table | Submits jobs through `PrintManager` with a real `PrintDocumentAdapter`; enumerates real printers via `PrintManager.getPrinters()` and supplements them with `NsdManager` mDNS discovery. |
| Hardcoded capability tables derived from model-string matching | `PrinterCapabilities` built from `PrinterCapabilitiesInfo` (real media sizes, resolutions, colour modes, duplex modes, max copies). Unknown attributes fall back to a conservative default and are surfaced to the user. |
| Hand-rolled IPP/LPD/PDL/BJNP/WSD/SMB/USB transports | Only `PrintManager` + `PrintDocumentAdapter`. Transport correctness becomes the platform's problem. |
| Runtime-downloaded native renderer libraries | Platform `PdfRenderer`, `BitmapFactory` + `ExifInterface`, `StaticLayout` for text, and a zip/XML text extractor for OOXML + ODF. No JNI, no `System.load`, no executable downloads. |
| `Message.what` progress protocol, `boolean[1]` cancel sentinel, no persistence | `StateFlow` job state, persisted `PrintJob` history via an atomic JSON store, `CancellationSignal` from the framework plus cooperative cancellation in the write loop. |
| SharedPreferences-only persistence, plaintext credentials | DataStore preferences for settings, atomic JSON stores for history/presets/printers, **no credentials stored at all**. |
| Views/XML, phone-only, 44 dp touch targets, unlabelled icon buttons | Compose + Material 3, `WindowSizeClass`-driven adaptive layouts, 48 dp minimum targets, content descriptions on every control. |
| 24 ad networks (3352 `com.facebook` classes, 1980 `sg.bigo.ads`, ≥1200 `com.bytedance` …) | Zero ad SDKs, zero analytics, zero trackers, no `QUERY_ALL_PACKAGES` need. |
| No print history, no watermark, no presets | Full job history with retry/cancel, watermark support, named presets. |

---

## 6. Dos Print architecture decisions driven by this analysis

1. **Submit through `PrintManager`, not as a `PrintService`.** The reference's architecture is
   forced by its need to talk raw sockets to ~20 printer languages; Dos Print has no such need, so
   inheriting it would be pure cost.
2. **Never call `complete()` unconditionally.** Job success/failure is derived from the framework's
   own job state plus the adapter's `WriteResultCallback` outcome, and always recorded.
3. **Never use fixed temp filenames.** Every write goes to the framework-provided
   `ParcelFileDescriptor`; the single scratch file (`PreviewCache`) is per-cache-entry.
4. **Always honour `CancellationSignal`.** Checked per sheet in the write loop.
5. **Never `wait()` without a timeout, never sleep in `close()`, never spin on `isAlive()`.**
   Discovery runs on a coroutine with `withTimeoutOrNull`; sockets are closed via `use{}`.
6. **Never mutate a shared capability object.** `PrinterCapabilities` is an immutable snapshot
   rebuilt per query.
7. **Validate every page-range token independently** and clamp to the real page count; cap total
   pages; reject rather than silently empty.
8. **Never discard a size return value.** `PageGeometry` is computed from real page dimensions and
   coerced to ≥1.
9. **Never swallow a render failure silently.** A page that cannot be rendered produces an explicit
   error, not a blank sheet.
10. **Install no global TLS overrides, store no credentials, no `System.load`.**
11. **Recompose-proof bitmap lifecycle.** Preview bitmaps are owned by a cache with an explicit LRU
    bound and are never recycled while a view can still reference them.
12. **Keep the UI thread free.** Every file, render and history operation is on
    `Dispatchers.IO`/`Default`; no `System.gc()`, no `Thread.sleep`.

---

## 7. Concrete improvements over the reference, by task area

| Area | Reference | Dos Print |
|---|---|---|
| Submission | Fake single printer via `PrintService`; unconditional `complete()` | Real `PrintManager` + `PrintDocumentAdapter`; explicit success/failure tracking |
| Capabilities | ~500 hardcoded model strings; resolution/colour/duplex invented | Real `PrinterCapabilitiesInfo`; unsupported options surface a warning naming the conflict |
| Validation | One generic "conflicting settings:" message with an appended list | Per-option validation with named conflicts and a suggested alternative |
| Page ranges | All-or-nothing parse; bad value applied after error shown | Per-token validation, clamping, dedup, page-count bound, explicit error |
| Large files | Full page-metadata materialisation up front; fixed temp files | Streaming sheet-by-sheet writes with a pixel budget; framework-provided FD |
| Discovery | 7 thread-based scanners, Activity-scoped, no `joinGroup`, unbounded thread creation | Single coroutine-bounded mDNS scan + `PrintManager.getPrinters()`; no manual socket probing |
| Cancellation | `boolean[1]` polled at render-stage boundaries only | `CancellationSignal` + cooperative checks per sheet |
| History | None | Persisted history with retry, cancel, per-printer settings recall |
| Presets | None | Named presets, usage-tracked |
| Watermark | Absent | Text/Draft/Confidential, rotated, drawn per sheet |
| N-up | 2/4/6/9-up + poster + booklet imposition | 1/2/2-wide/4/6/9-up, layout validated against printer capability |
| UI | Views, phone-only, 44 dp targets, unlabelled icons, 93 % vendor layouts | Compose + M3, `WindowSizeClass` adaptive, 48 dp targets, full content descriptions |
| Errors | `printStackTrace()` + Crashlytics; user sees "Invalid input" | `AppError` taxonomy with a user-facing message per error, file log + shareable report |
| i18n | Russian string in the default `strings.xml`; `values-zh` has no app strings; Thai false-friend | Single default (English) file, no vendor strings to leak, lint enforces no hardcoded text |
| Permissions | ~28 including location-for-BT, USB, notifications | Only what is used: internet/network/wifi/multicast, media read (image picker), notifications, foreground data-sync, Bluetooth scan/connect, USB |
| Size | 57 MB, 44k classes, 541 layouts (504 vendor) | Single module, zero ad SDKs, R8 + resource shrinking |

---

## 8. Notes and caveats on the analysis

* Findings come from a decompilation, not from source. `core/u.java` (23k lines) contains large
  regions where JADX emitted instruction dumps rather than structured code; claims derived from
  those regions rest on *signatures, string constants and allocation sizes*, not on control flow,
  and are flagged inline in `01`.
* Three candidate defects (D-21 in doc 01, D-21 in doc 02, the `discovery/i.run()` inverted stop
  condition) show mutually inconsistent decompiled output and are explicitly marked as needing
  re-verification rather than asserted.
* Counts of ad-SDK classes marked `>` or `≥` hit the enumeration ceiling before exhausting the
  tree; they are lower bounds.
* No code was executed from the APK. Analysis is static and read-only.
