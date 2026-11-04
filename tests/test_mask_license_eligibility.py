"""Regression checks for licence restrictions and conflicting provenance."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "propose_unapproved_masks.py"
spec = importlib.util.spec_from_file_location("propose_unapproved_masks", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

BASE = {
    "archived_original": "research/photos/originals/a.webp",
    "sha256": "a" * 64,
    "license": "CC BY 4.0",
    "author": "Example photographer",
    "source_page": "https://example.org/photograph",
    "model_id": "model-1",
    "model_name": "Example",
    "rights_evidence": "Author published this image under CC BY 4.0",
}

class LicenceEligibilityTests(unittest.TestCase):
    def test_cc_by_allowed(self):
        self.assertTrue(module.eligible_source(dict(BASE)))

    def test_nd_variants_rejected(self):
        for license_name in ("CC BY-ND 4.0", "CC BY-NC-ND 4.0",
                             "cc-by-nd-3.0", "CC BY-ND",
                             "https://creativecommons.org/licenses/by-nd/4.0/"):
            with self.subTest(license=license_name):
                self.assertFalse(module.eligible_source({**BASE, "license": license_name}))

    def test_missing_rights_evidence_rejected(self):
        self.assertFalse(module.eligible_source({**BASE, "rights_evidence": ""}))

if __name__ == "__main__":
    unittest.main()
