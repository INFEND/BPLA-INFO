import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
ENTRIES=json.loads((ROOT/"app/src/main/assets/catalog_lostarmour_2024_verified.json").read_text("utf-8"))["entries"]
UI=(ROOT/"app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
MODEL=(ROOT/"app/src/main/java/dev/infend/bplainfo/Drone.java").read_text("utf-8")

class CatalogueRegressionTests(unittest.TestCase):
    def test_all_87_records_have_some_speed(self):
        self.assertEqual(len(ENTRIES),87)
        for entry in ENTRIES:
            self.assertTrue(any("СКОРОСТЬ" in key and val.strip() for key,val in entry["specRows"]),entry["name"])
    def test_86_have_endurance(self):
        self.assertEqual(sum(any(key=="АВТОНОМНОСТЬ" and val.strip() for key,val in e["specRows"]) for e in ENTRIES),86)
    def test_filters_are_clean(self):
        options=UI.split('for(String group:new String[]{',1)[1].split('}){',1)[0]
        for removed in ("Другие","Схема крыло","Одномоторный","Двойная хвостовая балка"):
            self.assertNotIn(removed,options)
    def test_ui_uses_custom_dialogs_and_compact_stats(self):
        self.assertNotIn("AlertDialog",UI)
        self.assertIn('stat(stats,"АВТОНОМНОСТЬ"',UI)
        self.assertIn('UiDialogs.options(this,t,"Сортировка"',UI)
        self.assertNotIn("QR LINK",UI)
        self.assertNotIn('"Н/Д"',UI)
    def test_resolved_speed_contains_all_source_alternatives(self):
        for alternative in ("МАКСИМАЛЬНАЯ СКОРОСТЬ","МАКС. ГОРИЗОНТ. СКОРОСТЬ","КРЕЙСЕРСКАЯ СКОРОСТЬ"):
            self.assertIn(alternative,MODEL)
if __name__=="__main__":unittest.main()
