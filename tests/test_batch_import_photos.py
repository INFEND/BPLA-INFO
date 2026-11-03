"""Only synthetic fixtures for importer tests, never shipped as real drone photos."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("importer",ROOT/"scripts/batch_import_photos.py")
importer=importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)

class BatchPhotoImporterTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.repo=self.root/"repo"
        self.stage=self.root/"stage"
        self.stage.mkdir()
        self.assets=self.repo/"app/src/main/assets"
        self.assets.mkdir(parents=True)
        (self.assets/"catalog_lostarmour_2024_verified.json").write_text(
            json.dumps({"entries":[{"id":"test-model","name":"Test model"}]}),"utf-8")
        (self.assets/"bundled_photos.json").write_text(
            json.dumps({"schema":2,"storage":"apk-assets-only","items":{}}),"utf-8")

    def candidate(self,name="first",color=(120,80,40)):
        orig=self.stage/(name+"_original.png")
        prepared=self.stage/(name+"_prepared.webp")
        Image.new("RGBA",(640,480),(*color,255)).save(orig)
        Image.new("RGB",(640,480),color).save(prepared,format="WEBP",quality=94)
        return {
            "model_id":"test-model","model_name":"Test model","file_name":"test_"+name+".webp",
            "original":orig.name,"original_sha256":hashlib.sha256(orig.read_bytes()).hexdigest(),
            "prepared":prepared.name,"author":"Test photographer","license":"CC0 1.0",
            "source":"Local test fixture with documented origin",
            "rights_evidence":"CC0 1.0 allows redistribution and modification of this fixture",
            "processing_status":"verified_clean_original","visual_review_approved":True,
            "visual_review_notes":"Verified unique object, real model and clean original"
        }

    def batch(self,items):
        (self.stage/"batch.json").write_text(json.dumps({"photos":items}),"utf-8")

    def test_two_photos_atomic_and_dry_run(self):
        self.batch([self.candidate("one"),self.candidate("two",(200,100,20))])
        path,manifest,planned=importer.prepare(self.stage,self.repo)
        saved=path.read_bytes()
        self.assertEqual(importer.apply_batch(path,manifest,planned)["photos"],2)
        self.assertEqual(path.read_bytes(),saved)
        result=importer.apply_batch(path,manifest,planned,True)
        self.assertEqual(result["models"],["test-model"])
        self.assertEqual(len(json.loads(path.read_text())["items"]["test-model"]["files"]),2)
        for image,data,original,raw in planned:
            self.assertEqual(image.read_bytes(),data)
            self.assertEqual(original.read_bytes(),raw)
            self.assertFalse(original.is_relative_to(self.assets))

    def test_rejects_unreviewed_and_unlicensed(self):
        item=self.candidate()
        item["visual_review_approved"]=False
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"Visual review"):
            importer.prepare(self.stage,self.repo)
        item["visual_review_approved"]=True
        item["license"]="All rights reserved"
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"License"):
            importer.prepare(self.stage,self.repo)

    def test_rejects_remote_path_and_changed_original(self):
        item=self.candidate()
        item["source"]="https://example.org/image.webp"
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"Prohibited"):
            importer.prepare(self.stage,self.repo)
        item["source"]="Local test fixture with documented origin"
        item["original"]="../other.png"
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"Unsafe"):
            importer.prepare(self.stage,self.repo)
        item["original"]="first_original.png"
        item["original_sha256"]="0"*64
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"Original SHA-256"):
            importer.prepare(self.stage,self.repo)

    def test_background_removal_needs_real_alpha(self):
        item=self.candidate()
        item["processing_status"]="background_removed_verified"
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"alpha"):
            importer.prepare(self.stage,self.repo)

if __name__=="__main__":
    unittest.main()
