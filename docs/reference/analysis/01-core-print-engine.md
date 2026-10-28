# NokoPrint (`com.nokoprint`) — Core Print Engine: Architecture, Defects & Lessons

Decompiled source root: `/home/dopamide/Projects/DosPrint/nokoprint-reference/jadx/sources`
Focus: `com/nokoprint/core` + print orchestration in `com/nokoprint`

---

## 0. Scope, entry points, and decompiler caveats

| Role | Class | File |
|---|---|---|
| `Application` | `com.nokoprint.App` | `App.java` |
| Launcher activity (`MAIN`) | `com.nokoprint.ActivityHome` | manifest line 378 |
| Base activity chain | `ActivityRoot` → `ActivityCore` → `ActivityBase` → `ActivityPrint` → {`ActivityPrintPhotos`, `ActivityPrintDocs` → `ActivityPrintNative`, `ActivityPrintWeb`} | manifest lines 376–588 |
| Print **service** (the only Android print-framework integration) | `com.nokoprint.core.NativePrintService` | `core/NativePrintService.java:578` |
| Printer discovery session | `com.nokoprint.core.h extends PrinterDiscoverySession` | `core/h.java` |
| `printservice_meta.xml` | `addPrintersActivity=com.nokoprint.ActivityDevices` | `resources/res/xml/printservice_meta.xml` |

**There is NO `PrintDocumentAdapter` anywhere in the APK.** A repo-wide grep for
`PrintManager|PrintJobInfo|PrintAttributes|PrintDocumentAdapter|PrintDocumentInfo` over
`jadx/sources` returns only 9 hits: `core/h.java`, `core/NativePrintService.java`,
`ActivityPrintNative.java`. There is no `getSystemService("print")`, no `PrintJobInfo.Builder`,
no `PrintManager.print(...)`, no `onLayout`, no `onWrite`, no `onFinish`. **NokoPrint does not
submit jobs — it receives them as a system print service.**

**Decompiler caveats**

* `core/u.java` is 23,444 lines and jadx produced instruction dumps for the biggest methods.
  Method *signatures*, *string constants*, *bitmap allocation sizes* and *progress-message shapes*
  inside those dumps are reliable; local control flow is not.
* Several methods decompile to `while (...) { synchronized (...) { if (...) { } } }` with an
  **empty body**. This is a jadx `Code restructure failed: missing block` artifact, not an
  infinite loop.
* `ActivityPrint.R()` contains `e = e; e.printStackTrace();` — a `Multi-variable search result
  rejected` artifact. Findings derived from `R()` are low confidence.

---

## 1. Print job submission architecture

### 1.1 NokoPrint is a `PrintService`, not a `PrintDocumentAdapter` producer

```xml
<service
    android:name="com.nokoprint.core.NativePrintService"
    android:permission="android.permission.BIND_PRINT_SERVICE">
    <intent-filter>
        <action android:name="android.printservice.PrintService"/>
    </intent-filter>
    android:resource="@xml/printservice_meta"/>
</service>
```

`NativePrintService` (102 lines) is the entire print-framework surface:

```java
// core/NativePrintService.java:42-44
public final PrinterDiscoverySession onCreatePrinterDiscoverySession() { return new h(this); }

// core/NativePrintService.java:52-95 (abridged)
public final void onPrintJobQueued(PrintJob printJob) {
    printJob.start();
    ParcelFileDescriptor data = printJob.getDocument().getData();
    if (data != null) {
        try {
            File file = new File(App.k(), "nokoprint_temp_print.pdf");
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            FileInputStream fileInputStream = new FileInputStream(data.getFileDescriptor());
            byte[] bArr = new byte[Segment.SIZE];
            while (true) {
                int i2 = fileInputStream.read(bArr);
                if (i2 == -1) break; else fileOutputStream.write(bArr, 0, i2);
            }
            fileOutputStream.close();
            fileInputStream.close();
            Intent intent = new Intent(CommonConstant.ACTION.HWID_SCHEME_URL);
            intent.putExtra("job_info", printJob.getInfo());
            ...
            startActivity(intent);                       // API < 29
            // or: NotificationManager.notify(1, ...)   // API >= 29
        } catch (Exception e2) { e2.printStackTrace(); App.w(null, e2); }
    }
    printJob.complete();                                 // <-- unconditional
}
```

Inbound path: Android print dialog → `PrintJobInfo` → spooler writes the PDF to a
`ParcelFileDescriptor` → the app copies it to `getExternalCacheDir()/nokoprint_temp_print.pdf` →
launches `ActivityPrintNative` with the `PrintJobInfo` in the extra `"job_info"`.

`onConnected()` cancels **all** active jobs (lines 31–34).

### 1.2 Advertised capabilities

`core/h.java:31-101` builds a single synthetic `PrinterInfo` from the currently selected printer:

```java
PrinterCapabilitiesInfo.Builder builder = new PrinterCapabilitiesInfo.Builder(printerIdGeneratePrinterId);
for (int i2 = 0; i2 < vVar.v.size(); i2++) {
    l lVar3 = (l) vVar.v.get(i2);
    builder.addMediaSize(new PrintAttributes.MediaSize(lVar3.f45480b, lVar3.c,
        ((lVar3.f45481d * 1000) / 254) + i3, ((lVar3.f45482e * 1000) / 254) + i3),
        lVar != null && lVar3.f45480b.equals(lVar.f45480b));
}
if (z) {
    builder.addResolution(new PrintAttributes.Resolution("default", "Default", 300, 300), true);
    builder.setColorModes(2, 2);                       // COLOR_MODE_COLOR / COLOR_MODE_COLOR
    Vector vector = vVar.F;
    if (vector != null && vector.size() > 1) builder.setDuplexModes(7, 1);
    builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS);
    printerInfoBuild = new PrinterInfo.Builder(printerIdGeneratePrinterId, vVar.d(), 1)
        .setDescription(vVar.c()).setCapabilities(builder.build()).build();
}
```

| Attribute | Value |
|---|---|
| Media size | from the capability list `v.v`; mils computed as `tenthsOfMm * 1000 / 254` |
| Resolution | hard-coded `("default","Default",300,300)` |
| Colour modes | `setColorModes(2, 2)` — colour supported, colour default |
| Duplex | `setDuplexModes(7, 1)` only if `v.F.size() > 1` |
| Margins | `setMinMargins(NO_MARGINS)` |
| Printer id | `generatePrinterId("default")` — always the literal `"default"` |
| Printer status | hard-coded `1` (`STATUS_IDLE`) |

`onStartPrinterStateTracking`, `onStopPrinterStateTracking`, `onStopPrinterDiscovery` and
`onValidatePrinters` are **all empty**.

### 1.3 Attribute ingestion on the inbound path

```java
// ActivityPrintNative.java:11-46
int copies = printJobInfo.getCopies();
if (copies > 1) this.E0 = copies;
PrintAttributes.MediaSize mediaSize = printJobInfo.getAttributes().getMediaSize();
if (mediaSize != null) this.f45352b.edit().putString(f().concat("#paper"), mediaSize.getId()).apply();
int duplexMode = printJobInfo.getAttributes().getDuplexMode();
if (duplexMode == 4) { jVar.f45474b = "DuplexTumble"; jVar.c = "Short Edge"; }
if (duplexMode == 2) { jVar.f45474b = "DuplexNoTumble"; jVar.c = "Long Edge"; }
```

A repo-wide grep confirms **`getPageRanges()`, `getColorMode()` and `getResolution()` are never
called** on a `PrintJobInfo`. The inbound path ignores the user's page-range, colour and
resolution choices from the system dialog entirely.

### 1.4 Outbound path: NokoPrint talks to printers itself

```java
// ActivityPrint.java:444-453
public final void W() {
    if (ActivityCore.F == null) return;
    h0(null);
    App.c();
    com.nokoprint.core.u uVar = new com.nokoprint.core.u(this, ActivityCore.F, ...);
    this.r0 = uVar;
    uVar.start();
}
```

`core/u.java:run()` (line 20537) dispatches on the URI scheme: `pdf_printer` → writes via the
ContentResolver; otherwise `a()` branches — `ipp` → IPP `Print-Job`; `wprt://` → WSD SOAP
`CreatePrintJob` + `SendDocument`; `pdl://` → raw socket; `bjnp://` → BJNP socket; `smb://` →
`com.nokoprint.smb.m0`; `usb://` → `core.a0`; `tpl://` → `core.x`; else raw LPD. Within that,
`r16[3]` (the driver name) selects one of ~20 rasterisers: `generic_escpos`, `generic_zpl`,
`generic_cpcl`, `generic_epl`, `generic_capt`, `generic_opl`, `generic_urf`,
`generic_ricoh_jbig`, `generic_panasonic_jbig`, `generic_bjnp_jpeg`, `generic_bjnp_ufr`,
`generic_bjnp_raster`, `generic_hbp_jpeg`, `generic_sprocket`, `generic_oki9pin`,
`generic_woosim`, `generic_fujitsu`, `generic_rongta`, `generic_datamax`, `generic_gwp80`,
`brother_pjmw`, `able_ap`.

The **`internal` driver path** shells out to native shared libraries through
`core/DrvWrapper.java`:

```java
// core/DrvWrapper.java:14-52
int iIndexOf = name.indexOf("drv");
int iIndexOf2 = name.indexOf("JNI", iIndexOf + 1);
if (iIndexOf >= 0 && iIndexOf2 > iIndexOf) name = name.substring(iIndexOf + 3, iIndexOf2);
Hashtable<String, DrvWrapper> hashtable = drivers;
DrvWrapper drvWrapper = hashtable.get(name);
if (drvWrapper == null) {
    System.load(file.getAbsolutePath());
    drvWrapper = (DrvWrapper) Class.forName(DrvWrapper.class.getName() + "_" + name).newInstance();
    hashtable.put(name, drvWrapper);
}
...
eVar.c = new c(ParcelFileDescriptor.adoptFd(iArr[0]).getFileDescriptor(), ...);
```

Drivers: `drv_escpr|1.8.5|libdrvescprJNI.so`, `drv_hplip|3.25.6|libdrvhplipJNI.so`,
`drv_splix|2.0.0s|libdrvsplixJNI.so`, `drv_gutenprint|5.2.8-pre1s|libdrvgutenprintJNI.so`
(`core/b.java:7,10,15,20`).

IPP attributes are assembled by hand with correct RFC 8010 value tags:

```java
// core/u.java:1574-1658 (instruction dump — constants are reliable)
r3.add(new b((byte) 71, "attributes-charset", "utf-8"));
r3.add(new b((byte) 72, "attributes-natural-language", "en-us"));
r3.add(new b((byte) 69, "printer-uri", r2.f4620b));
r3.add(new b((byte) 66, "job-name", "Printed from NokoPrint"));
r3.add(new b((byte) 73, "document-format", "image/urf"));   // only for generic_urf
op.add(new b((byte) 68, "media", lVar.f45489m));
op.add(new b((byte) 52, "media-col", ""));
op.add(new b((byte) 74, "", "media-size"));
op.add(new b((byte) 52, "", ""));
op.add(new b((byte) 74, "", "x-dimension")); op.add(new b("", 33, lVar.f45481d * 10));
if ("Gray".equals(iVar.f45471b)) op.add(new b((byte) 68, "print-color-mode", "monochrome"));
if ("Draft".equals(kVar.f45477b)) op.add(new b("print-quality", 35)); else op.add(new b("print-quality", 4));
if ("DuplexNoTumble".equals(jVar.f45474b)) op.add(new b((byte) 68, "sides", "two-sided-long-edge"));
if ("DuplexTumble".equals(jVar.f45474b))    op.add(new b((byte) 68, "sides", "two-sided-short-edge"));
```

---

## 2. Page pagination & layout

### 2.1 Two-stage page model

`ActivityPrint` holds **two** `Vector`s of pages: `j0` = **content pages**, `k0` = **sheet pages**
(what actually gets printed after selection, N-up, poster, booklet and duplex re-ordering).

```java
// ActivityPrintDocs.java:197-212
public final Vector U() {
    Vector vector = new Vector();
    if (!this.z1 || G1) {
        for (int i2 = 0; i2 < this.A1.size(); i2++) vector.add(new a2((Picture) this.A1.get(i2)));
    } else {
        for (int i2 = 0; i2 < this.A1.size(); i2++) vector.add(new m2((Picture) this.A1.get(i2)));
    }
    return vector;
}
```

`m2` re-renders on every `a()` call; `a2` precomputes orientation
(`picture.getWidth() > picture.getHeight()`).

### 2.2 Page count

```java
// r2.java:119-127 (DocsRender)
int pageCount = DocsRender.getPageCount();
while (i6 < pageCount) {
    int[] iArr = new int[2];
    i6++;
    DocsRender.getPageSize(i6, 100, iArr);            // 1-based
    this.f45849b.A1.add(new s2(this.f45849b, i6, iArr[0], iArr[1]));
}

// r2.java:142-148 (PdfRender)
int pageCount2 = PdfRender.getPageCount();
for (int i7 = 0; i7 < pageCount2; i7++) {
    double[] dArr = new double[2];
    PdfRender.getPageSize(i7, dArr);                 // 0-based
    this.f45849b.A1.add(new w2(this.f45849b, i7, (int)((dArr[0]*300.0)/72.0), (int)((dArr[1]*300.0)/72.0)));
}
```

`getPageSize` return values are **discarded** in one variant — a failure yields `{0,0}` and
produces a zero-dimension page (see D-11).

### 2.3 Page selection

```java
// ActivityPrint.java:477-510
public final Vector Y() {
    Vector vector = new Vector();
    int i2 = this.H0;                           // 0=all 1=odd 2=even 3=checkbox 4=range
    Vector vector2 = this.j0;
    if (i2 == 0) { vector.addAll(vector2); return vector; }
    if (i2 == 1) { for (i3=0; i3<vector2.size(); i3+=2) vector.add((a2) vector2.get(i3)); }
    else if (i2 == 2) { for (i4=1; i4<vector2.size(); i4+=2) vector.add((a2) vector2.get(i4)); }
    else if (i2 == 3) { for (...) if (this.K0.contains(...)) vector.add(...); }
    else {
        Vector vectorI0 = i0(this.I0);
        while (i3 < vectorI0.size()) {
            vector.add((a2) vector2.get(((Integer) vectorI0.get(i3)).intValue()));
            i3++;
        }
    }
    return vector;
}
```

### 2.4 Page-range parsing — `ActivityPrint.i0(String)`

```java
// ActivityPrint.java:743-792
StringTokenizer stringTokenizer = new StringTokenizer(str, ",");
Boolean bool = null;                                   // tri-state: null=empty, TRUE=ok, FALSE=bad
while (stringTokenizer.hasMoreTokens()) {
    String strNextToken = stringTokenizer.nextToken();
    int iIndexOf = strNextToken.indexOf("-");
    int i3 = 0;
    if (iIndexOf >= 0) { ...substring split... } else { ... }
    if (i2 <= 0 || i3 < i2) { bool = Boolean.FALSE; }  // ANY bad token poisons the WHOLE string
    else {
        while (i2 <= i3) {
            int i4 = i2 - 1;
            if (i4 < this.j0.size()) {                    // only the upper bound is checked
                if (bool == null) bool = Boolean.TRUE;
                vector.add(Integer.valueOf(i4));
            }
            i2++;
        }
    }
}
return (bool == null || !bool.booleanValue()) ? new Vector() : vector;
```

The caller shows an error but still applies the value:

```java
// androidx/appcompat/widget/l0.java:194-206
if (activityPrint.i0(string).size() == 0) editText.setError(getString(R.string.message_incorrect_page_range));
...
activityPrint.H0 = i9;
activityPrint.I0 = editText.getText().toString();
if (i3 == 4) { hashSet.clear(); hashSet.addAll(activityPrint.i0(activityPrint.I0)); }
activityPrint.y();
dialogInterface.dismiss();
```

### 2.5 N-up / poster / booklet / duplex → sheet pages

```java
// ActivityPrint.java:1139-1201 (abridged)
Vector vectorY = Y();
int i10 = this.M0;                       // 0 normal, 1..4 N-up, 5..7 poster, 8 booklet
if (i10 == 8) {                          // BOOKLET — reorder to 4-up imposition
    Vector vector5 = new Vector();
    ...
    int size3 = vector6.size();
    int i12 = ((size3 + 3) / 4) * 4;      // pad to a multiple of 4
    for (int i13 = 0; i13 < i12 / 4; i13++) {
        int i14 = i13 * 2, i15 = i12 - i14, i16 = i15 - 1;
        vector5.add(i16 < size3 ? vector6.get(i16) : blank);   // back-right
        vector5.add(i14 < size3 ? vector6.get(i14) : blank);   // front-left
        vector5.add(i14+1 < size3 ? vector6.get(i14+1) : blank);// front-right
        vector5.add(i15-2 < size3 ? vector6.get(i15-2) : blank);// back-left
    }
} else {                                  // N-up
    int i20 = new int[]{1, 2, 2, 3}[i10 - 1];
    int i21 = new int[]{2, 2, 3, 3}[i10 - 1];
    for (int i22 = 0; i22 < ((vector2.size() + (i20*i21)) - 1) / (i20*i21); i22++)
        vectorY.add(new a2(new w1(this, i22 * i20 * i21, i20, i21, vector2)));
}
// DUPLEX re-ordering (ActivityPrint.java:1235-1259)
if (this.W0 > 2) {
    if (this.W0 > 3 && vectorY.size() % 2 == 1) { /* pad to even with a blank page */ }
    if (i30 == 3) for (...) vector9.add(vectorY.get(i31));
    else if (i30 == 4) for (...) vector9.add(vectorY.get(i32));
    else if (i30 == 5) for (...) vector9.add(0, vectorY.get(i33));
    vectorY = vector9;
}
```

### 2.6 Content → media canvas

`w4 extends Picture` routes `Picture.draw(Canvas)` into a template method, and the cancellation
token `b.p()` is checked per tile.

**Document pages — `s2.c(Canvas)`** does the full media transform (margins → printable area →
orientation swap → margins policy → user position → scale → clip):

```java
// s2.java:144-280 (abridged)
canvas.drawColor(-1);
boolean zB = b();                                    // page is landscape?
int i14 = lVar.f45481d; int i15 = (i14 * 300) / 254;  // 10ths-of-mm → 300 dpi canvas units
int i18 = activityPrintDocs.e1;                       // margins policy 0..4
if (i18 > 1) { int iMin = Math.min(i14, i16) / 36; if (i18 == 3) iMin *= 2; if (i18 == 4) iMin *= 3;
               i15 -= i22; i17 -= i22; canvas.translate(f, f); }
if (zB ^ (t0.f45481d > t0.f45482e)) { int i23 = i17; i17 = i15; i15 = i23; }   // orientation swap
int i24 = activityPrintDocs.D1;                        // 0 actual, 1 fit, 2 shrink-fit, 3 custom %
if (i24 == 3) rectF = new RectF(0, 0, (i26*i27)/100f, (i27*i25)/100f);
else if (i24 == 1 || (i24 == 2 && (i26 > i15 || i25 > i17))) { /* fit */ }
else rectF = new RectF(0, 0, i26, i25);
switch (activityPrintDocs.h1) { case 2: canvas.translate((-(rectF.width()-i15))/2f, 0); ... }
canvas.scale(rectF.width()/i26, rectF.height()/i25);
canvas.clipRect(new Rect(0, 0, i26, i25));
```

**Band-wise rasterisation with an OOM ladder:**

```java
// s2.java:80-124
while (true) {
    try {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i5, i7, Bitmap.Config.ARGB_8888);
        int i8 = 0;
        while (i8 < i6) {
            if (bVar != null && i8 > 0) { if (!bVar.p()) break; }         // cancel per band
            int iDrawPage = DocsRender.drawPage(..., bitmapCreateBitmap);
            if (iDrawPage != 0 && iDrawPage != 4194304) throw new ...(iDrawPage);
            canvas.drawBitmap(...);
            i8 += i13;
        }
        break;
    } catch (OutOfMemoryError e2) {
        if (i7 <= 64) throw e2;
        i7 = (i7 % 2) + (i7 / 2);        // halve band height (rounded up)
    }
}
```

**PDF pages — `w2.a(Canvas)`** renders into a `v4` (a `Canvas` subclass) with an explicit clip
rect and full affine matrix, including a Y-flip for API < 28 and a `4.1666665f` scale (300 dpi →
72 dpi PDF user space).

**N-up — `w1.a(Canvas)`** rotates the sheet when needed and delegates each tile to `v1.run()`,
which recomputes the source index and is **bounds-checked** (`if (i2 < vector.size())`).

**"Print to PDF" — `core/u.java:y(OutputStream)`** is a hand-rolled PDF 1.4 writer: `%PDF-1.4`
header, one `/Type/Page` per sheet with `/MediaBox`, each page sliced into horizontal JPEG strips
(`/Filter/DCTDecode`), the strip list compressed with `DeflaterOutputStream` into
`/Filter/FlateDecode` content streams, then a manually built `xref` + `trailer` + `startxref`.

---

## 3. Job state / queue management

### 3.1 No persistent queue, no foreground service

Repo-wide greps confirm: no `extends Service` / `startForeground` / `WorkManager` under
`com/nokoprint`; no SQLite/Room; no DataStore usage (`androidx.datastore.preferences.protobuf.y0`
is only used as a static math helper). **Job state lives entirely in the Activity + one worker
`Thread`.**

### 3.2 Progress: a private `Message.what` protocol, not enums

There is **no job status enum**. State is a small `int` protocol on `android.os.Message`:

| `what` | `arg1` | `arg2` / `obj` | Meaning |
|---|---|---|---|
| 1 | — | — | job started (open dialog, acquire locks) |
| 2 | page index | percent, or `-1` for indeterminate | progress |
| 3 | — | — | "finishing" |
| 4 | — | `obj` = `String` error | failure |
| 5 | — | — | success |

```java
// r1.java:28-110 (abridged)
if (i2 == 1) {
    activityPrint.c();  activityPrint.d();                    // WakeLock + WifiLock
    activityPrint.getWindow().addFlags(128);                  // FLAG_KEEP_SCREEN_ON
    AlertDialog dlg = new u3(activityPrint).setTitle(R.string.dialog_printing_title)
        .setView(LayoutInflater.from(activityPrint).inflate(R.layout.dialog_progress, null))
        .setCancelable(false).setNegativeButton(R.string.button_cancel, null).show();
    ((ProgressBar) dlg.findViewById(R.id.progress)).setIndeterminate(true);
} else if (i2 == 2) {
    ... textView3.setText("Page (arg1+1)/(u.f45517k)") or "Copy (arg1/k+1)/(l) - Page ..."
    if (message.arg2 == -1) { progressBar.setIndeterminate(true); return; }
    progressBar.setProgress(message.arg2);
    textView4.setText(message.arg2 + "%");
} else if (i2 == 3) { /* finishing */ }
  else if (i2 == 4) { /* error */ }
  else if (i2 == 5) { /* done */ }
```

Analytics bundles are built by `ActivityPrint.T()` with `getClass().getSimpleName().substring(13)`
(see D-23).

### 3.3 Cancellation: a 1-element `boolean[]` sentinel

```java
// core/u.java:84
public final boolean[] f45522p = new boolean[1];
```

```java
// androidx/appcompat/app/c.java:188-200
case 11:
    if (activityPrint.h0 == null || (uVar = activityPrint.r0) == null) return;
    synchronized (uVar.f45522p) { uVar.f45522p[0] = true; }
    Button button = activityPrint.h0.getButton(-2);
    button.setText(R.string.dialog_printing_label_canceling);
    button.setEnabled(false);
```

Checked inside **30+ `synchronized (this.f45522p) { ... }` blocks**, one per renderer stage —
per page and per band, not per socket write.

### 3.4 Retry / resume

**There is no retry or resume.** Failures are terminal: `u.a()` jumps to an epilogue emitting
`what=4`. The only retry-like behaviour: one 2 s retry when the printer answers `199` (busy); a
6 × 500 ms USB poll; a `setSoTimeout(500)` read loop. There is **no** job history, **no**
resume-after-process-death, and `printJob.complete()` is called **unconditionally** even when the
copy threw.

---

## 4. Settings persistence

### 4.1 Storage: SharedPreferences only

```java
// ActivityRoot.java:433-435
public void l() { this.f45352b = PreferenceManager.getDefaultSharedPreferences(this); }
```

No Room, no DataStore, no SQLite, no JSON. Files on disk are only downloaded driver packs,
downloaded renderer `.so`s, `deps.txt`, and `nokoprint_temp_*` / `nokoprint_out.tmp` scratch files.

### 4.2 Complete preference key inventory

**Global keys**: `printer`, `printer_direct_address`, `printer_manual_address`, `printer_host`,
`printer_title`, `printer_model`, `printer_login`, `printer_password`, `printer_info_PDL` /
`_MFG` / `_MDL` / `_CMD` / `_URF` / `_COLOR`, `smb_auth`, `lib_docsrender`, `lib_pdfrender`,
`is_no_ads`, `is_no_ads_temporary`, `is_no_ads_time`, `privacy_accepted`,
`last_interstitial_time`, `last_promo_time`, `last_ask_purchase_time`, `review_cf`, `psp_asked`,
`purchase_sku`, `purchase_store`, `installer`, `accumulated_ad_revenue_<network>_micros`.

**Per-content-type keys** — built from the class simple name:

```java
// ActivityRoot.java:267-279
public final String f() {
    String simpleName = getClass().getSimpleName();
    int i2 = 0; String strU = "";
    while (i2 < simpleName.length()) {
        int i3 = i2 + 1;
        String strSubstring = simpleName.substring(i2, i3);
        String lowerCase = strSubstring.toLowerCase();
        strU = strU + (strSubstring.equals(lowerCase) ? "" : "_") + lowerCase;
        i2 = i3;
    }
    return strU.length() > 1 ? strU.substring(1) : strU;
}
```

→ `activity_print`, `activity_print_photos`, `activity_print_docs`, `activity_print_web`,
`activity_print_native`. Then `#paper`, `#type`, `#tray`, `#mode`, `#color`, `#orientation`,
`#margins`, `#position`, `#scaling`, `#scaling_custom`, `#size`, `#multi`, `#multi_gaps`,
`#multi_grid_columns`, `#multi_grid_rows`, `#multi_compact_group`, `#multi_restrict_to_grid`.

**NOT persisted (session-only):** copies, collate, page-selection mode, range string, reverse
order, multipage layout, duplex mode, booklet flags, poster cut marks.

### 4.3 Per-printer remembering

There is **one** selected printer, remembered globally under `printer`; there is **no**
per-printer profile store:

```java
// ActivityCore.java:145143-145170
public final void L(com.nokoprint.core.v vVar) {
    F = vVar;
    if (vVar != null) {
        SharedPreferences.Editor editorEdit = this.f45352b.edit();
        editorEdit.putString("printer", vVar.f45524b + (vVar.f45528h != null ? "|" + vVar.f45528h : ""));
        editorEdit.putString("printer_direct_address", vVar.c);
        ... 12 more putString calls ...
        editorEdit.apply();
    }
}
```

### 4.4 Printer settings resolution — `ActivityPrint.b0()`

Last-match-wins backward scan with a two-tier fallback:

```java
// ActivityPrint.java:540-560 (paper)
String string = this.f45352b.getString(f().concat("#paper"), null);
String str = App.o() ? "Letter" : "A4";      // US/CA/CL/CO/MX/PH/PR/VE/GT
if (ActivityCore.F != null && ActivityCore.F.v != null) {
    for (int size = ActivityCore.F.v.size() - 1; size >= 0; size--) {
        l lVar = (l) ActivityCore.F.v.elementAt(size);
        if (lVar.f45480b.equals(string)) { this.t0 = lVar; break; }
        if ((lVar.f45480b.equals(ActivityCore.F.u) && this.t0 == null) || lVar.f45480b.equalsIgnoreCase(str)) this.t0 = lVar;
    }
}
if (this.t0 == null) this.t0 = "A4".equals(str) ? new l("", "A4", 2100, 2970) : new l("", "Letter", 2159, 2794);
l lVar2 = this.t0;
lVar2.f45482e = lVar2.f;                    // <-- mutates the shared capability object (D-18)
```

---

## 5. Document / file parsing

### 5.1 Native renderers (the two parsers)

```java
// core/PdfRender.java — pdfium-based, all methods `native synchronized`
public abstract class PdfRender {
    public static native synchronized int create(String str, String str2);
    public static native synchronized int destroy();
    public static native synchronized int drawPage(int i2, int[] iArr, float[] fArr, int i3, Bitmap bitmap);
    public static native synchronized int getPageCount();
    public static native synchronized int getPageSize(int i2, double[] dArr);
}

// core/DocsRender.java — custom Office/text engine
public class DocsRender {
    public static native int closeFile();
    public static native int createViewer(String str, int i2);
    public static native int deleteViewer();
    public static native int drawPage(int i2,int i3,int i4,int i5,int i6,int i7,int i8, boolean z, Bitmap bitmap);
    public static native int getPageCount();
    public static native int getPageSize(int i2, int i3, int[] iArr);
    public static native int init(String[] strArr);
    public static native int openFile(String str, int i2, String str2, ReadingCallback readingCallback);
    public static native int setDPI(int i2);
}
```

**Neither `android.graphics.pdf.PdfRenderer` nor `PrintedPdfDocument` is used.**

### 5.2 Supported MIME types

```java
// App.java:471-488
if (z) strArr = { "application/pdf",
    "application/msword", "application/vnd.ms-word.document.macroenabled.12",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel", ..., "application/vnd.ms-powerpoint", ...,
    "application/haansofthwp", "text/plain" };
else  strArr = { "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp" };
intent.setAction("android.intent.action.OPEN_DOCUMENT");
intent.addCategory("android.intent.category.OPENABLE");
intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", z2);       // true only for images
intent.putExtra("android.intent.extra.MIME_TYPES", strArr);
```

### 5.3 Native library acquisition

The `.so` files are **downloaded and installed at runtime**, keyed by version string in prefs:

```java
// ActivityPrintDocs.java:226-343 (abridged)
if ("3.8.1".equals(this.f45352b.getString("lib_docsrender", ""))) {
    ... System.load(file.getAbsolutePath());
    int iInit = DocsRender.init(strArr);
    int iCreateViewer = DocsRender.createViewer(absolutePath, i2 * 1024);
} else if ("8.0.3".equals(this.f45352b.getString("lib_pdfrender", ""))) {
    ... System.load(file3.getAbsolutePath());
    int iCreate = PdfRender.create(null, null);
}
```

`ActivityPrintDocs.q0()` (`:64-194`) is a dependency walker: it copies a system library into app
storage while **scanning byte-by-byte for `DT_NEEDED` names inside the ELF image**, then recurses
on every name found, skipping `libdl/libc/libm/libz/liblog/libstdc++/libicuuc/libicui18n`.

### 5.4 Images

`androidx.media3.decoder.i:b()` copies each image to a cache temp file, reads dimensions with
`inJustDecodeBounds=true`, and builds an **EXIF transform matrix** handling all 8 orientations.

### 5.5 WebView

```java
// androidx/appcompat/app/c.java:210-222
WebView webView = activityWeb.h0;
float f = activityWeb.getResources().getDisplayMetrics().density;
float scale = webView.getScale();
webView.zoomBy(f / scale);
ActivityWeb.j0 = webView.capturePicture();       // static volatile Picture
webView.zoomBy(scale / f);
```

### 5.6 Streaming / large files

* The whole file is copied to a temp file first (8 KiB chunks with a progress callback), then
  **all** pages are enumerated up front and one lightweight descriptor is built per page. Page
  *pixels* are rendered lazily at draw time in bands, so memory is bounded but page metadata is
  fully materialised — a 10 000-page PDF allocates 10 000 objects before printing starts.
* `m2.a()` calls `PdfRender.drawPage(..., null, ...)` on **every** access as a warm-up.
* "Print to PDF" is the only genuinely streaming writer.

---

## 6. Crash & reliability defects (ranked by severity)

### D-1 — CRITICAL: `throw null` in 20+ places

`core/a0.java` (USB socket) and `core/x.java` (IPP-over-USB) use `throw null;` after logging:
occurrences at `a0.java:103,137,141`; `x.java:82,96,119,127,131,141,149,153,157,161,326`.

### D-2 — CRITICAL: `core.a0` constructor can return with null `final` fields

`core/a0.java` extends `Socket` with `final` fields for the interface, endpoints, connection and
streams. Every failure path throws (caught by the constructor's own `catch (Exception)` at
`:182-185`) and then **falls out of the constructor**, so `getInputStream()`/`getOutputStream()`
return null and `close()` NPEs.

### D-3 — CRITICAL: `PrintJob.complete()` on every path, including failure

```java
// core/NativePrintService.java:90-96
} catch (Exception e2) { e2.printStackTrace(); App.w(null, e2); }
}
printJob.complete();      // <-- unconditional
```

The framework is told the job succeeded even when the copy failed. There is no `printJob.fail()`
anywhere in the APK.

### D-4 — CRITICAL: `ParcelFileDescriptor` never closed → FD leak

Neither `data` nor the two streams are in a `finally`. Any exception leaks 2–3 FDs **per print
job**, plus a partial temp file that is never cleaned up.

### D-5 — CRITICAL: fixed temp filenames across three subsystems

`nokoprint_temp_print.pdf` (`NativePrintService:57`), `nokoprint_out.tmp` (`u:23142`),
`nokoprint.tmp` (`u:20922`), plus **static single-slot arrays** `f45439h = new a0[1]`
(`a0.java:31`). Two concurrent print activities interleave their JPEG strips → corrupt PDF or a
wrong page on paper.

### D-6 — CRITICAL: blocked print thread on `"NOKOPRINT_USB_PERMISSION".wait()` with no timeout

Waiting on an **interned String monitor** with no timeout, with the permission dialog shown while a
job is in flight. The print thread hangs indefinitely and cannot be cancelled because
`f45522p` is only polled at render-stage boundaries. Same pattern at `discovery/o:175`.

### D-7 — HIGH: `DrvWrapper.exec` — non-atomic cache, leak on partial `adoptFd`, `Class.forName` on a filename-derived string

`iArr[0..2]` come from **native** code and are never validated (`-1`/`0` accepted). If the second
`adoptFd` throws, the first two FDs leak. `Class.forName(... + name)` + `System.load(path from
prefs)` means the download is **executable code**.

### D-8 — HIGH: `Thread.sleep(1000)` inside `Socket.close()` and 5 s after firmware push

```java
// core/a0.java:198-210
public final void close() {
    try { Thread.sleep(1000L); } catch (InterruptedException unused) {}
    a0[] a0VarArr = f45439h;
    synchronized (a0VarArr) { a0VarArr[0] = this; new ...i((byte) 9, this, z).start(); }
}
```

A second job started within 5 s finds the slot still set and immediately releases the previous
device's interface — killing the still-running first job.

### D-9 — HIGH: duplex mapping from the system dialog silently drops short-edge

```java
// ActivityPrintNative.java:29-41
if (duplexMode == 4) { jVar.f45474b = "DuplexTumble"; jVar.c = "Short Edge"; }
if (duplexMode == 2) { jVar.f45474b = "DuplexNoTumble"; jVar.c = "Long Edge"; }
```

The platform defines `ONE_SIDED=1`, `LONG_EDGE_BINDING=2`, `SHORT_EDGE_BINDING=3`,
`TUMBLE=3`, `FLIP_SHORT_EDGE_BINDING=4`, `FLIP_LONG_EDGE_BINDING=5`. **3 and 5 are silently
downgraded to one-sided.** It also constructs a *detached* `core.j` that is not in
`ActivityCore.F.F`, bypassing the normal duplex-selection logic.

### D-10 — HIGH: copies from the system dialog are unbounded and pre-allocate `pages × copies`

```java
// ActivityPrintNative.java:19-22
int copies = printJobInfo.getCopies();
if (copies > 1) { this.E0 = copies; }        // <-- no upper clamp (in-app clamps to 99)
```

```java
// core/u.java:187-192
int r6 = r6.size();
int r7 = r1.f45518l;                 // copies
int r6 = r6 * r7;
r5.<init>(r6);
```

`999 copies × 500 pages` ⇒ a 499 500-element array plus 499 500 progress `Message`s on the UI
thread — OOM/ANR-class.

### D-11 — HIGH: swallowed exceptions everywhere the page actually fails

`w2.java:54-61` catches `Throwable` and only reports to Crashlytics; the page silently prints
blank. `DocsRender.openFile` codes 1024/8192 are treated as "needs password" but leave `A1` empty →
zero pages printed, no error. `getPageSize` return values are discarded → `{0,0}` page sizes →
zero-area bitmaps and divide-by-zero.

### D-12 — HIGH: recycled bitmaps still referenced by attached views

`ActivityPrint.S()` unconditionally `recycle()`s every cached preview bitmap with **no
`isAttachedToWindow()` guard**. If a view re-enters `onDraw` between eviction and `invalidate()`,
`Canvas.drawBitmap` on a recycled bitmap throws `IllegalStateException`.

### D-13 — HIGH: unbounded recursion in the ELF dependency walker

```java
// ActivityPrintDocs.java:186-190
while (it.hasNext()) { q0((String) it.next(), vector); }   // path added AFTER recursion
vector.add(file.getAbsolutePath());
```

The visited-set is written after the recursive calls, so a cyclic `DT_NEEDED` graph recurses
forever → `StackOverflowError`, which is **not** caught by `catch (Exception)`.

### D-14 — MEDIUM: races on shared mutable state

`ActivityPrint.t1` is `public static volatile Picture`; `ActivityCore.F` is a single
`public static volatile` shared by every activity and the PrintService discovery session;
`PdfRender`/`DocsRender` are process-global singletons whose ownership is enforced with two
`static volatile` fields that **another thread nulls out from under you**; `b2.onDraw` mutates the
preview cache while the render thread mutates it; image loader fields are written on the loader
thread and read on the render thread with no synchronization; the error string field is written
from workers and read on the UI thread with no `volatile`.

### D-15 — MEDIUM: unchecked casts / unguarded element access on printer-supplied data

`r16[r24]` where `r16 = vVar.f45537q.split("\\|")` and `r24 == 3` — a 3-field discovery value ⇒
`ArrayIndexOutOfBoundsException` **before any output**. Plus `Integer.parseInt` on driver-supplied
resolution strings, URL path segments with no arity check, ~20 sequential `read()` checks with no
EOF guard, and `listFiles()` results dereferenced without a null check.

### D-16 — MEDIUM: `Integer.parseInt` on a nullable provider-supplied string at the top of 9 renderers

`core/u.java:14155` (and 8 more sites) does
`Integer.parseInt(this.f45511d.f45489m)` unguarded. Its assigned values include **non-numeric
media keywords** like `"iso_a4_210x297mm"`, `"na_letter_8.5x11in"`, and it is **`null`** for
fallback objects built in `b0()` because the `l` constructors never assign it ⇒
`NumberFormatException` / `NullPointerException` for ESC/POS, ZPL, EPL, CPCL and URF drivers.

### D-17 — MEDIUM: integer overflow in progress arithmetic

`(bytes * 100) / total` with `int` operands; `contentLength` is `(int) cursor.getLong("_size")` so
a >2 GB document truncates to a negative `int`. Overflow at >21 MB ⇒ negative percentages.

### D-18 — MEDIUM: `b0()` mutates the shared printer capability object

```java
// ActivityPrint.java:561-562
com.nokoprint.core.l lVar2 = this.t0;
lVar2.f45482e = lVar2.f;
```

`this.t0` is a reference into `ActivityCore.F.v`, also consumed by `core/h.java` for
`PrintAttributes.MediaSize` construction. The override leaks into every other consumer and into
subsequent jobs.

### D-19 — MEDIUM: pump thread loses buffered output and never closes the sink

`core/s.java:122-170`: the flush return value is discarded on the EOF path so a failed final flush
is silent; the driver `OutputStream` is never closed on any path.

### D-20 — MEDIUM: PPD file content reflected into ~100 private fields

```java
// core/w.java:227-262 (abridged)
declaredField = w.class.getDeclaredField(strTrim4);
declaredField.setAccessible(true);
if (cls.equals(declaredField.getType())) declaredField.setInt(this, Integer.parseInt(strTrim5));
```

Unguarded array indexing on PPD lines without `=`, `NumberFormatException` not caught by the
surrounding `catch (NoSuchFieldException)`, a downloaded PPD able to set `cupsWidth`/`cupsHeight`
to 0 (feeding `new byte[((i2*i4)+7)/8]`), and a `BufferedReader` never closed.

### D-21 — MEDIUM: process-wide TLS interception + hidden-API reflection

```java
// App.java:550-567
HttpsURLConnection.setDefaultSSLSocketFactory(new s4());
HttpsURLConnection.setDefaultHostnameVerifier(new ...hostname.a(b2));
...
Field obj = URI.class.getDeclaredField("AUTHORITY_ENCODER");   // hidden API
declaredField2.set(obj, "@[]%");
```

### D-22 — LOW: min-heap bump + forced-GC helper on the UI thread

`App.c()` does 3 `System.gc()` plus two `Thread.sleep(100)` calls, and is called **from the UI
thread** at `ActivityPrint.S():365` and `W():449` — the moment the user taps Print ⇒ 200 ms of
sleep plus 3 forced GCs on the main thread.

### D-23 — LOW: `ActivityPrint.T()` uses a magic `substring(13)`

Any subclass whose simple name is shorter than 14 characters throws inside the analytics path —
which runs in both the success and error handlers.

### D-24 — LOW: unbounded driver-description scan

`ActivityCore.C(v)` is a ~142 800-line straight-line blob parsing multi-megabyte `pkg_data` driver
tables, indexing split results with **no arity validation**, leaving the capability model
half-populated on a malformed string.

---

## 7. Threading

### 7.1 Executor / thread inventory

**There are no coroutines, no `Executor`/`ThreadPoolExecutor`, no `runBlocking`, no `.get()`/
`.join()` and no `Handler`-based coroutines in the print path.** Everything is raw `Thread`:

| Thread | Role |
|---|---|
| `core.u extends Thread` | the print job worker: `run()` → `a()` (rasterise + transmit) or `y()` (PDF writer) |
| `core.s extends Thread` | driver stdout pump; 8 KiB ring + 4 KiB read buffer; packet-boundary flushing |
| `core.q extends Thread` | drains driver **stderr**; **never joined** |
| `t1 extends Thread` | starts a native driver, then `while (this.c.D.isAlive()) Thread.yield();` |
| `r2 extends Thread` | document load + page-count enumeration |
| `v2 extends Thread` | downloads/unpacks renderer libs |
| `j0 extends Thread` | driver-pack download + install |
| `s extends Thread` | printer connect/validate + pack install |
| `x extends Thread` | driver stderr pump (32 KiB) |
| `o0 extends Thread` | SMB job submission |
| `androidx.media3.decoder.i extends Thread` | multi-purpose worker: preview rasteriser loop, photo loader, BT/USB teardown, discovery |
| `c6` | `new ThreadPoolExecutor(0, Integer.MAX_VALUE, ...)` — **unbounded** pool, ad-SDK path only |

**No thread pool sizing** for print work: exactly one `core.u` per print, nothing preventing two
from running concurrently.

### 7.2 Blocking / sleeping

| Where | Sleep | Thread |
|---|---|---|
| `App.c()` | `Thread.sleep(100)` ×2 + 3× `System.gc()` | **UI** and worker |
| `a0.java:106` | `Thread.sleep(500)` ×6 | USB worker |
| `a0.java:168`, `x.java:312` | `Thread.sleep(5000)` after firmware push | USB worker |
| `a0.java:201` | `Thread.sleep(1000)` inside `close()` | worker |
| `u.java:20947` | **`Thread.sleep(40000)`** before the first page | worker |
| `u.java:21040` | `Thread.sleep(2000)` after printer `199` (busy) | worker |
| `i.java:1377` | `Thread.sleep(12000)` | BT teardown |
| `i.java:1399` | `Thread.sleep(4000)` | USB teardown |
| `a0.java:121`, `discovery/o:175` | `.wait()` **no timeout** | worker |

**Busy-waits (`Thread.yield()` spin loops, no sleep, no timeout):** `t1.java:151-153`,
`ActivityCore.java:143726-143728`, `y.java:23-27`, and ~30 sites in `u.java`. These burn 100 % of
a core and are **not interruptible** because the loop never checks the interrupt flag.

### 7.3 Cross-thread hand-offs

| Producer | Mechanism |
|---|---|
| `core.u` → UI | `Message` + one `Runnable` allocation **per progress tick** (up to 100/page/renderer) |
| `r2` → UI | `runOnUiThread` with a 6-value byte protocol |
| `DocsRender` → Java | `ReadingCallback.on_reading(int)` → `runOnUiThread` |
| discovery → UI | a 15-way `Message` switch handler |
| discovery cancel | `boolean[]` + `synchronized` |

### 7.4 Locks actually used

`synchronized (this.f45522p)` (cancel sentinel, 30+ sites);
`synchronized (activityPrint)` (preview bitmap cache); `synchronized (s)` (driver pump buffer);
`synchronized ("NOKOPRINT_USB_PERMISSION")` (**interned String monitor**);
`synchronized (a0VarArr)` (static USB-socket slot); `Hashtable` for the driver cache
(individually synchronized but `get`/`put` not atomic); `Vector`/`ArrayList` used as if
thread-safe, with `j0`/`k0`/`K0` touched from both worker and UI with **no** enclosing lock.

---

## Lessons for reimplementation

**What to copy conceptually**

1. **Adopt the `PrintService` role rather than `PrintDocumentAdapter`** if the goal is "print any
   document to any printer": one `PrinterInfo` whose capabilities mirror the selected device lets
   the OS print dialog supply copies/media/duplex for free.
2. **Represent paper as a first-class value object** with id, label, width/height, four margins, a
   `borderless` flag and a media keyword — the layout engine needs all of it, and it makes
   rotation/margin logic a pure function.
3. **Resolve printer attributes by "last match wins, then printer default, then hard-coded
   fallback"** — simple, total, never NPEs.
4. **Separate content pages from sheets** and express selection, N-up, poster, booklet imposition
   and duplex re-ordering as pure `List → List` transforms. This makes the pipeline testable and
   the preview indistinguishable from the output.
5. **Keep content pages lazy**: a renderer that re-renders on `draw()` gives exact WYSIWYG previews
   with no intermediate bitmaps.
6. **Band-wise rasterisation with an OOM ladder** (halve band height, cap at 64 px, rethrow below)
   and `inSampleSize` doubling are the right pattern for large media on small devices.
7. **Thread-strip the PDF writer**: JPEG strips → `Deflater` content stream → XObject, with a
   byte-counting `OutputStream` wrapper.
8. **Send progress as a tiny private protocol with an explicit "indeterminate" sentinel.**
9. **Check the cancel flag at page and band granularity**, not at socket-write granularity.
10. **Ship the system-dialog job into a dedicated Activity** so the user still gets the app's full
    UI; carry the `PrintJobInfo` in the `Intent` extra.

**What to avoid**

11. **Never call `printJob.complete()` unconditionally.** `fail()` on error; `block(reason)` only
    when you can resume, and never `return` after `block()` without a terminal state.
12. **Never leak the `ParcelFileDescriptor`** from `getDocument().getData()`, and never copy a
    whole print payload on the print-service thread.
13. **Never use fixed temp filenames** for per-job data — use `createTempFile` / a per-job
    directory, and delete in a `finally`.
14. **Never `throw null`** — it destroys the stack trace and produces a message-less NPE that
    Crashlytics cannot triage.
15. **Never `wait()` without a timeout**, and never on an interned `String`.
16. **Never `Thread.sleep()` in a `Socket.close()`** — it makes teardown latency part of the next
    job's critical path.
17. **Never call `Runtime.gc()`/`System.gc()` as a memory strategy.**
18. **Never spin on `Thread.isAlive()` with `Thread.yield()`** — use `join(timeout)` or a latch.
19. **Never mutate a shared capability object** — clone.
20. **Never silently swallow a render failure.** A user cannot tell "blank page" from "failed
    page". Surface per-page errors.
21. **Never discard a size/query return value** — `{0,0}` sizes cascade into divide-by-zero.
22. **Never parse page ranges all-or-nothing.** This implementation collapses to empty if *any*
    token is bad, has no open-ended-range support, no de-duplication, and no overflow guard on
    `"1-2000000000"` (2 × 10⁹ iterations with no cancel check). Validate per token, cap the upper
    bound to `pageCount`, de-duplicate, and surface per-token errors.
23. **Never leave `List`/`Map` state shared between the UI thread and workers without a lock.**
24. **Never clamp attributes asymmetrically** — inbound copies are unclamped while in-app clamps
    to 99, and `pages × copies` is pre-allocated.
25. **Never map enums with incomplete coverage** — map every constant or assert.
26. **Never `Integer.parseInt` a string a data provider controls** without a guard.
27. **Never let a download define behaviour without bounds.** Recursion over ELF `DT_NEEDED` edges
    with the visited-set written after recursion ⇒ `StackOverflowError`; `System.load` +
    `Class.forName` on a filename-derived string means the download is executable code.
28. **Never replace `HttpsURLConnection`'s global SSL factory / hostname verifier** in a printing
    app that talks to arbitrary LAN printers.
29. **Never reflect into hidden platform APIs** to work around URL parsing — handle bracketed
    IPv6 literals yourself.