"""Regression tests for pixel-exact manual-mask photo preparation."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageChops

MODULE = Path(__file__).resolve().parents[1] / "scripts/prepare_pixel_exact_cutout.py"
spec = importlib.util.spec_from_file_location("pixel_cutout", MODULE)
cutout = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cutout)

class PixelExactCutoutTests(unittest.TestCase):
    def test_pixel_preservation_and_no_approval(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src, mask, out = root/"src.png", root/"mask.png", root/"result.png"
            Image.new("RGB", (30, 30), (12, 90, 210)).save(src)
            m = Image.new("L", (30, 30), 0)
            m.putpixel((15, 15), 255)
            m.save(mask)
            result = cutout.prepare(src, mask, out)
            self.assertFalse(result["visual_review_approved"])
            self.assertFalse(result["apk_imported"])
            with Image.open(out) as im:
                self.assertEqual(im.mode, "RGBA")
                self.assertEqual(im.getpixel((15, 15)), (12, 90, 210, 255))
                self.assertEqual(im.getpixel((0, 0)), (12, 90, 210, 0))
            self.assertTrue(out.with_suffix(".review.json").exists())

    def test_reject_mask_dimension_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src, mask, out = root/"src.png", root/"mask.png", root/"result.png"
            Image.new("RGB", (30, 30)).save(src)
            Image.new("L", (20, 20)).save(mask)
            with self.assertRaises(ValueError):
                cutout.prepare(src, mask, out)

    def test_reject_overwriting_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src, mask = root/"src.png", root/"mask.png"
            Image.new("RGB", (30, 30)).save(src)
            Image.new("L", (30, 30), 255).save(mask)
            with self.assertRaises(ValueError):
                cutout.prepare(src, mask, src)

if __name__ == "__main__":
    unittest.main()
