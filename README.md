# Stage Manager

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

**Note:** Android version numbers and API levels are different. Android **12** is API **31** — well above the minimum.

## Features

### Song archive

- **Share to import** — Share an image, PDF, or URL from another app. Stage Manager appears in the share sheet (single launcher activity handles share intents).
- **Metadata on import** — Each import prompts for **Title**, **Key**, and **Notes**.
- **Duplicate entries** — The same file can be imported multiple times with different Key/Notes (separate archive rows).
- **Song list** — Shows title, key, and notes preview (first ~20 characters), plus file type badge.
- **Song viewer** — Tap a song for fullscreen view: images via Coil, or swipe left/right through multi-page PDFs (platform `PdfRenderer`). Pinch to zoom on images and PDF pages.

### Playlists

- **Create / rename** — New playlists get an editable name.
- **Ordered sequences** — A playlist is an ordered list of songs from the archive.
- **Add songs** — Search dialog with full-text match across title, key, and notes.
- **Drag reorder** — Long-press and drag rows in the Songs tab, Playlists tab, or playlist detail screen. Uses the same center-vs-center swap logic as NoTube (`DraggableItem` + `ReorderLogic`).
- **Duplicate playlist** — Copies name (with “(copy)”) and full song order.
- **Playback mode** — Swipe horizontally through each song in the playlist (images and PDFs).
- **Remote play** — Wi‑Fi icon on the playlist toolbar starts a local HTTP server; open the URL on another device on the same network (tablet, laptop) for a fullscreen browser view. Swipe or arrow keys advance songs/pages while the phone keeps serving the playlist. A banner shows the active URL and **Stop**.

### Quickstart playlist

Paste a block of text (one song title per line, e.g. a set list). The app fuzzy-matches lines against the archive and assembles a playlist from the best hits. Review matches, then create the playlist.

## Screens

Sketch of the main flows (not to scale):

```
┌─────────────────────────────────────┐
│ Stage Manager                       │
├─────────────────────────────────────┤
│ [ Songs ]  [ Playlists ]            │
├─────────────────────────────────────┤
│                                     │
│  SONGS TAB                          │
│  ┌─────────────────────────────┐    │
│  │ Amazing Grace          PDF │🗑│  │
│  │ Key: G · intro notes · 2 pg│    │
│  └─────────────────────────────┘    │
│                                     │
│  tap row → fullscreen viewer        │
│  long-press drag → reorder          │
│  🗑 → remove from archive (soft)    │
│                                     │
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
│              [ Save ]               │
└─────────────────────────────────────┘

        tap playlist row
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Sunday set                        │
├─────────────────────────────────────┤
│  +   ▶   📶  ✎   ⧉   ○   🗑            │
│ add play remote rename dup color delete  │
├─────────────────────────────────────┤
│ http://192.168.1.5:8080/    [ Stop ]  │  ← remote play banner (when active)
├─────────────────────────────────────┤
│  Amazing Grace                      │
│  (G) intro                          │
│  How Great Thou Art          Remove │  ← red if deleted from archive
│                                     │
│  empty: “Tap + above to add songs.” │
└─────────────────────────────────────┘
```

## Usage

1. **Import a song** — Gallery or browser → Share → Stage Manager → fill Title, Key, Notes → Save.
2. **Browse** — **Songs** tab lists the archive; tap to open fullscreen.
3. **New playlist** — **Playlists** tab → **New playlist** → enter name.
4. **Add songs** — Open a playlist → **+** → search → tap a result.
5. **Reorder** — Long-press a row and drag (Songs, Playlists, or playlist detail).
6. **Play** — Open a playlist → **Play** → swipe between songs.
7. **Remote play** — Open a playlist → **Wi‑Fi** → open the URL on another device on the same LAN; swipe there to change songs/pages. **Stop** ends the server.
8. **Quickstart** — **Playlists** tab → **Quickstart playlist** → paste text → **Match songs** → **Create**.

## Project layout

```
playlists/
├── .github/workflows/android.yml   # CI: test → build → GitHub Release
├── update.sh                       # Interactive rsync sync, commit, push
├── app/
│   ├── build.gradle.kts
│   ├── keystore/playlists.keystore # Shared sideload signing key (committed)
│   └── src/main/java/com/playlists/app/
│       ├── data/                   # Room: Song, Playlist, PlaylistSong
│       ├── remote/                 # Local HTTP server + browser remote UI
│       ├── ui/
│       │   ├── MainActivity.kt     # Single Compose entry + share intents
│       │   ├── PlaylistsViewModel.kt
│       │   ├── navigation/         # NavHost routes
│       │   ├── screens/            # Compose screens
│       │   ├── components/         # Media viewer, dialogs
│       │   ├── reorder/            # DraggableItem + list drag handler
│       │   └── theme/              # Material 3 theme
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

CI bootstraps the Gradle wrapper on each run (`gradlew` is not committed; see `.gitignore`).

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

1. Compiles and runs unit tests
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
3. **Download** — Progress banner; fetches `app.apk` from the release.
4. **Install** — Opens the system package installer via `FileProvider`.

Implementation: `AppUpdate.kt`, `PlaylistsViewModel.kt`, `MainActivity.kt`. Change `AppUpdate.REPO` if the GitHub repo slug differs.

## Remote play

Control playback from a **second screen** on the same Wi‑Fi network (e.g. iPad on a music stand while the phone sits on a stand).

1. **Start** — Open a playlist → tap the **Wi‑Fi** toolbar icon. The phone starts a small HTTP server and opens the URL in the browser (or copy it from the banner).
2. **Browser UI** — Fullscreen sheet music / image for the current song and page. Title bar shows playlist name and `3/12: Song title · page 2/3`.
3. **Navigate** — Swipe left/right (or laptop arrow keys) for next/previous song; multi-page PDFs advance page before moving to the next song.
4. **Stop** — Tap **Stop** on the banner in the app, or leave the playlist screen (server stops when the screen is disposed).

Requires Wi‑Fi with a LAN IP (not cellular-only). HTTP is cleartext on the local network (`usesCleartextTraffic`). Implementation: `PlayRemoteController.kt`, `PlayRemoteServer.kt`, `assets/remote/play.html`.

## update.sh

Interactive script to pull sources from a remote machine (via rsync), review changes, commit, and push to `origin main`:

```bash
./update.sh
```

1. Cleans local build artifacts
2. Rsyncs from the configured remote into the parent directory (edit the host/path in the script if needed)
3. Shows `git status` / `git diff` with confirmation prompts
4. Commits (min 10-char message) and pushes to `origin main`

## Data model

- **Song** — title, key, notes, file path, type (IMAGE/PDF), mime type, sort order. Multiple songs can point at the same file with different metadata.
- **Playlist** — name, optional accent color, creation time.
- **PlaylistSong** — playlist + song + position (ordered).

Files are stored in app-internal storage (`files/songs/`). Metadata lives in Room (`playlists.db`).

## Tech stack

- Kotlin, **Jetpack Compose**, Material 3
- Single-activity navigation (`NavHost`)
- Room + KSP
- Coroutines / Flow
- Coil (images in Compose)
- Platform `PdfRenderer` + Compose `HorizontalPager` (multi-page PDFs)
