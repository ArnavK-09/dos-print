# 03 — UI, Resources & Manifest of `com.nokoprint` (NokoPrint 5.28.4)

Decompiled with JADX. All paths relative to `/home/dopamide/Projects/DosPrint/nokoprint-reference/`.
All counts are measured from the decompiled tree; where a count is a lower bound it is written as `≥ N`.

---

## 1. Manifest inventory

Source of truth: `jadx/resources/AndroidManifest.xml` (1759 lines).

### 1.1 Identity and SDK levels

| Attribute | Value |
|---|---|
| `package` | `com.nokoprint` |
| `versionName` | `5.28.4` |
| `versionCode` | `52804` |
| `minSdkVersion` | `32` (Android 12L) |
| `targetSdkVersion` | `36` |
| `android:name` (application) | `com.pairip.application.Application` |
| `android:appComponentFactory` | `androidx.core.app.ComponentFactory` |
| `android:largeHeap` | `true` |
| `android:usesCleartextTraffic` | `true` |

Two facts dominate every other observation:

* `minSdk 32` excludes the entire Android 8–11 install base. Nothing in the code needs API 32 — this is an install-base-filtering decision, not a technical requirement.
* The declared `Application` is **not** the app's own class. It is the PairIP licence wrapper, which extends the real one.

`jadx/sources/com/pairip/application/Application.java`
```java
public class Application extends com.nokoprint.App {
    protected void attachBaseContext(Context ctx) {
        super.attachBaseContext(ctx);
        com.pairip.licensing.LicenseClient.checkLicense(...);
    }
}
```

`jadx/sources/com/nokoprint/App.java` (the real `onCreate`):

```java
FirebaseRemoteConfig.getInstance().setDefaultsAsync(
    new FirebaseRemoteConfig.Builder()
        .putString("ads_applovin_*", ...)
        .putString("ads_admob_*",   ...).build());
Debug.setMinimumHeapSize(4194304);
StrictMode.setVmPolicy(...);
HttpsURLConnection.setDefaultSSLSocketFactory(...);
if ("ca".equals(lang)) lang = "es";
TikTokBusinessSdk.init(...);
```

Four notable consequences:

1. Ad unit IDs are seeded from Firebase Remote Config, then overridden by hard-coded per-activity constants. Two sources of truth for the same values.
2. `setMinimumHeapSize(4194304)` is an emulator-only knob — dead code on real hardware.
3. Language `"ca"` (Catalan) is forcibly remapped to `"es"`.
4. `HttpsURLConnection.setDefaultSSLSocketFactory(...)` mutates JVM-global TLS state from `Application`, so every library in the process inherits it.

### 1.2 Components declared by the app itself

| # | Component | Type | Trigger |
|---|---|---|---|
| 1 | `com.nokoprint.ActivityHome` | activity | `MAIN` + `LAUNCHER`; only entry point |
| 2 | `com.nokoprint.ActivityPrintPhotos` | activity | `VIEW` + `SEND` (+`SEND_MULTIPLE`) — image/* |
| 3 | `com.nokoprint.ActivityPrintDocs` | activity | `VIEW` + `SEND` (+`SEND_MULTIPLE`) — documents |
| 4 | `com.nokoprint.ActivityWeb` | activity | internal web surfaces |
| 5 | `com.nokoprint.ActivityDevices` | activity | printer list (WiFi / BT / USB tabs) |
| 6 | `com.nokoprint.ActivityPrintNative` | activity | consumes `PrintJobInfo` from the framework dialog |
| 7 | `com.nokoprint.ActivityPrintWeb` | activity | print from an in-app `WebView` |
| 8 | `com.nokoprint.ActivityDrivers` | activity | on-demand driver-pack downloads |
| 9 | `com.nokoprint.ActivityPreview` | activity | preview / rasterise step |
| 10 | `com.nokoprint.ActivityUSB` | activity | `USB_DEVICE_ATTACHED` |
| 11 | `com.nokoprint.AppStoreRustoreActivity` | activity | `nokorustore://` scheme |
| 12 | `com.nokoprint.ActivityEndisableService` | activity | Huawei HMS prompt |
| 13 | `com.nokoprint.core.NativePrintService` | service | `android.printservice.PrintService` |

**12 app activities + 1 app service.** The app's own manifest surface is tiny; everything else belongs to third parties (AppLovin MAX, AdMob, Pangle/ByteDance, Chartboost, IronSource, Unity, Meta, Vungle, Mintegral, Moloco, PubMatic/OpenWrap, Smaato, PubNative, Ogury, Tappx, AppBrain, Digital Turbine, Inneractive, Verve — plus Firebase).

Print attribute construction — `jadx/sources/com/nokoprint/core/h.java` (`PrinterDiscoverySession`):

```java
builder.setColorModes(2, 2);
builder.setDuplexModes(7, 1);
builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS);
builder.addResolution(new PrintAttributes.Resolution("id", "label", 600, 600));
builder.addMediaSize(new PrintAttributes.MediaSize(id, label, wMils, hMils, isDefault));
```

`setMinMargins(NO_MARGINS)` advertises zero hardware margin — i.e. the app handles margins itself, which is why it carries its own margin/position UI.

### 1.3 Permissions, features, queries

| Group | Count | Comment |
|---|---|---|
| `<uses-feature>` | 9 | all `required="false"` |
| `<uses-permission>` | ~28 | Bluetooth scan/connect, USB, location, notifications |
| `<queries><package>` | ~190 | banking packages (CafeBazaar in-app billing) |
| Ad-SDK `<meta-data>` | dozens | per-network SDK keys |

All 9 features being optional is a defensible choice: one universal APK that prints over network, BT *or* USB, filtered nowhere.

---

## 2. UI framework

### 2.1 Views/XML, not Compose

The app is **100 % View-based**. No Compose runtime, no `@Composable`, no `ComposeView`. The only Compose-adjacent artefact is `META-INF/lifecycle-runtime-compose_release.kotlin_module`, pulled in transitively by the ad SDKs.

### 2.2 Layout counts

| Resource dir | Files | Composition |
|---|---|---|
| `res/layout` | **541** | ~37 app-owned + 504 vendor |
| `res/layout-land` | **18** | 100 % vendor — **0 app layouts** |
| `res/layout-sw600dp` | **4** | 100 % vendor — **0 app layouts** |
| `res/layout-watch` | **2** | `abc_*` only |

The 37 app-owned layouts: `activity_*_bar` (4), `activity_*_main` (4), `activity_preview_main`, `activity_endisable_service`, `dialog_*` (16), `list_item_*` (6), `mainlayout`, `custom_dialog`, `image_frame`, `loading_alert`, `expand_button`, `dt_store_promo_layout`, `offline_ads_dialog`.

**~93 % of shipped layout resources are third-party.**

### 2.3 Shared activity shell

Every screen inherits `com.nokoprint.ActivityRoot`, which inflates two layouts and stitches them:

```java
protected void t(int barLayout, int mainLayout) { ... }   // dual inflate
public void w(boolean showAds) { ... }                   // ad_container logic
public   void q() { ... }                                   // hide ads
public   void setContentView(View v) { ... }                // window-insets listener
```

`setContentView(View)` is overridden to attach a `WindowInsets` listener — hand-rolled edge-to-edge rather than `enableEdgeToEdge()`.

| Activity | Bar layout | Main layout |
|---|---|---|
| `ActivityHome` | `activity_home_bar` | `activity_home_main` |
| `ActivityDevices` | `activity_devices_bar` | `activity_devices_main` |
| `ActivityPrint` | `activity_print_bar` | `activity_print_main` |
| `ActivityWeb` | `activity_web_bar` | `activity_web_main` |
| `ActivityPreview` | — | `activity_preview_main` |

### 2.4 Ad slots

Ad slots are plain `FrameLayout`s whose `android:tag` carries a size contract (`"VH"`, `"350|320"`), created `visibility="gone"` and shown only when an ad fills — a clean way to avoid layout gaps.

---

## 3. Main user flows

### 3.1 Print a photo

```
ActivityHome → ActivityPrintPhotos → ActivityPreview → ActivityDevices
             → ActivityDrivers (~5 MB) → ActivityPrint → NativePrintService
```

### 3.2 Print a document

```
ActivityHome → ActivityPrintDocs → (~60 MB rendering library download)
             → ActivityPreview → ActivityDevices → ActivityDrivers → ActivityPrint
```

Both downloads are gated by a size-disclosure dialog — good practice.

### 3.3 Print from the Android print dialog

`ActivityPrintNative` receives `PrintJobInfo` from the framework dialog and maps it onto the app's settings model:

```java
int duplex = jobInfo.getDuplexMode();
switch (duplex) {
    case 2: appDuplex = DuplexNoTumble;   label = "Long Edge";  break;
    case 4: appDuplex = DuplexTumble;     label = "Short Edge"; break;
}
int copies = jobInfo.getCopies();
String mediaId = jobInfo.getAttributes().getMediaSize().getId();
```

This is the highest-value interop path in the app and the first thing to reimplement.

### 3.4 Discovery and driver resolution

* **WiFi** — mDNS/SSDP in `core/`.
* **Bluetooth** — classic SPP; requires location permission, explained well in-dialog.
* **USB** — `ActivityUSB` registered for `USB_DEVICE_ATTACHED`, claims the printer on plug-in.

Driver resolution has a two-tier fallback with explicit honesty about guessing:

```xml
<string name="dialog_printer_setup_text_no_driver">We don't have driver for your
printer or can't match it automatically. Please select it manually.</string>

<string name="dialog_printer_setup_text_generic_driver">We don't have a driver
for your printer or can't match it automatically, but we've found a generic
driver that can work with your printer. You can try it or select a driver
manually.</string>
```

### 3.5 Monetisation

`<string name="dialog_ads_button_pause_text">Watch one video ad to the end and enjoy ad-free printing for one hour.</string>` — a rewarded video unlocks an ad-free hour.

### 3.6 Flows that do **not** exist

* **No print-history screen.** No queue, no history, no retry-from-history.
* **No watermark option** — the feature is not exposed at all.
* No firmware update, no account, no sync, no cloud spooler.

---

## 4. Print-settings UI

### 4.1 Structure

* `ActivityPrint.l0()` — main Print Settings list
* `ActivityPrint.k0()` — paper size / tray / type / duplex / orientation
* `ActivityPrint.f0()` — size and layout options
* `ActivityPrint.j0()` / `i0()` — page-range modal and parser

`jadx/sources/com/nokoprint/b1.java` is a 14-case switch dispatching every settings dialog: copies, collate, multipage, duplex, orientation, margins, position, paper size, paper type, paper tray, output mode, output color, +2.

### 4.2 Complete option inventory

**Paper**: size, type, tray, orientation (auto/portrait/landscape).
**Output**: output mode, output color.
**Margins**: `none`, `by_printable_area`, `narrow`, `normal`, `wide`.
**Position**: 9-way grid (center, 4 corners, 4 edges).
**Copies/pages**: copies, collate, all pages, odd, even, selected, range (hint `"1, 3–5, 8"`), reverse order.
**Multipage**: single, multiple pages, poster, booklet; 2/4/6/9-up; order (right_left, bottom_top, border_lines); poster 2x2/3x3/4x4 + cut marks; booklet binding left/right + sets.
**Duplex**: 8 modes including manual front/back.
**Photo**: image scaling, image size, images per page.

This is a very complete surface — feature-for-feature what the framework dialog offers, plus booklet binding and poster cut marks. The cost is complexity: 14 dialogs from one obfuscated switch with no state machine, no undo, and no validation beyond "conflicting settings".

### 4.3 The one validation path

```xml
<string name="dialog_incompatible_settings_text">Unable to print. There are one
or more conflicting settings:</string>
```

The dialog appends a computed list, but never names *which* setting conflicts with *which* or how to resolve it. This is the single biggest UX gap in the print flow.

---

## 5. String resources and localisation

| Metric | Value |
|---|---|
| `strings.xml` files | **28** = default + 27 locales |
| Default `values/strings.xml` | **1077 lines** |
| `values-en/strings.xml` | **157 lines** |
| Locales with app strings | **25** |
| Locales without app strings | **2** — `values-en`, `values-zh` |

### 5.1 The `values-zh` defect

`values-zh` contains **only** vendor ad strings (`applovin_*`, `mbridge_*`, `tt_*`) — no app strings at all. A user whose locale is plain `zh` gets an **English printing app with Chinese ads**. This is a shipping bug.

`values-en` is equally redundant: the default `values/` is already English, so the 157-line `values-en` guarantees future English strings will silently drift.

Verified coverage of print terminology across locales:

| Locale | `menu_settings_duplex_both_sides_long_edge` |
|---|---|
| `values-de` | `Lange Kante` |
| `values-fr` | `Bord long` |
| `values-ru` | `По длинному краю` |
| `values-tr` | `Uzun kenar` |
| `values-ja` | `長辺綴じ` |
| `values-th` | `แนวตั้ง` ← **means "Portrait"** |

The Thai entry is a direct false-friend with `menu_paper_orientation_portrait`. No linter catches this class of error.

### 5.2 Defects in the resource pipeline

* `default_open_error` is **Russian in the default file** (`Не удалось открыть приложение`); only `values-en` has the English, so most users see a Russian error.
* Legacy locale codes `iw` (Hebrew) and `in` (Indonesian) are still shipped.
* `"ca"` (Catalan) force-remapped to `"es"` at runtime.
* `values-land/dimens.xml` contains only `abc_`/`material_`/`mtrl_` entries — **zero app dimens**.

### 5.3 Dimension defect

`res/values/dimens.xml`: `<dimen name="item_min_height">44dp</dimen>` — Material Design **1** minimum. Material 2 and current accessibility guidance require 48dp. Every list row is 4dp short.

### 5.4 Verbatim error strings

| Key | Value |
|---|---|
| `default_error_message` | `Invalid input` |
| `default_open_error` | `Не удалось открыть приложение` (Russian in default file) |
| `dialog_incompatible_settings_text` | `Unable to print. There are one or more conflicting settings:` |
| `dialog_action_required_text_access_location` | `Access location permission is required for discovering Bluetooth devices. Android only allows this if location services are enabled. Please grant the corresponding permission when prompted. You may revoke the permission after the setup completes.` |
| `dialog_action_required_text_install_drivers_pack` | `In order to print you need to download and install drivers pack. The download size is about 5 Mb.` |
| `dialog_action_required_text_install_rendering_library` | `In order to open documents you need to download and install docs rendering library and fonts. The download size is about 60 Mb.` |
| `dialog_printer_setup_text_no_driver` | `We don't have driver for your printer or can't match it automatically. Please select it manually.` |
| `dialog_printer_setup_text_generic_driver` | `We don't have a driver for your printer or can't match it automatically, but we've found a generic driver that can work with your printer. You can try it or select a driver manually.` |
| `message_incorrect_page_range` | `Empty or incorrect page range` |
| `dialog_ads_button_pause_text` | `Watch one video ad to the end and enjoy ad-free printing for one hour.` |

The Bluetooth-location explanation is the best string in the app: it tells the user *why*, that location *services* must be on, and that the permission can be revoked afterwards.

---

## 6. Advertising & monetisation footprint

| Network | Package prefix | Class count |
|---|---|---|
| Meta Audience Network | `com.facebook` | **3352** |
| BIGO Ads | `sg.bigo.ads` | **1980** |
| Pangle / ByteDance | `com.bytedance` | **≥1200** |
| AppLovin MAX (+16 mediation adapters) | `com.applovin` | **824** |
| Mintegral / MBridge | `com.mbridge` | **>900** |
| Moloco (+ full VAST renderer) | `com.moloco` | **>800** |
| Chartboost 9.14.0 | `com.chartboost` | **>600** |
| PubMatic / OpenWrap | `com.pubmatic` | **>400** |
| Unity, IronSource, Vungle, Smaato, PubNative, Ogury, Tappx, AppBrain, Digital Turbine, Inneractive, Verve, Amazon APS, Huawei Petal, Samsung, Xiaomi, TikTok Business | various | present |

**≈ 24 distinct ad networks** linked into one printing utility. Counts marked `>`/`≥` hit the enumeration ceiling before exhausting the tree; they are lower bounds.

Also:

* Ad unit IDs are hardcoded per-activity as instance fields (`ActivityHome`, `ActivityPreview` each declare their own different set) — duplicated, not centralised.
* Firebase Remote Config seeds `ads_applovin_*`/`ads_admob_*`, then each Activity overrides them — remote ad config is decorative.
* IAP via Samsung, Huawei HMS and the in-house CafeBazaar store (`nokorustore://`).
* `<queries>` with ~190 banking packages exists purely for in-app billing checks.

---

## 7. Static defects

### 7.1 No app-specific landscape or tablet layouts

* `res/layout-land` → 18 files, all vendor. **No app layout.**
* `res/layout-sw600dp` → 4 files, all vendor. **No app layout.**
* `res/layout-watch` → 2 files, `abc_*` only.
* `res/values-land/dimens.xml` → every entry `abc_`/`material_`/`mtrl_`.

The only app-specific resource in any qualified `values-*` directory is a dialog width fraction:

```
values-w480dp/integers.xml   <integer name="dialog_width_percentage">0.6836</integer>
values-w600dp/integers.xml   <integer name="dialog_width_percentage">0.6</integer>
values-w960dp/integers.xml   <integer name="dialog_width_percentage">0.375</integer>
values-w1920dp/integers.xml  <integer name="dialog_width_percentage">0.25</integer>
```

So the app is responsive-by-`dp` for dialogs but **structurally single-form**: one vertical layout stretched to tablets. No master-detail, no two-pane, no side rail. This is the largest single UX gap.

### 7.2 Touch targets and unlabelled controls

`item_min_height = 44dp` (should be 48dp). Worse, the three transport toggles in `activity_devices_bar.xml` have **no `contentDescription`**:

```xml
<ImageView android:id="@+id/btn_wifi"
    android:background="?android:attr/selectableItemBackground"
    android:layout_width="0dp"  android:layout_height="match_parent"
    android:src="@drawable/ic_wifi"  android:scaleType="center"
    android:layout_weight="1"/>
```

Icon-only controls with no content description are announced by TalkBack as just the view class. **Three of the app's four primary navigation controls are unlabelled.**

### 7.3 Accessibility coverage is essentially zero

Scoping `contentDescription` to the 37 app-owned layouts returns exactly **one** hit — and it is in a *vendor* dialog (`offline_ads_dialog.xml`). The attribute appears 10+ times in vendor layouts. The ad SDKs are more careful with TalkBack than the app is.

### 7.4 Hardcoded, unlocalised string

```xml
res/layout/activity_endisable_service.xml:17
    android:text="Please enable the HMS service in settings tag"
```

Untranslatable, and "in settings tag" leaks a machine translation of "в настройках". Shipped verbatim to every non-English user on Huawei devices.

### 7.5 Configuration hygiene

* `largeHeap="true"` **and** `Debug.setMinimumHeapSize(4194304)` — two overlapping heap hacks; the second is emulator-only.
* `HttpsURLConnection.setDefaultSSLSocketFactory(...)` mutates process-global TLS from `Application.onCreate`.
* `dialog_width_percentage = 0.6836` magic number repeated in 4 resource files.

### 7.6 Things that are clean

| Check | Result |
|---|---|
| Hardcoded secrets in `strings.xml` | none (ad IDs live in Java fields) |
| Hardcoded URLs in app layouts | not found |
| Watermark code | not present at all |
| Print-history code | not present at all |
| Debug-only logging left enabled in app scope | not found |
| Duplicate resource names across locales | not found |
| Malformed XML | none; all parsed |

---

## Lessons for reimplementation

**Manifest and packaging**

1. Choose `minSdk` for the business, not for convenience. `minSdk 32` discards Android 8–11 for no technical reason.
2. Keep `required="false"` on every optional transport feature — one universal APK, no device filtered out.
3. Implement `android.printservice.PrintService`, and consume `PrintJobInfo` from the system print dialog (2→NoTumble/"Long Edge", 4→Tumble/"Short Edge", plus `getCopies()` and `attributes.mediaSize.id`). Without this you lose every user who prints from a document viewer.
4. Declare `setMinMargins(NO_MARGINS)` only if you really will implement margins yourself — it transfers the whole margin/position UX onto you.
5. Never let a third-party licence wrapper be your declared `android:name` unless it must be first.
6. Audit `<queries>`: ~190 banking packages is a payments feature, not a printing feature.

**UI architecture**

7. Views/XML is a perfectly good answer for a print-settings UI. Do not reach for Compose here — it would drag in a second UI stack and a `ViewInterop` bridge for no benefit given the View-based SDK ecosystem.
8. Use a single dual-inflate shell (bar + main) for every screen — `ActivityRoot.t(bar, main)` is a 15-line pattern that makes toolbar handling, insets and slot logic exist exactly once. Copy it.
9. Separate responsive *sizing* from responsive *structure*. The `values-w<N>dp` dialog-width trick is fine for dialogs, but this app has zero `layout-land` and zero `layout-sw600dp` files and is a stretched phone app. Add tablet master-detail variants.
10. Tag slots with a machine-readable size contract and create them `visibility="gone"`. Centralise unit IDs in one registry; do not redeclare them per screen.
11. Put the platform-quirk explanation in the dialog body, not just the rationale — the Bluetooth-location string is the best string in the app and costs nothing to write.
12. Disclose every download size before the download.

**Print settings**

13. Treat the settings key taxonomy as a spec worth copying verbatim: `pages_{all,odd,even,selected,range}`, `margins_{none,by_printable_area,narrow,normal,wide}`, 9-way `position_*`, 8-mode `duplex_*`, booklet + poster.
14. Give every non-obvious input a concrete example hint (`"1, 3–5, 8"` teaches the grammar in nine characters).
15. Name the *conflict*, not just the fact of it ("Booklet binding is unavailable with 2-up on this paper size"). Highest-value single improvement over the original.
16. Implement a three-tier driver fallback (exact → generic → manual) and tell the user when the driver is a guess.
17. Ship per-transport "no printers found" diagnostics that enumerate plausible causes.
18. Build print history / retry from the start — its complete absence here is conspicuous.
19. Expose a watermark option; it does not exist in the original.

**Localisation and resources**

20. Never put a non-English string in the default `values/strings.xml`; add a lint rule asserting the default file is the `en` reference.
21. Audit every `values-XX/strings.xml` for *app* keys, not just presence of the file — `values-zh` exists and is non-empty but has no app strings, so a "locale dir exists" check will not catch it. Assert on a known app key.
22. Do not ship a `values-en/` that only contains vendor strings; it guarantees future drift.
23. Use current locale codes (`he` not `iw`, `id` not `in`) and never silently remap a language at runtime.
24. Human-review low-resource locales for false friends — Thai "Long edge" → `แนวตั้ง` ("Portrait") is exactly the class of error no linter catches.
25. Make `HardcodedText` and `ContentDescription` release-blocking lint rules.
26. Set `item_min_height` to 48dp, not 44dp.

**Ad / SDK footprint**

27. Count SDK cost in classes, not features: 3352 (`com.facebook`), 1980 (`sg.bigo.ads`), ≥1200 (`com.bytedance`), 824 (`com.applovin`), >900 (`com.mbridge`), >800 (`com.moloco`), >600 (`com.chartboost`) — for an app whose own code is a small fraction of the tree.
28. Gate ad SDKs behind dynamic feature modules or a remote plugin loader, and keep their layouts out of the base APK.
29. Have exactly one source of truth for ad configuration.
30. Never mutate process-global state from `Application.onCreate`.
31. Do not set `largeHeap="true"` and also call `setMinimumHeapSize()`; fix the underlying retention instead.
32. Ship your own accessibility metadata even when the ad SDKs do better.