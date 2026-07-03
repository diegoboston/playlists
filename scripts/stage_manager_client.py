"""Shared HTTP client helpers for Stage Manager remote API scripts."""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import re
import sys
import urllib.error
import urllib.request

DEFAULT_PIN = "44444"
WORKER_NAME = "play"
DOMAIN_RE = re.compile(r"^[a-z0-9-]+$")


def normalize_domain(raw: str) -> str:
    domain = raw.strip().lower()
    if not domain or not DOMAIN_RE.fullmatch(domain):
        raise ValueError(
            f"Invalid Workers domain {raw!r} — use lowercase letters, numbers, and hyphens only",
        )
    return domain


def build_stable_url(domain: str) -> str:
    return f"https://{WORKER_NAME}.{normalize_domain(domain)}.workers.dev"


def build_opener() -> urllib.request.OpenerDirector:
    cookie_jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))


def resolve_remote_base(base_url: str, opener: urllib.request.OpenerDirector) -> str:
    base_url = base_url.rstrip("/")
    if ".workers.dev" not in base_url:
        return base_url
    req = urllib.request.Request(f"{base_url}/url", method="GET")
    try:
        with opener.open(req, timeout=15) as resp:
            body = resp.read().decode(errors="replace").strip()
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        raise RuntimeError(f"Could not resolve stable URL ({e.code}): {body}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"Could not reach stable URL: {e.reason}") from e
    if not body:
        raise RuntimeError(
            "Stable URL has no active tunnel — start Cloudflare remote play on the phone first",
        )
    return body.rstrip("/")


def resolve_configured_base(args: argparse.Namespace, *, script_name: str) -> str:
    if args.url:
        return args.url.rstrip("/")
    env_url = os.environ.get("STAGE_MANAGER_URL", "").strip()
    if env_url:
        return env_url.rstrip("/")
    domain = (args.domain or os.environ.get("STAGE_MANAGER_DOMAIN", "")).strip()
    if not domain:
        domain = input("Workers domain (e.g. diegoppp): ").strip()
    if not domain:
        print(
            f"{script_name}: domain required (--domain, STAGE_MANAGER_DOMAIN, or prompt)",
            file=sys.stderr,
        )
        raise SystemExit(2)
    return build_stable_url(domain)


def resolve_pin(args: argparse.Namespace) -> str:
    return args.pin or os.environ.get("STAGE_MANAGER_PIN", DEFAULT_PIN)


def add_connection_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--url",
        default=None,
        help="Full remote base URL (overrides --domain); stable *.workers.dev or direct trycloudflare",
    )
    parser.add_argument(
        "-d",
        "--domain",
        default=None,
        help=f"Workers account domain → https://{WORKER_NAME}.<domain>.workers.dev "
        "(or set STAGE_MANAGER_DOMAIN)",
    )
    parser.add_argument(
        "--pin",
        default=None,
        help=f"5-digit remote PIN (default: $STAGE_MANAGER_PIN or {DEFAULT_PIN})",
    )


def authenticate(base_url: str, pin: str, opener: urllib.request.OpenerDirector) -> None:
    req = urllib.request.Request(
        f"{base_url.rstrip('/')}/api/auth",
        data=json.dumps({"pin": pin}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with opener.open(req) as resp:
            resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        raise RuntimeError(f"Authentication failed ({e.code}): {body}") from e


def api_get(
    base_url: str,
    path: str,
    opener: urllib.request.OpenerDirector,
) -> dict:
    url = f"{base_url.rstrip('/')}/{path.lstrip('/')}"
    req = urllib.request.Request(url, method="GET")
    try:
        with opener.open(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        raise RuntimeError(f"GET {path} failed ({e.code}): {body}") from e


def connect_remote(
    args: argparse.Namespace,
    *,
    script_name: str,
    quiet: bool = False,
) -> tuple[str, urllib.request.OpenerDirector]:
    configured_base = resolve_configured_base(args, script_name=script_name)
    opener = build_opener()
    remote_base = resolve_remote_base(configured_base, opener)
    if not quiet and remote_base != configured_base:
        print(f"Resolved tunnel: {remote_base}")
    authenticate(remote_base, resolve_pin(args), opener)
    return remote_base, opener


def fetch_playlists(
    base_url: str,
    opener: urllib.request.OpenerDirector,
) -> dict:
    return api_get(base_url, "/api/playlists", opener)


def fetch_playlist_entries(
    base_url: str,
    playlist_id: int,
    opener: urllib.request.OpenerDirector,
) -> dict:
    return api_get(base_url, f"/api/playlists/{playlist_id}/entries", opener)
