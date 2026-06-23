# Local ↔ remote parity matrix

Update this file when adding features or recording **intentional** differences.

## Playlist management

| Behaviour | Local | Remote | Status |
| --------- | ----- | ------ | ------ |
| Row title | `SongDisplay.titleWithKey` | `edit.html` `.song-title` | Align — use `titleWithKey()` in JS |
| Row subtitle | Notes preview only (`notesLine`) | `metaLine`: key · notes | Align — notes only on line 2; key belongs in title |
| Deleted song highlight | `errorContainer` | `.row.deleted` | Aligned |
| Remove from playlist | Trash icon | Remove button | Aligned |
| Drag reorder | `DraggableItem` / long-press | HTML5 drag-and-drop | Aligned (different UX, same outcome) |
| Add from archive | `AddSongDialog` + `searchSongs` | Search input + `/api/songs/search` | Align search row formatting with local |
| Empty playlist copy | `empty_playlist` string | “No songs in this playlist.” | Aligned (wording may differ slightly) |
| Playlist name in header | Accent color **background** on title row | Plain `#title` text | OK — color styling is local-only |
| Rename / duplicate / color / delete playlist | Toolbar on detail screen | `/api/playlists/*` (library CRUD) | API for library; duplicate remains local-only |

## Playback

| Behaviour | Local | Remote | Status |
| --------- | ----- | ------ | ------ |
| Title bar | `playback_song_indicator` + `titleWithKey` | `#meta` + `titleWithKey` | Aligned |
| Multi-page PDF indicator | `playback_page_indicator` | ` · page n/m` in meta | Aligned |
| Tap left/right halves | `PlaybackStage` | `handleSideTap` | Aligned |
| Swipe prev/next | `PlaybackStage` | touch `touchend` swipe | Aligned |
| Pinch zoom / pan | `PlaybackStage` | touch handlers in `play.html` | Aligned |
| Arrow keys | focusable stage | `keydown` listener | Aligned |
| Nav thresholds | `PlaybackNav.kt` | `NAV` in `play.html` | Must stay in sync |
| Zoom blocks navigation | `scale > ZOOM_NAV_MAX` | same | Aligned |
| Record song view | `recordSongView` on change | — | **Intentional** (local analytics) |
| Upload new song | Share intent / import flow | `+` overlay, `/api/upload` | **Intentional** (remote only) |
| Open playlist editor | In-app detail screen | Pencil → `/edit` | Aligned entry points |

## Server / data

| Endpoint | Purpose |
| -------- | ------- |
| `GET /api/songs` | Full song archive list |
| `POST /api/songs/update` | Edit song title/key/notes |
| `GET /api/songs/search` | Archive search (global; used by edit add dialog) |
| `GET /api/parse-filename` | Suggest title/key/notes on upload |
| `GET /api/playlists` | All playlists (id, name, color, songCount) |
| `POST /api/playlists/create` | Create playlist |
| `POST /api/playlists/reorder` | Reorder playlists (library order) |
| `POST /api/playlists/{id}/rename` | Rename playlist |
| `POST /api/playlists/{id}/color` | Set or clear playlist accent color |
| `POST /api/playlists/{id}/delete` | Delete playlist |
| `GET /api/playlists/{id}/state` | Playback position + song list for `play.html` |
| `GET /api/playlists/{id}/entries` | Full entries for `edit.html` |
| `POST /api/playlists/{id}/navigate` | prev/next song/page |
| `GET /api/playlists/{id}/media` | Image/PDF bytes for a page |
| `POST /api/playlists/{id}/reorder` | Reorder songs in playlist |
| `POST /api/playlists/{id}/remove` | Remove entry from playlist |
| `POST /api/playlists/{id}/add` | Add archive song to playlist |
| `POST /api/playlists/{id}/add-placeholder` | Add placeholder to playlist |
| `POST /api/playlists/{id}/upload` | Add song file + metadata |

Local mutations from remote go through `PlayRemoteController` callbacks into the same Room repos as `PlaylistsViewModel`.

## File map

```
app/src/main/java/com/playlists/app/
  ui/screens/PlaylistDetailScreen.kt   # local playlist management
  ui/screens/PlaylistPlaybackScreen.kt # local playback
  ui/SongDisplay.kt                    # title/key + notes preview rules
  ui/playback/PlaybackNav.kt           # gesture thresholds
  ui/components/PlaybackStage.kt       # local playback gestures
  remote/PlayRemoteServer.kt           # HTTP API + JSON builders
  remote/PlayRemoteController.kt       # bridge to repos + tunnel

app/src/main/assets/remote/
  play.html                            # remote playback
  edit.html                            # remote playlist editor
  pin.html                             # Cloudflare PIN gate
```
