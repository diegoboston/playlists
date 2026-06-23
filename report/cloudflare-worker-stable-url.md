# Cloudflare Worker stable URL — implementation plan

**Stage Manager (playlists)** · June 2026  
**Status:** Proposed (not implemented)

## Summary

Stage Manager’s Cloudflare remote play uses a **quick tunnel** (`*.trycloudflare.com`). That URL changes every session, which forces users to re-share links and reconfigure tools like `scripts/multi_upload.py`.

This plan adds a **stable redirect** on Cloudflare’s edge using a **Cloudflare Worker** and **Workers KV**. The phone continues to create the tunnel as today; when remote play starts, it **registers** the new tunnel URL with the Worker. Bookmarks and scripts then use one fixed address forever.

All components are from **Cloudflare** (same vendor as `cloudflared` / quick tunnels), but they are different products:

| Product | Role |
|---------|------|
| **Cloudflare Tunnel** (`cloudflared`, quick tunnel) | Phone → temporary public URL (existing) |
| **Cloudflare Workers** | Fixed `*.workers.dev` URL that issues HTTP redirects |
| **Workers KV** | Stores the current tunnel base URL |

Authentication is **one-time manual copy/paste** in Settings (Worker URL + write secret). No OAuth or Cloudflare login inside the app.

---

## Problem

| Actor | Pain today |
|-------|------------|
| Human (browser) | Must get a new `*.trycloudflare.com` link each session |
| `multi_upload.py` | `DEFAULT_URL` is hardcoded; breaks when tunnel changes |
| Phone app | Shows ephemeral URL in `RemotePlayStartedDialog` only |

Quick tunnels are intentionally ephemeral. The PIN in Settings still protects access; the missing piece is a **stable pointer** to whichever tunnel is active.

---

## Target behaviour

```text
One-time setup (laptop + phone Settings)
  → deploy Worker, paste stable URL + write secret

Each Cloudflare remote-play session
  → phone starts tunnel
  → phone POSTs new base URL to Worker
  → Worker stores it in KV

Ongoing use
  → bookmark https://stage-manager-tunnel.<you>.workers.dev/
  → browser redirects to current *.trycloudflare.com (PIN gate)
  → multi_upload.py uses same stable URL; resolves /url then calls API
```

```mermaid
sequenceDiagram
    participant Phone
    participant CF as cloudflared
    participant Worker
    participant KV
    participant Client as Browser / multi_upload

    Note over Phone,Client: One-time: deploy Worker; paste URL + secret in Settings

    Phone->>CF: start quick tunnel
    CF-->>Phone: https://abc.trycloudflare.com
    Phone->>Worker: POST /register (Bearer secret)
    Worker->>KV: current = abc.trycloudflare.com

    Client->>Worker: GET /?playlist=3
    Worker-->>Client: 302 Location: https://abc.trycloudflare.com/?playlist=3

    Client->>Worker: GET /url
    Worker-->>Client: text/plain tunnel base (for scripts)
```

---

## Architecture

### Worker (new: `workers/tunnel-redirect/`)

Edge script with a KV binding `TUNNEL`, key `current` → tunnel base URL string.

| Method | Path | Auth | Response |
|--------|------|------|----------|
| `GET` | `/` | None | `302` to stored base URL; **preserve query string** (`?playlist=`) |
| `GET` | `/url` | None | `text/plain` body = tunnel base (no trailing slash) |
| `POST` | `/register` | `Authorization: Bearer <WRITE_SECRET>` | JSON `{"url":"https://….trycloudflare.com"}` → store in KV |
| Other | — | — | `404` |

**Register validation**

- HTTPS only
- Host must match `*.trycloudflare.com` (align with `CloudflareTunnel.URL_PATTERN` in `CloudflareTunnel.kt`)
- Strip trailing `/`
- Reject empty or malformed URLs

**Empty / missing KV value**

- `GET /` → `503` with short plain text (“No tunnel active”)
- `GET /url` → `200` with empty body or `503` (prefer empty + script-side error message)

**Do not proxy** `/api/*` through the Worker. `POST` requests do not follow redirects reliably; scripts must call `GET /url` first, then talk to the real tunnel.

### One-time deploy (manual)

```bash
cd workers/tunnel-redirect
wrangler kv namespace create TUNNEL
# add namespace id to wrangler.toml
wrangler secret put WRITE_SECRET   # long random string → also paste in app Settings
wrangler deploy
# → https://stage-manager-tunnel.<account>.workers.dev
```

User copies:

1. **Stable redirect URL** — deploy output
2. **Write secret** — same value as `WRITE_SECRET`

Free tier is sufficient for personal use (Workers + KV free allowances).

### Android app

#### Settings (`SettingsScreen.kt`)

New fields under **Remote play**:

| Field | Type | Notes |
|-------|------|-------|
| Stable redirect URL | Text | e.g. `https://….workers.dev`, no trailing slash |
| Write secret | Password | Bearer token for `/register` |

Both optional. Publishing runs only when both are set. Hint text explains one-time Worker deploy. Optional **Copy stable URL** action for bookmarking.

#### Persistence (`AppPrefs.kt`, `StageManagerState.kt`)

Extend existing JSON + SharedPreferences pattern:

```kotlin
getTunnelRedirectBase(context): String?
getTunnelRedirectSecret(context): String?
isTunnelRedirectConfigured(context): Boolean
setTunnelRedirect(context, base: String?, secret: String?)
```

Store in app-private prefs. Acceptable for personal use; EncryptedSharedPreferences optional later.

#### Publisher (`TunnelRedirectClient.kt` — new)

- `publish(baseUrl, secret, tunnelUrl): Result<Unit>`
- `HttpURLConnection` POST to `{baseUrl}/register` (same style as `AppUpdate.kt`)
- Headers: `Authorization: Bearer $secret`, `Content-Type: application/json`
- Body: `{"url":"https://….trycloudflare.com"}`
- Timeout ~10 s
- **Best-effort:** failure must **not** block remote play

#### Lifecycle hook (`PlayRemoteController.kt`)

After successful Cloudflare tunnel start (~line 122), before building `publicUrl`:

```kotlin
if (mode == RemotePlayMode.CLOUDFLARE && AppPrefs.isTunnelRedirectConfigured(context)) {
    TunnelRedirectClient.publish(...)
}
```

- Publish **base** tunnel URL only (no `?playlist=` suffix)
- **LAN mode:** skip
- **On stop:** v1 does not clear KV (stale redirect until next session). Optional v2: clear endpoint

#### Started dialog (`RemotePlayStartedDialog.kt`)

When redirect is configured, show:

1. **This session** — current `*.trycloudflare.com` URL (existing)
2. **Stable bookmark** — `{workerBase}/?playlist=…` for copy/bookmark

### `scripts/multi_upload.py`

Worker root redirects browsers; API calls need the resolved tunnel.

```python
def resolve_remote_base(url: str, opener) -> str:
    url = url.rstrip("/")
    if ".workers.dev" in url:
        # GET {url}/url → plain tunnel base
        ...
    return url
```

Call before `authenticate()`.

| Change | Detail |
|--------|--------|
| `DEFAULT_URL` | Worker stable URL (or env `STAGE_MANAGER_URL`) |
| `--url` help | “Worker stable URL or direct trycloudflare URL” |
| Env | `STAGE_MANAGER_URL` overrides default |

Example:

```bash
export STAGE_MANAGER_URL=https://stage-manager-tunnel.you.workers.dev
python scripts/multi_upload.py --playlist 1 scores.pdf
```

---

## Security model

| Concern | Mitigation |
|---------|------------|
| Write secret in APK | Secret only in Settings on device, not in source |
| Public read of current tunnel | `GET /` and `/url` are public; PIN still gates the app |
| Guessed write secret | Long random `WRITE_SECRET` |
| Stale redirect after stop | User must start remote play on phone before bookmark/script works |

Exposing `*.trycloudflare.com` is equivalent to sharing the session link today.

---

## Files to add or modify

### Add

| Path | Purpose |
|------|---------|
| `workers/tunnel-redirect/wrangler.toml` | Worker + KV binding |
| `workers/tunnel-redirect/src/index.ts` | Redirect + register logic |
| `workers/tunnel-redirect/README.md` | Deploy instructions |
| `app/.../remote/TunnelRedirectClient.kt` | HTTP client for `/register` |
| `app/src/test/.../TunnelRedirectClientTest.kt` | URL validation, normalisation |

### Modify

| Path | Change |
|------|--------|
| `PlayRemoteController.kt` | Publish after tunnel start |
| `SettingsScreen.kt` | URL + secret fields |
| `AppPrefs.kt`, `StageManagerState.kt` | New prefs keys |
| `RemotePlayStartedDialog.kt` | Show stable bookmark |
| `app/src/main/res/values/strings.xml` | Copy for Settings + dialog |
| `scripts/multi_upload.py` | Resolve `/url` |
| `README.md` | User-facing setup + usage (per `update-readme` skill when implemented) |

---

## Tests

| Area | Approach |
|------|----------|
| URL validation / normalisation | JVM unit test (`TunnelRedirectClientTest`) |
| `resolve_remote_base` | Python unit test or manual |
| Worker | Manual `curl` after deploy |
| Instrumented Android | Not required for v1 |

---

## Implementation order

1. Worker — deploy, verify with `curl`
2. `TunnelRedirectClient` + unit tests
3. `AppPrefs` / Settings UI
4. `PlayRemoteController` hook
5. `RemotePlayStartedDialog` stable URL
6. `multi_upload.py` resolve
7. README update

---

## Out of scope (v1)

- OAuth or Cloudflare login in the app
- Clearing KV when remote play stops
- Custom domain on the Worker
- Named Cloudflare tunnel (best long-term; separate effort)
- Publishing from LAN mode
- Proxying API traffic through the Worker

---

## Alternatives considered

| Approach | Stable redirect? | One-time phone setup | Verdict |
|----------|------------------|----------------------|---------|
| GitHub Gist | No (plain text) | OAuth | Good for scripts only |
| jsonbin.io | No | Paste API key | Weaker UX |
| **Cloudflare Worker** | **Yes** | Paste URL + secret | **Chosen** |
| Named CF tunnel + domain | Yes | CF account + DNS | Best long-term; needs domain |
| bit.ly / TinyURL | Yes (paid / new link each time) | Varies | Poor fit |

---

## References

- Existing tunnel extraction: `app/src/main/java/com/playlists/app/remote/CloudflareTunnel.kt`
- Remote play start/stop: `app/src/main/java/com/playlists/app/remote/PlayRemoteController.kt`
- Upload script: `scripts/multi_upload.py`
- [Cloudflare Workers docs](https://developers.cloudflare.com/workers/)
- [Workers KV docs](https://developers.cloudflare.com/kv/)
