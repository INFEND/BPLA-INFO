import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("audit_apk_photos",ROOT/"scripts/audit_apk_photos.py")
audit=importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class ApkPhotoAuditTests(unittest.TestCase):
    def make_apk(self, path, manifest, extra=None):
        with zipfile.ZipFile(path,"w") as z:
            z.writestr("assets/bundled_photos.json",json.dumps(manifest))
            for name,content in (extra or {}).items():
                z.writestr(name,content)
    def test_empty_offline_manifest_is_valid(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"test.apk"
            self.make_apk(path,{"schema":2,"storage":"apk-assets-only","items":{}})
            audit.audit(path)
    def test_old_manifest_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"test.apk"
            self.make_apk(path,{"schema":2,"storage":"apk-assets-only","items":{}},
                          {"assets/photo_sources.json":"{}"})
            with self.assertRaisesRegex(ValueError,"Old photo sources"):
                audit.audit(path)
    def test_missing_photo_binary_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"test.apk"
            manifest={"schema":2,"storage":"apk-assets-only","items":{"model":{"files":[{
                "file":"photos/model_front.webp","sha256":"0"*64,
                "author":"A","license":"CC0","source":"archive"
            }]}}}
            self.make_apk(path,manifest)
            with self.assertRaisesRegex(ValueError,"Missing photo binary"):
                audit.audit(path)

if __name__=="__main__":
    unittest.main()
