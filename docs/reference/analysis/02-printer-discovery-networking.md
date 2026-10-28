# 02 — Printer Discovery & Networking (NokoPrint / Print-Buddy `com.nokoprint`)

Decompiled source root: `nokoprint-reference/jadx/sources`
Focus packages: `com/nokoprint/{ipp,mdns,discovery,smb,snmp,bt}`, plus `com/nokoprint/core/*`,
`androidx/appcompat/widget/w.java`, `androidx/media3/decoder/i.java` (proguard-renamed host classes).

> Proguard note: the real IPP client was relocated into `androidx.appcompat.widget.w` and the
> background prober threads into `androidx.media3.decoder.i`. Both are merged multi-purpose classes
> that also contain unrelated androidx/exoplayer code. All line numbers are from the JADX output.

---

## 1. Network printer discovery

### 1.1 There are SEVEN independent network discovery mechanisms

`com/nokoprint/ActivityDevices.java:166-180` (the "Add devices → WiFi" path) starts five scanner
threads in parallel, plus USB (`o`) and Bluetooth (`b`) separately:

```java
com.nokoprint.discovery.f fVar = new com.nokoprint.discovery.f(15000, null, inetAddress, e0Var, (byte) 0);
this.t0 = fVar; fVar.start();
com.nokoprint.discovery.i iVar = new com.nokoprint.discovery.i(15000, null, inetAddress, e0Var);
this.u0 = iVar; iVar.start();
com.nokoprint.discovery.f fVar2 = new com.nokoprint.discovery.f(15000, null, inetAddress, e0Var, (byte) 1);
this.v0 = fVar2; fVar2.start();
com.nokoprint.discovery.l lVar = new com.nokoprint.discovery.l(15000, null, inetAddress, e0Var, (byte) 0);
this.w0 = lVar; lVar.start();
com.nokoprint.discovery.l lVar2 = new com.nokoprint.discovery.l(15000, null, inetAddress, e0Var, (byte) 1);
this.x0 = lVar2; lVar2.start();
```

| Scanner | Class | Mechanism | Ports |
|---|---|---|---|
| mDNS/DNS-SD + BJNP | `discovery.f` (`b2=0` → mDNS, `b2=1` → BJNP) | custom DNS encoder, UDP multicast | 5353 / 8611 |
| SNMP | `discovery.i` + `discovery.h`/`discovery.g` | SNMPv1 GET, UDP broadcast | 161 |
| WS-Discovery + TPL/KC300 | `discovery.l` (`b2=0` → WSD, `b2=1` → TPL) | SOAP-over-UDP multicast / UDP broadcast | 3702 / 9303,30202,7410-7440,7472 |
| SMB browse | jCIFS `smb.m0.r()` | NetBIOS browser + `srvsvc` RPC | 137/139/445 |
| USB | `discovery.o` | `UsbManager` + `controlTransfer` | – |
| Bluetooth | `discovery.b` | classic BT discovery + `getBluetoothClass()` | – |

Additionally there is a **manual probe** path per URI scheme in `o0.java:242-434` (IPP Validate-Job,
TCP connect for BJNP/pdl/lpd, HTTP GET).

### 1.2 mDNS / DNS-SD

**Service types probed** — `com/nokoprint/discovery/f.java:27`:

```java
public static final String[] f45582l = {
    "_pdl-datastream._tcp.local.", "_canon-bjnp1._tcp.local.", "_printer._tcp.local.",
    "_ipp._tcp.local.", "_ipps._tcp.local."
};
```

**Query prober** (3 rounds, 1 s apart) — `androidx/media3/decoder/i.java:557-660`:

```java
com.nokoprint.mdns.c cVar = new com.nokoprint.mdns.c();
cVar.a(new com.nokoprint.mdns.d(com.nokoprint.discovery.f.f45582l[i3], 12, 1));
...
if (eVar.f45579b.getAddress().length != 4) {
    datagramPacketB.setAddress(InetAddress.getByName("FF02::FB"));
    datagramPacketB.setPort(5353);
} else {
    datagramPacketB2.setAddress(InetAddress.getByName("224.0.0.251"));
    datagramPacketB2.setPort(5353);
}
```

Record type **12 = PTR**, class 1 (IN), sent to `224.0.0.251:5353` (IPv4) and `FF02::FB:5353` (IPv6),
plus to every directed-broadcast address from `App.e()`.

**Follow-up queries** — `discovery/f.java:594-611` (standard DNS RR types):

```java
new d(this, str19, i3 == true ? (byte) 1 : (byte) 0).start();   // SRV (33)
new d(this, str19, (byte) 1).start();                            // TXT (16)
new d(this, strConcat5, c2).start();                             // A (1)
new d(this, strConcat6, (byte) 3).start();                       // AAAA (28)
```

**mDNS encoder** — `com/nokoprint/mdns/c.java`: 1460-byte buffer, 12-byte header, static TXN-ID counter
`public static final int[] f = {1};` guarded by `synchronized (iArr)`, name-label compression
dictionary, UTF-8 label encoding.

**mDNS decoder** — `com/nokoprint/mdns/b.java:78-128` materialises only RR types 1(A), 5(CNAME),
12(PTR), 16(TXT), 28(AAAA), 33(SRV).

**TXT (RFC 6763) key parsing** — `discovery/f.java:762-782`: `rp` (resource path), `ty`, `product`,
`inetname`, `pdl`, `mfg`/`usb_mfg`, `mdl`/`usb_mdl`, `cmd`/`usb_cmd`, `urf`/`usb_urf`, `color`.

**URI construction** — `discovery/f.java:916-949`:

```java
if (str21.indexOf("._ipps") > 0) { if (i19 == 0) i19 = 631; str9 = "ipps://" + hostAddress + ":" + i19 + string; }
else if (str21.indexOf("._ipp") > 0) { if (i19 == 0) i19 = 631; str9 = "ipp://" + hostAddress + ":" + i19 + string; }
else if (str21.indexOf("._pdl") > 0) { if (i19 == 0) i19 = 9100; str9 = "pdl://" + hostAddress + ":" + i19; }
else if (str21.indexOf("._printer") > 0) { if (i19 == 0) i19 = 515; str9 = "lpd://" + hostAddress + ":" + i19 + string; }
else if (str21.indexOf("._canon-bjnp1") > 0) { str9 = "bjnp://" + hostAddress + ":" + i19; }
```

IPv6 literals are bracketed via `com/ironsource/W3.j` `f34879d`/`f34880e`, with scope-id conversion at
`discovery/f.java:375-388`.

### 1.3 BJNP (Canon proprietary)

Prober: `androidx/media3/decoder/i.java:469-555`, sends `bjnp.a{version=1, command=1, id=bjnp.b.w()}`
to every directed broadcast address plus `FF02::1`, port **8611**, 3 rounds 1 s apart.

Responder: `discovery/f.java:1410-1527`. Retries up to 3 times (`bjnp/b.java:117-143`,
`setSoTimeout(1000)`), then parses the `MFG:/MDL:/CMD:/DES:/URF:` `;`-separated blob.

### 1.4 WS-Discovery (WSD)

Prober: `androidx/media3/decoder/i.java:744-825`, UDP **3702**, multicast `239.255.255.250` (v4) /
`FF02::C` (v6), SOAP `Probe` message with `wsdp:Device` type.

Responder: `discovery/j.java`. **String-index-based SOAP parsing** (no XML parser): `:ProbeMatches`,
`:ProbeMatch`, `:Address`, `:XAddrs`, `:Types`. Then a WS-Transfer `Get` over HTTP POST (3 s
connect/read timeouts) to each `XAddrs` entry, parsing `:Manufacturer`, `:ModelName`,
`:FriendlyName`, and per-service `:ServiceId`, `:Address`, `:Types` — filtering
`PrinterServiceType` and rejecting fax. URI scheme: `wprt://<host><path>`.

### 1.5 "TPL" / KC300 discovery

Prober: `androidx/media3/decoder/i.java:827-880`:

```java
byte[] bArr = new byte[53];
System.arraycopy("WHERE ARE YOU KC300".getBytes(), 0, bArr, 0, 19);
bArr[50] = 1;
```

**34 dedicated listening sockets** are bound in `discovery/l.java:398-408`:
`{9303, 30202, 7410…7440, 7472}`. `discovery/n.java:84` filters out our own probes, and
`discovery/l.java:462-468` matches the response magic `"KANNOU"`.

Follow-up is a proprietary binary handshake on **TCP port 20005** (`discovery/m.java:283-387`).

### 1.6 Is `PrintManager.getPrinters()` used?

**No.** A whole-tree grep for `PrintManager|getPrinters\(|PrintServiceInfo|getSystemService("print")`
returns **zero matches**.

Instead it publishes a **single synthetic printer** into the framework from its own `PrintService`
(`com/nokoprint/core/h.java:82`):

```java
printerInfoBuild = new PrinterInfo.Builder(printerIdGeneratePrinterId, vVar.d(), 1)
        .setDescription(vVar.c()).setCapabilities(builder.build()).build();
```

`onStartPrinterDiscovery` (`core/h.java:109-115`) merely launches `ActivityCore` to re-run the app's own
discovery. `onStartPrinterStateTracking` / `onStopPrinterDiscovery` / `onValidatePrinters` are all **empty**.

---

## 2. IPP

### 2.1 Data model (`com/nokoprint/ipp`)

| Class | Role |
|---|---|
| `ipp.a` | **Base64 encoder only** (`a(String)->String`); there is **no decoder** |
| `ipp.b` | one IPP attribute: value-tag byte + name + value |
| `ipp.c` | `InputStream` decoding HTTP `Transfer-Encoding: chunked` |
| `ipp.d` | IPP request/response container |

`com/nokoprint/ipp/d.java:12-36`:

```java
public static int f45718h;                    // static request-id counter (NOT synchronized)
public short f45719a = 257;                   // 0x0101 = IPP1.1
public short f45720b;                         // operation-id / status-code
public int c;                                 // request-id
public Vector f45721d, f45722e, f, f45723g;   // op-attrs(1), job-attrs(2), printer-attrs(4), unsupported(5)
```

### 2.2 Transport: raw-socket HTTP/1.1 **and** `HttpURLConnection`

Client class: `androidx/appcompat/widget/w.java`. `ipp://…`/`ipps://…` map to `http`/`https`.

`w.java:328-412` — the raw-socket request write path:

```java
outputStream2.write(("POST " + url.getPath() + " HTTP/1.1\r\n").getBytes());
outputStream2.write(("Host: " + url.getHost() + "\r\n").getBytes());
outputStream2.write("Connection: close\r\n".getBytes());
...
outputStream2.write("Content-Type: application/ipp\r\n".getBytes());
outputStream2.write("Transfer-Encoding: chunked\r\n".getBytes());
```

The binary IPP envelope is serialised correctly per RFC 8010:

```java
dataOutputStream.writeShort(dVar.f45719a);   // version
dataOutputStream.writeShort(dVar.f45720b);   // operation-id
dataOutputStream.writeInt(dVar.c);           // request-id
if (dVar.f45721d.size() > 0) { dataOutputStream.writeByte(1); /* operation-attributes-tag */ ... }
if (dVar.f45722e.size() > 0) { dataOutputStream.writeByte(2); ... }
if (dVar.f.size() > 0)       { dataOutputStream.writeByte(4); ... }
if (dVar.f45723g.size() > 0) { dataOutputStream.writeByte(5); ... }
dataOutputStream.writeByte(3);               // end-of-attributes-tag
```

`HttpURLConnection` branch (`w.java:351-371`) sets 15 000 ms connect **and** read timeouts,
`setChunkedStreamingMode(32768)`, and strips the IPv6 zone id from `Host`.

### 2.3 Response decoding — `w.java:745-833`

```java
String line = inputStream.readLine();
if (!line.startsWith("HTTP/1.1 200")) throw new Exception("Can't connect to printer. HTTP error " + ...);
...
byte b2 = dataInputStream.readByte();
if ((b2 & 240) != 0) { /* value-tag */ ... }
else if (b2 == 1) vector = dVar.f45721d; // operation-attributes
else if (b2 == 2) vector = dVar.f45722e;   // job-attributes
else if (b2 == 3) return dVar;             // end-of-attributes
else if (b2 == 4) vector = dVar.f;          // printer-attributes
else if (b2 == 5) vector = dVar.f45723g;   // unsupported-attributes
```

### 2.4 Operations implemented — **only two**

| Op-id | RFC 8010 name | Where | Purpose |
|---|---|---|---|
| `2` | `Print-Job` | `core/u.java:1538-1747` | the actual print |
| `4` | `Validate-Job` | `o0.java:260` | liveness/auth probe when adding a printer |

**NOT implemented**: no `Get-Printers`, no `CUPS-Get-Printers`, no `Get-Printer-Attributes`, no
`CUPS-Purge-Jobs`, no `Cancel-Job`, no job-status polling.

`Validate-Job` is used as a de-facto ping:

```java
com.nokoprint.ipp.d dVar = new com.nokoprint.ipp.d((short) 4);
dVar.f45721d.add(new com.nokoprint.ipp.b((byte) 71, "attributes-charset", "utf-8"));
dVar.f45721d.add(new com.nokoprint.ipp.b((byte) 72, "attributes-natural-language", "en-us"));
dVar.f45721d.add(new com.nokoprint.ipp.b((byte) 69, "printer-uri", (String) wVar.f4620b));
wVar.E(dVar).close();
```

### 2.5 `Print-Job` attributes sent (`core/u.java:1538-1747`)

Operation attributes: `attributes-charset` (0x47), `attributes-natural-language` (0x48),
`printer-uri` (0x45), `job-name` (0x42), and `document-format` (0x49) = `image/urf` only for the
`generic_urf` driver.

Job attributes for `generic_urf` are the legacy **PWG 5100 media collection**:

```java
new ipp.b((byte) 68, "media", r6.f45489m);   // 0x44 keyword "iso_a4_210x297mm"
new ipp.b((byte) 52, "media-col",  "");           // 0x34 begCollection
new ipp.b((byte) 74, "media-size", "");           // 0x4A memberAttrName
new ipp.b((byte) 52, "", "");                     // 0x34 begCollection
new ipp.b("", 33, r6.f45481d * 10);               // 0x21 integer hundredths of mm
new ipp.b((byte) 74, "y-dimension", "");
new ipp.b("", 33, r6.f45482e * 10);
new ipp.b((byte) 55, "", "");                     // 0x37 endCollection
if ("Gray".equals(colorMode)) new ipp.b((byte) 68, "print-color-mode", "monochrome");
if ("Draft".equals(quality))  new ipp.b("print-quality", 35, r24);   // 0x23 enum
if ("High".equals(q))        new ipp.b("print-quality", 4, 5);      // enum = 5
else                          new ipp.b("print-quality", 4, 4);      // enum = 4
if ("DuplexNoTumble".equals(duplex)) new ipp.b((byte) 68, "sides", "two-sided-long-edge");
if ("DuplexTumble".equals(duplex))    new ipp.b((byte) 68, "sides", "two-sided-short-edge");
```

### 2.6 IPP-over-TLS

`ipps://` is rewritten to `https://`, and the **global default** socket factory is overridden in
`App.java:550-551` with a trust-all `TrustManager` plus a permissive hostname verifier.

### 2.7 Legacy status handling

`core/u.java:4924-4936` treats only `0x0000` and `0x0001` as success; any other code is shown to the
user as a raw `IPP error <code>` with no mapping.

---

## 3. SMB

`com/nokoprint/smb` is a **stripped/renamed fork of jCIFS** (1.x, SMB1 only). There is **no SMB2/3**.

### 3.1 Dialects

`smb/n.java:31` offers only **`PC_NETWORK_LM_0.12` (0x0202)**:

```java
byte[] bytes = "\u0002NT LM 0.12\u0000".getBytes("ASCII");
System.arraycopy(bytes, 0, bArr, i2, bytes.length);
```

### 3.2 Ports / NetBIOS

Default port **445**; `smb/t0.java:161-183` falls back 139 → 445. Connect timeout **15 000 ms**.
Name service: NetBIOS `1\2__MSBROWSE__\2` on UDP 137.

### 3.3 Commands used

| Command | Class | Use |
|---|---|---|
| `0x72` NEGOTIATE | `smb/n.java` | dialect + capability negotiation |
| `0x73` SESSION_SETUP_ANDX | `smb/z.java` | NTLMSSP |
| `0x32` TREE_CONNECT_ANDX | `smb/m0.java` | connects to `IPC$` |
| `0x25` TRANSACTION | `smb/m0.java:778-808` | `spoolss`/`srvsvc` printer enumeration |
| `0x32` TRANS2_QUERY_PATH_INFORMATION | `smb/m0.java:195-272` | directory listing |
| `0xA2` NT_CREATE_ANDX | `smb/m0.java:837-848` | file write |
| `0x2B`/`0x2A` WRITE_ANDX / READ_ANDX | `smb/i0`, `smb/d0` | spool to printer |
| `0x04` LOGOFF_ANDX | `smb/r.java:757` | cleanup |

### 3.4 What SMB is used for

**(a) Printer enumeration via `spoolss` RPC** — `smb/m0.java:426-467`:

```java
androidx.media3.exoplayer.k kVarF = androidx.media3.exoplayer.k.f("ncacn_np:" + k().b() + "[\\PIPE\\spoolss]", this.f45994g);
... OpenPrinter ... EnumPrinters level 1
    if (bVar.f45904h == 122) { ...retry level 2... }
return new String[]{ bVar.f45909m, bVar.f45910n, bVar.f45912p, bVar.f45911o };  // name, server, share, port
```

RPC interface UUIDs (`smb/dcerpc/b.java:32,35`): `srvsvc` `4b324fc8-1670-01d3-1278-5a47bf6ee188:3.0`
and `spoolss` `12345678-1234-abcd-ef00-0123456789ab:1.0`. Fallback for very old servers: `\PIPE\LANMAN`.

**(b) Share browsing for discovery** — `m0.r()` dispatches to `__MSBROWSE__` browse,
`NetrShareEnum`, or Trans2 dir listing; shares of type `32` (printer) become discovered printers.

### 3.5 NTLM / negotiation

`smb/q0.java` selects NTLMv1 vs NTLMv2 via extended security, signs session keys, and retries
type-3. **No Kerberos / SPNEGO / GSS-API.**

Credentials class `smb/k.java` parses `user;pass:domain` with `%XX` unescaping, plus LM/NT one-way
functions and the NTLMv2 hash.

Credential escalation ladder (`o0.java:142-166`): anonymous → guest → stored `smb_auth` →
previous/parent host's stored credentials.

Session watchdog: daemon thread with `wait(15000)`; still state 1 → `throw new g1("Connection timeout")`.

---

## 4. SNMP

### 4.1 Version and auth

**SNMPv1 only, community `"public"`, no authentication/privacy, no v2c/v3.**
`discovery/i.java:129-147`:

```java
vector3.insertElementAt(new com.nokoprint.snmp.f(i2), 0);      // request-id
vector3.insertElementAt(new com.nokoprint.snmp.f(0L), 1);       // version 0 == SNMPv1
vector3.insertElementAt(new com.nokoprint.snmp.f(0L), 2);       // error-status
vector3.insertElementAt(mVar, 3);                              // varbind list
kVar.f46128a = CacheControl.PUBLIC.getBytes();                 // "public"
```

PDU type `0xA0` = GetRequest, to UDP **161**.

### 4.3 OIDs queried (`discovery/g.java`)

| OID | Meaning | Use |
|---|---|---|
| `1.3.6.1.2.1.1.5.0` | `sysName` | identity, synthetic ID; fallback from `ifPhysAddress` |
| `1.3.6.1.2.1.2.2.1.6.2` | `ifPhysAddress` | fallback ID / MAC |
| `1.3.6.1.2.1.1.1.0` | `sysDescr` | model text, compared against `hrDeviceDescr` |
| `1.3.6.1.2.1.25.3.2.1.3.1` | `hrDeviceDescr` | more precise model |
| `1.3.6.1.4.1.11.2.3.9.1.1.7.0` | HP LaserJet pro MIB `ljDevDesc` | HP model |
| `1.3.6.1.4.1.2699.1.2.1.2.1.1.2.<i>` | Canon `bjnpDescriptionModel` | model name |
| `1.3.6.1.4.1.2699.1.2.1.2.1.1.3.<i>` | Canon `bjnpDescriptionInfo` | `MFG:/MDL:/CMD:/DES:/URF:` blob |
| `1.3.6.1.4.1.2699.1.2.1.3.1.1.2/4/5/6.<i>` | Canon status/ink OIDs | **queried but the values are discarded** |

### 4.4 What is done with the values

Result → a synthetic mDNS-shaped record plus a URI (`discovery/g.java:752-813`):

```java
str15 = "snmp_" + str + "_" + strB2;
if ("8".equals(strB4))  { str16 = "lpd://"  + hostAddress + ":515/";   str15 += "._printer._tcp.local."; }
if ("11".equals(strB4)) { str16 = "pdl://"  + hostAddress + ":9100";  str15 += "._pdl-datastream._tcp.local."; }
if ("44".equals(strB4)) { str17 = "ipp://"  + hostAddress + ":631/" + strB3; str15 += "._ipp._tcp.local."; }
```

### 4.5 Explicitly NOT implemented

- **No ink-level / toner-level queries.** No `marker-names`, `marker-colors`, `marker-supplies`,
  `marker-levels` anywhere. Canon ink OIDs are *fetched and thrown away*.
- No `printer-state-reasons` via SNMP, no traps, no `SNMP GETBULK`, no `SET`, no subtree walk — each
  OID is a separate unicast GET.

---

## 5. Bluetooth

### 5.1 Classic Bluetooth only — **no BLE whatsoever**

No `BluetoothGatt`, `ScanCallback`, `BluetoothLeScanner`, or `BLUETOOTH_LE` reference anywhere.
Everything goes through `java.lang.reflect` on the classic API (`com/nokoprint/bt/b.java:72-127`):

```java
bVar.f45406b = cls3.getMethod("isEnabled", null);
bVar.c       = cls3.getMethod("isDiscovering", null);
bVar.f45407d = cls3.getMethod("startDiscovery", null);
bVar.f45408e = cls3.getMethod("cancelDiscovery", null);
bVar.f45415m = cls4.getMethod("createRfcommSocket", cls2);
bVar.f45416n = cls4.getMethod("createInsecureRfcommSocket", cls2);
bVar.f45417o = cls4.getMethod("createRfcommSocketToServiceRecord", UUID.class);
bVar.f45419q = cls4.getMethod("getServiceChannel", Class.forName("android.os.ParcelUuid"));
```

Reflection is presumably to dodge static-analysis detection of BT API usage.

### 5.2 UUIDs

`com/nokoprint/bt/a.java:16-19`:

```java
public static final UUID f      = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // SPP
public static final UUID f45400g = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb"); // OBEX / OPP
```

OBEX is selected for Zebra/Dataro "sprocket" drivers, SPP otherwise.

Socket ladder: `createRfcommSocketToServiceRecord` → insecure variant → `getServiceChannel` +
`createRfcommSocket(channel)` → `createRfcommSocket(1)` → `createInsecureRfcommSocket(1..3)`.

### 5.3 Discovery

Bonded devices first, then `startDiscovery()` with a receiver for
`android.bluetooth.device.action.{FOUND,NAME_CHANGED,CLASS_CHANGED}`.

Class filter (`discovery/b.java:161-165`):

```java
if (((iIntValue & 7936) >> 8) != 6 || (iIntValue & 128) == 0) {  // major class == IMAGING(0x0600)
    objArr[3] = Boolean.FALSE; return;
}
```

Discovery timeout: **30 000 ms** on the Devices screen, **20 000 ms** when resolving a saved printer.

Permissions: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on API ≥ 31, else `ACCESS_FINE_LOCATION`.

---

## 6. Printer data model

### 6.1 `com/nokoprint/core.v` — the discovered-printer record

```java
public final class v implements Comparable {
    public String f45524b;  // b: id / mDNS or SNMP or BJNP service name
    public String c;        //    direct address / URI ("ipp://host:port/ipp/print", "bluetooth://MAC")
    public String f45525d;  // d: host / MAC / serial
    public String f45526e;  // e: friendly title
    public String f;        //    normalised model string
    public String f45528h;  // h: manually-chosen driver id
    public String f45529i;  // i: login (IPP/SMB user)
    public String f45530j;  // j: password
    public String f45531k;  // k: PDL list (from TXT "pdl")
    public String f45532l;  // l: MFG
    public String f45533m;  // m: MDL
    public String f45534n;  // n: CMD
    public String f45535o;  // o: URF
    public String f45536p;  // p: colour
    public String f45537q;  // q: driver spec
    public Vector v;        //    media size list -> core.l
    public Vector F;        // duplex list -> core.j
    public Vector B;        // print quality list -> core.k
    public Vector H;        // child printers on the same host (multi-socket)
}
```

Ordering (`v.java:162-180`): PDL(0) < BJNP < IPPS < IPP < LPD < other, +100 for IPv6.

Capability sub-models: `core.l` (media size, width/height in 1/1000 inch, four margins, IPP media
keyword), `core.i` (colour), `core.j` (duplex), `core.k` (quality, dpi string `"1200x1200"`).

### 6.2 Persistence — **SharedPreferences only, no SQLite**

`ActivityCore.java:145147-145169`:

```java
editorEdit.putString("printer", sb.toString());
editorEdit.putString("printer_direct_address", vVar.c);
editorEdit.putString("printer_title", vVar.f45526e);
editorEdit.putString("printer_model", vVar.f);
editorEdit.putString("printer_login", vVar.f45529i);
editorEdit.putString("printer_password", vVar.f45530j);   // plaintext password
editorEdit.putString("printer_info_PDL", vVar.f45531k);
...
```

Also `"smb_auth"` (URL-encoded `user;pass:domain`). **No `EncryptedSharedPreferences`.**

---

## 7. Capability querying

### 7.1 Answer: **hardcoded tables, not IPP**

The app never issues `Get-Printer-Attributes`. It runs a ~17 000-line heuristic
`ActivityCore.C(com.nokoprint.core.v)` which string-matches MFG/MDL/CMD/TXT-product against ~500
hardcoded model strings (Canon, Brother, Zebra, Epson, Kodak, Able, generic) and writes the
capability vectors into the model:

```java
vVar.b();
vVar.f45537q = "internal|||generic_urf";
vVar.v = new Vector(); vVar.u = str36;
com.nokoprint.core.l lVar78 = new com.nokoprint.core.l("A4", "A4", 2099, 2970, new Rect(0, 0, 2099, 2970));
lVar78.f45489m = "iso_a4_210x297mm";
vVar.v.add(lVar78);
```

The unknown-printer fallback is `Letter, A4, Legal, A3, Ledger, B4` with `Draft/Normal/High` at
`150/200/300` dpi.

### 7.2 Bridging to the Android print framework

`com/nokoprint/core/h.java:53-82`:

```java
builder.addMediaSize(new PrintAttributes.MediaSize(lVar3.f45480b, lVar3.c,
    ((lVar3.f45481d * 1000) / 254) + i3, ((lVar3.f45482e * 1000) / 254) + i3), ...);
...
builder.addResolution(new PrintAttributes.Resolution("default", "Default", 300, 300), true); // 300 dpi hardcoded
builder.setColorModes(2, 2);                    // hardcoded COLOR
if (vector != null && vector.size() > 1) builder.setDuplexModes(7, 1);
builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS);
```

**Resolution, colour support and duplex support are NOT read from the printer at all** — the framework
preview is told "300 dpi, colour, duplex" unconditionally.

---

## 8. Connection lifecycle

### 8.1 Scheduling — no WorkManager / JobScheduler / AlarmManager / Service

All discovery is **foreground, Activity-scoped**, driven by raw `Thread` objects, cancelled in
`onDestroy()`. The only "service" is the Android *print service*, not a background service.

### 8.2 Timeouts (all hardcoded literals)

| Context | Value |
|---|---|
| Discovery — Devices screen: mDNS/BJNP/SNMP/WSD/TPL | **15 000 ms** |
| Discovery — BT | **30 000 ms** |
| Resolve saved printer: network | **10 000 ms** |
| Resolve saved printer: BT | **20 000 ms** |
| Prober rounds | 3 rounds, 1 000 ms apart |
| SNMP | `setSoTimeout(3000)`, 3 attempts |
| BJNP | `setSoTimeout(1000)` |
| WSD HTTP | 3 000 / 3 000 ms |
| TPL TCP | connect 3 000 ms |
| USB descriptor | `controlTransfer` 3 000 ms |
| IPP `HttpURLConnection` | connect 15 000 / read 15 000 ms |
| pdl:// and lpd:// sockets | connect 15 000 / soTimeout 15 000 ms |
| SMB connect | 15 000 ms |
| "Add printer" probes | 5 000 / 15 000 ms |
| Driver-pack download | 15 000 ms, 3 retries @500 ms |

### 8.3 Threads

Everything is `new Thread(...)`; **there is no `ExecutorService`/thread pool** in `com.nokoprint`.
Per discovery round the app can create:

- 1 scanner thread + **1 listener thread per local multicast interface**
- **1 thread per follow-up DNS query**
- **1 thread per responding SNMP host** / per responding BJNP host
- **34 bound UDP sockets** for the TPL/KC300 scan

### 8.4 Socket close handling

There *is* a cleanup chain in `core/u.java:5351-5373` closing raw sockets, USB/TPL wrappers, SMB
sessions, and the IPP client (`socket.close()` + `HttpURLConnection.disconnect()`). SMB has
`smb/t0.d(boolean)` doing `shutdownOutput(); close() ×3` and clearing the connection pool.

### 8.5 Retry counts

No generic retry: 3 fixed probe rounds; SNMP GET `for (i2 < 3)`; BJNP 3 retries; driver download 3
attempts; IPP scheme downgrade exactly once (HTTP 426 → `ipps://`, HTTP 404 → append `/ipp`);
HTTP 401 pops a credential dialog and **aborts** the add.

---

## 9. Defects (ranked)

### D1 — CRITICAL: unbounded busy-spin in the SNMP GET retry loop
`com/nokoprint/discovery/i.java:179-213`

```java
do {
    synchronized (this.f45599h) {
        if (!this.f45599h[0]) {
            try { datagramSocket.receive(datagramPacket); strArrE = e(datagramPacket, iC, str, false); }
            catch (SocketTimeoutException unused) { }
            ...
        }
    }
} while (strArrE == null);
```

`strArrE` is **not assigned** on timeout/cancel, so the loop never observes progress; the enclosing
`for (i2 < 3)` is unreachable. After a single 3 s timeout the loop re-enters `receive()`; after
**cancellation** the body becomes a no-op and the thread spins at 100 % CPU forever.

### D2 — CRITICAL: infinite read loops that swallow cancellation
`com/nokoprint/discovery/m.java:358-386` (TPL) and `com/nokoprint/core/x.java:104-116` (IPP-over-TPL)

```java
while (true) {
    try {
        i8 = inputStream.read();
        if (i8 == -1) break;
        else if (!z) { socket2.setSoTimeout(500); z = true; }
    } catch (SocketTimeoutException unused) { }
}
```

Terminates only on EOF. On cancellation the socket closes → `IOException` → swallowed → **hot spin**.
`core/x.java` also leaks the socket on every protocol mismatch by throwing `NullPointerException`
(`ki.b(); throw null;`).

### D3 — HIGH: `ipp.d` request-id counter is a non-atomic static
```java
int i2 = f45718h + 1;
f45718h = i2;
```
Concurrent `new ipp.d(...)` → lost updates and **duplicate request-ids**, making IPP responses
ambiguous and defeating print-path response matching.

### D4 — HIGH: leaked `BroadcastReceiver` on the Bluetooth error path
`com/nokoprint/discovery/b.java:280-360` unregisters the receiver only on the success path. If
`startDiscovery()` throws, the receiver registered at `:251/:258` is never unregistered → leaked
Context registration for the process lifetime.

### D5 — HIGH: unbounded `wait()` on USB permission with no timeout
`com/nokoprint/discovery/o.java:172-181` calls `"NOKOPRINT_USB_PERMISSION".wait()` with no timeout
while holding a string-literal monitor. If the permission broadcast never arrives the scanner thread
blocks forever.

### D6 — HIGH: `Thread.yield()` busy-wait on the render thread while draining a socket
`com/nokoprint/core/u.java:4883-4888` (also `:4850-4859`, `:5124-5127`) burns a core for the entire
drain with no backoff, no `wait/notify`, no timeout — while `core/s.b()` already exposes exactly the
"is it blocked?" flag that is needed.

### D7 — HIGH: data race on the SMB browse `Stack`
`ActivityDevices.i0` is a plain `java.util.Stack` mutated on the UI thread while the worker thread
reads it without a lock (`i.java:931,961-962`). `lastElement()` can return an item concurrently
popped by the UI thread → crash or credentials applied to the wrong host.

### D8 — MEDIUM-HIGH: mDNS listeners never join the multicast group
`discovery/e.java:26-47` and `discovery/k.java:30-47` construct a bound `MulticastSocket` and call
`setLoopbackMode(true)` but **never call `joinGroup()`** (grep for `joinGroup` in the whole tree: 0
hits). Only unicast replies to the app's own PTR queries are seen; passive advertisements are
silently dropped → intermittent "printer not found".

### D9 — MEDIUM: missing multicast / connection-state checks in the IPP client
`androidx/appcompat/widget/w.java:938-955` throws a deliberate `throw null` NPE for any unrecognised
scheme; `E()` returns a live `OutputStream` and leaks the connection if `v()` is never called;
`v()` calls `getResponseCode()` first, so 4xx/5xx raise a platform exception instead of the intended
message and the response stream is never closed on parse failure; the raw socket path sets **no**
`soTimeout`, so `readLine()` can block indefinitely on a half-open server.

### D10 — MEDIUM: global trust-all TLS + wildcard hostname verifier
`App.java:550-551` installs a permissive `TrustManager` as the process default. Every IPP/IPPS, WSD,
BJNP and driver-download connection is MITM-able, and the whole process is affected.

### D11 — MEDIUM: unbounded read into `ByteArrayOutputStream` in the TPL prober
`discovery/m.java:368-383` has no size cap → a hostile device on port 20005 can OOM the app.

### D12 — MEDIUM: unbounded thread creation during discovery
One thread per DNS answer, per responding SNMP host, per responding BJNP host, plus 34 bound sockets.
No pool, no cap, no back-pressure.

### D13 — MEDIUM: socket `getLength()` trusted blindly
`discovery/l.java:464-467` indexes `data[i3 + 19]` and `discovery/o.java:191` slices
`new String(bArr, 2, len - 2)`; a short datagram produces `ArrayIndexOutOfBoundsException` and drops
the whole packet batch.

### D14 — MEDIUM: `DiscoveryList` compound read-modify-write races
`discovery/f.c()` writes under `synchronized (this.f45589j) { add }` but reads it outside the lock at
`:1085`; two concurrent receive threads can both conclude "not present" and add duplicates. Same
pattern in `discovery/j.java:96-100` vs `:309-311`.

### D15 — MEDIUM: check-then-act on the stop flag
Every scanner uses `boolean[1]` under `synchronized` as the cancel flag, and cancellation latency
equals the full discovery timeout (10–30 s). The send path **holds the cancel lock across
`Thread.sleep(100)`**.

### D16 — LOW-MEDIUM: static global lock for the DNS transaction ID
`mdns.c` synchronises all mDNS queries on one static array — a contention hot-spot during a 15 s scan.

### D18 — LOW: plaintext credentials at rest
`printer_login`/`printer_password` and `smb_auth` in default `SharedPreferences` in cleartext.

### D19 — LOW: LPD response validation is effectively dead code
`core/u.java:5228-5246` accepts any non-empty LPD response as success.

### D20 — LOW: `core.v.e()` / `compareTo` NPE risk
`v.java:163-179` dereferences `this.c` and `this.f45524b` with no null guard; a `v` built by an
interrupted discovery thread can be compared on the UI thread → NPE.

### D21 — LOW: `discovery/i.run()` inconsistent logic in the decompiled output
The two copies of the block disagree, showing an inverted stop condition plus a `break` at the end of
the loop body — most likely a JADX structural-recovery error rather than a source bug. Flagged for
manual re-verification, not asserted as fact.

### D22 — LOW: USB handle leak on exception paths
`claimInterface(usbInterface, true)` has no `finally`, and the only backstop is a 4 s watchdog thread
using a **single static slot** — concurrent opens overwrite each other.

### D23 — INFO: one UI Runnable per discovered printer, uncoalesced
`e0.java:636-652` posts a `runOnUiThread` message per discovery result; on a busy segment the UI queue
can flood, and cancellation still posts.

---

## Lessons for reimplementation

1. **Do not rely solely on `PrintManager.getPrinters()`** for coverage — this app proves the spooler's
   list is insufficient. Conversely, publishing to the framework requires mandatory
   `PrinterCapabilitiesInfo`, and here everything fed to it is invented anyway.
2. **Treat `Validate-Job` with only `printer-uri` as a cheap liveness/auth probe.** Idempotent,
   side-effect-free, works on printers that ignore `Get-Printer-Attributes`.
3. **Handle HTTP 426 → retry once as `ipps://` and HTTP 404 → retry once with `/ipp` appended.** Both
   fallbacks are load-bearing in practice.
4. **Build IPP attributes with the real RFC 8010 value tags** (0x21 integer, 0x23 enum, 0x34
   begCollection, 0x37 endCollection, 0x42 nameWithoutLanguage, 0x44 keyword, 0x45 uri, 0x47
   charset, 0x48 naturalLanguage, 0x49 mimeMediaType, 0x4A memberAttrName).
5. **Do not assume the printer answers `Get-Printer-Attributes`.** For legacy hardware, deriving
   capabilities from `sysDescr`, `hrDeviceDescr`, `bjnpDescriptionInfo` and mDNS TXT keys is the only
   thing that works.
6. **Learn the driver from a model string, not from the printer** — normalise the MFG, then match a
   curated table. Accept its fragility as the price of supporting old hardware.
7. **You must `joinGroup()` the multicast address on a bound `MulticastSocket`.** NokoPrint omits this
   and misses all unsolicited advertisements — a bug worth deliberately not repeating.
8. **Query DNS record types by standard number** (A=1, PTR=12, TXT=16, AAAA=28, SRV=33) and walk
   PTR → SRV → A/TXT.
9. **Compute IPv4 directed-broadcast addresses yourself** from the interface netmask instead of relying
   on 255.255.255.255, which many Wi-Fi APs drop.
10. **Cache the interface and broadcast lists** — interface enumeration via `/sys/class/net` plus
    `ifconfig` parsing is a genuinely useful fallback when `NetworkInterface.getNetworkInterfaces()`
    returns null.
11. **IPv6 literals must be bracketed in URIs and the zone id stripped from the `Host:` header.**
    Also translate link-local IPv4 A-records into IPv6 with the packet's scope id.
12. **Always set both connect and read timeouts**, and set `soTimeout` on any raw socket you write to.
13. **Never structure a retry loop as `do { … } while (result == null)` where `result` is only assigned
    inside a `try`** (defect D1).
14. **Never swallow `SocketTimeoutException`/`IOException` inside an infinite `while (true)` read loop**
    (defect D2). Use a deadline computed from `SystemClock.elapsedRealtime()`.
15. **Bound every read into memory.** A `ByteArrayOutputStream` fed straight from a device socket is a
    one-line OOM.
16. **Use a fixed, small thread pool for discovery**, not `new Thread(...).start()` per answer.
17. **Close sockets and receivers in `finally` or with try-with-resources.** A watchdog thread as the
    only backstop does not compose.
18. **Use `join(timeout)` / `CountDownLatch.await(timeout)` instead of `Thread.yield()` spin loops.**
19. **Make cancel flags a real `AtomicBoolean`**, and never hold the cancel lock across a `sleep()`.
20. **Guard every shared list with the same monitor for reads and writes**, or prefer a concurrent queue
    with a single-consumer drain.
21. **Use `AtomicInteger` for protocol sequence numbers** — duplicate IPP request-ids silently break
    response matching.
22. **If you must disable TLS verification, scope it to the printer connection.** A process-wide
    trust-all factory also disables verification for everything else in the app.
23. **For SMB, budget for jCIFS (SMB1 + NTLM) if legacy servers matter**, and remember printer
    enumeration is `spoolss` → `EnumPrinters` + `GetPrinter`, *not* a share listing; share listing is
    what discovers hosts.
24. **Encode credentials separately from the URI** and try, in order: anonymous, guest, stored
    credentials, previous host's credentials.
25. **For Bluetooth printers use classic RFCOMM/SPP**, filter by `BluetoothClass.Device.Major.IMAGING`,
    and keep the fallback ladder service-record → insecure → channel.
26. **Do not reflectively patch `java.net.URI$AUTHORITY_ENCODER`** to make `smb://user:p@host` parse.
27. **Persist only the identity; re-derive everything else on launch.** Store credentials in
    `EncryptedSharedPreferences`, never cleartext.
28. **Schedule nothing in the background if you don't need to.** Activity-scoped threads cancelled in
    `onDestroy()` is a legitimate choice provided every thread is cancellable.
29. **Never trust a device-reported length.** Validate before indexing; treat a malformed packet as
    "ignore this one", never "abort this batch".
30. **Keep a model-normalisation pass on every device-supplied name** — vendor strings like
    `"Canon Inc."` and names wrapped in parentheses otherwise produce duplicate "unknown printer" rows.