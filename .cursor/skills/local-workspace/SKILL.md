---
name: local-workspace
description: >-
  This repo is the canonical local workspace — all source files are here. Do not
  rsync or fetch from a remote host to recover missing files. Use when a path
  is not found, when considering update.sh, rsync, or remote sync.
---

# Local workspace

## Rules

- **No rsync is needed — all files are local.**
- **`update.sh` is run on another machine** to pull this tree from a remote copy
  into that machine’s parent directory. Agents working in this workspace must
  **not** run `update.sh` or rsync to “recover” sources.

## If a file looks missing

1. Re-read the path with normal tools (`Read`, `Glob`, `Grep`, `ls`, `find`) from
   the repository root.
2. Check for typos, wrong directory, or `.cursorignore` — not a remote copy gap.
3. Ask the user if the file is still absent after a local search. Do **not**:
   - rsync from a remote host to replace project sources
   - curl/GitHub-fetch to replace project sources
   - run `update.sh`

## What `update.sh` is for (human workflow only)

Run **on another machine** (not by the agent in this repo) to:

1. Clean build artifacts locally on that machine
2. Pull the latest tree from the configured remote
3. Review diff, commit, and push

This workspace **is** the source side of that pull — not the destination.
