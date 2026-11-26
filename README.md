# Property Hub — Collect • Generate • Share

Complete offline native Android application (Java & XML, Material 3, `minSdk 23`, `targetSdk 35`) for collecting **House for Rent** and **Land for Sale** property listings, generating **4:3 Landscape (1440×1080)** offline JPG posters with native Android `Canvas`, running OOM-safe bulk poster batches, and exporting verified ZIP archives using Android Storage Access Framework (SAF).

## Folder & File Structure

On first launch, authorize a parent folder (such as `Documents`). Property Hub creates and maintains:

```text
Property Hub/
├── House for Rent/
│   └── yyyy-MM-dd.txt
├── Land for Sale/
│   └── yyyy-MM-dd.txt
├── Generated Posters/
│   ├── House for Rent/
│   │   └── House_000001.jpg
│   └── Land for Sale/
│       └── Land_000001.jpg
└── ZIP Exports/
    └── Property_Hub_<Category>_<DateOrRange>.zip
```

## Key Capabilities

- **Collect Data**: Independent **House for Rent** (blue) and **Land for Sale** (green) cards with non-blocking top-sliding toast notifications and verbatim multiline details preservation.
- **Monotonic Record IDs**: Global sequence counters (`HOUSE_000001`, `LAND_000001`) that never reset across days or reuse deleted IDs.
- **Native 4:3 Canvas Posters**: Distinct typographic/geometric templates (`HousePosterTemplate` & `LandPosterTemplate`) with dynamic font-scaling (`PosterTextFitter`) and zero external images or internet dependency.
- **Bulk Poster Generator**: Skips existing posters by default, processes sequentially with live progress/cancellation/retry support.
- **Settings & Themes**: Storage location switching (never deletes existing files), `Auto JPG Image by Submit` toggle, and `Blue (Default)` / `Light` / `Dark` app themes.
- **ZIP Exports**: Archives filtered JPG posters (and optionally TXT records) and verifies every entry using `ZipInputStream`.

## Build Debug APK with GitHub Actions

1. Push this repository to GitHub (`main` or `master` branch).
2. Open the **Actions** tab in your GitHub repository.
3. Wait for **Build Property Hub Android APK** (`.github/workflows/build.yml`) to complete unit tests (`testDebugUnitTest`) and APK compilation (`assembleDebug`).
4. Download the **PropertyHub-debug-apk** artifact (`app-debug.apk`).

## Local Android Studio Build

Open the project root in Android Studio (JDK 17, Android SDK 35, Gradle 8.7) and run **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
