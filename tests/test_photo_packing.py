"""Verify build-time WebP compression on synthetic, non-catalog photographs."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from io import BytesIO

import numpy as np
from PIL import Image
from scripts.optimize_apk_photos import expected_dimensions, pack, fidelity, optimize


class PhotoPackingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / "app/src/main/assets"
        (self.assets / "photos").mkdir(parents=True)
        (self.root / "photo_originals").mkdir()
        # Test-only gradient with texture, not a UAV image.
        yy, xx = np.mgrid[:780, :1100]
        rng = np.random.default_rng(101)
        noise = rng.integers(-4, 5, (780, 1100), dtype=np.int16)
        pixels = np.stack((
            np.broadcast_to((xx * 160 // 1100).astype(np.int16), noise.shape) + 30 + noise,
            np.broadcast_to((yy * 145 // 780).astype(np.int16), noise.shape) + 32 + noise,
            np.broadcast_to(((xx + yy) * 125 // 1880).astype(np.int16), noise.shape) + 55 + noise
        ), axis=2).clip(0, 255).astype(np.uint8)
        image = Image.fromarray(pixels, "RGB")
        raw = BytesIO()
        image.save(raw, format="PNG")
        self.original = raw.getvalue()
        self.original_file = self.assets / "photos/test_original.png"
        self.original_file.write_bytes(self.original)
        self.archive_file = self.root / "photo_originals/test_original.png"
        self.archive_file.write_bytes(self.original)
        digest = hashlib.sha256(self.original).hexdigest()
        self.photo = {
            "file": "photos/test_original.png", "sha256": digest,
            "original_sha256": digest,
            "original_archive": "photo_originals/test_original.png",
            "processing_status": "verified_unmodified_original",
            "model_review_approved": True, "visual_review_approved": True,
            "background_removed": False,
            "author": "synthetic test", "source": "generated solely for tests",
            "license": "CC0 1.0"
        }
        self.manifest = {
            "schema": 2, "storage": "apk-assets-only",
            "items": {"synthetic-test": {"name": "Synthetic test fixture", "files": [self.photo]}}
        }
        self.save_manifest()

    def save_manifest(self):
        (self.assets / "bundled_photos.json").write_text(
            json.dumps(self.manifest), encoding="utf-8")

    def test_optimizer_preserves_archived_original_and_updates_manifest(self):
        result = pack(root=self.root, assets=self.assets)
        self.assertEqual(result["photos"], 1)
        self.assertEqual(result["optimized"], 1)
        self.assertGreater(result["saved_percent"], 8)
        self.assertEqual(self.archive_file.read_bytes(), self.original)
        photo = json.loads((self.assets / "bundled_photos.json").read_text())["items"]["synthetic-test"]["files"][0]
        self.assertEqual(photo["processing_status"], "optimized_from_verified_original")
        self.assertEqual(photo["original_sha256"], hashlib.sha256(self.original).hexdigest())
        self.assertFalse(photo["background_removed"])
        self.assertTrue(photo["file"].endswith(".webp"))
        self.assertFalse(self.original_file.exists())
        packed = (self.assets / photo["file"]).read_bytes()
        self.assertEqual(hashlib.sha256(packed).hexdigest(), photo["sha256"])
        self.assertGreaterEqual(photo["optimization"]["ssim_luma"], 0.974)
        self.assertGreaterEqual(photo["optimization"]["psnr_luma_db"], 33.5)
        with Image.open(BytesIO(packed)) as image:
            self.assertEqual(image.format, "WEBP")
            image.load()

    def test_corrupt_original_sha_fails_without_mutation(self):
        self.photo["original_sha256"] = "0" * 64
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "Unverified or changed"):
            pack(root=self.root, assets=self.assets)
        self.assertEqual(self.original_file.read_bytes(), self.original)
        self.assertEqual(self.archive_file.read_bytes(), self.original)
        self.assertEqual(list((self.assets / "photos").glob("*.webp")), [])

    def test_resizing_does_not_destroy_short_edge(self):
        self.assertEqual(expected_dimensions(6000, 1000), (2560, 427))
        self.assertEqual(expected_dimensions(1600, 1000), (1600, 1000))
        self.assertGreaterEqual(min(expected_dimensions(6000, 400)), 300)


if __name__ == "__main__":
    unittest.main()
