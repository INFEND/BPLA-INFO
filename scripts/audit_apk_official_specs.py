"""Physically verify source-verified facts in every actual compiled APK."""
import hashlib
import json
import pathlib
import sys
import zipfile

if len(sys.argv)!=2:
    raise SystemExit("Usage: audit_apk_official_specs.py FILE.apk")
apk=pathlib.Path(sys.argv[1])
path="assets/official_specs_2026.json"
expected=pathlib.Path("app/src/main/assets/official_specs_2026.json").read_bytes()
with zipfile.ZipFile(apk) as z:
    actual=z.read(path)
    if actual!=expected:
        raise SystemExit("FAIL: official sources missing or altered in APK")
    data=json.loads(actual)
    if len(data.get("entries",[]))!=2:
        raise SystemExit("FAIL: unexpected verified fact count")
    ids={record["modelId"] for record in data["entries"]}
    if ids!={"lostarmour-pdf-2024-006","bulletin30-2025-p036l"}:
        raise SystemExit("FAIL: official source model IDs invalid")
print("PASS:",apk,"2 embedded source-verified text facts",
      hashlib.sha256(actual).hexdigest())
