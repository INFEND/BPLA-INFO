"""Regression: a corrupt first source must not consume the proposal batch limit."""
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw


class ProposalBatchLimitTests(unittest.TestCase):
    def test_skipped_sha_does_not_consume_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            originals = root / "originals"
            originals.mkdir()
            entries = []
            for index in range(2):
                path = originals / f"{index}.png"
                image = Image.new("RGB", (128, 128), (70 + index * 30, 90, 110))
                ImageDraw.Draw(image).ellipse((30, 30, 98, 98), fill=(220, 35, 25))
                image.save(path)
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                entries.append({
                    "archived_original": str(path),
                    "sha256": "0" * 64 if index == 0 else digest,
                    "license": "CC BY 4.0",
                    "author": "Test fixture",
                    "source_page": "https://example.invalid/test",
                    "model_id": f"test-{index}",
                    "model_name": f"Fixture {index}",
                    "rights_evidence": "Test fixture only, never a real aircraft",
                    "view": "front",
                })
            index_file = root / "index.json"
            index_file.write_text(json.dumps({"entries": entries}), encoding="utf-8")
            output = root / "proposals"
            script = Path(__file__).resolve().parents[1] / "scripts" / "propose_unapproved_masks.py"
            result = subprocess.run(
                [sys.executable, str(script), "--index", str(index_file),
                 "--originals", str(originals), "--output", str(output), "--limit", "1"],
                capture_output=True, text=True, timeout=90,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(len(manifest), 1)
            self.assertEqual(manifest[0]["model_id"], "test-1")
            self.assertFalse(manifest[0]["manual_visual_review"])


if __name__ == "__main__":
    unittest.main()
