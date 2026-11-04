"""Safe one-time import of source-attributed general UAV data from user PDF."""
import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSET=ROOT/"app/src/main/assets/catalog_bpla_vsu_pdf.json"
BASE=ROOT/"app/src/main/assets/catalog_lostarmour_2024_verified.json"
SUPPLEMENT=ROOT/"app/src/main/assets/bulletin30_2025.json"
CODE=ROOT/"app/src/main/java/dev/infend/bplainfo/DroneStore.java"

class PdfVsuImportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.doc=json.loads(ASSET.read_text("utf-8"))
        cls.entries=cls.doc["entries"]

    def test_pdf_identity_and_no_images(self):
        self.assertEqual(self.doc["schema"],1)
        self.assertEqual(self.doc["sourcePdfSha256"],
                         "2913a58a55163c912160539b44509a772fa518d4fbd7ff9b4cd03dbeaf044b65")
        self.assertEqual(self.doc["totalPages"],60)
        self.assertEqual(self.doc["photosImported"],0)
        self.assertEqual(self.doc["frequenciesImported"],0)
        self.assertEqual(len(self.entries),34)

    def test_page_provenance_and_unique_ids(self):
        self.assertEqual(len({e["id"] for e in self.entries}),len(self.entries))
        self.assertTrue(all(1<=e["page"]<=60 and
                            "ПДФ.pdf, страница" in e["source"] for e in self.entries))
        self.assertEqual(sum(len(e["specRows"]) for e in self.entries),167)

    def test_source_pdf_never_imports_unlicensed_images_or_rf_targeting(self):
        data=ASSET.read_text("utf-8")
        for token in ("http://","https://","wikimedia","wikipedia",
                      "galleryUris","imageUri","МГц","ГГц","кГц",
                      "ППРЧ","модуляция","канал управления"):
            self.assertNotIn(token,data)
        self.assertFalse(any("photo" in e for e in self.entries))

    def test_explicit_identity_and_ambiguous_vector(self):
        named={e["name"]:e for e in self.entries}
        self.assertIsNone(named["Vector (Integrated Dynamics, Пакистан)"]["targetId"])
        self.assertEqual(named["Quantom Vector (Quantum Systems)"]["targetId"],
                         "lostarmour-pdf-2024-081")
        ids=set()
        for file in (BASE,SUPPLEMENT):
            ids.update(e["id"] for e in json.loads(file.read_text("utf-8"))["entries"])
        for e in self.entries:
            if e["targetId"]:
                self.assertIn(e["targetId"],ids,e["name"])
        self.assertEqual(sum(e["targetId"] is None for e in self.entries),11)

    def test_preserves_user_photos_and_prior_catalogs(self):
        self.assertEqual(len(json.loads(BASE.read_text("utf-8"))["entries"]),87)
        self.assertEqual(len(json.loads(SUPPLEMENT.read_text("utf-8"))["entries"]),374)
        java=CODE.read_text("utf-8")
        self.assertEqual(java.count("mergeVsuPdf(c,out)"),2)
        self.assertIn("vsu_pdf_revision",java)
        self.assertIn("catalog_bpla_vsu_pdf.json",java)
        self.assertIn('matched.specRows.add(new String[]{attributed,value})',java)
        self.assertNotIn("matched.galleryUris.clear()",java)

if __name__=="__main__":
    unittest.main()
