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

    def test_cache_rejects_corrupt_images_before_and_after_download(self):
        self.assertIn("private static boolean validImage(File file)", JAVA)
        self.assertIn("if(output.isFile()&&!validImage(output))output.delete();", JAVA)
        self.assertIn("!validImage(temp)||!temp.renameTo(output)", JAVA)
        self.assertIn('throw new java.io.IOException("Cached photo could not be decoded")', JAVA)

    def test_fullscreen_photo_is_not_zero_height_in_wrap_content_dialog(self):
        activity = (ROOT / "app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
        viewer = activity.split("private void showDocumentaryPhoto(", 1)[1].split("private void showFullPhoto(", 1)[0]
        self.assertIn("photoHeight", viewer)
        self.assertIn("new LinearLayout.LayoutParams(-1,photoHeight)", viewer)
        self.assertNotIn("content.addView(photo,new LinearLayout.LayoutParams(-1,0,1))", viewer)

    def test_network_blocked_links_do_not_pass_verification(self):
        verifier = (ROOT / "scripts/verify_photo_sources.py").read_text("utf-8")
        self.assertIn('if blocked:', verifier)
        self.assertIn('INCONCLUSIVE:', verifier)

    def test_photo_loading_exposes_visible_failure_state(self):
        activity = (ROOT / "app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
        self.assertIn("public interface ResultCallback", JAVA)
        self.assertIn("status.complete(bitmap!=null)", JAVA)
        self.assertIn("ЗАГРУЗКА ФОТО", activity)
        self.assertIn("ФОТО НЕДОСТУПНО", activity)

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
