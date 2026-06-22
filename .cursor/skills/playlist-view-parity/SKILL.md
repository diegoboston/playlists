---
name: playlist-view-parity
description: >-
  Maintains feature parity between local Compose playlist/playback UI and remote
  web views (edit.html, play.html). Use when changing PlaylistDetailScreen,
  PlaylistPlaybackScreen, SongDisplay, PlaybackNav, PlayRemoteServer APIs, or
  assets under app/src/main/assets/remote/.
---

# Playlist view parity (local ↔ remote)

Two **paired surfaces** must stay aligned:

| Surface | Local (Compose) | Remote (browser) |
| ------- | --------------- | ---------------- |
| **Playlist management** | `PlaylistDetailScreen.kt` | `assets/remote/edit.html` + `/api/playlist`, `/api/reorder`, `/api/remove`, `/api/add`, `/api/songs/search` |
| **Playback** | `PlaylistPlaybackScreen.kt`, `PlaybackStage.kt` | `assets/remote/play.html` + `/api/state`, `/api/navigate`, `/api/media` |

Server glue: `PlayRemoteServer.kt`, `PlayRemoteController.kt`.

Full feature matrix: [PARITY.md](PARITY.md).

## When to apply

Run this checklist whenever a task touches **either side** of a pair above — even
if the user only asked to change one.

## Workflow

1. **Classify the change** — management vs playback vs shared display/API.
2. **Update the counterpart** in the same PR/task before finishing.
3. **Reuse shared rules** (below) — do not invent a third format in HTML.
4. **Document intentional gaps** in PARITY.md only when a difference is deliberate.
5. **Run rebuild-app**, then **update-readme** if user-facing behaviour changed.

## Shared rules (single source of truth)

### Song title + key

Kotlin: `SongDisplay.titleWithKey(title, keySignature)` in `SongDisplay.kt`.

Remote HTML must mirror the same format: append ` (key)` only when key is non-blank.
Prefer a local `titleWithKey(title, key)` helper in each HTML file (as in `play.html`).

**Apply in:** playlist rows, search results, playback title/meta — local and remote.

### Notes preview

Kotlin: `SongDisplay.notesLine(notes)` — trimmed, max 20 chars + `…`.

Remote: match that truncation in meta/subtitle lines (do not show full notes in compact rows).

### Deleted archive songs

Local: `errorContainer` row background (`entry.isDeleted`).

Remote: `.deleted` row styling in `edit.html`. Keep both visible in the list.

### Playback navigation

Thresholds live in `PlaybackNav.kt`. **Must match** the `NAV` object in `play.html`
(comment there says “Keep in sync with PlaybackNav.kt”).

Local gestures: `PlaybackStage.kt` (tap halves, swipe, pinch zoom, pan when zoomed).

Remote: same behaviour in `play.html` (tap, swipe, pinch, arrow keys).

### Playback indicator

Format: `{index}/{count}: {Title (Key)}` plus ` · page n/m` when PDF has multiple pages.

Local: `playback_song_indicator` + `playback_page_indicator` in `PlaylistPlaybackScreen.kt`.

Remote: meta bar in `play.html` via `/api/state` (include `key` in song objects when needed).

### Search / add from archive

Both sides: full-text match on title, key, notes (`SongDao` / `onSearchSongs`).

Result rows: **Title (Key)** on line 1, notes preview on line 2 (same as playlist rows).

## API parity

When adding or changing playlist/playback behaviour, update **both**:

- Compose screen + ViewModel/repo call
- `PlayRemoteServer.kt` route + JSON field
- Consuming HTML/JS

`/api/state` song objects must expose fields the playback UI needs (e.g. `title`, `key`, `pageCount`).

`/api/playlist` entries must expose fields the editor UI needs (e.g. `entryId`, `title`, `key`, `notes`, `isDeleted`).

## Intentional differences (do not “fix”)

| Local only | Remote only |
| ---------- | ----------- |
| Rename, duplicate, color, delete playlist | — |
| In-app **Play** button (local fullscreen Compose) | Browser fullscreen via URL |
| — | **Upload song** overlay on play view (`/api/upload`) |
| — | Cloudflare PIN gate (`pin.html`, `/api/auth`) when tunnel mode |
| — | **Done** on edit view returns to `/` (play), not Android back stack |

New features should land on **both** management/playback pairs unless explicitly scoped to one column above.

## Completion checklist

Copy and complete before marking the task done:

```
Parity:
- [ ] Identified pair(s): management / playback / both
- [ ] Counterpart file(s) updated
- [ ] SongDisplay rules mirrored in remote JS (title/key, notes preview)
- [ ] PlaybackNav ↔ play.html NAV constants match (if playback touched)
- [ ] PlayRemoteServer JSON includes new fields (if API touched)
- [ ] PARITY.md matrix updated if behaviour intentionally diverges
- [ ] rebuild-app VERIFY OK
- [ ] README updated (update-readme) if user-visible
```

## Common mistakes

| Mistake | Fix |
| ------- | --- |
| Key only in remote meta line, not in title | Use `titleWithKey` on line 1; notes only on line 2 |
| Added `/api/state` field but not `buildStateJson()` | Add field in `PlayRemoteServer.kt` and read it in `play.html` |
| Changed swipe threshold in Compose only | Update `PlaybackNav.kt` **and** `play.html` `NAV` |
| Remote edit works but local list unchanged | Wire `PlayRemoteController` callbacks to same repo ops as `PlaylistsViewModel` |
