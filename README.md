# Playlists

Android app for building and playing ordered song lists from shared PDFs and images. Each imported file becomes a **song** in an archive; songs can be grouped into **playlists** with drag-to-reorder, full-text search, and swipe-through playback.

Designed for sideloading (not Google Play). CI builds signed release APKs and publishes them to GitHub Releases.

## Requirements

| Setting | Value |
|---------|-------|
| **minSdk** | 18 (Android 4.3 Jelly Bean MR2) |
| **targetSdk** | 34 |
| **compileSdk** | 34 |
| **Package** | `com.playlists.app` |
| **JDK** | 17 (required for Gradle/AGP) |

### Android 4.3 compatibility

The app runs on API 18 with a few deliberate trade-offs:

- **View-based UI** (AppCompat), not Jetpack Compose — Compose requires API 21+.
- **Pdfium** for PDF rendering on API 18–20; platform `PdfRenderer` on API 21+.
- **Older AndroidX/Material** versions pinned so dependencies still declare minSdk ≤ 18 (modern Material 1.12+ requires API 19+).
- **Multidex + desugaring** enabled for Java 17 APIs on older devices.

## Features

### Song archive

- **Share to import** — Share an image, PDF, or URL from another app. Playlists appears in the share sheet.
- **Metadata on import** — Each import prompts for **Title**, **Key**, and **Notes**.
- **Duplicate entries** — The same file can be imported multiple times with different Key/Notes (separate archive rows).
- **Song list** — Shows title, key, and notes preview (first ~20 characters), plus file type badge.
- **Song viewer** — Tap a song for fullscreen view: pinch-zoom images, or swipe left/right through multi-page PDFs.

### Playlists

- **Create / rename** — New playlists get an editable name.
- **Ordered sequences** — A playlist is an ordered list of songs from the archive.
- **Add songs** — Search dialog with full-text match across title, key, and notes.
- **Drag reorder** — Long-press and drag; center-vs-center swap logic in `ReorderLogic` / `ReorderTouchHelper`.
- **Duplicate playlist** — Copies name (with “(copy)”) and full song order.
- **Playback mode** — Swipe horizontally through each song in the playlist (images and PDFs).

### Quickstart playlist

Paste a block of text (one song title per line, e.g. a set list). The app fuzzy-matches lines against the archive and assembles a playlist from the best hits. Review matches, then create the playlist.

## Usage

1. **Import a song** — Gallery or browser → Share → Playlists → fill Title, Key, Notes → Save.
2. **Browse** — **Songs** tab lists the archive; tap to open fullscreen.
3. **New playlist** — **Playlists** tab → **+** → enter name.
4. **Add songs** — Open a playlist → **Add song** → search → tap a result.
5. **Reorder** — Long-press a row in the playlist and drag.
6. **Play** — Open a playlist → **Play** → swipe between songs.
7. **Quickstart** — **Playlists** tab → **Quickstart playlist** → paste text → **Match songs** → **Create**.

## Project layout

```
playlists/
├── .github/workflows/android.yml   # CI: test → build → GitHub Release
├── update.sh                       # Sync from shared6, commit, push
├── app/
│   ├── build.gradle.kts
│   ├── keystore/playlists.keystore # Shared sideload signing key (committed)
│   └── src/main/java/com/playlists/app/
│       ├── data/                   # Room: Song, Playlist, PlaylistSong
│       ├── ui/                     # Adapters, drag reorder, PDF helper
│       ├── ui/screens/             # Activities
│       └── util/                   # Share import, quickstart matcher
└── gradle/wrapper/
```

## Build

```bash
# Debug (local install)
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

# Release (all ABIs)
./gradlew :app:assembleRelease

# Release (single ABI, ~smaller — same as CI)
./gradlew :app:assembleRelease -Pabi=arm64-v8a

# Unit tests
./gradlew :app:testDebugUnitTest
```

Requires Android SDK (API 34 platform + build-tools 34.0.0) and JDK 17. Set `sdk.dir` in `local.properties` or via `ANDROID_HOME`.

## Signing

Both debug and release use the same repo-checked keystore so sideload updates install without uninstalling:

| | |
|---|---|
| **Keystore** | `app/keystore/playlists.keystore` |
| **Alias** | `playlists` |
| **Password** | `playlistsapp` |

This is a personal sideload key, not a Play Store key.

## CI / releases

On push to `main` or `master`, GitHub Actions (`.github/workflows/android.yml`):

1. Runs unit tests
2. Builds arm64-v8a release APK
3. Publishes a GitHub Release with:
   - `app-1.0.<run>.apk` (versioned)
   - `app.apk` (stable alias for updates)

`versionCode = GITHUB_RUN_NUMBER`, `versionName = 1.0.<run>`.

No GitHub Secrets are needed for signing — the keystore is in the repo. Release publishing uses the default `GITHUB_TOKEN`.

## update.sh

Interactive sync script (run from another machine to pull sources from `shared6`):

```bash
./update.sh
```

1. Cleans local build artifacts
2. Rsyncs from `shared6:code/d-a/playlists` into the parent directory
3. Shows `git status` / `git diff` with confirmation prompts
4. Commits (min 10-char message) and pushes to `origin main`

Excludes `.gradle/`, `build/`, `local.properties`, IDE files, etc.

## Data model

- **Song** — title, key, notes, file path, type (IMAGE/PDF), mime type. Multiple songs can point at the same file with different metadata.
- **Playlist** — name, creation time.
- **PlaylistSong** — playlist + song + position (ordered).

Files are stored in app-internal storage (`files/songs/`). Metadata lives in Room (`playlists.db`).

## Tech stack

- Kotlin, View Binding, AppCompat
- Room + KSP
- Coroutines / Flow
- Pdfium (`pdfium-android`) + platform PdfRenderer (API 21+)
- PhotoView (image pinch-zoom)
- ViewPager2 (PDF pages, playlist playback)
