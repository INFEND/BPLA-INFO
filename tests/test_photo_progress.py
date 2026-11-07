"""Completion is not awarded to photos that are unreviewed or duplicate."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("photo_progress", ROOT / "scripts/photo_progress.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ProgressTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.assets = self.root / "app/src/main/assets"
        (self.assets / "photos").mkdir(parents=True)
        catalog = {"entries": [{"id": "model-one", "name": "Model One"},
                               {"id": "model-two", "name": "Model Two"}]}
        (self.assets / "catalog_lostarmour_2024_verified.json").write_text(json.dumps(catalog))
        self.manifest = {"items": {"model-one": {"name": "Model One", "files": []}}}
        self.save()

    def save(self):
        (self.assets / "bundled_photos.json").write_text(json.dumps(self.manifest))

    def add(self, name, state="background_removed_verified", reviewed=True, angle=True, angle_id=None):
        data = ("unique-" + name).encode()
        (self.assets / "photos" / name).write_bytes(data)
        self.manifest["items"]["model-one"]["files"].append({
            "file": "photos/" + name, "sha256": hashlib.sha256(data).hexdigest(),
            "processing_status": state, "visual_review_approved": reviewed,
            "angle_review_approved": angle,
            "angle_id": angle_id if angle_id is not None else name
        })
        self.save()

    def test_bulletin_expands_catalog_without_fusing_existing_name(self):
        bulletin={"schema":1,"entries":[
            {"id":"bulletin-one","name":"Model Three","type":"Коптерный"},
            {"id":"bulletin-two","name":"model one","type":"Самолётный"}
        ]}
        (self.assets/"bulletin30_2025.json").write_text(json.dumps(bulletin))
        r=module.audit(self.root)
        self.assertEqual(r["models_total"],3)
        self.assertEqual(r["minimum_verified_photos_required"],9)
        self.assertEqual(r["models_complete"],0)

    def test_bulletin_different_category_does_not_auto_merge(self):
        base=json.loads((self.assets/"catalog_lostarmour_2024_verified.json").read_text())
        base["entries"][0]["type"]="Самолётный"
        (self.assets/"catalog_lostarmour_2024_verified.json").write_text(json.dumps(base))
        bulletin={"schema":1,"entries":[{"id":"bulletin-same-name","name":"model one","type":"Коптерный"}]}
        (self.assets/"bulletin30_2025.json").write_text(json.dumps(bulletin))
        self.assertEqual(module.audit(self.root)["models_total"],3)

    def test_empty_requires_six(self):
        r = module.audit(self.root)
        self.assertEqual((r["models_total"], r["minimum_verified_photos_required"],
                          r["minimum_additional_verified_photos"]), (2, 6, 6))

    def test_three_reviewed_distinct_complete_one_model(self):
        for i in range(3):
            self.add("photo%d.webp" % i)
        r = module.audit(self.root)
        self.assertEqual(r["models_complete"], 1)
        self.assertEqual(r["minimum_additional_verified_photos"], 3)

    def test_unreviewed_never_counts(self):
        self.add("a.webp", state="needs_background_review")
        self.add("b.webp", reviewed=False)
        self.add("c.webp", angle=False)
        r = module.audit(self.root)
        self.assertEqual(r["photos_embedded"], 3)
        self.assertEqual(r["photos_verified_processed_distinct"], 0)
        self.assertEqual(r["minimum_additional_verified_photos"], 6)

    def test_repeated_view_does_not_complete_gallery(self):
        self.add("a.webp", angle_id="front")
        self.add("b.webp", angle_id="front")
        self.add("c.webp", angle_id="side")
        report = module.audit(self.root)
        self.assertEqual(report["models_complete"], 0)
        self.assertEqual(report["photos_verified_processed_distinct"], 2)
        self.assertEqual(report["minimum_additional_verified_photos"], 4)

    def test_missing_angle_id_not_counted(self):
        self.add("a.webp")
        self.manifest["items"]["model-one"]["files"][0].pop("angle_id")
        self.save()
        report = module.audit(self.root)
        self.assertEqual(report["photos_verified_processed_distinct"], 0)

    def test_changed_bytes_fail(self):
        self.add("a.webp")
        (self.assets / "photos/a.webp").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "SHA mismatch"):
            module.audit(self.root)

    def test_duplicate_bytes_fail(self):
        self.add("a.webp")
        self.add("b.webp")
        (self.assets / "photos/b.webp").write_bytes((self.assets / "photos/a.webp").read_bytes())
        self.manifest["items"]["model-one"]["files"][1]["sha256"] = self.manifest["items"]["model-one"]["files"][0]["sha256"]
        self.save()
        with self.assertRaisesRegex(ValueError, "Repeated APK bytes"):
            module.audit(self.root)

    def test_three_verified_originals_complete_without_background_removal(self):
        for i in range(3):
            self.add('original%d.jpg'%i,state='verified_unmodified_original')
        for photo in self.manifest['items']['model-one']['files']:
            photo.update(original_sha256=photo['sha256'],model_review_approved=True,
                         background_removed=False)
        self.save()
        self.assertEqual(module.audit(self.root)['models_complete'],1)

    def test_original_counter_excludes_processed_images(self):
        self.add('processed.jpg', state='background_removed_verified')
        self.add('original.jpg', state='verified_unmodified_original')
        original = self.manifest['items']['model-one']['files'][1]
        original.update(original_sha256=original['sha256'], model_review_approved=True)
        self.save()
        report = module.audit(self.root)
        self.assertEqual(report['photos_verified_processed_distinct'], 1)
        self.assertEqual(report['photos_verified_distinct_originals'], 1)

    def test_distinct_original_and_background_removed_counters(self):
        self.add('processed.jpg', state='background_removed_verified')
        self.add('original.jpg', state='verified_unmodified_original')
        original = self.manifest['items']['model-one']['files'][1]
        original.update(original_sha256=original['sha256'], model_review_approved=True)
        self.save()
        report = module.audit(self.root)
        self.assertEqual(report['photos_verified_distinct_total'], 2)
        self.assertEqual(report['photos_verified_distinct_originals'], 1)
        self.assertEqual(report['photos_verified_distinct_background_removed'], 1)
        row = next(r for r in report['models'] if r['id'] == 'model-one')
        self.assertEqual(row['verified_distinct_total'], 2)
        self.assertEqual(row['verified_distinct_originals'], 1)
        self.assertEqual(row['verified_distinct_background_removed'], 1)

    def test_per_model_original_counter_excludes_processed_images(self):
        self.add('processed.jpg', state='background_removed_verified')
        self.add('original.jpg', state='verified_unmodified_original')
        original = self.manifest['items']['model-one']['files'][1]
        original.update(original_sha256=original['sha256'], model_review_approved=True)
        self.save()
        row = next(r for r in module.audit(self.root)['models'] if r['id'] == 'model-one')
        self.assertEqual(row['verified_distinct_processed'], 1)
        self.assertEqual(row['verified_distinct_originals'], 1)

    def test_original_without_model_review_does_not_count(self):
        self.add('original.jpg',state='verified_unmodified_original')
        photo=self.manifest['items']['model-one']['files'][0]
        photo['original_sha256']=photo['sha256']
        self.save()
        self.assertEqual(module.audit(self.root)['photos_verified_processed_distinct'],0)

    def test_mismatched_original_hash_fails_completion(self):
        self.add('original.jpg',state='verified_unmodified_original')
        photo=self.manifest['items']['model-one']['files'][0]
        photo.update(original_sha256='0'*64,model_review_approved=True)
        self.save()
        with self.assertRaisesRegex(ValueError,'Unmodified original bytes changed'):
            module.audit(self.root)

    def test_reviewed_alias_shares_gallery_without_inflating_physical_photo_count(self):
        for i in range(3):self.add('photo%d.jpg'%i)
        self.manifest['aliases']={'model-two':{'target_id':'model-one',
            'model_review_approved':True,'model_review_notes':'Two catalog spellings were reviewed as the exact same aircraft model.'}}
        self.save()
        report=module.audit(self.root)
        self.assertEqual(report['models_complete'],2)
        self.assertEqual(report['photos_embedded'],3)
        self.assertEqual(report['minimum_additional_verified_photos'],0)

    def test_unreviewed_alias_is_rejected(self):
        self.manifest['aliases']={'model-two':{'target_id':'model-one'}}
        self.save()
        with self.assertRaisesRegex(ValueError,'unreviewed gallery alias'):
            module.audit(self.root)

    def test_pdf_adds_distinct_models_but_respects_explicit_target(self):
        (self.assets/'catalog_bpla_vsu_pdf.json').write_text(json.dumps({'entries':[
            {'id':'pdf-one','name':'Alias One','targetId':'model-one','type':'Самолётный'},
            {'id':'pdf-two','name':'Distinct Model','type':'Самолётный'}]}))
        self.assertEqual(module.audit(self.root)['models_total'],3)

    def test_album_counts_new_aircraft_and_skips_reviewed_alias_and_ground_model(self):
        import gzip
        archive=self.root/'research/source_catalogs/catalog_album_2026_original.dat'
        archive.parent.mkdir(parents=True)
        archive.write_bytes(gzip.compress(json.dumps({'entries':[
            {'id':'album-one','name':'Aircraft alias','type':'Самолётный','page':5},
            {'id':'album-two','name':'Another aircraft','type':'Коптерный','page':6},
            {'id':'album-three','name':'Ground machine','type':'Наземный','page':7}]}).encode()))
        code=self.root/'app/src/main/java/dev/infend/bplainfo/DroneStore.java'
        code.parent.mkdir(parents=True)
        code.write_text('private static String albumAliasTarget(int page){ case 5:return "model-one"; } private static void mergeAlbum(')
        self.assertEqual(module.audit(self.root)['models_total'],3)

if __name__ == "__main__":
    unittest.main()
