---
name: remote-play-back-compat
description: >-
  Try to maintain back compat with old devices (Nexus 10, Android 4.3) or explain
  when not possible. Primary target is remote play fullscreen (play.html). Use when
  editing assets/remote/, PlayRemoteServer routes, or discussing tablet/browser
  support for remote play.
---

# Remote play back compat (old tablets)

Try to maintain back compat with old devices (Nexus 10, Android 4.3) or explain when not possible.

**Main feature to support:** fullscreen **play** mode (`play.html`) — sheet music visible, song meta, prev/next navigation.

## Scope

| In scope | Out of scope (say so explicitly) |
| -------- | -------------------------------- |
| Remote **play** fullscreen + its APIs (`/api/state`, `/api/navigate`, `/api/media`) | The **Android app** (minSdk 26) — it does not run on Android 4.3 |
| `compat.js`, `song-display.js`, PIN/index/edit pages when cheap | Phone-app features “for old tablets” |
| | |

Reference device: **Nexus 10, Android 4.3** — stock Browser / old WebKit (not latest Chrome).

## Rules

1. **Default:** keep remote web changes compatible with old WebKit unless there is a strong reason not to.
2. **Priority:** play mode must load media, show meta, and support prev/next (tap halves, swipe, arrow keys). Pinch/pan zoom is secondary — must not break navigation when missing or broken.
3. **If you cannot:** tell the user what failed, which API or CSS blocks it, and what older devices lose. Do not silently drop support.

## Implementation baseline

Patterns already in this repo:

- **`compat.js`** — ES5: `XMLHttpRequest` (not `fetch`), manual query params (not `URLSearchParams`), `setHidden`, `hypot`. Served at `/compat.js`; must be available **before PIN auth** in `PlayRemoteServer.kt`.
- **`song-display.js`** — ES5 IIFE; keep in sync with `SongDisplay.kt`.
- **Remote page scripts** — `var`, `function`, string concat; no `async`/`await`, optional chaining, arrow functions, or template literals.
- **CSS** — avoid `inset`, `min()`, `gap`, `calc()` on critical layout; use absolute positioning and margins; `-webkit-` prefixes for flex/transform; `[hidden] { display: none !important; }` plus `RemoteCompat.setHidden`.
- **Events** — two-argument `addEventListener` on play-mode paths (no `{ passive: true }` object form).

Add network helpers to `RemoteCompat` — do not introduce a second XHR style.

## When editing play mode

1. Read **playlist-view-parity** — keep `NAV` aligned with `PlaybackNav.kt`.
2. Script order: `compat.js` → `song-display.js` → page script.
3. Trace on Android 4.3: script parses → `refresh()` → `media.src` set → tap/swipe → `navigate`.
4. Run **rebuild-app** if Kotlin/assets/server routes changed.
5. Run **update-readme** if user-facing browser support changed.

## Acceptable trade-offs

Say so in the task summary when intentional:

- Upload drag-and-drop (file input still works on old tablets).
- Edit drag-reorder (play mode is the bar).
- `object-fit` — fall back to `max-width` / `max-height` on `#media`.

## Completion checklist

```
Back compat:
- [ ] Touches remote play assets or PlayRemoteServer routes
- [ ] play.html ES5 + compat.js; no new fetch/async/optional chaining
- [ ] Critical CSS avoids modern-only layout properties
- [ ] /compat.js still served (including pre-auth for pin.html)
- [ ] If incompatible: user told what breaks and why
- [ ] rebuild-app VERIFY OK (when app/assets changed)
```

## Pair with other skills

| Skill | Relationship |
| ----- | ------------ |
| **playlist-view-parity** | Local ↔ remote behaviour; this skill adds **browser age** constraints |
| **rebuild-app** | Required after asset/server changes |
| **update-readme** | When browser support wording changes |
