import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = (ROOT / "app/src/main/java/dev/infend/bplainfo/PhotoLibrary.java").read_text("utf-8")
MANIFEST = json.loads((ROOT / "app/src/main/assets/photo_sources.json").read_text("utf-8"))
CATALOG = json.loads((ROOT / "app/src/main/assets/catalog_lostarmour_2024_verified.json").read_text("utf-8"))

class PhotoDeliveryRegressionTests(unittest.TestCase):
    def test_primary_uses_public_thumbnail_cdn_with_original_fallback(self):
        helper = (ROOT / "app/src/main/java/dev/infend/bplainfo/CommonsImageUrls.java").read_text("utf-8")
        self.assertIn("https://upload.wikimedia.org/wikipedia/commons/thumb/", helper)
        self.assertIn("https://upload.wikimedia.org/wikipedia/commons/", helper)
        self.assertIn("1280px-", helper)
        self.assertIn("CommonsImageUrls.thumbnail(file)", JAVA)
        self.assertIn("CommonsImageUrls.original(file)", JAVA)
        self.assertIn("CommonsImageUrls.attribution(file)", JAVA)
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
        self.assertIn("if(output.isFile()&&!validImage(output)){", JAVA)
        self.assertIn('if(!output.delete())throw new java.io.IOException("Cannot remove corrupt photo cache entry")', JAVA)
        self.assertIn("!validImage(temp)||!temp.renameTo(output)", JAVA)
        self.assertIn('throw new java.io.IOException("Cached photo could not be decoded")', JAVA)

    def test_corrupt_photo_bounds_probe_is_guarded_against_oom(self):
        validator = JAVA.split("private static boolean validImage(File file){", 1)[1].split("public static void load(", 1)[0]
        self.assertLess(validator.index("try{"), validator.index("BitmapFactory.decodeFile(file.getAbsolutePath(),bounds)"))
        self.assertIn("catch(RuntimeException | OutOfMemoryError e)", validator)
        self.assertIn("if(decoded==null)return false;", validator)
        self.assertIn("decoded.recycle();", validator)

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
        self.assertIn("if wait>30:", verifier)
        self.assertIn("remaining not tested", verifier)

    def test_photo_loading_exposes_visible_failure_state(self):
        activity = (ROOT / "app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
        self.assertIn("public interface ResultCallback", JAVA)
        self.assertIn("status.complete(bitmap!=null)", JAVA)
        self.assertIn("ЗАГРУЗКА ФОТО", activity)
        self.assertIn("PhotoLibrary.unavailableMessage(this)", activity)

    def test_gallery_only_requests_visible_thumbnails(self):
        activity = (ROOT / "app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
        self.assertIn("lazyFrames.add(frame)", activity)
        self.assertIn("lazyLoads.add(()->{", activity)
        self.assertIn("if(requested[0])return;", activity)
        self.assertIn("strip.setOnScrollChangeListener(", activity)
        self.assertIn("strip.post(()->loadVisibleDocumentaryPhotos(", activity)
        self.assertIn("frame.getWidth()>0&&frame.getRight()>=left&&frame.getLeft()<=right", activity)
        self.assertIn("gallery.addOnLayoutChangeListener(", activity)
        self.assertIn("ОЖИДАНИЕ ПРОСМОТРА", activity)

    def test_fullscreen_requests_jump_ahead_of_queued_thumbnails(self):
        self.assertIn("PriorityBlockingQueue<Runnable>", JAVA)
        self.assertIn("maxPixels>=2000?0:1", JAVA)
        self.assertIn("new LoadTask(maxPixels,()->{", JAVA)
        self.assertIn("Long.compare(order,other.order)", JAVA)
        self.assertIn("1,1,0L,TimeUnit.MILLISECONDS", JAVA)

    def test_retry_after_and_download_size_guard(self):
        self.assertIn('connection.getHeaderField("Retry-After")', JAVA)
        self.assertIn("CommonsRetryPolicy.nextAllowedMillis(", JAVA)
        self.assertIn('getLong("cooldown_until",0L)', JAVA)
        self.assertIn('putLong("cooldown_until",nextAllowedNetworkAtMs)', JAVA)
        self.assertIn("if(System.currentTimeMillis()<nextAllowedNetworkAtMs)break;", JAVA)
        self.assertNotIn("seconds>15||attempt>0", JAVA)
        self.assertIn("connection.getContentLengthLong()", JAVA)

    def test_portrait_images_are_accepted_and_cooldown_is_visible(self):
        helper = (ROOT / "app/src/main/java/dev/infend/bplainfo/PhotoDimensions.java").read_text("utf-8")
        self.assertIn("Math.min(width,height)>=300", helper)
        self.assertIn("Math.max(width,height)>=500", helper)
        self.assertEqual(JAVA.count("PhotoDimensions.acceptable("), 2)
        self.assertIn('getLong("cooldown_until",0L)', JAVA)
        self.assertIn("public static String unavailableMessage(Context context)", JAVA)

    def test_offline_photos_use_cache_without_network_timeouts(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn("android.permission.ACCESS_NETWORK_STATE", manifest)
        self.assertIn("ConnectivityManager", JAVA)
        self.assertIn("getActiveNetwork()", JAVA)
        self.assertIn("NetworkCapabilities.NET_CAPABILITY_INTERNET", JAVA)
        self.assertIn("if(!hasNetwork(app))throw new java.io.IOException", JAVA)
        self.assertLess(JAVA.index("if(output.isFile()&&!validImage(output))"), JAVA.index("if(!hasNetwork(app))"))
        self.assertIn("НЕТ ИНТЕРНЕТА · СОХРАНЁННЫЕ ФОТО ДОСТУПНЫ", JAVA)

    def test_fullscreen_viewer_has_error_and_retry(self):
        activity = (ROOT / "app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
        viewer = activity.split("private void showDocumentaryPhoto(", 1)[1].split("private void showFullPhoto(", 1)[0]
        self.assertIn("loadStatus.setOnClickListener(v->retry.run())", viewer)
        self.assertIn("photo.setOnClickListener(v->retry.run())", viewer)
        self.assertIn("PhotoLibrary.unavailableMessage(this)", viewer)

    def test_reviewed_flyeye_images_have_attribution_and_unique_file_pages(self):
        flyeye = MANIFEST["items"]["lostarmour-pdf-2024-032"]
        self.assertEqual(4, len(flyeye["files"]))
        for photo in flyeye["files"]:
            self.assertEqual("Krzysztof Popławski", photo["author"])
            self.assertEqual("CC BY 4.0", photo["license"])
            self.assertTrue(photo["sourceUrl"].startswith("https://commons.wikimedia.org/wiki/File:"))
            self.assertNotIn("Warmate", photo["file"])
            self.assertIs(photo["binaryVerified"], False)

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
