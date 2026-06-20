
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
    --exclude='build/'
    --exclude='local.properties'
    --exclude='.DS_Store'
    --exclude='.idea/'
    --exclude='*.iml'
    --exclude='captures/'
    --exclude='.externalNativeBuild/'
    --exclude='.cxx/'
)

clean_artifacts() {
    echo "==> Removing build artifacts"
    rm -rf \
        .gradle \
        build \
        app/build \
        .idea \
        captures \
        .externalNativeBuild \
        .cxx
    rm -f local.properties
    find . -name '.DS_Store' -delete 2>/dev/null || true
    find . -name '*.iml' -delete 2>/dev/null || true
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
run git add .

read -r -p "Commit message: " message
if [ "${#message}" -lt 10 ]; then
    echo "Commit message must be at least 10 characters. Aborted."
    exit 1
fi

run git commit -m "$message"
run git push origin main
