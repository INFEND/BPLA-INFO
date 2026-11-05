"""Ensure proposal offsets skip only decodable, SHA-verified images."""
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw


class ProposalOffsetTests(unittest.TestCase):
    def test_corrupt_image_does_not_shift_next_batch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            originals = root / "originals"
            originals.mkdir()
            entries = []
            for i in range(3):
                source = originals / f"{i}.png"
                if i == 0:
                    source.write_bytes(b"not a PNG image")
                else:
                    image = Image.new("RGB", (128, 128), (80, 110, 140))
                    ImageDraw.Draw(image).ellipse((30, 30, 95, 95), fill=(225, 25, 20))
                    image.save(source)
                entries.append({
                    "archived_original": str(source),
                    "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                    "license": "CC BY 4.0", "author": "Test fixture",
                    "source_page": "https://example.invalid/fixture",
                    "model_id": f"fixture-{i}", "model_name": f"Fixture {i}",
                    "rights_evidence": "Fixture metadata, not publication approval",
                    "view": "front",
                })
            index = root / "index.json"
            index.write_text(json.dumps({"entries": entries}), encoding="utf-8")
            script = Path(__file__).resolve().parents[1] / "scripts" / "propose_unapproved_masks.py"
            for offset, expected in ((0, "fixture-1"), (1, "fixture-2")):
                output = root / f"out-{offset}"
                result = subprocess.run(
                    [sys.executable, str(script), "--index", str(index),
                     "--originals", str(originals), "--output", str(output),
                     "--offset", str(offset), "--limit", "1"],
                    capture_output=True, text=True, timeout=90,
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
                self.assertEqual(len(manifest), 1, result.stdout)
                self.assertEqual(manifest[0]["model_id"], expected)
                self.assertFalse(manifest[0]["manual_visual_review"])


if __name__ == "__main__":
    unittest.main()
