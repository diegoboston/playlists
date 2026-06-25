#!/usr/bin/env bash
# List commits on main not yet mentioned in report/changelog.md (newest first).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../../" && pwd)"
CHANGELOG="$ROOT/report/changelog.md"
REPO="${GITHUB_REPOSITORY:-diegoboston/playlists}"

if [ ! -f "$CHANGELOG" ]; then
    echo "changelog not found: $CHANGELOG" >&2
    exit 1
fi

mapfile -t KNOWN < <(grep -oE '`[0-9a-f]{7}`' "$CHANGELOG" | tr -d '`' | sort -u)

is_known() {
    local h="$1" k
    for k in "${KNOWN[@]}"; do
        [ "$k" = "$h" ] && return 0
    done
    return 1
}

format_line() {
    printf '%s  %s  %s\n' "$1" "$2" "$3"
}

collect_git() {
    git -C "$ROOT" log main --reverse --format='%h %s %cI' 2>/dev/null \
        || git -C "$ROOT" log --reverse --format='%h %s %cI'
}

collect_gh_page() {
    gh api "repos/$REPO/commits?sha=main&per_page=100&page=$1"
}

collect_curl_page() {
    curl -fsSL "https://api.github.com/repos/$REPO/commits?sha=main&per_page=100&page=$1"
}

parse_commits_json() {
    jq -r '.[] | "\(.sha[0:7])\t\(.commit.message | split("\n")[0])\t\(.commit.committer.date)"' | tac
}

pending=()

append_if_new() {
    local short="$1" subject="$2" iso="$3"
    is_known "$short" || pending+=("$(format_line "$short" "$subject" "$iso")")
}

if git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
    while read -r short subject iso; do
        [ -n "$short" ] || continue
        append_if_new "$short" "$subject" "$iso"
    done < <(collect_git)
elif command -v gh >/dev/null 2>&1; then
    page=1
    while :; do
        json=$(collect_gh_page "$page") || break
        [ "$json" = "[]" ] && break
        while IFS=$'\t' read -r short subject iso; do
            [ -n "$short" ] || continue
            append_if_new "$short" "$subject" "$iso"
        done < <(printf '%s' "$json" | parse_commits_json)
        [ "$(printf '%s' "$json" | jq 'length')" -lt 100 ] && break
        page=$((page + 1))
    done
elif command -v curl >/dev/null 2>&1 && command -v jq >/dev/null 2>&1; then
    page=1
    while :; do
        json=$(collect_curl_page "$page") || break
        [ "$json" = "[]" ] && break
        while IFS=$'\t' read -r short subject iso; do
            [ -n "$short" ] || continue
            append_if_new "$short" "$subject" "$iso"
        done < <(printf '%s' "$json" | parse_commits_json)
        [ "$(printf '%s' "$json" | jq 'length')" -lt 100 ] && break
        page=$((page + 1))
    done
else
    echo "Need git, gh, or curl+jq to list commits" >&2
    exit 1
fi

if [ "${#pending[@]}" -eq 0 ]; then
    echo "(none)"
else
    printf '%s\n' "${pending[@]}" | tac
fi
