import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from propose_unapproved_masks import eligible_source


class PhotoProposalProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.entry = {
            "archived_original": "research/photos/originals/abc.webp",
            "sha256": "a" * 64,
            "license": "CC BY 4.0",
            "author": "Example photographer",
            "source_page": "https://example.org/photo",
            "model_id": "example-drone",
            "model_name": "Example drone",
            "rights_evidence": "https://example.org/photo#license",
        }

    def test_attributed_source_is_eligible_for_proposal_only(self):
        self.assertTrue(eligible_source(self.entry))

    def test_unknown_or_restrictive_license_blocks_proposal(self):
        for license_name in ("Custom free license", "CC BY-ND 4.0", "CC BY-NC 4.0",
                             "All rights reserved", "Public domain"):
            with self.subTest(license=license_name):
                entry = dict(self.entry, license=license_name)
                self.assertFalse(eligible_source(entry))

    def test_invalid_sha_or_insecure_source_blocks_proposal(self):
        for digest in ("a" * 63, "not-a-sha", "g" * 64):
            with self.subTest(sha=digest):
                self.assertFalse(eligible_source(dict(self.entry, sha256=digest)))
        self.assertFalse(eligible_source(dict(self.entry, source_page="http://example.org/photo")))

    def test_known_government_source_can_enter_unapproved_queue(self):
        entry = dict(self.entry, license="Public domain (U.S. Government), as marked on the official DVIDS photo page")
        self.assertTrue(eligible_source(entry))

    def test_missing_provenance_blocks_proposal(self):
        for field in ("license", "author", "source_page", "sha256", "archived_original", "model_id", "model_name", "rights_evidence"):
            with self.subTest(field=field):
                entry = dict(self.entry)
                entry.pop(field)
                self.assertFalse(eligible_source(entry))

    def test_blank_provenance_blocks_proposal(self):
        for field in ("license", "author", "source_page", "model_id", "model_name", "rights_evidence"):
            with self.subTest(field=field):
                entry = dict(self.entry)
                entry[field] = "  "
                self.assertFalse(eligible_source(entry))


if __name__ == "__main__":
    unittest.main()
