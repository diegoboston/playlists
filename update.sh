#!/usr/bin/env bash
set -euo pipefail

run() {
    echo "==> $*"
    "$@"
}

confirm() {
    local prompt="$1"
    local reply
    read -r -p "$prompt (y/n) " reply
    case "$reply" in
        [yY]|[yY][eE][sS]) return 0 ;;
        *) echo "Aborted."; exit 1 ;;
    esac
}

cd "$(dirname "$0")"

RSYNC_EXCLUDES=(
    --exclude='.gradle/'
    --exclude='.kotlin/'
    --exclude='.tmp/'
    --exclude='build/'
    --exclude='local.properties'
    --exclude='.DS_Store'
    --exclude='.idea/'
    --exclude='*.iml'
    --exclude='captures/'
    --exclude='.externalNativeBuild/'
    --exclude='.cxx/'
    --exclude='.go-build-cache/'
)

clean_artifacts() {
    echo "==> Removing build artifacts"
    rm -rf \
        .gradle \
        .kotlin \
        .tmp \
        build \
        app/build \
        .idea \
        captures \
        .externalNativeBuild \
        .cxx \
        .go-build-cache
    rm -f local.properties
    find . -name '.DS_Store' -delete 2>/dev/null || true
    find . -name '*.iml' -delete 2>/dev/null || true
}

RSYNC_FROM_REMOTE=(
    rsync -avzP
    --exclude='.git/'
    "${RSYNC_EXCLUDES[@]}"
    shared6:code/d-a/playlists
    ..
)

sync_delete_extra_local_files() {
    echo "==> Local files not on shared6 (to be deleted):"
    local to_delete
    to_delete=$("${RSYNC_FROM_REMOTE[@]}" --delete -n 2>/dev/null | grep '^deleting ' | sed 's/^deleting //' || true)
    if [ -z "$to_delete" ]; then
        echo "  (none)"
        return 0
    fi
    echo "$to_delete" | sed 's/^/  /'
    confirm "Delete these files and re-sync from shared6?"

    echo "==> Deleting extras and syncing from shared6"
    local output deleted
    output=$("${RSYNC_FROM_REMOTE[@]}" --delete 2>&1 | tee /dev/stderr)
    deleted=$(printf '%s\n' "$output" | grep '^deleting ' | sed 's/^deleting //' || true)
    echo
    echo "==> Deleted:"
    if [ -z "$deleted" ]; then
        echo "  (none)"
    else
        printf '%s\n' "$deleted" | sed 's/^/  /'
    fi
}

clean_artifacts
run rsync -avzP --delete-excluded "${RSYNC_EXCLUDES[@]}" shared6:code/d-a/playlists ..

echo
run git status
confirm "Proceed?"

echo
run git diff
confirm "Proceed?"

echo
sync_delete_extra_local_files

echo
run git status
confirm "Proceed with commit and push?"

echo
run git add .

read -r -p "Commit message: " message
if [ "${#message}" -lt 10 ]; then
    echo "Commit message must be at least 10 characters. Aborted."
    exit 1
fi

run git commit -m "$message"
run git push origin main
