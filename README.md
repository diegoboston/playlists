# Playlists

Android app for building and playing ordered song lists from shared PDFs and images. Each imported file becomes a **song** in an archive; songs can be grouped into **playlists** with drag-to-reorder, full-text search, and swipe-through playback.

Designed for sideloading on recent 64-bit ARM phones (not Google Play). CI builds a signed arm64 release APK and publishes it to GitHub Releases.

## Requirements

| Setting | Value |
|---------|-------|
| **minSdk** | 26 (Android 8.0 Oreo) |
| **targetSdk** | 34 |
| **compileSdk** | 34 |
| **ABI** | arm64-v8a only |
| **Package** | `com.playlists.app` |
| **JDK** | 17 (required for Gradle/AGP) |

**Note:** Android version numbers and API levels are different. Android **12** is API **31** — well above the minimum. This app does not target old devices (no Multidex, Pdfium, or 32-bit builds).

## Features

### Song archive

- **Share to import** — Share an image, PDF, or URL from another app. Playlists appears in the share sheet.
- **Metadata on import** — Each import prompts for **Title**, **Key**, and **Notes**.
- **Duplicate entries** — The same file can be imported multiple times with different Key/Notes (separate archive rows).
- **Song list** — Shows title, key, and notes preview (first ~20 characters), plus file type badge.
- **Song viewer** — Tap a song for fullscreen view: images via Coil, or swipe left/right through multi-page PDFs (platform `PdfRenderer`). Pinch to zoom on images and PDF pages.

### Playlists

- **Create / rename** — New playlists get an editable name.
- **Ordered sequences** — A playlist is an ordered list of songs from the archive.
- **Add songs** — Search dialog with full-text match across title, key, and notes.
- **Drag reorder** — Long-press and drag; center-vs-center swap logic in `ReorderLogic` / `ReorderTouchHelper`.
- **Duplicate playlist** — Copies name (with “(copy)”) and full song order.
- **Playback mode** — Swipe horizontally through each song in the playlist (images and PDFs).

### Quickstart playlist

Paste a block of text (one song title per line, e.g. a set list). The app fuzzy-matches lines against the archive and assembles a playlist from the best hits. Review matches, then create the playlist.

## Screens

Sketch of the main flows (not to scale):

```
┌─────────────────────────────────────┐
│ Playlists                           │
├─────────────────────────────────────┤
│ [ Songs ]  [ Playlists ]            │
├─────────────────────────────────────┤
│                                     │
│  SONGS TAB                          │
│  ┌─────────────────────────────┐    │
│  │ Amazing Grace          PDF │🗑│  │
│  │ Key: G · intro notes · 2 pg│    │
│  └─────────────────────────────┘    │
│  ┌─────────────────────────────┐    │
│  │ Blue Moon             IMAGE│🗑│  │
│  │ Key: C · verse 1 · 1 pg    │    │
│  └─────────────────────────────┘    │
│                                     │
│  tap row → fullscreen viewer        │
│  🗑 → remove from archive (soft)    │
│                                     │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Playlists                           │
├─────────────────────────────────────┤
│ [ Songs ]  [ Playlists ]            │
├─────────────────────────────────────┤
│                                     │
│  PLAYLISTS TAB                      │
│  ┌─────────────────────────────┐    │
│  │▌Sunday set        ○  ✎     │    │
│  └─────────────────────────────┘    │
│  ┌─────────────────────────────┐    │
│  │▌Rehearsal         ○  ✎     │    │  ← long-press drag to reorder
│  └─────────────────────────────┘    │
│                                     │
│  [ Quickstart playlist ]            │
│                              [ + ]  │  ← new playlist
└─────────────────────────────────────┘

        share from another app
                 │
                 ▼
┌─────────────────────────────────────┐
│ Import song                         │
├─────────────────────────────────────┤
│ Title  [________________]           │
│ Key    [________________]           │
│ Notes  [________________]         │
│                                     │
│              [ Save ]               │
└─────────────────────────────────────┘

        tap playlist row
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Sunday set                        │
├─────────────────────────────────────┤
│  +   ▶   ✎   ⧉   ○   🗑             │
│ add play rename dup color delete      │
├─────────────────────────────────────┤
│  Amazing Grace                      │
│  (G) intro                          │
│  How Great Thou Art          🗑     │  ← red if deleted from archive
│  (Bb)                               │
│                                     │
│  empty: “Tap + above to add songs.” │
└─────────────────────────────────────┘

        ▶ play
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Sunday set                        │
├─────────────────────────────────────┤
│ 1/5: Amazing Grace · page 2/3       │
├─────────────────────────────────────┤
│                                     │
│         [ sheet music / image ]     │
│         swipe ↔ next song           │
│         pinch to zoom               │
│                                     │
└─────────────────────────────────────┘

        tap song in archive
                 │
                 ▼
┌─────────────────────────────────────┐
│ Amazing Grace                       │
├─────────────────────────────────────┤
│                                     │
│         [ fullscreen image/PDF ]    │
│         PDF: swipe ↔ pages          │
│         pinch to zoom               │
│              2 / 3                  │
└─────────────────────────────────────┘

        Quickstart playlist
                 │
                 ▼
┌─────────────────────────────────────┐
│ Quickstart playlist                 │
├─────────────────────────────────────┤
│ paste set list (one title per line) │
│ ┌─────────────────────────────────┐ │
│ │ Amazing Grace                   │ │
│ │ Blue Moon                       │ │
│ └─────────────────────────────────┘ │
│                                     │
│ [ Match songs ]  →  review  → Create│
└─────────────────────────────────────┘
```

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
│       ├── ui/                     # Adapters, drag reorder, PDF helper, MainViewModel
│       ├── ui/screens/             # Activities
│       └── util/                   # Share import, quickstart matcher, AppUpdate
└── gradle/wrapper/
```

## Build

```bash
# Debug (local install)
./gradlew :app:assembleDebug

# Release (arm64-v8a — same as CI)
./gradlew :app:assembleRelease
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
2. Builds an arm64-v8a release APK
3. Publishes a GitHub Release tagged `v1.0.<run>` with:
   - `app-1.0.<run>.apk` (versioned)
   - `app.apk` (stable alias for in-app updates)

Stable download URL: `https://github.com/diegoboston/playlists/releases/latest/download/app.apk`

Each CI run sets `versionCode = GITHUB_RUN_NUMBER` and `versionName = 1.0.<run>`. No GitHub Secrets are needed for signing — the keystore is in the repo.

## In-app updates

On cold start the app checks GitHub Releases for a newer build:

1. **Check** — `GET https://api.github.com/repos/diegoboston/playlists/releases/latest`, read `tag_name` (e.g. `v1.0.42`), parse `versionCode` `42`, compare to the installed app.
2. **Prompt** — If remote is newer, a snackbar offers to update.
3. **Download** — Progress bar at the top of the main screen; fetches `app.apk` from the release.
4. **Install** — Opens the system package installer via `FileProvider`.

The stable URL is only for downloading. Version detection uses the GitHub API release tag, not the APK filename.

Implementation: `AppUpdate.kt`, `MainViewModel.kt`, `MainActivity.kt`. Change `AppUpdate.REPO` if the GitHub repo slug differs.

## update.sh

Interactive sync script (run from another machine to pull sources from `shared6`):

```bash
./update.sh
```

1. Cleans local build artifacts
2. Rsyncs from `shared6:code/d-a/playlists` into the parent directory
3. Shows `git status` / `git diff` with confirmation prompts
4. Commits (min 10-char message) and pushes to `origin main`

## Data model

- **Song** — title, key, notes, file path, type (IMAGE/PDF), mime type. Multiple songs can point at the same file with different metadata.
- **Playlist** — name, creation time.
- **PlaylistSong** — playlist + song + position (ordered).

Files are stored in app-internal storage (`files/songs/`). Metadata lives in Room (`playlists.db`).

## Tech stack

- Kotlin, View Binding, Material Components
- Room + KSP
- Coroutines / Flow
- Coil (images)
- Platform `PdfRenderer` + ViewPager2 (multi-page PDFs)
