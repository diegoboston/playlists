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
- **Import from the menu** — On the main **Songs** / **Playlists** tabs, the **☰** menu offers **Import from camera** (system camera), **Import from gallery** (Android photo picker), and **Import from storage** (images or PDFs via the document picker, same metadata flow as share). Camera and gallery: if an OpenAI key is configured, the first page is OCR’d for a title (empty if there is no key or OCR fails). On the import panel, **Add page** takes another photo; two or more pages are saved as one multi-page PDF. If **Adjust page after camera** is on in Settings (off by default), each camera photo opens **Adjust page**: drag four corners to mark the paper, then **Use page** (perspective-warp and crop, no AI redraw) or **Use original**.
- **Metadata on import** — Each import prompts for **Title**, **Key**, and **Notes**, pre-filled from the filename (underscores and dashes → spaces, extension dropped, trailing key → Key, trailing instrument → Notes). Scan-from-camera skips filename hints so the title comes from OCR or stays blank.
- **Duplicate entries** — The same file can be imported multiple times with different Key/Notes (separate archive rows).
- **Song list** — Compact rows: **Title (Key)** on the first line, notes preview on the second. **Search** filters the archive by title, key, or notes. Placeholder songs (no real sheet yet) show a 🚧 after the title. **Sort:** **A-Z**, **Added**, **Viewed** outlined buttons (same style as **New playlist**) — tap to sort the archive (persists order); tap the same button again to reverse. Opening a song in the viewer or playlist playback records its last-viewed time. **Pencil** opens edit (title, key, notes) with **Annotate**, plus **Delete** and confirmation. **AI lyrics** songs omit the key field and offer **Reformat** (font size) instead of **Change key**. If the song is used in playlists, the dialog lists those playlist names; confirming removes the archive entry, drops it from those playlists, and deletes its file (unless another archive row shares the same path).
- **Annotate** — From the pencil dialog on the song list or song viewer. Images and placeholders are converted to a title-based PDF first (same archive row, original image deleted if nothing else uses it). Then **Xodo** opens the song PDF in its own task when installed (system Back returns here); otherwise a dialog offers **Install Xodo** or **Use another app**. Xodo writes markup into that file — no second import. **↓ Share** remains a copy-out. Remote play does not offer Annotate.
- **Song viewer** — Tap a song for fullscreen view: images via Coil, or swipe left/right through multi-page PDFs. Embedded PDF annotations (for example Xodo ink and highlights) are drawn via PDFBox so they show on Android 12 devices such as the Nord N20; unmarked pages still use platform `PdfRenderer`. Pinch to zoom on images and PDF pages. **↓** in the top bar opens the system share sheet to save or send the original image or PDF, unless **Hide song share button** is on in Settings. Pencil **Annotate** is the in-place edit path (see above).

### Playlists

- **Create / rename** — New playlists get an editable name. The **Playlists** tab shows each playlist as a **colorful block** (NoTube-style folder colors) with a **menu** (☰) for rename (pencil icon), color, delete, duplicate, and export PDF.
- **Ordered sequences** — A playlist is an ordered list of songs from the archive.
- **Add songs** — Search dialog with full-text match across title, key, and notes. If a title is not in the archive, tap **Add placeholder page** to create a synthetic sheet with just the title (stored in the archive with a 🚧 marker).
- **Drag reorder** — Long-press and drag rows in the Songs tab, Playlists tab, or playlist detail screen. Uses the same center-vs-center swap logic as NoTube (`DraggableItem` + `ReorderLogic`).
- **Duplicate playlist** — Copies name (with “(copy)”) and full song order.
- **Export PDF** — **Pencil** menu on playlist detail or the Playlists tab builds one combined PDF: a set-list table of contents (playlist name + song titles with keys, no page numbers) followed by every chart page in playlist order. PDF charts are merged as **vector pages** (original page size preserved); photos and placeholders are embedded on letter-size pages at full resolution. If a PDF cannot be merged, that page falls back to raster. Opens the system share sheet to save or send the file.
- **Push playlist to server** — Once stable redirect keys are validated in Settings, the playlist menu offers **Push playlist to server** (arrow up) even when remote play is off: builds the same combined PDF and uploads it as **last.pdf** on the stable Worker (same flow as the auto-upload when starting stable remote play). Shows the “Uploading playlist PDF…” progress dialog.
- **Playlist detail** — Two-line header: **back + title** on a **colored background** (playlist accent color) on line 1; **tools** on line 2 (+ add, **bolt** find chart, play, remote, **menu** ☰). The menu offers rename (pencil icon), **playlist color** (palette icon), delete, duplicate, export PDF, and (when stable redirect is ready) push playlist to server. Compact song rows: **Title (Key)** + notes, small **trash** to remove from the playlist. If that song is not in any other playlist, a dialog offers to delete it from the song archive too (or keep it). Tap the **green Wi‑Fi** icon while remote is active (pulsing green dot) to reopen connection status and the URL; use the system notification **Stop** to end remote play.
- **AI find chart** — Voice or typed search for chords/lyrics on the web. After search, the app parses the first web result and shows a PDF preview with **Is this correct?** — **Yes** saves, **Try next** parses the following result, **Cancel** leaves the flow. Pages that cannot be parsed are skipped automatically. Lyrics-only previews allow font changes without key or transposition controls. Preview and **Reformat** font size can go from 9 to 20 (auto-fit still starts at 14). From the main **Songs** / **Playlists** tabs (**☰** → **AI song search**, shown when an OpenAI key is configured) the chart is added to the **song archive**; from a **playlist** tool row (**bolt**) it is also appended to that playlist. See **AI chart assistant** below.
- **Playback mode** — Swipe horizontally through each song in the playlist (images and PDFs). **↓** shares the current song file unless **Hide song share button** is on in Settings; **↻** in the top bar jumps to the first song and page.
- **Settings** — **☰** on the main tabs → **Settings**. Changes save immediately (no Save button). Compact rows: **PIN for remote play, 5 digits** with the code box on the same line (hint below: also the LAN port; IANA dynamic range 49152–65535), then **Show UI to adjust page after camera** and **Hide song share button** switches, then **App icon** with the **Original** and **Guido** previews on the same line. **Advanced** (stable Worker URL + OpenAI API key) sits just above the **status card** (version, **Check for updates**, library storage). The 5-digit code is the Cloudflare PIN and LAN port. Optional **stable play URL** fields (Workers account subdomain + write secret) build `https://play.<subdomain>.workers.dev` (see `workers/tunnel-redirect/`). **Show UI to adjust page after camera** (off by default) shows the corner-drag crop screen after each camera photo. **Hide song share button** (off by default) removes the download/share icon from song view and playlist playback. Under Advanced, paste your **OpenAI API key** (never stored in git); a **green check** confirms the key works. Tap **OpenAI billing overview** to open your account balance on platform.openai.com. Pick **Original** (orchestra conductor) or **Guido** to change the launcher icon (your home screen may take a moment to refresh).

  | Original | Guido |
  |:---:|:---:|
  | <img src="app/src/main/res/drawable-nodpi/ic_launcher_foreground.png" width="96" alt="Original launcher icon — orchestra conductor"> | <img src="app/src/main/res/drawable-nodpi/ic_launcher_alt_foreground.png" width="96" alt="Guido launcher icon"> |

- **Remote play** — On the main tabs, **☰** → **Web server** (Wi‑Fi icon; green with a pulse on the menu while remote is active). On a playlist, tap the **Wi‑Fi** icon. Choose **Stable play URL**, **Cloudflare tunnel (internet)**, or **LAN only (same Wi‑Fi)**. **Stable** starts Cloudflare, registers the live tunnel with your Worker, uploads a combined **playlist PDF** to Cloudflare R2 for offline fallback, and bookmarks `https://play.<you>.workers.dev` (requires Settings + R2 bucket — see `workers/tunnel-redirect/`). If the phone goes offline later, that stable URL shows a PIN gate and serves the last uploaded PDF (same PIN as live remote play). **Cloudflare** uses a session `*.trycloudflare.com` URL. **LAN** serves `http://<phone-ip>:code/` on your Wi‑Fi. While remote play is active, the start/status dialog lists **all available URLs** (stable bookmark, Cloudflare session link, and each LAN address — e.g. Wi‑Fi and hotspot when both are up). Each row has a clickable link and **▼** QR chevron. Cloudflare/stable modes still use the **PIN** from Settings. You can start remote from the **main tabs** (uses the last-opened playlist for playback when one exists, or starts in archive-only mode for HTTP API access) or from a **playlist detail** screen (that playlist). **STOP 🛑** ends remote play (**OK** dismisses the dialog without stopping). If Cloudflare setup hits a problem, the dialog adds **connection checks** (local server, tunnel reachability, cloudflared log). Open **Web server** again while remote is active (or tap **Wi‑Fi** on playlist detail) to reopen status. Open the URL on another device (tablet, laptop) for a fullscreen browser view: after the PIN (Cloudflare/stable), the home page lists your playlists with **Play** and **Edit**; tap **Play** to open the slideshow for that playlist. While active, a **foreground notification** shows **Stop** only. The **Web server** / **Wi‑Fi** icon is **green** when remote play is active (with a pulsing green dot on the main ☰), gray when off. In the browser, **pencil** opens a web editor to reorder, remove, or add songs from the archive (mirrors the in-app playlist screen). The HTTP API also exposes the full song archive and playlist list for scripting (see **HTTP API** below).
- **In-app updates** — On cold start, checks GitHub Releases for a newer signed APK; snackbar prompt, download progress banner, then system installer (requires **Install unknown apps** permission for this package).

### Quickstart playlist

Paste a block of text (one song title per line, e.g. a set list). The app fuzzy-matches lines against the archive. The review lists matched songs first, then any lines with no match at the end. **Create** builds a playlist from matched songs only (original order among hits). **Create with placeholders** keeps the full set-list order and adds a placeholder page for each unmatched line.

## Screens

Sketch of the main flows (not to scale):

```
┌─────────────────────────────────────┐
│ Stage Manager                    ☰ │  ← menu: camera, gallery, storage, AI, web server, piano, settings
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
│     Annotate · (AI lyrics: Reformat)│
│     (delete with confirmation)      │
│                                     │
└─────────────────────────────────────┘

        share from another app, or ☰ → camera / gallery / storage
                 │
                 ▼  (camera + Adjust page on in Settings)
┌─────────────────────────────────────┐
│ ← Adjust page                       │
│     •─────────────────•             │
│    /   chart photo     \            │
│   •─────────────────────•           │
│ [ Use original ]  [ Use page ]      │
└─────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│ Import song                         │
├─────────────────────────────────────┤
│ Title  [________________]           │
│ Key    [________________]           │
│ Notes  [________________]         │
│  1 page                             │  ← camera import: Add page for extra sheets
│         [ Add page ]                │
│              [ Save ]               │
└─────────────────────────────────────┘

        tap playlist block
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Sunday set                        │  ← line 1: back + title on accent-color background
├─────────────────────────────────────┤
│  +   ⚡   ▶   📶   ✎               │  ← ⚡ find chart; ✎ = rename, color, delete, dup, export
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
│ │ Sunday set                     ✎ │ │  ← colorful block; tap to open; ✎ = actions menu
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ Rehearsal                      ✎ │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

        ☰ → Settings
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Settings                          │
├─────────────────────────────────────┤
│ PIN for remote play, 5 digits. [___]│
│ (also LAN port; 49152–65535)        │
│ Show UI to adjust page after cam [ ]│
│ Hide song share button           [ ]│
│ App icon  [Original] [Guido]        │
│ Advanced                         ▾ │
│ ┌─────────────────────────────────┐ │
│ │ App version              1.0.42 │ │
│ │ Updates    [ Check for updates ]│ │
│ │ Storage                    42 MB│ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

REMOTE PLAY ACTIVE (notification shade)
┌─────────────────────────────────────┐
│ Remote: Sunday set                  │
│ Active — tap Stop here or use  │
│ STOP in the start dialog; tap 📶 │
│ in the app for status / debug    │
│                        [ Stop ]     │
└─────────────────────────────────────┘

        playlist detail → ⚡
                 │
                 ▼
┌─────────────────────────────────────┐
│ ← Find chart                        │
├─────────────────────────────────────┤
│  Hold mic — say e.g.                │
│  “Amazing Grace by John Newton”     │
│              [ 🎤 ]                 │
│  [SONG TITLE by ARTIST        ]     │
│  (Chords+lyrics) (Lyrics only)      │
│                         [ Search ]  │
├─────────────────────────────────────┤
│  Is this correct?                   │
│  (PDF preview)                      │
│  [ Yes ]  [ Try next ]  [ Cancel ]  │
└─────────────────────────────────────┘
```

## Usage

1. **Import a song** — **☰** → **Import from camera**, **Import from gallery**, or **Import from storage** (image/PDF). Camera and gallery OCR-fill the title when an OpenAI key is set. From camera, **Add page** captures extra sheets into one PDF. Turn on **Adjust page after camera** in Settings to drag page corners after each photo. Or share from another app. Then fill Title, Key, Notes → Save.
2. **Browse** — **Songs** tab lists the archive; tap to open fullscreen. Use **Sort: A-Z / Added / Viewed** — tap again on the same button to reverse order.
3. **New playlist** — **Playlists** tab → **New playlist** → enter name (opens the new playlist). Or rename / recolor / delete / duplicate / export from the **menu** (☰) on each colorful block.
4. **Add songs** — Open a playlist → **+** → search → tap a result. If the song is missing, tap **Add placeholder page** (🚧) to add a title-only stand-in sheet.
5. **Find chart** — Main tabs **☰** → **AI song search** (when an OpenAI key is configured) to add to the archive, or open a playlist → **bolt** to add there. Hold mic or type `Title by Artist`, pick chords+lyrics or lyrics only → the first web result is parsed → **Yes** / **Try next** / **Cancel**. See **AI chart assistant** below.
6. **Reorder** — Long-press a row and drag (Songs, Playlists, or playlist detail).
7. **Play** — Open a playlist → **Play** → swipe between songs.
8. **Remote play** — Main tabs **☰** → **Web server**, or playlist detail → **Wi‑Fi**. Pick Cloudflare (enter the 5-digit code) or LAN (code is the port in the URL). Main-tab start works without opening a playlist first (shared URL deep-links to the last-opened playlist when available). Open **Web server** / **Wi‑Fi** while remote is active to reopen connection status and **Copy debug info**. **Stop** works from the start dialog **STOP 🛑**, the system notification, or when deleting the playlist that was used to start remote.
9. **Settings** — Main tabs **☰** → **Settings**. PIN, switches, app icon, and Advanced fields save as you change them. **Check for updates** anytime from the same screen.
10. **Quickstart** — **Playlists** tab → **Quickstart playlist** → paste text → **Match songs** → **Create** (matched only) or **Create with placeholders** (full order).
11. **Update** — If a newer GitHub Release exists, a snackbar offers **Update now**; allow installs from this app when prompted.

## Project layout

```
playlists/
├── .cursor/skills/                 # Cursor agent skills (compile, README sync)
├── .github/workflows/android.yml   # CI: test → cloudflared → release → GitHub Release
├── .githooks/pre-push              # Off by default; RUN_COMPILE_HOOK=1 git push
├── scripts/
│   ├── fetch-cloudflared.sh        # Build cloudflared for Android arm64 (CI + local release)
│   ├── install-git-hooks.sh        # Copy .githooks into .git/hooks
│   └── ai-song-search.sh           # Local CLI: same AI web-search + extract path as the app
├── tools/
│   └── ai-song-search/             # JVM CLI; compiles shared search/extract Kotlin from app/
├── update.sh                       # Interactive rsync sync, commit, push
├── gradlew                         # Gradle wrapper (committed)
├── app/
│   ├── build.gradle.kts
│   ├── keystore/playlists.keystore # Shared sideload signing key (committed)
│   └── src/main/
│       ├── assets/
│       │   ├── jniLibs/arm64-v8a/libcloudflared.so  # Bundled tunnel binary (gitignored; built by script)
│       │   └── remote/             # index.html, play.html, edit.html, pin.html, compat.js, song-display.js
│       └── java/com/playlists/app/
│           ├── data/               # Room: Song, Playlist, PlaylistSong
│           ├── ai/                 # OpenAI client, AiPrompts, chart intent/draft, playlist name resolve
│           ├── find/               # Web search + page fetch for chord sites
│           ├── render/             # Chart renderer + vector playlist PDF export
│           ├── remote/             # HTTP server, tunnel, foreground service, notification
│           ├── ui/
│           │   ├── MainActivity.kt # Single Compose entry + share intents
│           │   ├── PlaylistsViewModel.kt
│           │   ├── ChartAssistantViewModel.kt
│           │   ├── navigation/     # NavHost routes
│           │   ├── screens/        # Compose screens (incl. Settings, ChartAssistant)
│           │   ├── components/     # Media viewer, dialogs, update banner
│           │   ├── reorder/        # DraggableItem + list drag handler
│           │   └── theme/          # Material 3 theme
│           └── util/               # Share import, storage paths, AppUpdate, AppPrefs, AI credentials
└── gradle/wrapper/
```

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Remote play tunnel, in-app update check/download, AI chart search, scan-image OCR, and OpenAI API |
| `RECORD_AUDIO` | Voice commands for AI find chart (main **☰** → **AI song search**, or playlist detail **bolt** → hold mic) |
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

After app changes, local verify is the **rebuild-app** skill (compile, unit tests, debug APK). **compile-kotlin** is the faster compile + unit-test subset when you do not need an APK:

```bash
bash .cursor/skills/rebuild-app/scripts/rebuild-app.sh
bash .cursor/skills/compile-kotlin/scripts/compile-kotlin.sh
```

`git push` does not compile unless you opt in: `RUN_COMPILE_HOOK=1 git push` (requires `bash scripts/install-git-hooks.sh` once per clone). CI always compiles from a clean checkout.

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

1. Compiles and runs unit tests (clean checkout — no local Gradle incremental cache)
2. Builds `cloudflared` for Android arm64 with CGO + NDK (`scripts/fetch-cloudflared.sh`, Go 1.22, `ndk;26.1.10909125`)
3. Builds an arm64-v8a release APK
4. Publishes a GitHub Release tagged `v1.0.<run>` with:
   - `app-1.0.<run>.apk` (versioned)
   - `app.apk` (stable alias for in-app updates)

Stable download URL: `https://github.com/diegoboston/playlists/releases/latest/download/app.apk`

Each CI run sets `versionCode = GITHUB_RUN_NUMBER` and `versionName = 1.0.<run>`. No GitHub Secrets are needed for signing — the keystore is in the repo. CI publishes APKs straight to GitHub Releases in the same job (no Actions artifacts), so storage quota is not used.

## In-app updates

On cold start the app checks GitHub Releases for a newer build:

1. **Check** — `GET https://api.github.com/repos/diegoboston/playlists/releases/latest`, read `tag_name` (e.g. `v1.0.42`), parse `versionCode` `42`, compare to the installed app.
2. **Prompt** — If remote is newer, a snackbar offers to update (or use **Check for updates** in **Settings**).
3. **Download** — Progress banner; fetches `app.apk` from the release.
4. **Install** — Opens the system package installer via `FileProvider`. Android may prompt to allow **Install unknown apps** for Stage Manager first.

Implementation: `AppUpdate.kt`, `PlaylistsViewModel.kt`, `MainActivity.kt`, `AppUpdateBanner`. Change `AppUpdate.REPO` if the GitHub repo slug differs.

## AI chart assistant

Find chords and lyrics on the web by **voice or typed title**, turn them into a one-page PDF chart, and add the result to the playlist you have open. Personal demo feature — uses **your** OpenAI account; the API key is stored **encrypted on the device** and is never committed to git.

### Setup

1. **Settings** (main tabs **☰** → **Settings**) → expand **Advanced** and paste an [OpenAI API key](https://platform.openai.com/api-keys). A **green check** appears when the key passes a quick API test; **red** if it fails (error text below the field). The key is stored encrypted on the device as you type. Tap **OpenAI billing overview** to check your balance on the OpenAI site.
2. Grant **microphone** permission when the app asks (first voice use of find chart).

### Flow

1. Tap **☰** → **AI song search** on the main tabs (Songs or Playlists) to save into the **song archive**, or open a **playlist** and tap **bolt** on the tool row (between **+** and **Play**) to also append to that playlist.
2. Choose **Chords + lyrics** or **Lyrics only** (applies to both voice and typed search). Then either:
   - **Hold the mic** and speak a song (e.g. “Amazing Grace by John Newton”), or
   - Type `SONG TITLE by ARTIST` (artist optional) and tap **Search**.
3. After voice, the app shows **Heard** while it searches. Typed search skips that and searches immediately.
4. **Web search** — always builds `{title} {artist} chords lyrics` for chord searches, or `{title} {artist} lyrics -chords -tabs` for lyrics-only searches. The suffix is added by the app. The **first** result is fetched and parsed automatically (no result list to tap).
5. **Extract** — fetches the page (preferring a lyric/chart block when the HTML has one, and including the search snippet) and asks OpenAI for a **line-oriented chart** (`TITLE:` / `ARTIST:` / `[Verse 1]` …), not JSON. If the reply is cut off, complete lines already returned are kept. Lyrics-only results strip chords, capo, and key. If a page cannot be parsed, that result is skipped and the next hit is tried.
6. **Is this correct?** — PDF preview (transpose and font as before; font size 9–20, default still auto-fit from 14). **Yes** saves to the archive (and the playlist when opened from a playlist). **Try next** parses the following search hit. **Cancel** (or Back from the preview) leaves Find chart.
7. **Preview** — same PDF viewer as songs. Saving still writes the chart file as before.

Voice handles the **command** only when you use the mic. You still confirm with **Yes** before anything is saved.

### What gets stored

- New **song** row: title, **key** (target key you asked for; omitted for lyrics-only), notes like `AI chart · {source URL}` or `AI lyrics · {source URL}` for lyrics-only results. Pencil edit on an **AI lyrics** song hides the key field and uses **Reformat** (font size 9–20, same as AI chart preview) instead of **Change key**.
- PDF file under `Music/StageManager/songs/` (same as share import).
- **Playlist** link at the end of the current playlist order.

### Limits (current version)

- **OpenAI only** (Whisper + `gpt-4o-mini` for intent, extract, and scan-image OCR). Extract asks for a line-oriented chart so a truncated reply can still keep complete verses. The page fetch prefers a lyric block when the HTML has one (e.g. lyrics.com `pre#lyric-body-text`).
- **DuckDuckGo** HTML search — result quality varies; unreadable pages are skipped and **Try next** walks the remaining hits.
- No refine pass yet (“two columns”, “drop chorus”), no “add after song X”, no delete/add-existing voice commands.
- Not exposed on remote web or HTTP API.

### Local search simulator

`scripts/ai-song-search.sh` runs the same typed-search path on your laptop: `ChartIntent.fromTypedQuery`, DuckDuckGo, parse the **first** hit via `ChartAssistantService.fetchAndExtractChart` (line-oriented OpenAI extract), then **Is this correct?** — `yes` / `next` / `cancel`. It does not save a PDF or write to the song archive.

Requires JDK 17 (same `JAVA_HOME` / `~/tmp/android-build/env.sh` as other Gradle tasks). Parsing needs `OPENAI_API_KEY` in the environment — the phone key in Settings is not readable from the desktop.

```bash
export OPENAI_API_KEY=sk-...
bash scripts/ai-song-search.sh
bash scripts/ai-song-search.sh --lyrics Volare
bash scripts/ai-song-search.sh --lyrics --yes "X Colpa di chi"
bash scripts/ai-song-search.sh --help
```

With no arguments the script prompts for the song text (same as the app field) and **Chords + lyrics** / **Lyrics only**. `--yes` accepts the first chart that parses (also the default when there is no TTY). **Try next** parses the following hit. Failed pages are skipped, matching the app.

Planning doc: `report/ai-chord-chart-integration.md`.

Implementation: `ChartAssistantScreen.kt`, `ChartAssistantViewModel.kt`, `OpenAiClient` / `ChartAssistantService.kt`, `ChartTextParser.kt`, `AiPrompts.kt`, `WebSearchService.kt`, `PageFetcher.kt`, `ChordTransposer.kt`, `ChartPdfRenderer.kt`, `AiCredentialStore.kt`, `AudioRecorder.kt`, `SettingsScreen.kt`, `LocalFileImport.kt`, `ImportImagePrep.kt`, `ImagePagesPdf.kt`, `AdjustPageScreen.kt`, `PageWarper.kt`, `PageDetector.kt`, `scripts/ai-song-search.sh`, `tools/ai-song-search/`.

## Remote play

Control playback from a **second screen** over the internet (e.g. iPad on a music stand while the phone sits on a stand).

1. **Settings** — Set the **5-digit code** (Cloudflare PIN + LAN port). Optionally set **Workers account subdomain** (your Cloudflare account — `https://play.<subdomain>.workers.dev`) and **write secret** after deploying `workers/tunnel-redirect/`.
2. **Start** — Main tabs **☰** → **Web server**, or tap **Wi‑Fi** on a playlist. Pick **Stable play URL** (registers tunnel with Worker and uploads the playlist PDF for offline access), **Cloudflare tunnel**, or **LAN only**. Stable mode shows **Uploading playlist PDF…** while the combined export is pushed to your Worker. The dialog lists every URL that applies: stable bookmark, session Cloudflare link, and each LAN IP (Wi‑Fi / hotspot when present).
3. **Connect** — Cloudflare/stable: open a URL and enter the PIN. LAN: open a LAN URL on the same network — no PIN. If the phone is offline but stable mode was used earlier, the stable bookmark still works: enter the PIN to view or download the last uploaded playlist PDF (`/pdf?download=1`).
4. **Browser UI** — Fullscreen sheet music / image for the current song and page. Title bar shows playlist name and `3/12: Song title · page 2/3`. **2-up** toggles a two-page spread; the right page is the next page in the playlist (even across songs), and navigation still advances one page at a time. **+** uploads a new file with **Title**, **Key**, and **Notes** pre-filled from the filename (same rules as share/import). **↻** (start over) jumps to the first song and page. **↓** downloads the current song’s original image or PDF file. **Pencil** opens `/edit` to reorder, remove, or add songs from the archive. The playlist picker at `/` includes **Quickstart playlist** (paste → match → create, same flow as the app) and a link to the **song archive** at `/songs`.
5. **Navigate** — Swipe left/right (or laptop arrow keys) for next/previous song; multi-page PDFs advance page before moving to the next song.
6. **Edit playlist** — On `/edit`, drag rows to reorder, tap **Remove**, or search the archive to add (results appear only after you type a query). After **Remove**, if the song is not in any other playlist, a confirm asks whether to delete it from the archive too. **Upload** adds a new file to the playlist; **Play** opens the stage view; **Done** returns to the stage view as well. Changes sync to the phone database immediately.
7. **Stop** — **STOP 🛑** in the start dialog, **Stop** on the system notification, or delete the playlist that was used to start remote.
8. **Status / debug** — While remote is active, **☰** → **Web server** on the main tabs (or tap **Wi‑Fi** on playlist detail) to reopen the URL (with the same **▼** QR chevron), **STOP 🛑**, and connection checks when something looks wrong. **Copy debug info** is available from that panel — useful if the browser says the URL is unreachable.

Requires **internet** on the phone for Cloudflare/stable modes. LAN mode needs both devices on the same network. Ephemeral Cloudflare URLs change each session; the stable Worker URL stays fixed once configured. Remote web views use ES5 JavaScript (`compat.js` + `XMLHttpRequest`) so playback works on old tablet browsers (e.g. Android 4.x WebKit). On Android 13+, the app requests notification permission so the remote-play foreground notification can appear. CI bundles `cloudflared` via `scripts/fetch-cloudflared.sh` on every release build. Implementation: `PlayRemoteController.kt`, `TunnelRedirectClient.kt`, `CloudflareTunnel.kt`, `NetworkAddresses.kt`, `RemotePlayUrls.kt`, `RemotePlayFlowDialog.kt`, `RemotePlayStartedDialog.kt`, `RemotePlayService.kt`, `RemotePlayNotification.kt`, `PlayRemoteServer.kt`, `SettingsScreen.kt`, `workers/tunnel-redirect/`, `assets/remote/…`.

### HTTP API

While remote play is active, the phone serves JSON over HTTP. Cloudflare mode requires PIN auth first (`POST /api/auth` with `{"pin":"12345"}`); LAN mode has no PIN. All requests use `Content-Type: application/json` for POST bodies unless noted.

**Auth**

| Method | Path | Body | Response |
|--------|------|------|----------|
| `POST` | `/api/auth` | `{"pin":"12345"}` | `{"ok":true}` + `Set-Cookie: remote_auth=…` |

**Song archive**

| Method | Path | Body / query | Response |
|--------|------|------------|----------|
| `GET` | `/api/songs` | — | `{"sort":{"criterion","reversed"}, "songs":[{"id", "title", "key", "notes", "fileType"}, …]}` |
| `POST` | `/api/songs/sort` | `{"criterion":"alpha"\|"added"\|"viewed"}` | Updated songs JSON (tap same criterion again to reverse) |
| `POST` | `/api/songs/upload` | `multipart/form-data` (`file`, `title`, `key`, `notes`, …) | Updated `{"songs":[…]}` (catalog only) |
| `POST` | `/api/songs/update` | `{"songId", "title", "key", "notes"}` | Updated `{"songs":[…]}` |
| `POST` | `/api/songs/delete` | `{"songId"}` | `{"ok":true}` |
| `GET` | `/api/songs/search` | `?q=…` | `{"songs":[{"id", "title", "key", "notes"}, …]}` (archive search) |
| `GET` | `/api/parse-filename` | `?raw=…` | `{"title", "key", "notes"}` (filename parse hint) |

**Playlists (library)**

| Method | Path | Body | Response |
|--------|------|------|----------|
| `GET` | `/api/playlists` | — | `{"playlists":[{"id", "name", "color", "songCount"}, …]}` (`color` is ARGB int or `null`) |
| `POST` | `/api/playlists/create` | `{"name"}` | `{"id", "name"}` |
| `POST` | `/api/playlists/quickstart/match` | `{"text"}` | `{"results":[{"line", "songId", "title", "key"}, …]}` (`songId`/`title`/`key` are `null` when no match) |
| `POST` | `/api/playlists/quickstart/create` | `{"name", "text", "withPlaceholders":true\|false}` | `{"id", "name"}` (creates playlist and fills from matched lines; placeholders when `withPlaceholders` is true) |
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
| `POST` | `/api/playlists/{playlistId}/navigate` | `{"direction":"next"\|"prev"\|"reset"}` | Updated state JSON |
| `GET` | `/api/playlists/{playlistId}/media` | `?song=&page=` | Image/PDF bytes for a page |
| `GET` | `/api/playlists/{playlistId}/download` | `?song=` | Original song file (`Content-Disposition: attachment`) |
| `POST` | `/api/playlists/{playlistId}/reorder` | `{"entryIds":[…]}` | Updated entries JSON (song order **in** playlist) |
| `POST` | `/api/playlists/{playlistId}/remove` | `{"entryId"}` | Updated entries JSON; may include `orphanedSong` `{id, title}` if unused elsewhere |
| `POST` | `/api/playlists/{playlistId}/add` | `{"songId"}` | Updated entries JSON |
| `POST` | `/api/playlists/{playlistId}/add-placeholder` | `{"title", "key", "notes"}` | Updated entries JSON |
| `POST` | `/api/playlists/{playlistId}/upload` | `multipart/form-data` (`file`, `title`, `key`, `notes`, …) | Updated state JSON |

**HTML pages**

| Path | Purpose |
|------|---------|
| `/` | Playlist picker (`index.html`) — **Quickstart playlist**, link to song archive, choose a playlist to play |
| `/play?playlist={id}` | Playback view (`play.html`) for that playlist |
| `/?playlist={id}` | Same as `/play?playlist={id}` (backward compatible) |
| `/edit?playlist={id}` | Playlist editor (`edit.html`) |
| `/songs` | Song archive browser (`songs.html`) — search, sort, catalog upload |
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
| **ai-prompts** | When adding or editing OpenAI prompts — put named constants in `AiPrompts.kt` and import them from there |
| **playlist-view-parity** | When changing playlist detail, playback, remote HTML, or `PlayRemoteServer` — keep local Compose and remote web views aligned |
| **remote-play-back-compat** | When changing remote play web assets — try to maintain back compat with old devices (Nexus 10, Android 4.3); play fullscreen is the main target |
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
| `songs/` | PDF and image sheet music (paths in the DB are stored as `Music/StageManager/songs/{filename}`) |
| `playlists.db` | Room database (songs, playlists, order) |
| `playlists.db-wal` / `-shm` | SQLite write-ahead log (unmerged writes while the app is running) |
| `state.json` | Remote-play code and last-opened playlist |

On first launch the app shows a **storage access** screen and does not open the library until **All files access** is granted for `Music/StageManager/`. After that grant, startup runs a SQLite WAL checkpoint so committed data is merged into `playlists.db` (a copied folder is then more likely to restore on another device). Uninstalling and reinstalling the app restores your library from that folder as long as it is intact. Song paths in the database are stored as `Music/StageManager/songs/{filename}`. Copy the folder after opening the app once; include `-wal` and `-shm` if those files are still present and non-empty.

## Tech stack

- Kotlin, **Jetpack Compose**, Material 3
- Single-activity navigation (`NavHost`)
- Room + KSP
- Coroutines / Flow
- Coil (images in Compose)
- Platform `PdfRenderer` + Compose `HorizontalPager` (multi-page PDFs); PDFBox when a page has annotations
- PDFBox Android (`pdfbox-android`) for vector playlist PDF export and annotated page rasters
- NanoHTTPD (local remote-play server)
- Bundled `cloudflared` binary (Cloudflare Quick Tunnel)
