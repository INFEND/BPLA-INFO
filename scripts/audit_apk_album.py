"""Verify exact gzipped user-album bytes inside debug, release and signed APK."""
import collections
import gzip
import hashlib
import json
import pathlib
import sys
import zipfile

if len(sys.argv)!=2:
    raise SystemExit("Usage: audit_apk_album.py FILE.apk")
apk=pathlib.Path(sys.argv[1])
asset="assets/catalog_album_2026.json.gz"
source=pathlib.Path("app/src/main/assets/catalog_album_2026.json.gz").read_bytes()
with zipfile.ZipFile(apk) as z:
    raw=z.read(asset)
    if raw!=source:
        raise SystemExit("FAIL: album byte mismatch in actual APK")
    book=json.loads(gzip.decompress(raw))
    entries=book["entries"]
    if len(entries)!=146 or book["photosImported"]!=0 or book["operationsImported"]!=0:
        raise SystemExit("FAIL: incomplete or unsafe album")
    if sum(map(lambda x:len(x["specRows"]),entries))!=714:
        raise SystemExit("FAIL: missing album characteristics")
    types=collections.Counter(e["type"] for e in entries)
    if types!={"Самолётный":103,"Коптерный":11,"Барражирующий":6,"Морской":15,"Наземный":11}:
        raise SystemExit("FAIL: missing categories")
print("PASS:",apk,": 146 album models, 714 general fields; bytes SHA256",hashlib.sha256(raw).hexdigest())
