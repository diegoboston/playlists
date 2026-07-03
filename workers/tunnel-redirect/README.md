# Stage Manager tunnel redirect Worker

Fixed `*.workers.dev` URL that redirects to the current Cloudflare **quick tunnel**
(`*.trycloudflare.com`). The phone registers the live tunnel URL on each remote-play
session; bookmarks and `scripts/multi_upload.py` can keep one stable address.

See [`report/cloudflare-worker-stable-url.md`](../../report/cloudflare-worker-stable-url.md)
for the full design.

## Prerequisites

- [Cloudflare account](https://dash.cloudflare.com/sign-up) (free tier is enough)
- Node.js 18+ and npm
- Wrangler CLI (installed via `npm install` below)

## One-time setup

```bash
cd workers/tunnel-redirect
cp wrangler.toml.example wrangler.toml
npm install
npx wrangler login
```

`wrangler.toml` is gitignored (it holds your account-specific KV namespace id). Only
`wrangler.toml.example` is committed.

Create a KV namespace and copy its id into `wrangler.toml`:

```bash
npx wrangler kv namespace create TUNNEL
```

Edit `wrangler.toml` — replace `REPLACE_WITH_KV_NAMESPACE_ID` with the `id` from
that command.

Set the write secret (long random string). Use the **same** value in Stage Manager
Settings once the Android app supports it:

```bash
npx wrangler secret put WRITE_SECRET
```

Deploy:

```bash
npm run deploy
```

Wrangler prints the stable URL, e.g. `https://play.<account>.workers.dev` (from `name` in
`wrangler.toml` + your account subdomain).

Save for the phone app:

1. **Stable redirect URL** — deploy output (no trailing slash)
2. **Write secret** — same string as `WRITE_SECRET`

## Manual verification

Validate write secret (Settings probe):

```bash
curl -sS -X POST "$WORKER/validate" -H "Authorization: Bearer $SECRET"
# Expect: {"ok":true}
```

Register a tunnel (replace values):

```bash
WORKER=https://play.you.workers.dev
SECRET=your-write-secret
TUNNEL=https://abc-def.trycloudflare.com

curl -sS -X POST "$WORKER/register" \
  -H "Authorization: Bearer $SECRET" \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"$TUNNEL\"}"
```

Redirect (browser or curl):

```bash
curl -sS -D - -o /dev/null "$WORKER/?playlist=3"
# Expect: 302 Location: https://abc-def.trycloudflare.com/?playlist=3
```

Script resolution:

```bash
curl -sS "$WORKER/url"
# Expect: https://abc-def.trycloudflare.com
```

Empty KV (before first register):

```bash
curl -sS -D - -o /dev/null "$WORKER/"
# Expect: 503 No tunnel active

curl -sS "$WORKER/url"
# Expect: empty body, 200
```

## API

| Method | Path | Auth | Response |
|--------|------|------|----------|
| `GET` | `/` | None | `302` to stored tunnel; query string preserved |
| `GET` | `/url` | None | `text/plain` tunnel base (empty if none) |
| `POST` | `/validate` | `Authorization: Bearer <WRITE_SECRET>` | JSON `{"ok":true}` — checks secret, no KV access |
| `POST` | `/register` | `Authorization: Bearer <WRITE_SECRET>` | JSON `{"url":"https://….trycloudflare.com"}` |
| `POST` | `/unregister` | `Authorization: Bearer <WRITE_SECRET>` | JSON `{"ok":true}` — deletes stored tunnel |

Only `https://*.trycloudflare.com` URLs are accepted (`api.trycloudflare.com` is rejected).

## Local dev (optional)

```bash
# Optional preview KV namespace for wrangler dev:
# npx wrangler kv namespace create TUNNEL --preview
# Add preview_id to wrangler.toml

echo 'WRITE_SECRET=dev-secret' > .dev.vars
npm run dev
```

`.dev.vars` is gitignored; do not commit secrets.

## Cost

Cloudflare Workers + KV free tier is sufficient for personal use. See
[Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/) and
[KV pricing](https://developers.cloudflare.com/kv/platform/pricing/).
