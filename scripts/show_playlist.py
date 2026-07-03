#!/usr/bin/env python3
"""List song titles in a Stage Manager playlist via the remote API."""

from __future__ import annotations

import argparse
import json
import sys

from stage_manager_client import (
    add_connection_args,
    connect_remote,
    fetch_playlist_entries,
    fetch_playlists,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="List playlists or fetch song titles from a Stage Manager playlist.",
    )
    add_connection_args(parser)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument(
        "--playlist",
        type=int,
        metavar="ID",
        help="Playlist id to show songs for (use --all to list ids)",
    )
    target.add_argument(
        "--all",
        action="store_true",
        help="List all playlists with their ids",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print full API response as JSON",
    )
    return parser.parse_args()


def print_playlists(data: dict) -> None:
    playlists = data.get("playlists", [])
    print(f"Playlists ({len(playlists)}):")
    for playlist in playlists:
        playlist_id = playlist.get("id", "?")
        name = playlist.get("name", "")
        song_count = playlist.get("songCount", 0)
        print(f"  {playlist_id}: {name} ({song_count} songs)")


def print_entries(data: dict, playlist_id: int) -> None:
    playlist_name = data.get("playlistName", f"playlist {playlist_id}")
    entries = data.get("entries", [])
    print(f"{playlist_name} ({len(entries)} songs)")
    for index, entry in enumerate(entries, start=1):
        print(f"  {index:2d}: {entry.get('title', '')}")


def main() -> int:
    args = parse_args()
    try:
        base_url, opener = connect_remote(args, script_name="show_playlist")
        if args.all:
            data = fetch_playlists(base_url, opener)
        else:
            data = fetch_playlist_entries(base_url, args.playlist, opener)
    except SystemExit:
        raise
    except Exception as e:
        print(f"show_playlist: {e}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(data, indent=2))
        return 0

    if args.all:
        print_playlists(data)
    else:
        print_entries(data, args.playlist)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
