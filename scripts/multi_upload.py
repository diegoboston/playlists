#!/usr/bin/env python3
"""Split a multi-page PDF into single-page PDFs and upload each as a Stage Manager song."""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import re
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

try:
    import fitz
except ImportError:
    print("multi_upload: pymupdf is required (pip install pymupdf)", file=sys.stderr)
    sys.exit(1)


DEFAULT_PIN = "44444"
DEFAULT_URL = "https://unexpected-comedy-mag-optimum.trycloudflare.com"


def extract_first_column_title(page: fitz.Page) -> str:
    lines: list[tuple[float, float, str]] = []
    for block in page.get_text("dict")["blocks"]:
        if block.get("type") != 0:
            continue
        for line in block["lines"]:
            text = "".join(span["text"] for span in line["spans"]).strip()
            text = text.replace("\u200b", "").strip()
            if not text:
                continue
            x0 = min(span["bbox"][0] for span in line["spans"])
            y0 = min(span["bbox"][1] for span in line["spans"])
            lines.append((y0, x0, text))
    if not lines:
        return ""
    left_x = min(x for _, x, _ in lines)
    col_lines = [(y, x, t) for y, x, t in lines if abs(x - left_x) < 30]
    col_lines.sort()
    return col_lines[0][2]


def safe_filename(page_num: int, title: str) -> str:
    stem = re.sub(r"[^\w\s\-'().,]+", "", title).strip()
    if not stem:
        stem = f"page-{page_num:02d}"
    return f"{page_num:02d} - {stem[:60]}.pdf"


def split_pdf(src: Path, out_dir: Path) -> list[dict]:
    doc = fitz.open(src)
    manifest: list[dict] = []
    try:
        for page_index in range(doc.page_count):
            page = doc[page_index]
            page_num = page_index + 1
            title = extract_first_column_title(page) or f"Page {page_num}"
            filename = safe_filename(page_num, title)

            single = fitz.open()
            single.insert_pdf(doc, from_page=page_index, to_page=page_index)
            path = out_dir / filename
            single.save(path)
            single.close()

            manifest.append(
                {
                    "page": page_num,
                    "title": title,
                    "file": path,
                    "filename": filename,
                }
            )
    finally:
        doc.close()
    return manifest


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


def upload_song(
    base_url: str,
    item: dict,
    opener: urllib.request.OpenerDirector,
) -> dict:
    boundary = "----StageManagerBoundary7MA4YWxkTrZu0gW"
    parts: list[bytes | str] = []
    for name, value in [
        ("title", item["title"]),
        ("key", ""),
        ("notes", ""),
        ("filename", item["filename"]),
        ("mime", "application/pdf"),
    ]:
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'
        )

    file_bytes = Path(item["file"]).read_bytes()
    parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{item["filename"]}"\r\n'
        f"Content-Type: application/pdf\r\n\r\n".encode()
        + file_bytes
        + b"\r\n"
    )
    parts.append(f"--{boundary}--\r\n".encode())
    body = b"".join(part if isinstance(part, bytes) else part.encode() for part in parts)

    req = urllib.request.Request(
        f"{base_url.rstrip('/')}/api/upload",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with opener.open(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        raise RuntimeError(f"Upload failed ({e.code}): {body}") from e


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Split a PDF into single-page files and upload each page as a Stage Manager song.",
    )
    parser.add_argument("pdf", type=Path, help="Path to the source PDF")
    parser.add_argument(
        "--url",
        default=DEFAULT_URL,
        help=f"Stage Manager remote base URL (default: {DEFAULT_URL})",
    )
    parser.add_argument(
        "--pin",
        default=None,
        help=f"5-digit remote PIN (default: $STAGE_MANAGER_PIN or {DEFAULT_PIN})",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=None,
        help="Directory for split PDFs (default: temp dir, deleted unless --keep-pages)",
    )
    parser.add_argument(
        "--keep-pages",
        action="store_true",
        help="Keep split PDF files after upload",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Split and print titles only; do not upload",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=0.15,
        help="Seconds to wait between uploads (default: 0.15)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    pdf_path = args.pdf.expanduser().resolve()
    if not pdf_path.is_file():
        print(f"multi_upload: file not found: {pdf_path}", file=sys.stderr)
        return 1

    pin = args.pin or __import__("os").environ.get("STAGE_MANAGER_PIN", DEFAULT_PIN)
    temp_dir: tempfile.TemporaryDirectory[str] | None = None
    if args.out_dir:
        out_dir = args.out_dir.expanduser().resolve()
        out_dir.mkdir(parents=True, exist_ok=True)
        cleanup = False
    elif args.keep_pages:
        out_dir = Path(tempfile.mkdtemp(prefix="multi_upload_"))
        cleanup = False
    else:
        temp_dir = tempfile.TemporaryDirectory(prefix="multi_upload_")
        out_dir = Path(temp_dir.name)
        cleanup = True

    manifest = split_pdf(pdf_path, out_dir)
    print(f"Split {pdf_path.name} into {len(manifest)} page(s) in {out_dir}")

    for item in manifest:
        print(f"  {item['page']:2d}: {item['title']!r}")

    if args.dry_run:
        if cleanup and temp_dir is not None:
            temp_dir.cleanup()
        return 0

    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    authenticate(args.url, pin, opener)

    ok = 0
    failures: list[tuple[dict, str]] = []
    for item in manifest:
        try:
            result = upload_song(args.url, item, opener)
            song_count = len(result.get("songs", []))
            print(f"Uploaded {item['page']:2d}: {item['title']!r} (playlist now {song_count} songs)")
            ok += 1
            if args.delay > 0:
                time.sleep(args.delay)
        except Exception as e:
            print(f"Failed  {item['page']:2d}: {item['title']!r} -> {e}", file=sys.stderr)
            failures.append((item, str(e)))

    if cleanup and temp_dir is not None:
        temp_dir.cleanup()
    elif args.keep_pages and not args.out_dir:
        print(f"Split pages kept in {out_dir}")

    print(f"Done: {ok} uploaded, {len(failures)} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
