"""Only documented non-operational source facts may augment local cards."""
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
ASSET=ROOT/"app/src/main/assets/official_specs_2026.json"
STORE=ROOT/"app/src/main/java/dev/infend/bplainfo/DroneStore.java"

class VerifiedSourcesTests(unittest.TestCase):
    def test_sourced_facts_and_whitelist(self):
        data=json.loads(ASSET.read_text("utf-8"))
        self.assertEqual(data["schema"],1)
        self.assertEqual(len(data["entries"]),2)
        rows={e["modelId"]:e for e in data["entries"]}
        self.assertEqual(rows["lostarmour-pdf-2024-006"]["factName"],"ТЕМПЕРАТУРА ЭКСПЛУАТАЦИИ")
        self.assertEqual(rows["bulletin30-2025-p036l"]["factName"],"ПРОИЗВОДИТЕЛЬ")
        self.assertEqual(rows["bulletin30-2025-p036l"]["factValue"],"Terminal Autonomy")
        self.assertTrue(all(e["sourceUrl"].startswith("https://") and
                            e["sourceLabel"] and e["status"] for e in data["entries"]))
        self.assertEqual(len({(e["modelId"],e["factName"]) for e in data["entries"]}),2)
    def test_no_photo_or_operative_content(self):
        doc=ASSET.read_text("utf-8").casefold()
        for forbidden in ("wikimedia","wikipedia","imageuri","galleryuris",
                          "боевой части","частот","мгц","ghz","инструкц","управлени"):
            self.assertNotIn(forbidden,doc)
        self.assertEqual(len([e for e in json.loads(doc)["entries"]]),2)
    def test_offline_merge_does_not_erase_local_content(self):
        source=STORE.read_text("utf-8")
        self.assertIn("mergeVerifiedSources(c,out)",source)
        self.assertIn('open("official_specs_2026.json")',source)
        self.assertIn('"verified_sources_revision"',source)
        self.assertNotIn("drone.galleryUris.clear()",source)
        self.assertNotIn("drone.favorite=false",source)
if __name__=="__main__":
    unittest.main()
