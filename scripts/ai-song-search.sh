#!/usr/bin/env bash
# Local CLI for the in-app AI song search (web search + page extract).
# See README "AI chart assistant" → Local search simulator.
set -euo pipefail

ENV_SH="${ANDROID_BUILD_ENV:-$HOME/tmp/android-build/env.sh}"
if [[ -f "$ENV_SH" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_SH"
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "ai-song-search: ERROR — JAVA_HOME is unset. Install JDK 17 and source $ENV_SH, or export JAVA_HOME." >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

need_key=1
for arg in "$@"; do
  case "$arg" in
    -h|--help) need_key=0 ;;
  esac
done
if [[ "$need_key" -eq 1 && -z "${OPENAI_API_KEY:-}" ]]; then
  echo "ai-song-search: note — OPENAI_API_KEY is unset; web search still runs, but parsing a result needs the key." >&2
fi

echo "ai-song-search: building CLI (Java $($JAVA_HOME/bin/java -version 2>&1 | head -1))"
./gradlew :tools:ai-song-search:installDist --quiet

BIN="$ROOT/tools/ai-song-search/build/install/ai-song-search/bin/ai-song-search"
if [[ ! -x "$BIN" ]]; then
  echo "ai-song-search: ERROR — missing $BIN" >&2
  exit 1
fi

exec "$BIN" "$@"
