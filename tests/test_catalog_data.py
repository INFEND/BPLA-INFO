import json
import pathlib
import unittest

SOURCE = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/catalog_lostarmour_2024_verified.json"

class TestCatalogue(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.db = json.loads(SOURCE.read_text(encoding="utf-8"))
        cls.rows = cls.db["entries"]

    def test_model_count(self):
        self.assertEqual(len(self.rows),87)
        self.assertEqual(self.db["sourcePublished"],"2024-03")
        self.assertTrue(self.db["complete"])

    def test_ids_and_models_unique(self):
        self.assertEqual(len({x["id"] for x in self.rows}),87)
        self.assertEqual(len({x["name"].casefold() for x in self.rows}),87)

    def test_original_order_and_groups(self):
        groups = {x["sourceGroup"] for x in self.rows}
        self.assertEqual(len(groups),6)
        for item in self.rows:
            self.assertTrue(item["catalogSource"])
            self.assertGreaterEqual(len(item["specRows"]),8)
            self.assertEqual(item["specRows"][0][0],"РАЗМАХ КРЫЛЬЕВ")
            self.assertEqual(item["specRows"][1][0],"ДЛИНА ФЮЗЕЛЯЖА")
            for row in item["specRows"]:
                self.assertEqual(len(row),2)
                self.assertTrue(all(isinstance(v,str) and v.strip() for v in row))
    def test_nonstandard_field_positions(self):
        by_name = {item["name"]:item for item in self.rows}
        fury = [x[0] for x in by_name["FURY"]["specRows"]]
        self.assertLess(fury.index("РАДИУС ДЕЙСТВИЯ"),fury.index("МАКСИМАЛЬНАЯ СКОРОСТЬ"))
        warmate = [x[0] for x in by_name["WARMATE (2.0)"]["specRows"]]
        self.assertEqual(warmate[2],"МАКС. ГОРИЗОНТ. СКОРОСТЬ")
        self.assertEqual(warmate[3],"СКОРОСТЬ АТАКИ")

if __name__=="__main__":unittest.main()
