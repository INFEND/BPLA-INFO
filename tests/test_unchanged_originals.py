"""Verify unchanged licensed originals without requiring background removal.

This test deliberately leaves legacy preview statuses untouched: provenance and
model identity must be reviewed before an old record can be promoted.
"""
import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MANIFEST = ASSETS / "bundled_photos.json"
ORIGINAL = "verified_unchanged_original"


def validate_original(photo, data):
    """A verified original is byte-for-byte identical to its archived source."""
    if photo.get("processing_status") != ORIGINAL:
        return
    digest = hashlib.sha256(data).hexdigest()
    if digest != photo.get("sha256") or digest != photo.get("original_sha256"):
        raise ValueError("Verified original SHA256 mismatch")
    if not photo.get("author") or not photo.get("source") or not photo.get("license"):
        raise ValueError("Missing original attribution")
    if photo.get("background_removed") is True:
        raise ValueError("Unchanged original incorrectly marked as edited")


class UnchangedOriginalTests(unittest.TestCase):
    def test_verified_original_hashes(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        for gallery in manifest["items"].values():
            for photo in gallery["files"]:
                if photo.get("processing_status") == ORIGINAL:
                    with self.subTest(file=photo["file"]):
                        validate_original(photo, (ASSETS / photo["file"]).read_bytes())

    def test_reject_changed_bytes(self):
        data = b"original test bytes"
        photo = dict(processing_status=ORIGINAL,
                     sha256=hashlib.sha256(data).hexdigest(),
                     original_sha256=hashlib.sha256(data).hexdigest(),
                     author="Test author", source="https://example.org/original",
                     license="CC BY 4.0")
        validate_original(photo, data)
        with self.assertRaises(ValueError):
            validate_original(photo, data + b"modified")

    def test_reject_mismatched_original_digest(self):
        data = b"original test bytes"
        photo = dict(processing_status=ORIGINAL,
                     sha256=hashlib.sha256(data).hexdigest(),
                     original_sha256="0" * 64,
                     author="Test author", source="https://example.org/original",
                     license="CC BY 4.0")
        with self.assertRaises(ValueError):
            validate_original(photo, data)

    def test_legacy_previews_not_promoted_automatically(self):
        validate_original({"processing_status": "needs_background_review"}, b"legacy")


if __name__ == "__main__":
    unittest.main()
