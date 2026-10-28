# Build Instructions

## Prerequisites

The local `.toolchain/` directory is excluded from this repository. Install or provision the
following versions under those paths before running `./build.sh`.

| Tool | Version | Location |
|---|---|---|
| OpenJDK | 21 (Temurin) | `.toolchain/jdk` |
| Gradle | 8.13 | `.toolchain/gradle-8.13` |
| Android SDK Platform | 35 | `.toolchain/android-sdk/platforms/android-35` |
| Android Build-Tools | 35.0.0 | `.toolchain/android-sdk/build-tools/35.0.0` |
| AGP | 8.10.1 | resolved from Google Maven |
| Kotlin | 2.1.21 | resolved from Maven Central |

Network access is required on the first build so Gradle can resolve dependencies. Afterwards the
Gradle cache lives in `.toolchain/gradle-home` and builds work offline.

## Steps

```bash
cd /path/to/DosPrint

./build.sh compileDebugKotlin    # verify it compiles
./build.sh testDebugUnitTest     # 170 unit tests
./build.sh detekt                # static analysis
./build.sh spotlessCheck         # formatting
./build.sh lintVitalRelease      # release lint
./build.sh assembleRelease       # release build; unsigned without local signing files
```

`assembleRelease` copies the minified APK to:

```
build-outputs/dos-print-v0.0.1.apk
```

## Verifying the artifact

```bash
.toolchain/android-sdk/build-tools/35.0.0/aapt2 dump badging build-outputs/dos-print-v0.0.1.apk
.toolchain/android-sdk/build-tools/35.0.0/apksigner verify -v build-outputs/dos-print-v0.0.1.apk
```

Expected: `versionName='0.0.1' versionCode='1'`, `minSdkVersion:'26'`, and
`targetSdkVersion:'35'`. Signature verification succeeds only when local signing files are set up.

## Installing

```bash
adb install -r build-outputs/dos-print-v0.0.1.apk
```

Runtime permissions are requested in-app:

1. **Notifications** (Android 13+) — print progress and results.
2. **Files and media** — rendering images and documents.
3. **Nearby devices** — Bluetooth printer discovery.

## Release signing

Release signing files are intentionally excluded from this public repository. To sign locally, place
your own keystore at `dos-print/config/signing/` and configure
`dos-print/config/signing/signing.properties`. Never commit either file.

## Troubleshooting

| Problem | Fix |
|---|---|
| `JAVA_HOME is not set` or `JAVA_HOME is wrong` | Use `./build.sh`, not a bare `gradle` |
| `SDK location not found` | Provision Android SDK Platform 35 under `.toolchain/android-sdk` |
| Dependency resolution fails offline | The first build needs network; later builds use `.toolchain/gradle-home` |
| `Unresolved reference` on a platform API | Check the stripped `android.jar`; see [DEVELOPMENT.md](DEVELOPMENT.md) |
