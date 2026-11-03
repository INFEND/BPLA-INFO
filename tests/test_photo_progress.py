"""Completion is not awarded to photos that are unreviewed or duplicate."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("photo_progress", ROOT / "scripts/photo_progress.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ProgressTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.assets = self.root / "app/src/main/assets"
        (self.assets / "photos").mkdir(parents=True)
        catalog = {"entries": [{"id": "model-one", "name": "Model One"},
                               {"id": "model-two", "name": "Model Two"}]}
        (self.assets / "catalog_lostarmour_2024_verified.json").write_text(json.dumps(catalog))
        self.manifest = {"items": {"model-one": {"name": "Model One", "files": []}}}
        self.save()

    def save(self):
        (self.assets / "bundled_photos.json").write_text(json.dumps(self.manifest))

    def add(self, name, state="background_removed_verified", reviewed=True, angle=True, angle_id=None):
        data = ("unique-" + name).encode()
        (self.assets / "photos" / name).write_bytes(data)
        self.manifest["items"]["model-one"]["files"].append({
            "file": "photos/" + name, "sha256": hashlib.sha256(data).hexdigest(),
            "processing_status": state, "visual_review_approved": reviewed,
            "angle_review_approved": angle,
            "angle_id": angle_id if angle_id is not None else name
        })
        self.save()

    def test_empty_requires_six(self):
        r = module.audit(self.root)
        self.assertEqual((r["models_total"], r["minimum_verified_photos_required"],
                          r["minimum_additional_verified_photos"]), (2, 6, 6))

    def test_three_reviewed_distinct_complete_one_model(self):
        for i in range(3):
            self.add("photo%d.webp" % i)
        r = module.audit(self.root)
        self.assertEqual(r["models_complete"], 1)
        self.assertEqual(r["minimum_additional_verified_photos"], 3)

    def test_unreviewed_never_counts(self):
        self.add("a.webp", state="needs_background_review")
        self.add("b.webp", reviewed=False)
        self.add("c.webp", angle=False)
        r = module.audit(self.root)
        self.assertEqual(r["photos_embedded"], 3)
        self.assertEqual(r["photos_verified_processed_distinct"], 0)
        self.assertEqual(r["minimum_additional_verified_photos"], 6)

    def test_repeated_view_does_not_complete_gallery(self):
        self.add("a.webp", angle_id="front")
        self.add("b.webp", angle_id="front")
        self.add("c.webp", angle_id="side")
        report = module.audit(self.root)
        self.assertEqual(report["models_complete"], 0)
        self.assertEqual(report["photos_verified_processed_distinct"], 2)
        self.assertEqual(report["minimum_additional_verified_photos"], 4)

    def test_missing_angle_id_not_counted(self):
        self.add("a.webp")
        self.manifest["items"]["model-one"]["files"][0].pop("angle_id")
        self.save()
        report = module.audit(self.root)
        self.assertEqual(report["photos_verified_processed_distinct"], 0)

    def test_changed_bytes_fail(self):
        self.add("a.webp")
        (self.assets / "photos/a.webp").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "SHA mismatch"):
            module.audit(self.root)

    def test_duplicate_bytes_fail(self):
        self.add("a.webp")
        self.add("b.webp")
        (self.assets / "photos/b.webp").write_bytes((self.assets / "photos/a.webp").read_bytes())
        self.manifest["items"]["model-one"]["files"][1]["sha256"] = self.manifest["items"]["model-one"]["files"][0]["sha256"]
        self.save()
        with self.assertRaisesRegex(ValueError, "Repeated APK bytes"):
            module.audit(self.root)

if __name__ == "__main__":
    unittest.main()
