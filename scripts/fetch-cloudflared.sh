#!/usr/bin/env bash
# Build cloudflared for Android arm64 (bundled as a native lib for Quick Tunnels).
#
# Must use CGO + Android NDK — pure Go (CGO_ENABLED=0) cannot resolve DNS on Android
# (/etc/resolv.conf is missing; Go falls back to [::1]:53 → connection refused).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
OUT="$OUT_DIR/libcloudflared.so"
mkdir -p "$OUT_DIR"
GOCACHE="$ROOT/.go-build-cache"
mkdir -p "$GOCACHE"
export GOCACHE

ANDROID_API="${ANDROID_API:-26}" # matches app minSdk

resolve_ndk_home() {
  if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
    echo "$ANDROID_NDK_HOME"
    return 0
  fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
    find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1
    return 0
  fi
  return 1
}

resolve_ndk_prebuilt() {
  local os arch
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  arch="$(uname -m)"
  case "$os-$arch" in
    linux-x86_64) echo "linux-x86_64" ;;
    linux-aarch64|linux-arm64) echo "linux-aarch64" ;;
    darwin-x86_64) echo "darwin-x86_64" ;;
    darwin-arm64) echo "darwin-arm64" ;;
    *)
      echo "fetch-cloudflared: unsupported host $os-$arch for NDK cross-compile" >&2
      return 1
      ;;
  esac
}

NDK_HOME="$(resolve_ndk_home || true)"
if [ -z "$NDK_HOME" ]; then
  cat >&2 <<'EOF'
fetch-cloudflared: Android NDK not found.

cloudflared must be built with CGO for Android DNS to work. Install an NDK, e.g.:

  sdkmanager "ndk;26.1.10909125"
  export ANDROID_HOME=...   # parent of ndk/
  # or
  export ANDROID_NDK_HOME=.../ndk/26.1.10909125

CI installs the NDK before running this script.
EOF
  exit 1
fi

PREBUILT="$(resolve_ndk_prebuilt)"
CC="$NDK_HOME/toolchains/llvm/prebuilt/$PREBUILT/bin/aarch64-linux-android${ANDROID_API}-clang"
if [ ! -x "$CC" ]; then
  echo "fetch-cloudflared: NDK clang not found: $CC" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone --depth 1 https://github.com/cloudflare/cloudflared.git "$TMP/cloudflared"
(
  cd "$TMP/cloudflared"
  export CGO_ENABLED=1
  export GOOS=android
  export GOARCH=arm64
  export CC
  go build -ldflags="-s -w" -o "$OUT" ./cmd/cloudflared
)
chmod +x "$OUT"
echo "fetch-cloudflared: wrote $OUT ($(du -h "$OUT" | cut -f1)) via CGO CC=$CC"
