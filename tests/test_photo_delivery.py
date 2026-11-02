import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = (ROOT / "app/src/main/java/dev/infend/bplainfo/PhotoLibrary.java").read_text("utf-8")
MANIFEST = json.loads((ROOT / "app/src/main/assets/photo_sources.json").read_text("utf-8"))
CATALOG = json.loads((ROOT / "app/src/main/assets/catalog_lostarmour_2024_verified.json").read_text("utf-8"))

class PhotoDeliveryRegressionTests(unittest.TestCase):
    def test_primary_uses_documented_mediawiki_redirect_for_thumbnails(self):
        self.assertIn("https://commons.wikimedia.org/wiki/Special:Redirect/file/", JAVA)
        self.assertIn("?width=1600", JAVA)
        self.assertIn("photo.fallbackUrl", JAVA)
        self.assertIn('optString("license","")', JAVA)
        self.assertIn('optString("author","")', JAVA)

    def test_cache_and_protective_limits_remain(self):
        self.assertIn('new File(app.getCacheDir(),"uav-photos")', JAVA)
        self.assertIn('15_000_000L', JAVA)
        self.assertIn('trim(folder)', JAVA)
        self.assertIn('photo.file.equals(image.getTag())', JAVA)

    def test_photo_records_have_real_catalog_ids_and_no_duplicate_files(self):
        ids = {row["id"] for row in CATALOG["entries"]}
        self.assertEqual(len(ids), len(CATALOG["entries"]))
        for model_id, record in MANIFEST["items"].items():
            self.assertIn(model_id, ids)
            filenames = [image["file"] for image in record["files"]]
            self.assertTrue(1 <= len(filenames) <= 5)
            self.assertEqual(len(filenames), len(set(filenames)))

if __name__ == "__main__":
    unittest.main()
