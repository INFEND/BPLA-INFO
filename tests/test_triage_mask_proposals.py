import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image
from scripts.triage_mask_proposals import triage

class MaskTriageTests(unittest.TestCase):
    def test_clean_centered_candidate_never_auto_approved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mask = Image.new("L", (100, 100), 0)
            mask.paste(255, (20, 20, 80, 80))
            mask.save(root / "mask.png")
            rgba = Image.new("RGBA", (100, 100), (10, 20, 30, 0))
            rgba.putalpha(mask)
            rgba.save(root / "preview.png")
            (root / "manifest.json").write_text(json.dumps([{"original":"original.webp","sha256":"a"*64,"mask":"mask.png","preview":"preview.png"}]))
            report = triage(root)
            self.assertEqual(report["needs_manual_visual_review"], 1)
            self.assertFalse(report["entries"][0]["visual_review_approved"])
    def test_border_contact_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mask = Image.new("L", (100, 100), 0)
            mask.paste(255, (0, 20, 60, 80))
            mask.save(root / "mask.png")
            rgba = Image.new("RGBA", (100, 100), (10, 20, 30, 0))
            rgba.putalpha(mask)
            rgba.save(root / "preview.png")
            (root / "manifest.json").write_text(json.dumps([{"original":"original.webp","sha256":"a"*64,"mask":"mask.png","preview":"preview.png"}]))
            report = triage(root)
            self.assertEqual(report["rejected"], 1)
            self.assertIn("subject_touches_border", report["entries"][0]["reasons"])
    def test_fragmented_foreground_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mask = Image.new("L", (100, 100), 0)
            mask.paste(255, (20, 20, 55, 75))
            mask.paste(255, (65, 20, 90, 75))
            mask.save(root / "mask.png")
            rgba = Image.new("RGBA", (100, 100), (10, 20, 30, 0))
            rgba.putalpha(mask)
            rgba.save(root / "preview.png")
            (root / "manifest.json").write_text(json.dumps([{"original":"original.webp","sha256":"a"*64,"mask":"mask.png","preview":"preview.png"}]))
            report = triage(root)
            self.assertEqual(report["rejected"], 1)
            self.assertIn("fragmented_foreground", report["entries"][0]["reasons"])
            self.assertFalse(report["entries"][0]["visual_review_approved"])
if __name__ == "__main__":
    unittest.main()
