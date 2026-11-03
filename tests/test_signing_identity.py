"""Prevent accidental certificate rotation from silently breaking installed OTA."""
import pathlib
import sys
import unittest
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))
from sign_release import EXPECTED_SIGNING_CERT_SHA256, verify_signing_identity

class SigningIdentityTests(unittest.TestCase):
    def test_pinned_to_published_release(self):
        self.assertEqual(EXPECTED_SIGNING_CERT_SHA256, "6ba5b3c9cccdd4abed63e39685493312b75240e40d212b9bca44dc6708775153")
        verify_signing_identity(EXPECTED_SIGNING_CERT_SHA256, EXPECTED_SIGNING_CERT_SHA256)

    def test_changed_bundle_cannot_self_authorize(self):
        impostor="a"*64
        with self.assertRaisesRegex(ValueError, "OTA blocked"):
            verify_signing_identity(impostor, impostor)

    def test_invalid_bundle_claim_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "signing.json"):
            verify_signing_identity(EXPECTED_SIGNING_CERT_SHA256, "b"*64)

if __name__=="__main__":
    unittest.main()
