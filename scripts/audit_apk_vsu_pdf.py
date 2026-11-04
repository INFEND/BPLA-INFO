"""Verify exact new user-PDF asset is physically embedded in built APKs."""
import hashlib
import json
import pathlib
import sys
import zipfile

if len(sys.argv)!=2:
    raise SystemExit("Usage: audit_apk_vsu_pdf.py FILE.apk")
apk=pathlib.Path(sys.argv[1])
expected=pathlib.Path("app/src/main/assets/catalog_bpla_vsu_pdf.json").read_bytes()
with zipfile.ZipFile(apk) as z:
    data=z.read("assets/catalog_bpla_vsu_pdf.json")
    if data!=expected:
        raise SystemExit("FAIL: VSU PDF asset modified or missing from APK")
    source=json.loads(data)
    if len(source["entries"])!=34 or source["frequenciesImported"]!=0 or source["photosImported"]!=0:
        raise SystemExit("FAIL: unexpected imported PDF contents")
print("PASS:",apk,"34 verified text-only profiles;",
      hashlib.sha256(data).hexdigest())
