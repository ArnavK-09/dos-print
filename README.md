# 🖨️ Dos Print 🖨️

## Print your way — documents, photos, booklets and labels, right from your phone

[![hero](https://emojirouter.vercel.app/svg/🖨️)](https://github.com/ArnavK-09/DosPrint)

> [!NOTE]
> **Dos Print** is a native Android app (Kotlin + Jetpack Compose) that prints documents and
> arranges photos on a page exactly the way you want them. This codebase was **vibecoded** —
> designed and written end-to-end alongside an AI coding agent. Wi-Fi, USB and Bluetooth printers
> are discovered automatically, and preview, print and PDF export all render through one shared
> code path, so what you see is what gets printed.

## 🌟 Features

  - **Automatic printer discovery** – Finds Wi-Fi (mDNS), USB and Bluetooth printers, or add one by hand.
- **Layout presets** – 1/2/4/6/9/16 pages per sheet, booklets, 4x6/5x7/10x15 photo prints, business cards, address labels, CD labels, DL envelopes and 11x17 posters.
- **Live preview** – A live render of the first sheet on the Setup screen, plus a full zoomable sheet inspector at real paper proportions.
- **Photo mode** – Multiple photos per sheet with scaling (fit / fill / stretch), gaps, position, grid, borders and photo-size presets.
- **Advanced printing** – Page ranges, copies, rotation, duplex / booklet fold, colour / grayscale, quality profiles, watermarks and borderless printing.
- **Job history** – Every submitted job with printer, settings and live status; cancel active jobs or reopen finished ones.
- **Rock-solid error handling** – Structured error log readable in-app, crash reports surfaced on the next launch, corrupted storage quarantined automatically.

## 💻 Installation

> You can build **Dos Print** from source with one command:

###### terminal

```bash
./build.sh assembleRelease
```

> The signed APK lands in `build-outputs/dos-print-vX.Y.Z.apk` — install it on any Android
> device (sideload). Project guides (architecture, user guide, build details) live in [`docs/`](docs/).

## 📷 Screenshots

> Here's a working and expected screenshot of **Dos Print**

| Setup Screen | Print Preview |
| --- | --- |
| <p align="center"><em>📷 Screenshot coming soon</em></p> | <p align="center"><em>📷 Screenshot coming soon</em></p> |

---

## 💻 Contributing

> [!TIP]
> We welcome contributions to improve **Dos Print**! If you have suggestions, bug fixes, or new feature ideas, follow these steps:

1. **Fork the Repository**
   Click the **Fork** button at the top-right of the repo page.

2. **Clone Your Fork**
   Clone the repo locally:

   ```bash
   git clone https://github.com/ArnavK-09/DosPrint.git
   ```

3. **Create a Branch**
   Create a new branch for your changes:

   ```bash
   git checkout -b your-feature-branch
   ```

4. **Make Changes**
   Implement your changes (bug fixes, features, etc.). Build with `./build.sh assembleDebug`.

5. **Commit and Push**
   Commit your changes and push the branch:

   ```bash
   git commit -m "feat(scope): description"
   git push origin your-feature-branch
   ```

6. **Open a Pull Request**
   Open a PR with a detailed description of your changes.

7. **Collaborate and Merge**
   The maintainers will review your PR, request changes if needed, and merge it once approved.

## 🙋‍♂️ Issues

Found a bug or need help? Please create an issue on the [GitHub repository](https://github.com/ArnavK-09/DosPrint/issues) with a detailed description.

## 👤 Author

| [![Arnav K](https://github.com/ArnavK-09.png?s=100)](https://github.com/ArnavK-09) [**Arnav K**](https://github.com/ArnavK-09) |
| --- |

---

## 📄 License

**Dos Print** is licensed under the `Unlicense` License. See the [LICENSE](LICENSE) file for more details.

> The liquid glass bottom tab component in `dos-print/src/main/kotlin/com/dosprint/ui/components/liquidglass/` is adapted from the
> AndroidLiquidGlass 1.0.0 catalog by Kyant, licensed under the Apache License 2.0. The license text is in
> `third-party-licenses/AndroidLiquidGlass-Apache-2.0.txt`. The Backdrop effect library
> (`io.github.kyant0:backdrop:1.0.0`) is also used.
