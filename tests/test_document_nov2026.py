import pathlib
import subprocess
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo"
MAIN=(JAVA/"MainActivity.java").read_text("utf-8")
SORT=(JAVA/"SortSheet.java").read_text("utf-8")
STORE=(JAVA/"DroneStore.java").read_text("utf-8")
DEDUP=(JAVA/"CatalogDuplicates.java").read_text("utf-8")
PRES=r'''package dev.infend.bplainfo;
import java.util.*;
public final class FacetsSmoke {
  static void eq(String found,String expected){
    if(!expected.equals(found))throw new AssertionError("Expected "+expected+" got "+found);
  }
  public static void main(String... args){
    eq(CatalogFacets.airframe("Самолётный","Многомоторный","PD-2"),"Самолётный");
    eq(CatalogFacets.airframe("Коптерный","","FPV-7"),"Коптерный");
    eq(CatalogFacets.airframe("Барражирующий","Барражирующие боеприпасы","RAM-2S"),"Самолётный");
    eq(CatalogFacets.role("Барражирующий","",null),"Барражирующий");
    eq(CatalogFacets.country(Arrays.asList(new String[]{"СТРАНА (ПО АЛЬБОМУ)","Турция"})),"Турция");
    eq(CatalogFacets.flag("Россия"),"🇷🇺");
    if(CatalogFacets.identicalModel("Leleka 100 BASIC","Leleka 100 EFT"))
      throw new AssertionError("Variants were incorrectly collapsed");
    if(!CatalogFacets.identicalModel(" BAYRAKTAR TB2 ","bayraktar tb2"))
      throw new AssertionError("Exact duplicate case-folding failed");
  }
}'''

class DocumentTasks(unittest.TestCase):
    def test_facets_compile_and_work(self):
        with tempfile.TemporaryDirectory() as folder:
            source=pathlib.Path(folder)/"FacetsSmoke.java"
            source.write_text(PRES,"utf-8")
            result=subprocess.run(["javac","-encoding","UTF-8","-d",folder,
                str(JAVA/"CatalogFacets.java"),str(source)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            actual=subprocess.run(["java","-cp",folder,"dev.infend.bplainfo.FacetsSmoke"],
                capture_output=True,text=True)
            self.assertEqual(actual.returncode,0,actual.stderr)
    def test_sticky_navigation(self):
        self.assertIn('homeRoot=root;',MAIN)
        self.assertIn('setContentView(homeRoot);',MAIN)
        self.assertIn('this::returnToHome',MAIN)
        self.assertIn('if(showingDetail||showingSettings){returnToHome();return;}',MAIN)
    def test_persistent_sort(self):
        self.assertIn('SortSheet.open(this,t,state,options',MAIN)
        self.assertIn('changed.run();draw();',SORT)
        self.assertIn('↑',SORT)
        self.assertIn('↓',SORT)
        self.assertIn('Только избранное',SORT)
        self.assertIn('Все страны',SORT)
        self.assertIn('PopupWindow',SORT)
        self.assertNotIn('↧ JSON',MAIN)
        self.assertNotIn('↥ JSON',MAIN)
    def test_non_destructive_exact_dedup(self):
        self.assertIn('CatalogDuplicates.cleanup(c,pruneNonAir(c,out))',STORE)
        self.assertIn('catalog_exact_duplicates_before_v1.json',DEDUP)
        self.assertIn('keeper.favorite|=other.favorite;',DEDUP)
        self.assertIn('keeper.galleryUris.add(uri);',DEDUP)
if __name__=="__main__":unittest.main()
