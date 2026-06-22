#!/usr/bin/env bash
# Build cloudflared for Android arm64 (bundled as a native lib for Quick Tunnels).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
OUT="$OUT_DIR/libcloudflared.so"
mkdir -p "$OUT_DIR"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone --depth 1 https://github.com/cloudflare/cloudflared.git "$TMP/cloudflared"
(
  cd "$TMP/cloudflared"
  CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -ldflags="-s -w" \
    -o "$OUT" ./cmd/cloudflared
)
chmod +x "$OUT"
echo "fetch-cloudflared: wrote $OUT ($(du -h "$OUT" | cut -f1))"
