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
            "visual_review_notes":"Verified unique object, real model and clean original",
            "angle_id":name,"angle_review_approved":True
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

    def test_requires_explicit_reviewed_angle(self):
        item=self.candidate()
        item.pop("angle_id")
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"angle_id"):
            importer.prepare(self.stage,self.repo)
        item["angle_id"]="front"
        item["angle_review_approved"]=False
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"angle visual review"):
            importer.prepare(self.stage,self.repo)

    def test_duplicate_angles_in_same_batch_rejected(self):
        first=self.candidate("one")
        second=self.candidate("two",(200,100,20))
        second["angle_id"]=first["angle_id"]
        self.batch([first,second])
        with self.assertRaisesRegex(ValueError,"Duplicate reviewed angle"):
            importer.prepare(self.stage,self.repo)

    def test_imported_angles_count_in_completion_audit(self):
        first=self.candidate("one")
        first["processing_status"]="background_removed_verified"
        # Synthetic alpha cutout only: this is not a real aircraft photo.
        prepared=self.stage/first["prepared"]
        im=Image.new("RGBA",(640,480),(0,0,0,0))
        from PIL import ImageDraw
        ImageDraw.Draw(im).rectangle((70,60,570,420),fill=(100,100,100,255))
        # Add deterministic synthetic pixel variation so the lossless WebP fixture
        # exceeds the importer\'s 512-byte minimum (a flat rectangle does not).
        # This is only a unit-test fixture, never a published UAV photograph.
        for y in range(75, 415, 11):
            for x in range(75, 565, 11):
                im.putpixel((x, y), ((x * 7) % 256, (y * 11) % 256, 130, 255))
        im.save(prepared,format="WEBP",lossless=True)
        self.batch([first])
        path,manifest,pending=importer.prepare(self.stage,self.repo)
        importer.apply_batch(path,manifest,pending,True)
        record=json.loads(path.read_text())["items"]["test-model"]["files"][0]
        self.assertEqual(record["angle_id"],"one")
        self.assertTrue(record["angle_review_approved"])
        self.assertTrue(record["visual_review_approved"])

    def test_background_removal_needs_real_alpha(self):
        item=self.candidate()
        item["processing_status"]="background_removed_verified"
        self.batch([item])
        with self.assertRaisesRegex(ValueError,"alpha"):
            importer.prepare(self.stage,self.repo)

    def original_candidate(self):
        item=self.candidate()
        item.update(prepared=item['original'],file_name='unchanged_original.png',
                    processing_status='verified_unmodified_original',
                    model_review_approved=True,
                    model_review_notes='Source caption and actual photograph identify the exact catalog model.',
                    rights_evidence='CC0 permits redistribution of this original source fixture without changes.')
        return item

    def test_unmodified_original_accepts_source_bytes_without_mask_or_edit_license(self):
        item=self.original_candidate()
        self.batch([item])
        path,manifest,pending=importer.prepare(self.stage,self.repo)
        importer.apply_batch(path,manifest,pending,True)
        photo=manifest['items']['test-model']['files'][0]
        self.assertEqual((self.assets/photo['file']).read_bytes(),(self.stage/item['original']).read_bytes())
        self.assertEqual(photo['sha256'],photo['original_sha256'])
        self.assertFalse(photo['background_removed'])
        self.assertTrue(photo['model_review_approved'])

    def test_unmodified_original_rejects_reencoded_photo(self):
        item=self.original_candidate()
        item.update(prepared='first_prepared.webp',file_name='modified_original.webp')
        self.batch([item])
        with self.assertRaisesRegex(ValueError,'Unmodified original bytes changed'):
            importer.prepare(self.stage,self.repo)

    def test_unmodified_original_requires_positive_model_identification(self):
        item=self.original_candidate()
        item.pop('model_review_approved')
        self.batch([item])
        with self.assertRaisesRegex(ValueError,'Exact model identity not approved'):
            importer.prepare(self.stage,self.repo)

    def test_original_import_into_new_bulletin_model(self):
        (self.assets/'bulletin30_2025.json').write_text(json.dumps({'entries':[
            {'id':'bulletin-model','name':'Bulletin model'}]}))
        item=self.original_candidate()
        item.update(model_id='bulletin-model',model_name='Bulletin model')
        self.batch([item])
        _,manifest,_=importer.prepare(self.stage,self.repo)
        self.assertEqual(len(manifest['items']['bulletin-model']['files']),1)

    def test_original_import_into_aircraft_album_only_model(self):
        import gzip
        archive=self.repo/'research/source_catalogs/catalog_album_2026_original.dat'
        archive.parent.mkdir(parents=True)
        archive.write_bytes(gzip.compress(json.dumps({'entries':[
            {'id':'album-aircraft','name':'Album aircraft','type':'Самолётный','page':20},
            {'id':'album-ground','name':'Album ground','type':'Наземный','page':21}
        ]}).encode()))
        item=self.original_candidate()
        item.update(model_id='album-aircraft',model_name='Album aircraft')
        self.batch([item])
        _,manifest,_=importer.prepare(self.stage,self.repo)
        self.assertEqual(len(manifest['items']['album-aircraft']['files']),1)
        item.update(model_id='album-ground',model_name='Album ground')
        self.batch([item])
        with self.assertRaisesRegex(ValueError,'Unknown model'):
            importer.prepare(self.stage,self.repo)

if __name__=="__main__":
    unittest.main()
