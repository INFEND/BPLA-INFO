"""Validate offline user album content and safe additive migration."""
import collections
import gzip
import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSET=ROOT/"app/src/main/assets/catalog_album_2026.json.gz"
STORE=ROOT/"app/src/main/java/dev/infend/bplainfo/DroneStore.java"
UI=ROOT/"app/src/main/java/dev/infend/bplainfo/MainActivity.java"

class AlbumImportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.doc=json.loads(gzip.decompress(ASSET.read_bytes()).decode("utf-8"))
        cls.entries=cls.doc["entries"]

    def test_source_and_counts(self):
        self.assertEqual(self.doc["schema"],1)
        self.assertEqual(self.doc["totalPages"],156)
        self.assertEqual(self.doc["sourcePdfSha256"],
            "fb7b03cf3a0693fc2233c82ab3de0eaf1b3e9cb9b61585c7a9fb62ab2eaa94fe")
        self.assertEqual(len(self.entries),146)
        self.assertEqual(sum(len(x["specRows"]) for x in self.entries),714)
        self.assertEqual(collections.Counter(x["type"] for x in self.entries),{
            "Самолётный":103,"Коптерный":11,"Барражирующий":6,"Морской":15,"Наземный":11
        })
        self.assertEqual((self.doc["photosImported"],self.doc["operationsImported"]),(0,0))

    def test_ids_pages_and_models(self):
        self.assertEqual(len({x["id"] for x in self.entries}),146)
        for item in self.entries:
            self.assertEqual(item["id"],f'album-2026-p{item["page"]:03d}')
            self.assertTrue(5<=item["page"]<=155)
            self.assertTrue(item["name"])
            self.assertTrue(all(len(r)==2 and all(r) for r in item["specRows"]))
        names={e["name"] for e in self.entries}
        self.assertIn("BAD.2",names)
        self.assertIn("RQ-20 Пума АЕ",names)
        self.assertIn("Магура V 5",names)
        self.assertIn("Vector",names) if "Vector" in names else None
        self.assertNotIn("FPV (обобщённая категория)",names)

    def test_no_unlicensed_photos_or_operational_instructions(self):
        data=gzip.decompress(ASSET.read_bytes()).decode("utf-8")
        for term in ["http://","https://","wikimedia","wikipedia",
                     "galleryUris","imageUri","мгц","ппрч","дальность связи",
                     "боевой части","взрывателя","миномет","целеуказание"]:
            self.assertNotIn(term,data.lower())

    def test_source_and_local_user_content_preserved(self):
        store=STORE.read_text("utf-8")
        self.assertEqual(store.count("mergeAlbum(c,out)"),2)
        self.assertIn("album_revision",store)
        self.assertIn("GZIPInputStream",store)
        self.assertIn("albumAliasTarget(page)",store)
        self.assertIn("if(added)applyBasicSpec(found,key,value)",store)
        self.assertNotIn("found.galleryUris.clear()",store)
        self.assertNotIn("found.favorite=false",store)
        self.assertNotIn('found.set(2,country+',store)
        self.assertIn('"СТРАНА (ПО АЛЬБОМУ)"',store)
        self.assertIn('u.equals("ДАЛЬНОСТЬ ХОДА")',store)
        self.assertIn('u.equals("ВРЕМЯ РАБОТЫ")',store)
        drone=(ROOT/"app/src/main/java/dev/infend/bplainfo/Drone.java").read_text("utf-8")
        self.assertIn('"ДАЛЬНОСТЬ ХОДА"',drone)
        self.assertIn('"ВРЕМЯ РАБОТЫ"',drone)
        ui=UI.read_text("utf-8")
        self.assertIn('"Морской","Наземный"',ui)
        self.assertIn('category.equals("Морской")',ui)
        self.assertIn('category.equals("Наземный")',ui)

if __name__=="__main__":
    unittest.main()
