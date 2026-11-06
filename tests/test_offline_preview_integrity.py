"""Regression checks for the real offline preview gallery, without upgrading previews to verified cutouts."""
import hashlib
import json
import pathlib
import re
import unittest
from io import BytesIO
from PIL import Image, UnidentifiedImageError

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MANIFEST = ASSETS / "bundled_photos.json"
ALLOWED = {"CC0 1.0", "CC BY 4.0", "CC BY-SA 4.0", "Public domain (U.S. Government)",
           "Unsplash License", "Pexels License"}
SHA = re.compile(r"^[0-9a-f]{64}$")


class OfflinePreviewIntegrity(unittest.TestCase):
    def test_physical_bytes_and_provenance(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(manifest["storage"], "apk-assets-only")
        seen_paths = set()
        seen_digests = set()
        for model, gallery in manifest["items"].items():
            self.assertTrue(model)
            self.assertTrue(gallery["name"].strip())
            for photo in gallery["files"]:
                rel = photo["file"]
                self.assertTrue(rel.startswith("photos/"), rel)
                self.assertNotIn("..", pathlib.PurePosixPath(rel).parts)
                self.assertNotIn(rel, seen_paths)
                seen_paths.add(rel)
                path = (ASSETS / rel).resolve()
                self.assertTrue(path.is_relative_to(ASSETS.resolve()))
                self.assertTrue(path.is_file(), rel)
                data = path.read_bytes()
                digest = hashlib.sha256(data).hexdigest()
                self.assertRegex(photo["sha256"], SHA)
                self.assertEqual(digest, photo["sha256"], rel)
                # A matching SHA alone cannot prove that Android can decode the asset.
                # Decode all frames now and reject truncated or mislabeled files.
                try:
                    with Image.open(BytesIO(data)) as image:
                        self.assertIn(image.format, {"JPEG", "PNG", "WEBP"}, rel)
                        self.assertGreaterEqual(min(image.size), 64, rel)
                        image.load()
                        if photo["processing_status"] == "background_removed_verified":
                            self.assertIn("A", image.getbands(), rel)
                            lo, hi = image.getchannel("A").getextrema()
                            self.assertEqual((lo, hi), (0, 255), rel)
                except (OSError, UnidentifiedImageError) as exc:
                    self.fail(f"Undecodable bundled image {rel}: {exc}")
                self.assertNotIn(digest, seen_digests)
                seen_digests.add(digest)
                self.assertIn(photo["license"], ALLOWED)
                self.assertTrue(photo["author"].strip())
                self.assertTrue(photo["source"].strip())
                self.assertRegex(photo["original_sha256"], SHA)
                if photo["processing_status"] == "needs_background_review":
                    self.assertNotEqual(photo.get("visual_review_approved"), True)
                    self.assertNotEqual(photo.get("background_removed"), True)
                    self.assertNotEqual(photo.get("publication_status"), "verified")
                else:
                    self.assertIn(photo["processing_status"],
                                  {"verified_clean_original", "background_removed_verified", "verified_unmodified_original"})
                if photo["processing_status"] == "verified_unmodified_original":
                    self.assertEqual(photo["original_sha256"], digest)
                    self.assertIs(photo.get("model_review_approved"), True)
                    self.assertIs(photo.get("visual_review_approved"), True)
                    self.assertIs(photo.get("background_removed"), False)
        self.assertGreater(len(seen_paths), 0)

    def test_preview_counts_are_not_verified_cutouts(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        photos = [p for gallery in manifest["items"].values() for p in gallery["files"]]
        previews = [p for p in photos if p["processing_status"] == "needs_background_review"]
        verified = [p for p in photos if p["processing_status"] == "background_removed_verified"]
        self.assertEqual(len(photos), len(previews) + len([
            p for p in photos if p["processing_status"] != "needs_background_review"
        ]))
        self.assertFalse(set(map(id, previews)) & set(map(id, verified)))


if __name__ == "__main__":
    unittest.main()
