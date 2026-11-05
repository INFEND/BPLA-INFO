"""Audit the exact filtered aviation album inside debug, release and signed APK."""
import collections
import gzip
import hashlib
import json
import pathlib
import sys
import zipfile
from filter_air_catalog import ALLOWED, EXCLUDED, filter_album

if len(sys.argv) != 2:
    raise SystemExit("Usage: audit_apk_album.py FILE.apk")
apk = pathlib.Path(sys.argv[1])
original = pathlib.Path("research/source_catalogs/catalog_album_2026_original.dat").read_bytes()
expected = filter_album(original)
with zipfile.ZipFile(apk) as package:
    actual = package.read("assets/catalog_album_2026.dat")
    if actual != expected:
        raise SystemExit("FAIL: packaged aviation album differs from generated reviewed bytes")
    data = json.loads(gzip.decompress(actual))
    entries = data["entries"]
    types = collections.Counter(e["type"] for e in entries)
    if types != ALLOWED or any(e["type"] in EXCLUDED for e in entries):
        raise SystemExit("FAIL: maritime/ground profiles remain in the APK")
    if data.get("sourceArchiveSha256") != hashlib.sha256(original).hexdigest():
        raise SystemExit("FAIL: original album archive integrity mismatch")
    if data["photosImported"] != 0 or data["operationsImported"] != 0:
        raise SystemExit("FAIL: unexpected photo or operational content")
print("PASS:", apk, ": 120 aviation profiles, no marine/ground entries; SHA256", hashlib.sha256(actual).hexdigest())
