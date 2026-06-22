# Stage Manager

Android app for building and playing ordered song lists from shared PDFs and images. Each imported file becomes a **song** in an archive; songs can be grouped into **playlists** with drag-to-reorder, full-text search, and swipe-through playback.

Designed for sideloading on recent 64-bit ARM phones. CI builds a signed arm64 release APK and publishes it to GitHub Releases.

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
- **Metadata on import** — Each import prompts for **Title**, **Key**, and **Notes**. Underscores in the filename become spaces in the suggested title.
- **Duplicate entries** — The same file can be imported multiple times with different Key/Notes (separate archive rows).
- **Song list** — Compact rows: **Title (Key)** on the first line, notes preview on the second. **Pencil** opens edit (title, key, notes) with a **Delete** action and confirmation.
- **Song viewer** — Tap a song for fullscreen view: images via Coil, or swipe left/right through multi-page PDFs (platform `PdfRenderer`). Pinch to zoom on images and PDF pages.

### Playlists

- **Create / rename** — New playlists get an editable name. The **Playlists** tab shows each playlist as a **colorful block** (NoTube-style folder colors) with inline **pencil** (rename), **color**, and **delete**.
- **Ordered sequences** — A playlist is an ordered list of songs from the archive.
- **Add songs** — Search dialog with full-text match across title, key, and notes.
- **Drag reorder** — Long-press and drag rows in the Songs tab, Playlists tab, or playlist detail screen. Uses the same center-vs-center swap logic as NoTube (`DraggableItem` + `ReorderLogic`).
- **Duplicate playlist** — Copies name (with “(copy)”) and full song order.
- **Playlist detail** — Two-line header: **back + title** (playlist accent color) on line 1; **tools** on line 2 (+ add, play, remote, rename, duplicate, color, delete). Compact song rows: **Title (Key)** + notes, small **trash** to remove from the playlist. No in-app **Stop** for remote — use the system notification.
- **Playback mode** — Swipe horizontally through each song in the playlist (images and PDFs).
- **Settings** — **Gear** icon on the main tabs opens **Settings**: configure the **4-digit remote play PIN** (default `0000`) and the **local HTTP port** (default `44444`) used before the Cloudflare tunnel starts. Shows the **installed app version** and a **Check for updates** button (same GitHub Release flow as the launch snackbar).
- **Remote play** — Wi‑Fi icon starts a local HTTP server and a free **Cloudflare Quick Tunnel** (`*.trycloudflare.com`). Open the HTTPS URL on another device (tablet, laptop) anywhere on the internet for a fullscreen browser view. Visitors enter the PIN from **Settings** before the stage UI loads. Swipe or arrow keys advance songs/pages while the phone keeps serving the playlist. On start the URL opens in the phone’s browser automatically. While active, a **foreground notification** (default priority) shows a generic “remote play active” message and a **Stop** action — it does **not** show the public URL or PIN (tap the highlighted **Wi‑Fi** icon in the app to reopen the link). The main-tab **Wi‑Fi** shortcut uses the last-opened playlist when remote is off; the playlist detail screen starts remote for that playlist. The Wi‑Fi icon is highlighted when active, gray when off. In the browser, **pencil** opens a web editor to reorder, remove, or add songs from the archive (mirrors the in-app playlist screen).
- **In-app updates** — On cold start, checks GitHub Releases for a newer signed APK; snackbar prompt, download progress banner, then system installer (requires **Install unknown apps** permission for this package).

### Quickstart playlist

Paste a block of text (one song title per line, e.g. a set list). The app fuzzy-matches lines against the archive and assembles a playlist from the best hits. Review matches, then create the playlist.

## Screens

Sketch of the main flows (not to scale):

```
┌─────────────────────────────────────┐
│ Stage Manager              📶  ⚙ │  ← Wi‑Fi + Settings
├─────────────────────────────────────┤
│ [ Songs ]  [ Playlists ]            │
├─────────────────────────────────────┤
│                                     │
│  SONGS TAB                          │
│  ┌─────────────────────────────┐    │
│  │ Amazing Grace (G)          ✎ │    │
│  │ intro notes                  │    │
│  └─────────────────────────────┘    │
│                                     │
│  tap row → fullscreen viewer        │
│  long-press drag → reorder          │
│  ✎ → edit title / key / notes       │
│     (delete with confirmation)      │
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

        tap playlist block
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Sunday set                        │  ← line 1: back + title (accent color)
├─────────────────────────────────────┤
│  +   ▶   📶   ✎   ⧉   ○   🗑   │  ← line 2: tools (Wi‑Fi gray when off)
├─────────────────────────────────────┤
│  Amazing Grace (G)              🗑  │
│  intro notes                        │
│  How Great Thou Art (Bb)        🗑  │  ← red if deleted from archive
│                                     │
│  empty: “Tap + above to add songs.” │
└─────────────────────────────────────┘

PLAYLISTS TAB
┌─────────────────────────────────────┐
│ [ New playlist ]  [ Quickstart ]    │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Sunday set            ✎  ○  🗑 │ │  ← colorful block; tap to open
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ Rehearsal             ✎  ○  🗑 │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

        ⚙ Settings
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Settings                          │
├─────────────────────────────────────┤
│ Remote play PIN  [____]             │
│ HTTP port        [44444]            │
│              [ Save ]               │
│ App version      1.0.42             │
│      [ Check for updates ]          │
└─────────────────────────────────────┘

REMOTE PLAY ACTIVE (notification shade)
┌─────────────────────────────────────┐
│ Remote: Sunday set                  │
│ Active — open the app and tap the   │
│ Wi‑Fi icon for the link             │
│                        [ Stop ]     │
└─────────────────────────────────────┘
```

## Usage

1. **Import a song** — Gallery or browser → Share → Stage Manager → fill Title, Key, Notes → Save.
2. **Browse** — **Songs** tab lists the archive; tap to open fullscreen.
3. **New playlist** — **Playlists** tab → **New playlist** → enter name (opens the new playlist). Or rename / recolor / delete from the colorful blocks on the list.
4. **Add songs** — Open a playlist → **+** → search → tap a result.
5. **Reorder** — Long-press a row and drag (Songs, Playlists, or playlist detail).
6. **Play** — Open a playlist → **Play** → swipe between songs.
7. **Remote play** — Open a playlist → **Wi‑Fi** (or main-tab **Wi‑Fi** for the last-opened playlist). The URL opens in the phone browser; share that link to the tablet. Enter the PIN from **Settings**. Tap **Wi‑Fi** again anytime to reopen the link. **Stop** via the system notification (or when deleting the playlist).
8. **Settings** — Main tabs → **gear** → set remote PIN and local port → **Save**. **Check for updates** anytime from the same screen.
9. **Quickstart** — **Playlists** tab → **Quickstart playlist** → paste text → **Match songs** → **Create**.
10. **Update** — If a newer GitHub Release exists, a snackbar offers **Update now**; allow installs from this app when prompted.

## Project layout

```
playlists/
├── .cursor/skills/                 # Cursor agent skills (compile, README sync)
├── .github/workflows/android.yml   # CI: test → cloudflared → release → GitHub Release
├── scripts/
│   └── fetch-cloudflared.sh        # Build cloudflared for Android arm64 (CI + local release)
├── update.sh                       # Interactive rsync sync, commit, push
├── gradlew                         # Gradle wrapper (committed)
├── app/
│   ├── build.gradle.kts
│   ├── keystore/playlists.keystore # Shared sideload signing key (committed)
│   └── src/main/
│       ├── assets/
│       │   ├── cloudflared         # Bundled tunnel binary (gitignored; built by script)
│       │   └── remote/             # play.html, edit.html, pin.html
│       └── java/com/playlists/app/
│           ├── data/               # Room: Song, Playlist, PlaylistSong
│           ├── remote/             # HTTP server, tunnel, foreground service, notification
│           ├── ui/
│           │   ├── MainActivity.kt # Single Compose entry + share intents
│           │   ├── PlaylistsViewModel.kt
│           │   ├── navigation/     # NavHost routes
│           │   ├── screens/        # Compose screens (incl. SettingsScreen)
│           │   ├── components/     # Media viewer, dialogs, update banner
│           │   ├── reorder/        # DraggableItem + list drag handler
│           │   └── theme/          # Material 3 theme
│           └── util/               # Share import, quickstart matcher, AppUpdate, AppPrefs
└── gradle/wrapper/
```

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Remote play tunnel, in-app update check/download |
| `POST_NOTIFICATIONS` | Remote-play foreground notification (Android 13+) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Keep remote play alive while tunneled |
| `REQUEST_INSTALL_PACKAGES` | In-app update installs the downloaded APK |

## Build

```bash
# Debug (local install)
./gradlew :app:assembleDebug

# Release (arm64-v8a — same as CI; remote play tunnel needs cloudflared in assets)
bash scripts/fetch-cloudflared.sh   # requires Go 1.22+; writes app/src/main/assets/cloudflared
./gradlew :app:assembleRelease
```

Requires Android SDK (API 34 platform + build-tools 34.0.0) and JDK 17. Set `sdk.dir` in `local.properties` or via `ANDROID_HOME`. Release builds that include remote play need Go to run `fetch-cloudflared.sh` first — the binary is gitignored and CI builds it on every release workflow run.

The Gradle wrapper (`gradlew`, `gradle/wrapper/`) is committed so `./gradlew` works after clone. Keep `local.properties` (SDK path) out of git — it is in `.gitignore`.

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
2. Builds `cloudflared` for Android arm64 (`scripts/fetch-cloudflared.sh`, Go 1.22)
3. Builds an arm64-v8a release APK
4. Publishes a GitHub Release tagged `v1.0.<run>` with:
   - `app-1.0.<run>.apk` (versioned)
   - `app.apk` (stable alias for in-app updates)

Stable download URL: `https://github.com/diegoboston/playlists/releases/latest/download/app.apk`

Each CI run sets `versionCode = GITHUB_RUN_NUMBER` and `versionName = 1.0.<run>`. No GitHub Secrets are needed for signing — the keystore is in the repo.

## In-app updates

On cold start the app checks GitHub Releases for a newer build:

1. **Check** — `GET https://api.github.com/repos/diegoboston/playlists/releases/latest`, read `tag_name` (e.g. `v1.0.42`), parse `versionCode` `42`, compare to the installed app.
2. **Prompt** — If remote is newer, a snackbar offers to update (or use **Check for updates** in **Settings**).
3. **Download** — Progress banner; fetches `app.apk` from the release.
4. **Install** — Opens the system package installer via `FileProvider`. Android may prompt to allow **Install unknown apps** for Stage Manager first.

Implementation: `AppUpdate.kt`, `PlaylistsViewModel.kt`, `MainActivity.kt`, `AppUpdateBanner`. Change `AppUpdate.REPO` if the GitHub repo slug differs.

## Remote play

Control playback from a **second screen** over the internet (e.g. iPad on a music stand while the phone sits on a stand).

1. **PIN** — In **Settings** (gear on main tabs), set a **4-digit remote play PIN** (default `0000`). Anyone opening the remote URL must enter this PIN once per browser session. Optionally change the **local server port** (default `44444`).
2. **Start** — Open a playlist → tap the **Wi‑Fi** toolbar icon (or the main-tab **Wi‑Fi** shortcut for the last-opened playlist). The phone starts a local HTTP server, opens a free **Cloudflare Quick Tunnel**, opens the URL in the browser, and shows a **foreground notification** (“Active — open the app and tap the Wi‑Fi icon for the link”) with **Stop**. The notification does not include the URL or PIN.
3. **Connect** — On the tablet or laptop, open the URL (from the phone browser tab, or tap **Wi‑Fi** again in the app) and enter the PIN.
4. **Browser UI** — Fullscreen sheet music / image for the current song and page. Title bar shows playlist name and `3/12: Song title · page 2/3`. **+** uploads a new file; **pencil** opens `/edit` to reorder, remove, or add songs from the archive.
5. **Navigate** — Swipe left/right (or laptop arrow keys) for next/previous song; multi-page PDFs advance page before moving to the next song.
6. **Edit playlist** — On `/edit`, drag rows to reorder, tap **Remove**, or search the archive to add. **Done** returns to the stage view. Changes sync to the phone database immediately.
7. **Stop** — Tap **Stop** on the system notification, or delete the active playlist.

Requires **internet** on the phone (Wi‑Fi or cellular). The tunnel URL changes each session. On Android 13+, the app requests notification permission so the remote-play foreground notification can appear. CI bundles `cloudflared` via `scripts/fetch-cloudflared.sh` on every release build. Implementation: `PlayRemoteController.kt`, `CloudflareTunnel.kt`, `RemotePlayService.kt`, `RemotePlayNotification.kt`, `PlayRemoteServer.kt`, `SettingsScreen.kt`, `assets/remote/play.html`, `assets/remote/edit.html`, `assets/remote/pin.html`.

## update.sh

Interactive script to pull sources from a remote machine (via rsync), review changes, commit, and push to `origin main`:

```bash
./update.sh
```

1. Cleans local build artifacts
2. Rsyncs from the configured remote into the parent directory (edit the host/path in the script if needed)
3. Shows `git status` / `git diff` with confirmation prompts
4. Commits (min 10-char message) and pushes to `origin main`

## Cursor agent skills

Project-local skills under `.cursor/skills/` guide automated edits:

| Skill | When to run |
| ----- | ----------- |
| **rebuild-app** | After any app change — run `rebuild-app.sh` (Java 17 env, compile, unit tests, debug APK; must print `VERIFY OK`) |
| **compile-kotlin** | Fast Kotlin-only check (no APK) when explicitly requested |
| **update-readme** | After user-facing or structural changes — keeps this README accurate |
| **local-workspace** | When a path looks missing — search locally; do not rsync or run `update.sh` from this repo |

See each skill's `SKILL.md` for the exact command or checklist.

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
- NanoHTTPD (local remote-play server)
- Bundled `cloudflared` binary (Cloudflare Quick Tunnel)
