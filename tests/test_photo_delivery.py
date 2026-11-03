import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
JAVA = (ROOT / "app/src/main/java/dev/infend/bplainfo/PhotoLibrary.java").read_text("utf-8")
UI = (ROOT / "app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
MANIFEST = json.loads((ASSETS / "bundled_photos.json").read_text("utf-8"))
CATALOG = json.loads((ASSETS / "catalog_lostarmour_2024_verified.json").read_text("utf-8"))

class LocalPhotoRegressionTests(unittest.TestCase):
    def test_no_wikimedia_photo_manifest_or_network_loader(self):
        self.assertFalse((ASSETS / "photo_sources.json").exists())
        self.assertFalse((ROOT / "app/src/main/java/dev/infend/bplainfo/CommonsImageUrls.java").exists())
        self.assertFalse((ROOT / "app/src/main/java/dev/infend/bplainfo/CommonsRetryPolicy.java").exists())
        for token in ("HttpURLConnection", "CommonsImageUrls", "https://", "fallbackUrl", "new URL(", "Wikimedia", "upload.wikimedia"):
            self.assertNotIn(token, JAVA)
        self.assertNotIn("Wikimedia Commons", UI)
        self.assertNotIn("item.attributionUrl", UI)

    def test_loader_reads_and_verifies_apk_assets(self):
        for token in ("getAssets().open(", "MessageDigest.getInstance(\"SHA-256\")",
                      'purgeLegacyCache(Context context)', 'new File(app.getCacheDir(),"uav-photos")',
                      '"bundled_photos.json"', '"apk-assets-only"'):
            self.assertIn(token, JAVA)
        self.assertIn("PhotoLibrary.purgeLegacyCache(this)", UI)
        self.assertIn("Фото пока не добавлены.", UI)
        self.assertIn("savePickedPhoto(", UI)

    def test_manifest_contains_only_embedded_licensed_originals(self):
        self.assertEqual(MANIFEST["schema"],2)
        self.assertEqual(MANIFEST["storage"],"apk-assets-only")
        ids = {entry["id"]:entry["name"] for entry in CATALOG["entries"]}
        referenced=set()
        for model_id, entry in MANIFEST["items"].items():
            self.assertIn(model_id, ids)
            self.assertEqual(entry["name"],ids[model_id])
            self.assertTrue(1<=len(entry["files"])<=5)
            files=set()
            for record in entry["files"]:
                file=record["file"]
                self.assertRegex(file,r"^photos/[a-z0-9][a-z0-9_-]{2,100}\.(webp|png|jpg|jpeg)$")
                self.assertNotIn(file,files)
                files.add(file)
                self.assertTrue(record["author"] and record["license"] and record["source"])
                self.assertNotIn("url",json.dumps(record).lower())
                real=ASSETS/file
                self.assertTrue(real.is_file(),file)
                self.assertGreaterEqual(real.stat().st_size,512,file)
                self.assertLessEqual(real.stat().st_size,12_000_000,file)
                self.assertEqual(hashlib.sha256(real.read_bytes()).hexdigest(),record["sha256"],file)
                referenced.add(file)
        photos=ASSETS/"photos"
        actual={str(x.relative_to(ASSETS)).replace("\\","/") for x in photos.rglob("*") if x.is_file()} if photos.exists() else set()
        self.assertEqual(actual,referenced)

    def test_no_legacy_download_workflows_or_research_scripts(self):
        for file in (".github/workflows/photo-links.yml", ".github/workflows/photo-research.yml",
                     "scripts/research_photos.py","scripts/verify_photo_sources.py",
                     "tests/CommonsImageUrlsSmoke.java","tests/CommonsRetryPolicySmoke.java"):
            self.assertFalse((ROOT/file).exists(),file)

if __name__ == "__main__":
    unittest.main()
