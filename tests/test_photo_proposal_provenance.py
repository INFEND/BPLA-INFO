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
        }

    def test_attributed_source_is_eligible_for_proposal_only(self):
        self.assertTrue(eligible_source(self.entry))

    def test_missing_provenance_blocks_proposal(self):
        for field in ("license", "author", "source_page", "sha256", "archived_original"):
            with self.subTest(field=field):
                entry = dict(self.entry)
                entry.pop(field)
                self.assertFalse(eligible_source(entry))

    def test_blank_provenance_blocks_proposal(self):
        for field in ("license", "author", "source_page"):
            with self.subTest(field=field):
                entry = dict(self.entry)
                entry[field] = "  "
                self.assertFalse(eligible_source(entry))


if __name__ == "__main__":
    unittest.main()
