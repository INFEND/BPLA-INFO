import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
base=json.loads((ROOT/"app/src/main/assets/catalog_lostarmour_2024_verified.json").read_text("utf-8"))
extra=json.loads((ROOT/"app/src/main/assets/spec_enrichment_2026.json").read_text("utf-8"))
source=ROOT/"app/src/main/java/dev/infend/bplainfo"
store=(source/"DroneStore.java").read_text("utf-8")
main=(source/"MainActivity.java").read_text("utf-8")

class EnrichmentTests(unittest.TestCase):
    def test_reference_integrity(self):
        originals={x["id"]:x for x in base["entries"]}
        self.assertEqual(len(originals),87)
        self.assertEqual(len(extra["entries"]),len({x["id"] for x in extra["entries"]}))
        for e in extra["entries"]:
            self.assertIn(e["id"],originals)
            self.assertEqual(e["name"],originals[e["id"]]["name"])
            self.assertTrue(e["source"].startswith("https://"))
            existing={row[0] for row in originals[e["id"]]["specRows"]}
            for name,value in e["fields"]:
                self.assertNotIn(name,existing)
                self.assertTrue(value.strip())
    def test_new_facts_and_remaining_gaps(self):
        new=sum(len(x["fields"]) for x in extra["entries"])
        self.assertGreaterEqual(new,10)
        names={x["id"]:{row[0] for row in x["specRows"]} for x in base["entries"]}
        for x in extra["entries"]:
            names[x["id"]].update(row[0] for row in x["fields"])
        self.assertEqual(sum("МАКСИМАЛЬНАЯ СКОРОСТЬ" not in names[k] for k in names),10)
    def test_migration_for_existing_installations(self):
        self.assertIn('mergeEnrichment(c,out)',store)
        self.assertIn('"enrichment_revision"',store)
        self.assertIn('if(already)continue;',store)
        self.assertIn('&&original.get(5).isEmpty())',store)
    def test_subtitle_is_only_bold_values(self):
        self.assertIn('labels.addView(text(d.displayType()+"  ·  "+',main)
        self.assertNotIn('"Тип: "+d.displayType()',main)
        self.assertNotIn('"Тип двигателя: "+',main)

if __name__=="__main__":
    unittest.main()
