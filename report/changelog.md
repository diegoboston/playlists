# Changelog

**Stage Manager (playlists)** — commit-by-commit release log (newest first).

---

## 1.0.46 · `0c69200` — retranspose/mic UI

**Released:** 2026-06-25 (8:51 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/0c6920090cdf70dc0fede70687460f55504953e4)

- **New key** on AI charts: edit dialog (Songs list or song view) opens transpose/PDF preview to pick a key and save; source chords/lyrics stored as `.chart.json` beside the PDF.
- Find chart no longer asks for a key by voice — search is title/artist only; transpose with +/− in preview before saving.
- Transposer uses conventional flat/sharp spelling (e.g. Bb in F, not A#).
- Settings: PIN + OpenAI key + **Save**, then app version and **Check for updates** (49152–65535 PIN range).
- Mic UX: “Listening… release to send”.

---

## 1.0.45 · `28a1bd7` — open AI integration

**Released:** 2026-06-25 (8:06 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/28a1bd7508e168a8d528b4c7e7394d18193c6f42)

- **Find chart** on playlist detail: hold the mic, voice-parse the request, search the web, preview a one-page chord PDF, transpose, and add to the playlist.
- Settings stores your **OpenAI API key** on-device (encrypted) with live validation before save.
- OpenAI pipeline: Whisper transcription, intent JSON, chart draft generation, plus chord transposition and PDF rendering.

---

## 1.0.44 · `3859c2d` — Song search/DB cleanup/song page

**Released:** 2026-06-24 (6:28 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/3859c2dc5f61a7e21a95916272bd4fb0db03b87c)

- Song search on the Songs tab and a new remote `/songs` archive page (search, sort, catalog upload).
- DB v9 drops `isPlaceholder` and `mimeType`; placeholders become 🚧 in the title plus `"placeholder"` in notes.
- Shared `upload.js` overlay for remote play/edit/songs; playback ↻ resets to song 1 locally and in the browser.
- New API routes: `POST /api/songs/sort`, `POST /api/songs/upload`, navigate `reset`; `isPlaceholder` removed from JSON.

---

## 1.0.43 · `e34c8dc` — fix upload

**Released:** 2026-06-24 (12:54 AM EDT)

- Fixed remote song upload handling in `play.html` and a small `update.sh` tweak.

---

## 1.0.42 · `8fed955` — backcompat old browsers

**Released:** 2026-06-24 (12:34 AM EDT)

- Added ES5 `compat.js` and rewrote remote pages so playback works on old tablet browsers (e.g. Android 4.x WebKit).
- Documented back-compat expectations in the remote-play-back-compat skill.

---

## 1.0.41 · `50e3a36` — add stop button

**Released:** 2026-06-24 (12:11 AM EDT)

- Added a STOP button to the remote-play-started dialog so you can end remote play without dismissing first.

---

## 1.0.40 · `4c40e1a` — barcode next to URL

**Released:** 2026-06-23 (11:28 PM EDT)

- Remote play dialog shows a QR code (chevron reveal) beside the tunnel/LAN URL for easier pairing on a second device.

---

## 1.0.39 · `8aeb756` — new icon / sorting

**Released:** 2026-06-23 (10:44 PM EDT)

- Updated the app launcher icon. Song archive sort state syncs between local UI and remote API.

---

## 1.0.38 · `500a551` — remove migrations and simplify

**Released:** 2026-06-23 (10:16 PM EDT)

- Removed destructive migration fallbacks and simplified Room migrations plus remote HTML assets.

---

## 1.0.37 · `cec93f8` — fix cloudflare checks

**Released:** 2026-06-23 (9:38 PM EDT)

- Improved Cloudflare tunnel health checks and error reporting in the remote-play flow.

---

## 1.0.36 · `bed07df` — orphan scan again

**Released:** 2026-06-23 (7:27 PM EDT)

- Another pass on orphan song-file detection and path repair in the repository layer.

---

## 1.0.35 · `b350b0e` — repair names and redo orphans

**Released:** 2026-06-23 (7:11 PM EDT)

- Song path repair migration, orphan-file rescan, and remote `edit.html` upload improvements.

---

## 1.0.34 · `c2cdb22` — orphan files/cloudfare wait

**Released:** 2026-06-23 (6:22 PM EDT)

- Orphan file handling on startup and a wait for Cloudflare tunnel readiness before showing the URL.

---

## 1.0.33 · `fbcb86e` — debug cloudfare

**Released:** 2026-06-23 (5:29 PM EDT)

- Cloudflare debug panel in the remote dialog: connection checks, cloudflared log, copy-debug-info. Large README/API doc update.

---

## 1.0.32 · `39e3f18` — Major API update

**Released:** 2026-06-23 (1:03 PM EDT)

- Per-playlist API paths (`/api/playlists/{id}/…`), remote `index.html` playlist picker, song filename migration utility, and `multi_upload.py` helper script.

---

## 1.0.31 · `18a2c80` — multi upload and minor UI

**Released:** 2026-06-23 (1:24 AM EDT)

- Batch/multi-file upload support and minor PIN-page and ViewModel tweaks.

---

## 1.0.30 · `be50a54` — migration to storage

**Released:** 2026-06-22 (8:24 PM EDT)

- Migrates song files into app-specific storage (`Music/StageManager`) with `StorageAccessScreen` and one-time migration logic.

---

## 1.0.29 · `9e57dda` — placeholder for missing songs

**Released:** 2026-06-22 (7:55 PM EDT)

- Placeholder songs (🚧) for setlist entries that don't have a real chart yet; parity docs updated.

---

## 1.0.28 · `7b9d709` — fix to UI, cloudflare, import

**Released:** 2026-06-22 (7:32 PM EDT)

- Remote play dialog and Cloudflare flow fixes, import tweaks, and `play.html` updates.

---

## 1.0.27 · `8954407` — more cloudflare/PIN port/title cleanup

**Released:** 2026-06-22 (6:57 PM EDT)

- Single 5-digit code serves as both Cloudflare PIN and LAN port; remote title bar cleanup and a DB migration.

---

## 1.0.26 · `3adcf4e` — clooudflare

**Released:** 2026-06-22 (6:29 PM EDT)

- CI bundles cloudflared in the release workflow; remote play error reporting improvements.

---

## 1.0.25 · `a830bd5` — fix Cloudflare URL

**Released:** 2026-06-22 (6:15 PM EDT)

- Fixed Cloudflare tunnel URL handling and the remote-play-started dialog.

---

## 1.0.24 · `ea363b2` — update to updates.sh

**Released:** 2026-06-22 (6:06 PM EDT) · also tagged **1.0.23**

- `update.sh` improvements and cloudflared binary refresh.

---

## `f0ded08` — cloudfare vs LAN

**Shipped in 1.0.25** · 2026-06-22 (6:00 PM EDT)

- Choose Cloudflare tunnel (internet) or LAN-only when starting remote play; new `RemotePlayMode` dialog.

---

## 1.0.22 · `f4a6fc0` — more cloudflare / sort songs

**Released:** 2026-06-22 (5:43 PM EDT)

- Cloudflared asset bundling in the app and song archive sort improvements.

---

## 1.0.21 · `3565192` — cloudfare build fix

**Released:** 2026-06-22 (5:27 PM EDT)

- Navigation and main-tabs fixes for the Cloudflare remote-play startup flow.

---

## `222e557` — cloudfare tunnel

**Shipped in 1.0.19** · 2026-06-22 (5:22 PM EDT)

- Initial Cloudflare tunnel: `cloudflared` binary, PIN gate (`pin.html`), Settings remote code, tunnel service, CI fetch script.

---

## 1.0.19 · `c093ece` — UI cleanup

**Released:** 2026-06-22 (5:01 PM EDT)

- Remote play UI polish and controller/service notification cleanup.

---

## 1.0.18 · `49732bf` — pencil/remote reorder

**Released:** 2026-06-22 (1:14 PM EDT)

- Remote `edit.html` to reorder, remove, and add songs from the archive (pencil from play view). Song title parsing utilities.

---

## 1.0.17 · `d51138e` — uniform local/remote plat

**Released:** 2026-06-22 (11:46 AM EDT)

- Playlist view parity skill/docs; aligned local Compose playback behavior with remote `play.html`.

---

## `ab821b7` — upload UI/navigation/gradle/skills

**Shipped in 1.0.15** · 2026-06-22 (11:32 AM EDT)

- Remote upload overlay, compile-kotlin/rebuild-app agent skills, navigation cleanup, and Gradle updates.

---

## 1.0.15 · `84284a8` — drag fix again

**Released:** 2026-06-22 (11:07 AM EDT)

- More drag-reorder fixes in playlist DAO, navigation, and `DraggableItem`.

---

## 1.0.14 · `f39aedf` — more drag fixes

**Released:** 2026-06-22 (10:56 AM EDT)

- Small fixes to `PlayRemoteServer` and drag-reorder gesture handling.

---

## `c17b159` — fix drag, add song remote

**Shipped in 1.0.12** · 2026-06-22 (10:50 AM EDT)

- **+** upload on remote `play.html` to add songs from the browser; additional drag-reorder fixes.

---

## 1.0.12 · `1338288` — fixes to drag, draw etc

**Released:** 2026-06-22 (10:35 AM EDT)

- Drag-reorder fixes across lists; remote `play.html` gesture and navigation improvements.

---

## 1.0.11 · `f48b277` — removed extra file that broke CI

**Released:** 2026-06-21 (8:31 PM EDT)

- README trim to fix a CI failure from an extra file reference.

---

## `3719e04` — Remove extra files

**Shipped in 1.0.8** · 2026-06-21 (8:29 PM EDT)

- Deleted leftover View-system files (fragments, adapters) after the Compose rewrite.

---

## `560b83f` — fix CI broken

**Shipped in 1.0.8** · 2026-06-21 (8:22 PM EDT)

- CI workflow fixes and `AppUpdateUiState` tweaks after the Compose migration.

---

## `58ddbe1` — rewrite as compose

**Shipped in 1.0.8** · 2026-06-21 (8:12 PM EDT)

- Full UI rewrite from Views/XML to Jetpack Compose: navigation, screens, theme, and reorder components.

---

## 1.0.8 · `24cd641` — better UI / fix drag

**Released:** 2026-06-21 (2:54 AM EDT)

- Remote play zoom/nav improvements; archive sort persisted in DB; drag-reorder fixes.

---

## 1.0.7 · `bf356b8` — migrate to m3

**Released:** 2026-06-21 (2:40 AM EDT)

- Material 3 theme updates and import-activity layout tweaks.

---

## 1.0.6 · `9350ad9` — fix crashes

**Released:** 2026-06-21 (2:33 AM EDT)

- Stability fixes in legacy View-based adapters and `ZoomImageView`.

---

## 1.0.5 · `96c3a2d` — remote playlist

**Released:** 2026-06-21 (2:15 AM EDT)

- First remote play: local HTTP server, `play.html` slideshow, Wi‑Fi icon on playlist detail, foreground notification.

---

## 1.0.4 · `f69bd12` — major UI fixes

**Released:** 2026-06-21 (1:59 AM EDT)

- Large UI pass: playlist colors, song sort (A‑Z / Added / Viewed), quickstart paste, placeholders, song delete with playlist warnings, filename parsing on import.

---

## 1.0.3 · `6a09f2b` — only modern support

**Released:** 2026-06-20 (10:00 PM EDT)

- Dropped legacy multi-ABI builds; targets modern 64-bit ARM only. Simplified build and README.

---

## 1.0.2 · `cac47d6` — multi ABI version

**Released:** 2026-06-20 (9:38 PM EDT)

- CI builds multiple APK ABIs (armeabi-v7a, arm64-v8a, x86_64) in the release workflow.

---

## 1.0.1 · `edf4396` — first code dump

**Released:** 2026-06-20 (9:28 PM EDT)

- Initial Android app: Room DB, song archive, playlists, share import, PDF/image viewer, drag reorder, in-app GitHub updates. README and CI workflow.

---

## `b7f7d59` — first commit

**2026-06-20 (5:25 PM EDT)** · no app release

- Added `update.sh` deploy/sync script for the project.
