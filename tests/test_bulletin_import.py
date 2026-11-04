"""Provenance and safe supplemental import from user's 2025 bulletin PDF."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"app/src/main/assets/bulletin30_2025.json"
BASE=ROOT/"app/src/main/assets/catalog_lostarmour_2024_verified.json"
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo/DroneStore.java"

class BulletinImportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=json.loads(SOURCE.read_text("utf-8"))
        cls.items=cls.source["entries"]

    def test_provenance_and_coverage(self):
        self.assertEqual(self.source["schema"],1)
        self.assertEqual(self.source["year"],2025)
        self.assertRegex(self.source["pdf_sha256"],r"^[a-f0-9]{64}$")
        self.assertEqual(len(self.items),374)
        self.assertEqual(sum(len(x["specRows"]) for x in self.items),3516)
        self.assertEqual(self.source["photosImported"],0)

    def test_model_name_and_id_uniqueness(self):
        self.assertEqual(len({x["id"] for x in self.items}),len(self.items))
        self.assertEqual(len({(x["name"].casefold(),x["type"]) for x in self.items}),len(self.items))
        self.assertTrue(all(1<=x["page"]<=213 for x in self.items))
        self.assertTrue(all(x["type"] in {"Самолётный","Коптерный","Барражирующий","Аэростат"} for x in self.items))

    def test_sample_page_mapping(self):
        examples={x["name"].casefold():x["page"] for x in self.items}
        for title,page in {"Frame V2":7,"Scan Eagle":8,"Домаха":38,"RAM 2X":201,"КТ-1":204}.items():
            self.assertEqual(examples.get(title.casefold()),page)
        self.assertGreaterEqual(sum(len(x["sourcePages"])>1 for x in self.items),7)

    def test_nonempty_specification_pairs(self):
        for model in self.items:
            self.assertTrue(model["specRows"],model["name"])
            for row in model["specRows"]:
                self.assertEqual(len(row),2)
                self.assertTrue(all(isinstance(v,str) and v.strip() for v in row))
                self.assertLess(len(row[0]),90)
                self.assertLess(len(row[1]),180)

    def test_no_photo_loader_or_fabricated_gallery(self):
        content=SOURCE.read_text("utf-8")
        self.assertNotIn("imageUri",content)
        self.assertNotIn("galleryUris",content)
        self.assertNotIn("http://",content)
        self.assertNotIn("https://",content)
        self.assertNotIn("wikimedia",content.lower())

    def test_import_wired_for_new_and_existing_users(self):
        code=JAVA.read_text("utf-8")
        self.assertEqual(code.count("mergeBulletin30(c,out)"),2)
        self.assertIn("bulletin30_revision",code)
        self.assertIn("bulletin30_2025.json",code)
        self.assertIn("sameCategory(existing.type(),type)",code)
        self.assertNotIn("existing.galleryUris.clear()",code)

    def test_preexisting_87_models_untouched(self):
        self.assertEqual(len(json.loads(BASE.read_text("utf-8"))["entries"]),87)

if __name__=="__main__":unittest.main()
