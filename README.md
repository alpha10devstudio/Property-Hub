# Property Hub — Offline Property Data Collection Android App

Native Android application (Java & XML) for collecting **House for Rent** and **Land for Sale** property records completely offline using Android's Storage Access Framework (SAF).

## Folder & Daily File Structure

On first launch, select a parent folder (such as `Documents`). Property Hub creates and maintains:

```text
Property Hub/
├── House for Rent/
│   └── yyyy-MM-dd.txt
└── Land for Sale/
    └── yyyy-MM-dd.txt
```

- Exactly one `.txt` file per category per local calendar day (`yyyy-MM-dd.txt`).
- Multiple submissions on the same day append sequentially (`PROPERTY RECORD #001`, `#002`, ...) to that day's file.
- A new file is automatically created when the device's local calendar date changes.

## Build Debug APK with GitHub Actions

1. Push this repository to GitHub (`main` or `master` branch).
2. Open the **Actions** tab in your GitHub repository.
3. Wait for the **Build Property Hub Android APK** workflow (`.github/workflows/build.yml`) to complete.
4. Download the **PropertyHub-debug-apk** artifact (`app-debug.apk`).

## Local Android Studio Build

Open the project root in Android Studio (JDK 17, Android SDK 35, Gradle 8.7) and run **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
