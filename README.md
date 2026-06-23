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
- **Metadata on import** — Each import prompts for **Title**, **Key**, and **Notes**, pre-filled from the filename (underscores and dashes → spaces, extension dropped, trailing key → Key, trailing instrument → Notes).
- **Duplicate entries** — The same file can be imported multiple times with different Key/Notes (separate archive rows).
- **Song list** — Compact rows: **Title (Key)** on the first line, notes preview on the second. Placeholder songs (no real sheet yet) show a ⚠ after the title. **A–Z**, **Recently added** (import date), and **Recently viewed** buttons sort the archive (persists order). Opening a song in the viewer or playlist playback records its last-viewed time. **Pencil** opens edit (title, key, notes) with a **Delete** action and confirmation. Deleting checks whether the song is used in any playlist: if so, the dialog lists those playlist names and keeps the file on disk (the song disappears from the archive but still plays in playlists, shown in red); if not, the confirmation also removes the file from `Music/StageManager/songs`.
- **Song viewer** — Tap a song for fullscreen view: images via Coil, or swipe left/right through multi-page PDFs (platform `PdfRenderer`). Pinch to zoom on images and PDF pages.

### Playlists

- **Create / rename** — New playlists get an editable name. The **Playlists** tab shows each playlist as a **colorful block** (NoTube-style folder colors) with inline **pencil** (rename), **palette** (color), and **delete**.
- **Ordered sequences** — A playlist is an ordered list of songs from the archive.
- **Add songs** — Search dialog with full-text match across title, key, and notes. If a title is not in the archive, tap **Add placeholder page** to create a synthetic sheet with just the title (stored in the archive with a ⚠ marker).
- **Drag reorder** — Long-press and drag rows in the Songs tab, Playlists tab, or playlist detail screen. Uses the same center-vs-center swap logic as NoTube (`DraggableItem` + `ReorderLogic`).
- **Duplicate playlist** — Copies name (with “(copy)”) and full song order.
- **Playlist detail** — Two-line header: **back + title** on a **colored background** (playlist accent color) on line 1; **tools** on line 2 (+ add, play, remote, rename, duplicate, palette color, delete). Compact song rows: **Title (Key)** + notes, small **trash** to remove from the playlist. Tap the highlighted **Wi‑Fi** icon again to stop remote play (a pulsing green dot shows while it is active), **long-press** it for connection status and debug info, or use the system notification.
- **Playback mode** — Swipe horizontally through each song in the playlist (images and PDFs).
- **Settings** — **Gear** icon on the main tabs opens **Settings**: under **Remote play**, set one **5-digit code** used as the Cloudflare PIN and the LAN port. The screen notes IANA’s dynamic/private port band (49152–65535) if you want to avoid common services. Shows the **installed app version** and a **Check for updates** button (same GitHub Release flow as the launch snackbar).
- **Remote play** — Tap the **Wi‑Fi** icon and choose **Cloudflare tunnel (internet)** or **LAN only (same Wi‑Fi)**. Both start the same local HTTP server on the phone; Cloudflare adds a public `*.trycloudflare.com` URL (enter the code from Settings — no port in the link), while LAN serves `http://<phone-ip>:code/` on your Wi‑Fi with no code prompt. You can start remote from the **main tabs** (uses the last-opened playlist for playback when one exists, or starts in archive-only mode for HTTP API access) or from a **playlist detail** screen (that playlist). On start, a dialog shows the URL with a clickable link, **Open in browser**, and live **connection checks** (local server, tunnel reachability, cloudflared log). Tap **Refresh** or **Copy debug info** if the browser says the site is unreachable. **Long-press** the Wi‑Fi icon while remote is active to reopen status. Open the URL on another device (tablet, laptop) for a fullscreen browser view: after the PIN (Cloudflare only), the home page lists your playlists with **Play** and **Edit**; tap **Play** to open the slideshow for that playlist. Swipe or arrow keys advance songs/pages while the phone keeps serving the playlist. While active, a **foreground notification** (default priority) shows a generic “remote play active” message and a **Stop** action — it does **not** show the public URL or code. Tap the highlighted **Wi‑Fi** icon again to stop remote play. The Wi‑Fi icon is highlighted when active, gray when off, with a pulsing green dot beside it while remote play is running. In the browser, **pencil** opens a web editor to reorder, remove, or add songs from the archive (mirrors the in-app playlist screen). The HTTP API also exposes the full song archive and playlist list for scripting (see **HTTP API** below).
- **In-app updates** — On cold start, checks GitHub Releases for a newer signed APK; snackbar prompt, download progress banner, then system installer (requires **Install unknown apps** permission for this package).

### Quickstart playlist

Paste a block of text (one song title per line, e.g. a set list). The app fuzzy-matches lines against the archive. The review lists matched songs first, then any lines with no match at the end. **Create** builds a playlist from matched songs only (original order among hits). **Create with placeholders** keeps the full set-list order and adds a placeholder page for each unmatched line.

## Screens

Sketch of the main flows (not to scale):

```
┌─────────────────────────────────────┐
│ Stage Manager           🟢📶  ⚙ │  ← tap 📶 = stop remote; long-press = status/debug
├─────────────────────────────────────┤
│ [ Songs ]  [ Playlists ]            │
├─────────────────────────────────────┤
│                                     │
│  SONGS TAB                          │
│  [ A–Z ]  [ Recently added ]        │
│  [ Recently viewed ]                │
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
│ ← Sunday set                        │  ← line 1: back + title on accent-color background
├─────────────────────────────────────┤
│  +   ▶   📶   ✎   ⧉   🎨   🗑   │  ← 📶 gray when off; long-press when on = status
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
│ │ Sunday set            ✎  🎨  🗑 │ │  ← colorful block; tap to open
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ Rehearsal             ✎  🎨  🗑 │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

        ⚙ Settings
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Settings                          │
├─────────────────────────────────────┤
│ Remote play                         │
│ Code [_____]                        │
│              [ Save ]               │
│ App version      1.0.42             │
│      [ Check for updates ]          │
└─────────────────────────────────────┘

REMOTE PLAY ACTIVE (notification shade)
┌─────────────────────────────────────┐
│ Remote: Sunday set                  │
│ Active — tap Stop here or tap 📶  │
│ in the app to end; long-press 📶  │
│ for connection status / debug     │
│                        [ Stop ]     │
└─────────────────────────────────────┘
```

## Usage

1. **Import a song** — Gallery or browser → Share → Stage Manager → fill Title, Key, Notes → Save.
2. **Browse** — **Songs** tab lists the archive; tap to open fullscreen. Use **A–Z**, **Recently added**, or **Recently viewed** to sort.
3. **New playlist** — **Playlists** tab → **New playlist** → enter name (opens the new playlist). Or rename / recolor / delete from the colorful blocks on the list.
4. **Add songs** — Open a playlist → **+** → search → tap a result. If the song is missing, tap **Add placeholder page** (⚠) to add a title-only stand-in sheet.
5. **Reorder** — Long-press a row and drag (Songs, Playlists, or playlist detail).
6. **Play** — Open a playlist → **Play** → swipe between songs.
7. **Remote play** — Main tabs or playlist detail → **Wi‑Fi**. Pick Cloudflare (enter the 5-digit code) or LAN (code is the port in the URL). Main-tab start works without opening a playlist first (playback uses the last-opened playlist when available). Tap **Wi‑Fi** again to stop; **long-press** it while remote is active to reopen connection status and **Copy debug info**. **Stop** also works from the system notification (or when deleting the playlist).
8. **Settings** — Main tabs → **gear** → set the remote code → **Save**. **Check for updates** anytime from the same screen.
9. **Quickstart** — **Playlists** tab → **Quickstart playlist** → paste text → **Match songs** → **Create** (matched only) or **Create with placeholders** (full order).
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
│       │   ├── jniLibs/arm64-v8a/libcloudflared.so  # Bundled tunnel binary (gitignored; built by script)
│       │   └── remote/             # index.html, play.html, edit.html, pin.html
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
│           └── util/               # Share import, storage migration, AppUpdate, AppPrefs
└── gradle/wrapper/
```

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Remote play tunnel, in-app update check/download |
| `POST_NOTIFICATIONS` | Remote-play foreground notification (Android 13+) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Keep remote play alive while tunneled |
| `REQUEST_INSTALL_PACKAGES` | In-app update installs the downloaded APK |
| `MANAGE_EXTERNAL_STORAGE` (Android 11+) | Read/write songs, database, and settings under `Music/StageManager` |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and below) | Same storage path on older devices |

## Build

```bash
# Debug (local install)
./gradlew :app:assembleDebug

# Release (arm64-v8a — same as CI; remote play tunnel needs cloudflared in assets)
bash scripts/fetch-cloudflared.sh   # requires Go 1.22+, Android NDK (CGO), writes app/src/main/jniLibs/arm64-v8a/libcloudflared.so
./gradlew :app:assembleRelease
```

Requires Android SDK (API 34 platform + build-tools 34.0.0) and JDK 17. Set `sdk.dir` in `local.properties` or via `ANDROID_HOME`. Release builds that include remote play need Go 1.22+ and the Android NDK (`ndk;26.1.10909125` or newer) to run `fetch-cloudflared.sh` — the bundled `cloudflared` must be built with CGO or DNS fails on device. The binary is gitignored and CI builds it on every release workflow run.

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
2. Builds `cloudflared` for Android arm64 with CGO + NDK (`scripts/fetch-cloudflared.sh`, Go 1.22, `ndk;26.1.10909125`)
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

1. **Settings** — In **Settings** (gear on main tabs), under **Remote play**, set a **5-digit code** (range 10000–65535). The same number is the Cloudflare PIN and the LAN port.
2. **Start** — Open a playlist → tap **Wi‑Fi** (or the main-tab shortcut). Pick **Cloudflare tunnel** for internet (`https://….trycloudflare.com/` — no port) or **LAN only** for same-Wi‑Fi (`http://phone-ip:code/`). The phone starts the HTTP server and shows a dialog with the URL (clickable link + **Open in browser**). A **foreground notification** with **Stop** also appears; it does not include the URL or code.
3. **Connect** — Cloudflare: open the URL and enter the code. LAN: open the URL on the same Wi‑Fi — no code prompt.
4. **Browser UI** — Fullscreen sheet music / image for the current song and page. Title bar shows playlist name and `3/12: Song title · page 2/3`. **+** uploads a new file with **Title**, **Key**, and **Notes** pre-filled from the filename (same rules as share/import). **Pencil** opens `/edit` to reorder, remove, or add songs from the archive.
5. **Navigate** — Swipe left/right (or laptop arrow keys) for next/previous song; multi-page PDFs advance page before moving to the next song.
6. **Edit playlist** — On `/edit`, drag rows to reorder, tap **Remove**, or search the archive to add. **Done** returns to the stage view. Changes sync to the phone database immediately.
7. **Stop** — Tap the highlighted **Wi‑Fi** icon again, **Stop** on the system notification, or delete the active playlist.
8. **Status / debug** — While remote is active, **long-press** the **Wi‑Fi** icon (main tabs or playlist detail) to reopen connection checks, cloudflared log, and **Copy debug info** — useful if the browser says the URL is unreachable.

Requires **internet** on the phone for Cloudflare mode (Wi‑Fi or cellular). LAN mode needs both devices on the same network; the URL uses the phone’s Wi‑Fi IPv4 address. Cloudflare tunnel URLs change each session. On Android 13+, the app requests notification permission so the remote-play foreground notification can appear. CI bundles `cloudflared` via `scripts/fetch-cloudflared.sh` on every release build. Implementation: `PlayRemoteController.kt`, `CloudflareTunnel.kt`, `NetworkAddresses.kt`, `RemotePlayFlowDialog.kt`, `RemotePlayStartedDialog.kt`, `RemotePlayService.kt`, `RemotePlayNotification.kt`, `PlayRemoteServer.kt`, `SettingsScreen.kt`, `assets/remote/play.html`, `assets/remote/edit.html`, `assets/remote/pin.html`, `assets/remote/index.html`.

### HTTP API

While remote play is active, the phone serves JSON over HTTP. Cloudflare mode requires PIN auth first (`POST /api/auth` with `{"pin":"12345"}`); LAN mode has no PIN. All requests use `Content-Type: application/json` for POST bodies unless noted.

**Auth**

| Method | Path | Body | Response |
|--------|------|------|----------|
| `POST` | `/api/auth` | `{"pin":"12345"}` | `{"ok":true}` + `Set-Cookie: remote_auth=…` |

**Song archive**

| Method | Path | Body / query | Response |
|--------|------|------------|----------|
| `GET` | `/api/songs` | — | `{"songs":[{"id", "title", "key", "notes", "fileType", "isDeleted", "isPlaceholder"}, …]}` |
| `POST` | `/api/songs/update` | `{"songId", "title", "key", "notes"}` | Updated `{"songs":[…]}` |
| `GET` | `/api/songs/search` | `?q=…` | `{"songs":[{"id", "title", "key", "notes", "isPlaceholder"}, …]}` (archive search) |
| `GET` | `/api/parse-filename` | `?raw=…` | `{"title", "key", "notes"}` (filename parse hint) |

**Playlists (library)**

| Method | Path | Body | Response |
|--------|------|------|----------|
| `GET` | `/api/playlists` | — | `{"playlists":[{"id", "name", "color", "songCount"}, …]}` (`color` is ARGB int or `null`) |
| `POST` | `/api/playlists/create` | `{"name"}` | `{"id", "name"}` |
| `POST` | `/api/playlists/reorder` | `{"playlistIds":[…]}` | Updated `{"playlists":[…]}` |
| `POST` | `/api/playlists/{playlistId}/rename` | `{"name"}` | Updated `{"playlists":[…]}` |
| `POST` | `/api/playlists/{playlistId}/color` | `{"color"}` (`color`: ARGB int or `null` to clear) | Updated `{"playlists":[…]}` |
| `POST` | `/api/playlists/{playlistId}/delete` | — | Updated `{"playlists":[…]}` |

**Per-playlist (playback + editor)**

All routes below require the playlist id in the path. Playback position (`songIndex`, `pageIndex`) is tracked per playlist id on the server. Responses include `"playlistId"` and `"playlistName"`.

| Method | Path | Body / query | Response |
|--------|------|------------|----------|
| `GET` | `/api/playlists/{playlistId}/state` | — | Playback position + song list for `play.html` |
| `GET` | `/api/playlists/{playlistId}/entries` | — | Full entries for `edit.html` |
| `POST` | `/api/playlists/{playlistId}/navigate` | `{"direction":"next"\|"prev"}` | Updated state JSON |
| `GET` | `/api/playlists/{playlistId}/media` | `?song=&page=` | Image/PDF bytes for a page |
| `POST` | `/api/playlists/{playlistId}/reorder` | `{"entryIds":[…]}` | Updated entries JSON (song order **in** playlist) |
| `POST` | `/api/playlists/{playlistId}/remove` | `{"entryId"}` | Updated entries JSON |
| `POST` | `/api/playlists/{playlistId}/add` | `{"songId"}` | Updated entries JSON |
| `POST` | `/api/playlists/{playlistId}/add-placeholder` | `{"title", "key", "notes"}` | Updated entries JSON |
| `POST` | `/api/playlists/{playlistId}/upload` | `multipart/form-data` (`file`, `title`, `key`, `notes`, …) | Updated state JSON |

**HTML pages**

| Path | Purpose |
|------|---------|
| `/` | Playlist picker (`index.html`) — choose a playlist to play |
| `/play?playlist={id}` | Playback view (`play.html`) for that playlist |
| `/?playlist={id}` | Same as `/play?playlist={id}` (backward compatible) |
| `/edit?playlist={id}` | Playlist editor (`edit.html`) |
| `/` (unauthenticated, Cloudflare) | PIN gate (`pin.html`) |

When remote play starts from a playlist, the app appends `?playlist={id}` to the shared URL. API clients must pass the same id in every per-playlist path — there is no implicit “current playlist” on the server.

Errors return HTTP 400/401 with `{"error":"message"}`.

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
| **playlist-view-parity** | When changing playlist detail, playback, remote HTML, or `PlayRemoteServer` — keep local Compose and remote web views aligned |
| **update-readme** | After user-facing or structural changes — keeps this README accurate |
| **local-workspace** | When a path looks missing — search locally; do not rsync or run `update.sh` from this repo |

See each skill's `SKILL.md` for the exact command or checklist.

## Data model

- **Song** — title, key, notes, file path, type (IMAGE/PDF), mime type, sort order, last viewed at. Multiple songs can point at the same file with different metadata.
- **Playlist** — name, optional accent color, creation time.
- **PlaylistSong** — playlist + song + position (ordered).

Files and app state live on shared storage under **`Music/StageManager/`** (typically `/storage/emulated/0/Music/StageManager/`):

| Path | Contents |
|------|----------|
| `songs/` | PDF and image sheet music, named `{Title_With_Underscores}-{Key}-{songId}.{ext}` (e.g. `Amazing_Grace-G-42.pdf`) |
| `playlists.db` | Room database (songs, playlists, order) |
| `state.json` | Remote-play code and last-opened playlist |

On first launch after install or upgrade, the app requests **All files access** so it can use this folder. Existing data in app-internal storage is migrated automatically. After migration, uninstalling and reinstalling the app restores your library from `Music/StageManager`. Once per install, if `songs/` contains files not linked to any archive entry, the app lists them and asks whether to delete those orphans.

## Tech stack

- Kotlin, **Jetpack Compose**, Material 3
- Single-activity navigation (`NavHost`)
- Room + KSP
- Coroutines / Flow
- Coil (images in Compose)
- Platform `PdfRenderer` + Compose `HorizontalPager` (multi-page PDFs)
- NanoHTTPD (local remote-play server)
- Bundled `cloudflared` binary (Cloudflare Quick Tunnel)
