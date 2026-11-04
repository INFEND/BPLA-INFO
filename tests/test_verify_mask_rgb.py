import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from verify_mask_rgb import verify


class MaskRGBIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.original = root / "original.png"
        self.preview = root / "preview.png"
        Image.new("RGB", (12, 12), (30, 60, 90)).save(self.original)
        self.sha = hashlib.sha256(self.original.read_bytes()).hexdigest()

    def make_preview(self, change_rgb=False, remove_background=True):
        image = Image.open(self.original).convert("RGBA")
        if remove_background:
            image.putpixel((0, 0), (30, 60, 90, 0))
        if change_rgb:
            image.putpixel((3, 3), (31, 60, 90, 255))
        image.save(self.preview)

    def test_pixel_preserving_alpha_is_not_visual_approval(self):
        self.make_preview()
        result = verify(self.original, self.preview, self.sha)
        self.assertTrue(result["ok"])
        self.assertIn("visual_review_required", result["reason"])

    def test_single_rgb_pixel_change_is_rejected(self):
        self.make_preview(change_rgb=True)
        self.assertEqual(verify(self.original, self.preview, self.sha)["reason"],
                         "original_rgb_modified")

    def test_no_background_removed_is_rejected(self):
        self.make_preview(remove_background=False)
        self.assertEqual(verify(self.original, self.preview, self.sha)["reason"],
                         "no_background_removed")

    def test_wrong_original_sha_is_rejected(self):
        self.make_preview()
        self.assertEqual(verify(self.original, self.preview, "0" * 64)["reason"],
                         "original_sha256_mismatch")

    def test_wrong_dimensions_are_rejected(self):
        Image.new("RGBA", (13, 12), (30, 60, 90, 0)).save(self.preview)
        self.assertEqual(verify(self.original, self.preview, self.sha)["reason"],
                         "dimensions_mismatch")


if __name__ == "__main__":
    unittest.main()
