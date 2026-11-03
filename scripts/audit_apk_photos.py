#!/usr/bin/env python3
"""Verify physical APK photo contents, not just manifest source text."""
import hashlib
import json
import re
import sys
import zipfile

PHOTO_PATTERN = re.compile(r"photos/[a-z0-9][a-z0-9_-]{2,100}\\.(?:webp|png|jpg|jpeg)\\Z")
LEGACY = ("assets/photo_sources.json",)
MAX_SIZE = 12_000_000

def audit(path):
    with zipfile.ZipFile(path) as apk:
        names = set(apk.namelist())
        for forbidden in LEGACY:
            if forbidden in names:
                raise ValueError("Old photo sources still embedded: " + forbidden)
        raw = apk.read("assets/bundled_photos.json")
        manifest = json.loads(raw)
        if manifest.get("schema") != 2 or manifest.get("storage") != "apk-assets-only":
            raise ValueError("APK is missing offline-only photo manifest")
        referenced = set()
        for model_id, model in manifest["items"].items():
            photos = model["files"]
            if not 1 <= len(photos) <= 5:
                raise ValueError("Invalid photo count: " + model_id)
            for record in photos:
                file = record["file"]
                if not PHOTO_PATTERN.fullmatch(file):
                    raise ValueError("Invalid embedded photo path: " + file)
                if file in referenced:
                    raise ValueError("Duplicate embedded photo: " + file)
                referenced.add(file)
                if not all(record.get(key) for key in ("author", "license", "source", "sha256")):
                    raise ValueError("Missing photo credits: " + file)
                asset = "assets/" + file
                if asset not in names:
                    raise ValueError("Missing photo binary in APK: " + asset)
                binary = apk.read(asset)
                if not 512 <= len(binary) <= MAX_SIZE:
                    raise ValueError("Invalid photo size: " + asset)
                if hashlib.sha256(binary).hexdigest() != record["sha256"]:
                    raise ValueError("Photo checksum mismatch: " + asset)
        actual = {name.removeprefix("assets/") for name in names
                  if name.startswith("assets/photos/") and not name.endswith("/")}
        if actual != referenced:
            raise ValueError("Unindexed photo binaries: " + repr(actual - referenced))
        print(f"PASS: {path}: {len(manifest['items'])} model galleries, {len(referenced)} physical photos, no legacy photo manifest")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: audit_apk_photos.py APK")
    try:
        audit(sys.argv[1])
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        raise SystemExit("FAIL: " + str(error))
