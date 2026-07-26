# Changelog

**Stage Manager** — commit-by-commit release log (newest first).

---

## `1b82431` — push playlist/readme

**2026-07-26 (9:43 AM EDT)** · [commit](https://github.com/diegoboston/playlists/commit/1b82431c328f45da650b8f936dc272cbda5c1de0)

- **Push playlist to server** is available as soon as stable redirect keys are validated in Settings — remote play no longer needs to be running.
- README documents the ungated push flow and shows side-by-side Original / Guido launcher icon previews.

---

## 1.0.70 · `d265741` — upload to server/menu icons

**Released:** 2026-07-12 (7:14 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/d26574177c617900eecbcaefb99fc40bd87992dc)

- Playlist menus (Playlists tab and detail) switch from pencil to **☰**, with per-action icons (rename, color, delete, duplicate, export).
- While remote play is active, **Push playlist to server** uploads the combined playlist PDF as **last.pdf** on the stable Worker (same path as stable-start auto-upload), with an uploading progress dialog.

---

## 1.0.69 · `bae62a8` — update PDF offline

**Released:** 2026-07-10 (10:23 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/bae62a85f1557f216136e8c0a6559df672002b0c)

- Stable remote start uploads the combined playlist PDF to the Worker (**POST /push-pdf** → R2 `last.pdf`) and shows **Uploading playlist PDF…** during that phase.
- When the phone tunnel is offline, the stable bookmark serves a PIN gate and the last uploaded playlist PDF (`/pdf`, optional download).
- Worker needs an R2 bucket binding (`PLAYLIST_PDF`); app/client and Worker README cover the offline fallback flow.

---

## 1.0.68 · `1c36b08` — 2up pdf fix

**Released:** 2026-07-10 (10:07 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/1c36b084cb7b60b4daef5f660a157d8385b04d4a)

- AI chart PDF rendering packs content across multiple letter pages (continuation headers) instead of forcing a single page.
- Chart key preview adds font size control plus previous/next page navigation for multi-page drafts.
- OpenAI key readiness is remembered via a validated flag so mic/find-chart gating stays consistent after Settings.

---

## 1.0.67 · `338ad98` — reuse cached APK for update

**Released:** 2026-07-10 (4:06 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/338ad98d21c1eec99b38114b3e7c216b188b236b)

- In-app update reuses a previously downloaded APK when it still matches the newer GitHub release version code (skips re-download).
- Stale or incomplete update cache files are cleaned; a still-newer complete APK is kept. Uses `longVersionCode` on API 28+.

---

## 1.0.66 · `de17f57` — UI reorganization

**Released:** 2026-07-04 (4:22 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/de17f577a157be055b4cc27a8cdf5cbefef2844b)

- Settings folds less-common options under an expandable **Advanced** section; OpenAI and Worker secret validation state is persisted once a live check succeeds.
- Stable play URL only appears in remote URL lists when redirect keys are validated (`isStableRedirectReady`).
- Playlist color picker uses a cleaner dialog layout (tap swatch to select; no separate OK).

---

## 1.0.65 · `33b6103` — cloudfare watch/UI fix

**Released:** 2026-07-04 (3:03 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/33b6103fa6eead8f9713c058bf367c5de3786439)

- Cloudflare/stable remote play: tunnel watchdog restarts cloudflared when it dies mid-session (capped retries) and re-publishes the stable URL; toast shows the new public URL.
- Remote web pages show the app icon (favicon + brand) and list alternate connect URLs on the home page when available.
- Serves `/app-icon.png` from the remote HTTP server for browser UI branding.

---

## `b64de56` — harden checks

**2026-07-03 (4:07 PM EDT)** · [commit](https://github.com/diegoboston/playlists/commit/b64de56f71e4ea4d3bbb931e4c59da727da9630c)

- Remote play start/stop: cancels in-flight starts cleanly; **Stop** from the notification no longer blocks on the main thread (NanoHTTPD teardown on a background thread).
- Settings: OpenAI key and Worker write-secret probes ignore stale results when you edit the field mid-check; OpenAI validation requires a models list; Worker validation requires `{"ok":true}` so misconfigured proxies cannot false-pass.
- Remote notification **tap** opens the app; status/debug polling stops when remote play is not running.
- `show_playlist.py` lists all playlists by default and prompts for domain/PIN; Python scripts send a **User-Agent** so Cloudflare stable URLs no longer return 403.
- `update.sh` skips and cleans `__pycache__/` and `*.pyc`; `.gitignore` ignores Python bytecode.

---

## 1.0.63 · `037de0b` — better check with validate

**Released:** 2026-07-03 (3:42 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/037de0b52bbcad835485e3b04a566e8ade0e0f47)

- Stable redirect Worker: **`POST /validate`** checks the write secret without touching KV; **`POST /unregister`** clears the stored tunnel.
- App Settings secret probe calls `/validate` instead of posting an empty `/register`.
- Worker README documents the new endpoints.

---

## 1.0.62 · `6e698a4` — better key check for workers

**Released:** 2026-07-03 (3:20 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/6e698a4ddb63b271ddce8ee67200e2bebdbd6fee)

- Worker write-secret check distinguishes **wrong secret** (401), **missing worker** (404), and a reachable worker (was probing via empty `/register`).
- Remote debug panel shows the cloudflared log only for tunnel/cloudflared issues—not stable-redirect KV registration warnings.

---

## 1.0.61 · `c2352c1` — piano/check secret/UI fixes

**Released:** 2026-07-03 (2:55 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/c2352c1ddc064bec68720d7c6088db86670b2c05)

- Main tabs: **Piano** icon opens a scrollable on-screen keyboard with synthesized tone for reference while prepping sets.
- On launch, scans the song archive for **missing files** and **shared file paths**; a snackbar reports issues when found.
- Settings: **Worker write secret** validates live (green check / error), matching the OpenAI key UX; **Check for updates** shows download progress on the status card.
- Shared update progress banner composable used app-wide during APK download.

---

## 1.0.60 · `db4e28f` — stable URL

**Released:** 2026-07-03 (9:08 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/db4e28f5e4bffec56a972fb2ea47d6a48ca1b5e3)

- Remote play adds **Stable play URL** mode: the phone registers the live Cloudflare tunnel with a **Workers KV redirect** (`workers/tunnel-redirect/`) so bookmarks stay at `https://play.<subdomain>.workers.dev`.
- Settings: **Workers account subdomain** + **write secret** (validated against the deployed Worker). The remote start dialog lists stable, Cloudflare, and LAN URLs, each with a link and QR chevron.
- `multi_upload.py`, `stage_manager_client.py`, and `show_playlist.py` accept the stable base URL; README and `report/cloudflare-worker-stable-url.md` document setup.

---

## 1.0.59 · `b3cbdcd` — simplify wifi icon

**Released:** 2026-07-03 (2:37 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/b3cbdcd3ae3b5049c540979623f9921447bed4f8)

- Remote play **Wi‑Fi** toolbar icon simplified on the Playlists tab and playlist detail.

---

## 1.0.58 · `dac4b79` — UI fix/cloudflare stability/wifi stop

**Released:** 2026-07-02 (3:42 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/dac4b7936c808a1a2a3fd060d4e26014858713f5)

- Remote play **STOP 🛑** ends the session from the start/status dialog; **OK** dismisses without stopping.
- Cloudflare remote play: hardened stale-session detection after backgrounding; long-press Wi‑Fi status/debug behaves correctly when reopening the app.
- Remote web (`index.html`, `play.html`, `edit.html`, `songs.html`) and light-theme UI polish.

---

## `7c350d5` — song download/playlist quicklist

**2026-07-01 (7:21 AM EDT)** · [commit](https://github.com/diegoboston/playlists/commit/7c350d57d5e6f5e392afc1336fec082c754282f5)

- Song viewer and playlist playback: **↓** opens the system share sheet to save or send the original image or PDF.
- Remote play: **↓** in the browser downloads the current song file (`GET /api/playlists/{id}/download?song=`).
- Remote playlist picker (`/`) adds **Quickstart playlist** — paste a set list, **Match songs**, then **Create** or **Create with placeholders** (same flow as the app).

---

## 1.0.56 · `9b920d0` — human filenames/better Guido/NFC report

**Released:** 2026-06-29 (6:52 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/9b920d099f3a8bdabf12f9d643b1994d6373e677)

- Song files on disk use `{Title}-{songId}.{ext}` (migration renames existing media and chart sidecars on first launch).
- Refreshed **Guido** alternate launcher icon artwork.
- Planning doc for NFC playlist sharing (`report/nfc-playlist-sharing.md`).

---

## 1.0.55 · `36d3ab1` — Guido icon

**Released:** 2026-06-27 (2:02 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/36d3ab1dce75b81ca3c9e5eb5957304c33f48ba2)

- Settings **App icon**: pick **Original** or **Guido** launcher icon (home screen may take a moment to refresh).

---

## 1.0.54 · `cde371f` — reorg pencil/fix export

**Released:** 2026-06-26 (12:29 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/cde371f3f6823c259ab59fb7ba8988b3f7fd8f21)

- Playlist **pencil** menu extracted to a shared component on the Playlists tab and playlist detail (rename, color, delete, duplicate, export PDF).
- **Export PDF** keeps source PDFs open during merge so vector page import is reliable.

---

## 1.0.53 · `b809186` — combined page views

**Released:** 2026-06-26 (12:19 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/b8091860e2feed8f63db87e9e27f5b321b82ff17)

- Song viewer reuses **PlaybackStage** — tap/swipe between PDF pages, pinch zoom on images and single-page PDFs (same gestures as playlist playback).
- Shared `PlaybackSongMedia` composable for playback and archive song view.

---

## `0e74654` — PDF playlist export/fix view UI for songs

**2026-06-26 (11:47 AM EDT)** · [commit](https://github.com/diegoboston/playlists/commit/0e746542f2f39259c8db4f03144885a1a7653bc6)

- **Export PDF** on playlist detail (**⋮**): set-list table of contents (playlist name + song titles with keys) then every chart page in order; PDF charts merged as **vector pages** (original page size), photos embedded on letter pages; raster fallback if a PDF cannot import.
- Song viewer: single-page PDFs use pinch zoom; multi-page PDF pager fixes (page indicator, empty-state handling).
- Settings: **OpenAI billing overview** link replaces the in-app credit balance row.

---

## 1.0.51 · `aeac535` — key change fixes/show OpenAI credit

**Released:** 2026-06-26 (10:44 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/aeac535de97a15455391621f985fc05413788b50)

- Find chart / **New key** preview separates **Chart key** (source) from **Play in** (transposed); tap **Chart key** to pick from a key sheet; hint when the key was guessed from the first chord.
- Transpose ± can prefer **flat** or **sharp** spellings; transposer handles richer chord suffixes (e.g. m7b5, G7#9, slash chords).
- Settings shows **remaining OpenAI credit** ($) after the API key validates.

---

## 1.0.50 · `ac40ff4` — changes on import

**Released:** 2026-06-25 (5:18 PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/ac40ff49ae24908f440a2013a12daa0a782b61d6)

- Share a chord-page **URL** to Stage Manager to open **Find chart** and extract the page (same as picking a search result); saves to the **archive**, or also adds to the **open playlist** if you shared while viewing one. PDF/image shares still use the normal import dialog.
- Find chart works without a playlist context — archive-only flow with **Add to archive** confirm.
- Settings: OpenAI key validation keeps the green check only (removed redundant “key accepted” text).

---

## 1.0.49 · `ab54932` — minor transpose fixes

**Released:** 2026-06-25 (11:43 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/ab549326a458bd473c8ec894bb02e85700081e69)

- Chart preview key label falls back to the first `<chord>` in the chart when the draft has no explicit key.
- Transposer only rewrites bracketed `<chord>` tokens (drops legacy bare-chord matching inside lyrics).
- Transpose ± uses the first chord as the source key when `key` and `sourceKey` are missing.

---

## 1.0.48 · `cabe04b` — don't show null key

**Released:** 2026-06-25 (11:14 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/cabe04b09e81270399669c4f8cbcf06dcfb7973d)

- JSON null or missing key fields no longer render as the literal string “null” in PDF headers or the key preview.
- Optional AI fields (artist, capo, notes, source URL) normalized the same way.

---

## 1.0.47 · `39818e1` — improve AI/storage count

**Released:** 2026-06-25 (10:49 AM EDT) · [commit](https://github.com/diegoboston/playlists/commit/39818e1e6097902afed451eff194cf21f0c80372)

- Settings **Storage** row shows total library size under `Music/StageManager` (song files, chart sidecars, database, and state).
- AI chart generation uses bracketed `<chord>` tokens; PDF renders chords in bold monospace separate from lyric lines.
- In-app update download/install banner appears on Settings (hidden on other screens while Settings is open).

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
