#!/usr/bin/env bash
# Build cloudflared for Android arm64 (bundled into app assets for Quick Tunnels).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/cloudflared"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone --depth 1 https://github.com/cloudflare/cloudflared.git "$TMP/cloudflared"
CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -ldflags="-s -w" \
  -o "$OUT" "$TMP/cloudflared/cmd/cloudflared"
chmod +x "$OUT"
echo "fetch-cloudflared: wrote $OUT ($(du -h "$OUT" | cut -f1))"
