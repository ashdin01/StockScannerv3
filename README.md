# StockScan APK

A fully offline Android barcode scanning app for stocktake management.
No hotspot, no server, no internet required once installed.

## How it works

The app runs a tiny embedded HTTPS server on `localhost:8743` inside the app process.
The WebView loads from `https://localhost:8743` — giving the browser a secure context,
which is what Android requires to grant camera access. No external network is ever used.

---

## Option A — Build via GitHub Actions (FREE, no software needed)

1. Create a free account at **github.com**
2. Create a new repository (can be private)
3. Upload all files from this folder to the repository
4. Go to the **Actions** tab in your repository
5. Click **"Build StockScan APK"** → **"Run workflow"**
6. Wait ~3 minutes, then download `StockScan-debug.apk` from the Artifacts section

---

## Option B — Build with Android Studio (free desktop app)

1. Download **Android Studio** from developer.android.com/studio (free)
2. Open this folder as a project
3. Click **Build → Build Bundle(s)/APK(s) → Build APK(s)**
4. APK appears at: `app/build/outputs/apk/debug/app-debug.apk`

---

## Option C — Build from command line (if you have the Android SDK)

```bash
chmod +x gradlew
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## Installing the APK on Android

1. Copy `app-debug.apk` to your Android device
2. Open your file manager and tap the APK
3. If prompted, enable **"Install from unknown sources"** in Settings → Security
4. Install and open **StockScan**
5. Grant camera permission when asked → scanning works immediately

---

## Features
- EAN-13 barcode scanning via device camera
- Uses Android's native BarcodeDetector API (fast, accurate)  
- Falls back to pure-JS decoder if unavailable
- Manual barcode entry
- Scanned items list with counts
- Export to CSV or TXT
- Fully offline — no internet needed
