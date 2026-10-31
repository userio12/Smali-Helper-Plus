#!/usr/bin/env python3
"""Builds the remote documentation-update manifest from the bundled content.

Copies app/src/main/assets/content/{opcodes,lessons,snippets}.json to content/ byte for byte and writes
content/manifest.json with the real SHA-256 and size of every copied file (see content/README.md).

Standard library only. Idempotent: running it twice without changes rewrites nothing, and `publishedAt` only moves
when a package actually changed. Use --check in CI to fail when content/ is out of date.

Usage (from the repository root):
    python3 scripts/build-content-manifest.py
    python3 scripts/build-content-manifest.py --check
    python3 scripts/build-content-manifest.py --description lessons="Two new lessons about try/catch"
"""
import argparse
import datetime
import hashlib
import json
import sys
from pathlib import Path

PACKAGE_IDS = ("opcodes", "lessons", "snippets")
SCHEMA_VERSION = 1
MAX_PACKAGE_BYTES = 5 * 1024 * 1024  # must match MAX_PACKAGE_BYTES in the app
DEFAULT_BASE_URL = "https://raw.githubusercontent.com/riyadmondol2006/Smali-Helper-Plus/main/content"
DEFAULT_DESCRIPTIONS = {
    "opcodes": "Opcode reference",
    "lessons": "Lessons, quizzes and flashcards",
    "snippets": "Built-in snippet library",
}


class BuildError(Exception):
    pass


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_bundle(path: Path, package_id: str) -> tuple[bytes, int]:
    """Returns (raw bytes, contentVersion) after sanity-checking the bundle header."""
    data = path.read_bytes()
    if len(data) > MAX_PACKAGE_BYTES:
        raise BuildError(f"{path}: {len(data)} bytes exceeds the {MAX_PACKAGE_BYTES} byte limit")
    if data.startswith(b"\xef\xbb\xbf"):
        raise BuildError(f"{path}: remove the UTF-8 byte order mark")
    try:
        doc = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        raise BuildError(f"{path}: not valid UTF-8 JSON ({e})") from e
    if not isinstance(doc, dict):
        raise BuildError(f"{path}: top-level value must be an object")
    if doc.get("schemaVersion") != SCHEMA_VERSION:
        raise BuildError(f"{path}: schemaVersion must be {SCHEMA_VERSION}, found {doc.get('schemaVersion')!r}")
    version = doc.get("contentVersion")
    if not isinstance(version, int) or isinstance(version, bool) or version < 1:
        raise BuildError(f"{path}: contentVersion must be an integer >= 1")
    key = {"opcodes": "opcodes", "lessons": "lessons", "snippets": "snippets"}[package_id]
    if not isinstance(doc.get(key), list) or not doc[key]:
        raise BuildError(f"{path}: '{key}' must be a non-empty list")
    return data, version


def load_existing(manifest_path: Path) -> dict:
    if not manifest_path.is_file():
        return {}
    try:
        doc = json.loads(manifest_path.read_text(encoding="utf-8"))
        return doc if isinstance(doc, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def build(args) -> int:
    assets = Path(args.assets)
    out = Path(args.out)
    manifest_path = out / "manifest.json"
    existing = load_existing(manifest_path)
    existing_pkgs = {p.get("id"): p for p in existing.get("packages", []) if isinstance(p, dict)}
    overrides = {}
    for item in args.description or []:
        pid, sep, text = item.partition("=")
        if not sep or pid not in PACKAGE_IDS:
            raise BuildError(f"--description expects <{'|'.join(PACKAGE_IDS)}>=<text>, got {item!r}")
        overrides[pid] = text.strip()

    packages = []
    copies = {}
    for pid in PACKAGE_IDS:
        src = assets / f"{pid}.json"
        if not src.is_file():
            if args.allow_missing:
                print(f"skip {pid}: {src} not found", file=sys.stderr)
                continue
            raise BuildError(f"{src} not found (use --allow-missing to publish only the packages that exist)")
        data, version = read_bundle(src, pid)
        old = existing_pkgs.get(pid, {})
        digest = sha256_hex(data)
        if pid in overrides:
            description = overrides[pid]
        elif old.get("contentVersion") == version and old.get("description"):
            description = old["description"]
        else:
            description = DEFAULT_DESCRIPTIONS[pid]
        packages.append({
            "id": pid,
            "contentVersion": version,
            "url": f"{args.base_url.rstrip('/')}/{pid}.json",
            "sha256": digest,
            "sizeBytes": len(data),
            "description": description,
        })
        copies[pid] = data
    if not packages:
        raise BuildError("no bundled content found")

    previous_signature = [(p.get("id"), p.get("contentVersion"), p.get("sha256"), p.get("sizeBytes"), p.get("url"), p.get("description"))
                          for p in existing.get("packages", []) if isinstance(p, dict)]
    new_signature = [(p["id"], p["contentVersion"], p["sha256"], p["sizeBytes"], p["url"], p["description"]) for p in packages]
    unchanged = previous_signature == new_signature and existing.get("schemaVersion") == SCHEMA_VERSION
    min_app = args.min_app_version_code or existing.get("minAppVersionCode") or 1
    if unchanged and existing.get("minAppVersionCode") == min_app and existing.get("publishedAt"):
        published_at = existing["publishedAt"]
    else:
        published_at = args.published_at or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "publishedAt": published_at,
        "minAppVersionCode": min_app,
        "packages": packages,
    }
    manifest_text = json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"

    stale = []
    for pid, data in copies.items():
        dst = out / f"{pid}.json"
        if not dst.is_file() or dst.read_bytes() != data:
            stale.append(dst)
    if not manifest_path.is_file() or manifest_path.read_text(encoding="utf-8") != manifest_text:
        stale.append(manifest_path)

    if args.check:
        if stale:
            print("content/ is out of date; run scripts/build-content-manifest.py. Stale: " + ", ".join(map(str, stale)), file=sys.stderr)
            return 1
        print("content/ is up to date")
        return 0

    out.mkdir(parents=True, exist_ok=True)
    for pid, data in copies.items():
        dst = out / f"{pid}.json"
        if dst in stale:
            dst.write_bytes(data)
    if manifest_path in stale:
        manifest_path.write_text(manifest_text, encoding="utf-8")
    if stale:
        print("updated: " + ", ".join(p.name for p in stale))
    else:
        print("nothing to do; content/ is already up to date")
    for p in packages:
        print(f"  {p['id']}: v{p['contentVersion']} {p['sizeBytes']} bytes sha256={p['sha256'][:12]}...")
    return 0


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--assets", default=str(root / "app/src/main/assets/content"), help="directory with the bundled JSON files")
    parser.add_argument("--out", default=str(root / "content"), help="output directory (published via raw.githubusercontent.com)")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="public base URL the package files will be served from (HTTPS)")
    parser.add_argument("--min-app-version-code", type=int, default=0, help="lowest app versionCode that can read the packages")
    parser.add_argument("--published-at", default="", help="override the publishedAt timestamp (default: now, UTC)")
    parser.add_argument("--description", action="append", metavar="ID=TEXT", help="release note for a package (repeatable)")
    parser.add_argument("--allow-missing", action="store_true", help="skip packages whose bundled file does not exist")
    parser.add_argument("--check", action="store_true", help="do not write; exit 1 if content/ is out of date")
    args = parser.parse_args()
    if not args.base_url.startswith("https://"):
        print("error: --base-url must be HTTPS", file=sys.stderr)
        return 2
    try:
        return build(args)
    except BuildError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
