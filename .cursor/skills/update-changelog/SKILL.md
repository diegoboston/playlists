---
name: update-changelog
description: >-
  Append missing git commits to report/changelog.md in the repo's release-log
  format. Use when the user asks to update the changelog, sync the changelog,
  capture new commits, or refresh report/changelog.md.
---

# Update Changelog

Keep `report/changelog.md` current: every shipped commit should appear once, newest entries at the top (after the header block).

## When to run

- User asks to update, refresh, or sync the changelog
- After a batch of commits landed on `main` and the log is behind
- Before tagging or publishing a release summary

Pair with **local-edt-times** for all timestamps shown to the user.

## Find uncaptured commits

1. Read `report/changelog.md` and collect every 7-char hash already present (pattern `` `abcdef0` ``).
2. List commits on `main` not in that set:

```bash
bash .cursor/skills/update-changelog/scripts/pending-commits.sh
```

Uses local `git` when available; falls back to the GitHub API (`gh` or `curl`).

3. If the script prints `(none)`, report that the changelog is up to date — do not edit the file.

## Write each missing entry

Process commits **newest first** (prepend below the `---` after the title block).

For each commit, inspect the change before writing bullets:

```bash
git show --stat <full-hash>
git show <full-hash> -- '*.kt' '*.html' '*.js' '*.md' ':!report/changelog.md'
```

Focus on **user-facing** behaviour (screens, remote web, API, DB migrations, CI/releases). Skip pure refactors unless they affect operators.

### Heading

**Released commit** (has a GitHub Release on `main` for this build):

```markdown
## 1.0.N · `abc1234` — subject line from commit
```

**Intermediate commit** (landed between releases, bundled into a later APK):

```markdown
## `abc1234` — subject line from commit
```

Derive `N` from the release tag `v1.0.N`. Match release to commit by comparing release `published_at` to commit time (within the same CI run). When unsure, check `.github/workflows/android.yml` — each push to `main` bumps `versionName` to `1.0.<GITHUB_RUN_NUMBER>`.

### Metadata line

**Released:**

```markdown
**Released:** YYYY-MM-DD (h:MM AM/PM EDT) · [commit](https://github.com/diegoboston/playlists/commit/FULLHASH)
```

Convert committer UTC time to `America/New_York`; label **EDT** or **EST** (see **local-edt-times**). Use 12-hour clock with AM/PM, e.g. `2026-06-24 (6:28 PM EDT)`.

**Shipped in a later release** (hash-only heading):

```markdown
**Shipped in 1.0.N** · YYYY-MM-DD (h:MM AM/PM EDT)
```

Optional extras on the same line when relevant: `· also tagged **1.0.M**`, `· no app release` (infra-only commits like the first `update.sh` commit).

Top entry may include the commit link; intermediate entries usually omit it.

### Body

2–4 bullet points. Active voice, concrete behaviour — same tone as existing entries. Example:

```markdown
- Song search on the Songs tab and a new remote `/songs` archive page (search, sort, catalog upload).
- DB v9 drops `isPlaceholder` and `mimeType`; placeholders become 🚧 in the title plus `"placeholder"` in notes.
```

Do **not** duplicate the commit subject in the bullets. Do **not** mention changelog or skill edits unless that is the whole commit.

## Insert location

```markdown
# Changelog

**Stage Manager (playlists)** — commit-by-commit release log (newest first).

---

## 1.0.N · `newest` — …        ← prepend new entries here

## 1.0.44 · `3859c2d` — …       ← existing entries stay below
```

Preserve chronological order within the new batch (newest at top).

## Checklist before finishing

- [ ] Every hash from `pending-commits.sh` now appears in the changelog
- [ ] Newest-first order preserved
- [ ] Timestamps in Boston local time with EDT/EST label
- [ ] Version numbers align with GitHub Release tags when releases exist
- [ ] Bullets describe user-visible impact, not file lists
- [ ] Re-read the diff — no duplicate entries, no broken markdown links

## Git unavailable in this workspace

This tree may lack `.git`. The pending-commits script uses `gh api` / GitHub REST as fallback. If neither git nor network works, ask the user to run the script on a machine with repo access or paste `git log --oneline -20`.

## Pair with other skills

| Change | Also run |
| ------ | -------- |
| User-facing app behaviour documented in README | **update-readme** |
| Any file under `app/` | **rebuild-app** (if you edited the app in the same task) |

Updating the changelog alone does **not** require rebuild-app.
