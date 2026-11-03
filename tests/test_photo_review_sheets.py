"""Synthetic originals for review sheet tests, never genuine UAV files."""
import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image

MOD = Path(__file__).resolve().parents[1] / 'scripts/make_photo_review_sheets.py'
spec = importlib.util.spec_from_file_location('make_photo_review_sheets', MOD)
sheets = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sheets)


class ContactSheetTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        folder = self.root / 'research/photos/originals'
        folder.mkdir(parents=True)
        self.image = folder / 'example.png'
        Image.new('RGB', (700, 450), (100, 110, 130)).save(self.image)
        self.entry = {
            'model_id': 'model-001', 'model_name': 'Test model',
            'source_id': 'Fixture 1', 'sha256': hashlib.sha256(self.image.read_bytes()).hexdigest(),
            'dimensions': [700, 450], 'archived_original': 'research/photos/originals/example.png',
            'author': 'Test author', 'license': 'Test license', 'view': 'front',
            'publication_status': 'not_approved', 'visual_review_notes': 'Test only',
        }
        (self.root / 'research/photos/source_index.json').write_text(
            json.dumps({'entries': [self.entry]}), 'utf-8')
        (self.root / 'research/photos/armyinform_review.json').write_text(
            json.dumps({'entries': []}), 'utf-8')

    def test_sheets_are_review_only_and_preserve_original(self):
        before = self.image.read_bytes()
        items = sheets.load_candidates(self.root)
        result = sheets.render(items, self.root, self.root / 'output')
        self.assertEqual(result, {'models': 1, 'originals': 1, 'sheets': 1})
        self.assertEqual(self.image.read_bytes(), before)
        manifest = json.loads((self.root / 'output/index.json').read_text('utf-8'))
        self.assertEqual(manifest['models']['model-001']['count_originals'], 1)
        self.assertTrue((self.root / 'output/model-001.jpg').is_file())
        self.assertIn('Photos imported into APK by this tool: 0', (self.root / 'output/SUMMARY.txt').read_text('utf-8'))

    def test_modified_original_fails(self):
        Image.new('RGB', (700, 450), (100, 100, 100)).save(self.image)
        with self.assertRaisesRegex(ValueError, 'checksum'):
            sheets.load_candidates(self.root)

    def test_duplicate_original_fails(self):
        doc = {'entries': [self.entry]}
        (self.root / 'research/photos/armyinform_review.json').write_text(json.dumps(doc), 'utf-8')
        with self.assertRaisesRegex(ValueError, 'Repeated'):
            sheets.load_candidates(self.root)

    def test_missing_original_fails(self):
        self.image.unlink()
        with self.assertRaisesRegex(ValueError, 'Original missing'):
            sheets.load_candidates(self.root)


if __name__ == '__main__':
    unittest.main()
