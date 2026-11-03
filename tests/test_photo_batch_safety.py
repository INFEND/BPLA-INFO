"""Failure-path tests use synthetic fixtures only; no fake UAVs enter assets."""
import hashlib
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from PIL import Image, ImageDraw
import test_batch_import_photos as fixtures

importer = fixtures.importer


class PhotoBatchSafetyTests(unittest.TestCase):
    setUp = fixtures.BatchPhotoImporterTests.setUp
    candidate = fixtures.BatchPhotoImporterTests.candidate
    batch = fixtures.BatchPhotoImporterTests.batch

    def snapshot(self):
        return {p.relative_to(self.repo).as_posix(): p.read_bytes()
                for p in self.repo.rglob("*") if p.is_file()}

    def test_same_original_cannot_be_counted_as_two_angles(self):
        first = self.candidate("one")
        second = self.candidate("two", (30, 90, 180))
        second["original"] = first["original"]
        second["original_sha256"] = first["original_sha256"]
        self.batch([first, second])
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, "Same original reused"):
            importer.prepare(self.stage, self.repo)
        self.assertEqual(self.snapshot(), before)

    def test_original_already_in_gallery_cannot_be_reused(self):
        first = self.candidate("one")
        self.batch([first])
        importer.apply_batch(*importer.prepare(self.stage, self.repo), write=True)
        second = self.candidate("two", (20, 110, 160))
        second["original"] = first["original"]
        second["original_sha256"] = first["original_sha256"]
        self.batch([second])
        with self.assertRaisesRegex(ValueError, "Same original reused"):
            importer.prepare(self.stage, self.repo)

    def test_supplied_prepared_hash_must_match_real_bytes(self):
        entry = self.candidate()
        entry["prepared_sha256"] = "0" * 64
        self.batch([entry])
        with self.assertRaisesRegex(ValueError, "Prepared SHA-256 mismatch"):
            importer.prepare(self.stage, self.repo)

    def test_cutout_cannot_touch_canvas_edge(self):
        entry = self.candidate()
        prepared = self.stage / "clipped.png"
        image = Image.new("RGBA", (640, 480), (0, 0, 0, 0))
        ImageDraw.Draw(image).rectangle((0, 90, 500, 360), fill="gray")
        image.save(prepared)
        entry.update(prepared=prepared.name, file_name="clipped_aircraft.png",
                     processing_status="background_removed_verified")
        self.batch([entry])
        with self.assertRaisesRegex(ValueError, "touches image edges"):
            importer.prepare(self.stage, self.repo)

    def test_bad_second_candidate_leaves_entire_project_unchanged(self):
        first = self.candidate("one")
        second = self.candidate("two", (20, 110, 160))
        second["model_id"] = "nonexistent-model"
        self.batch([first, second])
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, "Unknown model"):
            importer.prepare(self.stage, self.repo)
        self.assertEqual(self.snapshot(), before)

    def test_unindexed_destination_is_not_overwritten(self):
        entry = self.candidate()
        self.batch([entry])
        path = self.assets / "photos" / entry["file_name"]
        path.parent.mkdir()
        path.write_bytes(b"Existing unindexed file")
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, "Unindexed destination"):
            importer.prepare(self.stage, self.repo)
        self.assertEqual(self.snapshot(), before)

    def test_output_symlink_cannot_redirect_original_archive(self):
        entry = self.candidate()
        self.batch([entry])
        outside = self.root / "outside"
        outside.mkdir()
        (self.repo / "photo_originals").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "escapes repository"):
            importer.prepare(self.stage, self.repo)
        self.assertEqual(list(outside.iterdir()), [])

    def test_failed_replace_removes_new_files_and_restores_manifest(self):
        self.batch([self.candidate("one"), self.candidate("two", (20, 110, 160))])
        planned = importer.prepare(self.stage, self.repo)
        before = self.snapshot()
        real_replace = importer.os.replace
        attempts = 0

        def fail_third(source, target):
            nonlocal attempts
            attempts += 1
            if attempts == 3:
                raise OSError("Simulated disk failure")
            return real_replace(source, target)

        with patch.object(importer.os, "replace", side_effect=fail_third):
            with self.assertRaisesRegex(OSError, "disk failure"):
                importer.apply_batch(*planned, write=True)
        self.assertEqual(self.snapshot(), before)
        self.assertFalse(list(self.repo.rglob(".photo-import-*")))

    def test_short_staging_write_leaves_no_partial_file_or_temp_file(self):
        self.batch([self.candidate()])
        planned = importer.prepare(self.stage, self.repo)
        before = self.snapshot()
        real_temporary = importer.tempfile.NamedTemporaryFile

        class ShortWrite:
            def __init__(self, **kwargs):
                self.inner = real_temporary(**kwargs)
                self.name = self.inner.name
            def __enter__(self):
                return self
            def write(self, data):
                self.inner.write(data[:20])
                raise OSError("Simulated short write")
            def __exit__(self, *args):
                self.inner.close()

        with patch.object(importer.tempfile, "NamedTemporaryFile", ShortWrite):
            with self.assertRaisesRegex(OSError, "short write"):
                importer.apply_batch(*planned, write=True)
        self.assertEqual(self.snapshot(), before)
        self.assertFalse(list(self.repo.rglob(".photo-import-*")))


class PhotoSourceArchiveTests(unittest.TestCase):
    def test_research_sources_match_saved_bytes_and_catalog_models(self):
        root = Path(__file__).resolve().parents[1]
        index = json.loads((root / "research/photos/source_index.json").read_text("utf-8"))
        catalog = json.loads((root / "app/src/main/assets/catalog_lostarmour_2024_verified.json").read_text("utf-8"))
        models = {entry["id"]: entry["name"] for entry in catalog["entries"]}
        hashes = set()
        for entry in index["entries"]:
            with self.subTest(source=entry["source_id"]):
                path = root / entry["archived_original"]
                self.assertTrue(path.resolve().is_relative_to((root / "research/photos/originals").resolve()))
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), entry["sha256"])
                self.assertEqual(path.stem, entry["sha256"])
                self.assertNotIn(entry["sha256"], hashes)
                hashes.add(entry["sha256"])
                self.assertEqual(entry["model_name"], models[entry["model_id"]])
                self.assertIn(entry["publication_status"], ("not_approved", "approved_imported", "rejected"))
                with Image.open(path) as image:
                    image.load()
                    self.assertEqual(list(image.size), entry["dimensions"])

    def test_manifest_original_hashes_have_actual_archived_files(self):
        root = Path(__file__).resolve().parents[1]
        manifest = json.loads((root / "app/src/main/assets/bundled_photos.json").read_text("utf-8"))
        for gallery in manifest["items"].values():
            for record in gallery["files"]:
                original_hash = record.get("original_sha256")
                if original_hash:
                    paths = list((root / "research/photos/originals").glob(original_hash + ".*"))
                    paths += list((root / "photo_originals").glob("*/" + original_hash + ".*"))
                    self.assertTrue(paths, record["file"])
                    for path in paths:
                        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), original_hash)


if __name__ == "__main__":
    unittest.main()
