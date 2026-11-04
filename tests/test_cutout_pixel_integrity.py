import tempfile
import unittest
from pathlib import Path
from PIL import Image
from scripts.check_cutout_pixel_integrity import inspect


class CutoutIntegrityTests(unittest.TestCase):
    def test_preserved_subject(self):
        with tempfile.TemporaryDirectory() as folder:
            original = Path(folder) / "source.png"
            cutout = Path(folder) / "cutout.png"
            Image.new("RGB", (12, 12), (40, 90, 120)).save(original)
            result = Image.new("RGBA", (12, 12), (40, 90, 120, 0))
            for y in range(3, 9):
                for x in range(3, 9):
                    result.putpixel((x, y), (40, 90, 120, 255))
            result.save(cutout)
            self.assertTrue(inspect(original, cutout)["pixel_preservation_pass"])
            result.putpixel((5, 5), (255, 0, 0, 255))
            result.save(cutout)
            self.assertFalse(inspect(original, cutout)["pixel_preservation_pass"])

    def test_partial_alpha_edges_must_preserve_original_rgb(self):
        with tempfile.TemporaryDirectory() as folder:
            original = Path(folder) / "source.png"
            cutout = Path(folder) / "cutout.png"
            Image.new("RGB", (12, 12), (40, 90, 120)).save(original)
            result = Image.new("RGBA", (12, 12), (40, 90, 120, 0))
            for y in range(3, 9):
                for x in range(3, 9):
                    result.putpixel((x, y), (40, 90, 120, 255))
            result.putpixel((3, 4), (40, 90, 120, 128))
            result.save(cutout)
            self.assertTrue(inspect(original, cutout)["pixel_preservation_pass"])
            result.putpixel((3, 4), (255, 0, 0, 128))
            result.save(cutout)
            review = inspect(original, cutout)
            self.assertEqual(review["changed_partial_alpha_pixels"], 1)
            self.assertFalse(review["pixel_preservation_pass"])

    def test_border_contact(self):
        with tempfile.TemporaryDirectory() as folder:
            original = Path(folder) / "source.png"
            cutout = Path(folder) / "cutout.png"
            Image.new("RGB", (12, 12), (1, 2, 3)).save(original)
            image = Image.new("RGBA", (12, 12), (1, 2, 3, 0))
            image.putpixel((0, 4), (1, 2, 3, 255))
            image.save(cutout)
            self.assertTrue(inspect(original, cutout)["touches_border"])


if __name__ == "__main__":
    unittest.main()
