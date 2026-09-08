#!/usr/bin/env bash
# Copy versioned hooks into .git/hooks (no git config change).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOOKS_DIR="$ROOT/.git/hooks"
if [[ ! -d "$ROOT/.git" ]]; then
  echo "install-git-hooks: ERROR — $ROOT is not a git checkout" >&2
  exit 1
fi
mkdir -p "$HOOKS_DIR"
cp "$ROOT/.githooks/pre-push" "$HOOKS_DIR/pre-push"
chmod +x "$HOOKS_DIR/pre-push"
echo "install-git-hooks: installed $HOOKS_DIR/pre-push"
echo "install-git-hooks: pushes now run compile-kotlin (SKIP_COMPILE_HOOK=1 to skip)"
