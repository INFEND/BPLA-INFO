"""Tests for conservative photo-source eligibility. No APK assets are created."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "propose_unapproved_masks.py"
spec = importlib.util.spec_from_file_location("propose_unapproved_masks", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

VALID = {
    "archived_original": "research/photos/originals/real.webp",
    "sha256": "a" * 64,
    "license": "CC BY 4.0",
    "author": "Photographer",
    "source_page": "https://example.org/photo",
    "model_id": "test-model",
    "model_name": "Test aircraft",
    "rights_evidence": "Publisher grants redistribution and modification",
}

class PhotoSourceEligibilityTests(unittest.TestCase):
    def test_complete_attribution(self):
        self.assertTrue(module.eligible_source(VALID))

    def test_missing_identity_and_rights(self):
        for key in ("model_id", "model_name", "rights_evidence", "author", "license"):
            with self.subTest(key=key):
                record = dict(VALID)
                record.pop(key)
                self.assertFalse(module.eligible_source(record))

    def test_unverified_or_restrictive_license(self):
        for license_name in ("unknown", "unverified", "all rights reserved", "CC BY-ND 4.0", "no derivatives"):
            with self.subTest(license_name=license_name):
                record = dict(VALID, license=license_name)
                self.assertFalse(module.eligible_source(record))

    def test_rejected_source(self):
        self.assertFalse(module.eligible_source(dict(VALID, publication_status="rejected")))

if __name__ == "__main__":
    unittest.main()
