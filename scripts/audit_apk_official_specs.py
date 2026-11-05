"""Verify exact offline official facts in compiled APK and provenance invariants."""
import hashlib
import json
import pathlib
import sys
import zipfile
from urllib.parse import urlsplit

ASSET = "assets/official_specs_2026.json"
SOURCE = pathlib.Path("app/src/main/assets/official_specs_2026.json")
EXPECTED_IDS = {"lostarmour-pdf-2024-006", "bulletin30-2025-p036l"}
FORBIDDEN_KEYS = {"photo", "photos", "image", "images", "imageurl", "photourl",
                  "downloadurl", "latitude", "longitude", "coordinates"}


def validate(data):
    if data.get("schema") != 1 or not isinstance(data.get("entries"), list):
        raise ValueError("Unsupported official facts schema")
    records = data["entries"]
    if len(records) != len(EXPECTED_IDS):
        raise ValueError("Unexpected verified fact count")
    ids = []
    allowed_fields = {"modelId", "modelName", "factName", "factValue", "sourceLabel", "sourceUrl", "status"}
    for record in records:
        if not isinstance(record, dict):
            raise ValueError("Malformed official fact")
        if any(not isinstance(k, str) for k in record):
            raise ValueError("Non-text official fact field name")
        if set(record) - allowed_fields:
            raise ValueError("Unexpected official fact field")
        if FORBIDDEN_KEYS.intersection(k.lower() for k in record):
            raise ValueError("Photo/network or operational field in text-only facts")
        for key in ("modelId", "modelName", "factName", "factValue",
                    "sourceLabel", "sourceUrl", "status"):
            if not isinstance(record.get(key), str) or not record[key].strip():
                raise ValueError("Missing fact provenance: " + key)
        url = urlsplit(record["sourceUrl"])
        if url.scheme != "https" or not url.hostname or url.username or url.password or url.fragment or url.port not in (None, 443):
            raise ValueError("Invalid official HTTPS source")
        if any(c in record["factValue"] for c in ("<script", "javascript:")):
            raise ValueError("Unsafe official fact text")
        ids.append(record["modelId"])
    if len(ids) != len(set(ids)) or set(ids) != EXPECTED_IDS:
        raise ValueError("Unexpected or duplicate official fact IDs")


def main(apk):
    expected = SOURCE.read_bytes()
    validate(json.loads(expected))
    with zipfile.ZipFile(apk) as archive:
        if archive.namelist().count(ASSET) != 1:
            raise ValueError("Missing or duplicate official fact asset")
        actual = archive.read(ASSET)
    if actual != expected:
        raise ValueError("Official source facts missing or altered in APK")
    validate(json.loads(actual))
    print("PASS:", apk, len(EXPECTED_IDS), "verified offline text facts",
          hashlib.sha256(actual).hexdigest())


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: audit_apk_official_specs.py FILE.apk")
    try:
        main(pathlib.Path(sys.argv[1]))
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as exc:
        raise SystemExit("FAIL: " + str(exc)) from exc
