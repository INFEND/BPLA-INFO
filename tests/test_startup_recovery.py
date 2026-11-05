"""Startup and rendering regressions: no implicit creation of the whole catalog."""
import pathlib
import subprocess
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo"
MAIN=(JAVA/"MainActivity.java").read_text("utf-8")

HARNESS=r'''package dev.infend.bplainfo;
public final class CatalogPagingSmoke {
    public static void main(String[] args){
        if(CatalogPaging.end(0,540,18)!=18)throw new AssertionError("initial page");
        if(CatalogPaging.end(18,540,18)!=36)throw new AssertionError("second page");
        if(CatalogPaging.end(532,540,18)!=540)throw new AssertionError("last page");
        if(CatalogPaging.end(0,0,18)!=0)throw new AssertionError("empty");
        if(CatalogPaging.end(540,540,18)!=540)throw new AssertionError("final");
        try{CatalogPaging.end(541,540,18);throw new AssertionError("invalid offset");}
        catch(IllegalArgumentException expected){}
    }
}
'''
class StartupRecoveryTests(unittest.TestCase):
    def test_paging_does_not_auto_expand_all_cards(self):
        self.assertIn("CatalogPaging.end(offset,found.size(),CARD_BATCH_SIZE)",MAIN)
        self.assertIn('button("ПОКАЗАТЬ ЕЩЁ (',MAIN)
        self.assertNotIn("target.postDelayed(()->renderCardBatch(",MAIN)
        self.assertIn("target.post(()->",MAIN)
        self.assertIn("CARD_BATCH_SIZE=18",MAIN)

    def test_failed_open_keeps_private_data_and_renders_diagnostics(self):
        self.assertIn("showStartupFailure(\"Отображение каталога\",uiError)",MAIN)
        self.assertIn("showStartupFailure(\"Чтение каталога\",error)",MAIN)
        self.assertIn("showStartupFailure(\"Список моделей\",error)",MAIN)
        self.assertIn('"last_startup_error.txt"',MAIN)
        self.assertIn('"СКОПИРОВАТЬ ОТЧЁТ"',MAIN)
        self.assertIn('"Данные и пользовательские фотографии не удалены."',MAIN)
        self.assertNotIn("deleteDatabase(",MAIN)
        self.assertNotIn(".edit().clear()",MAIN)

    def test_bounded_paging_jvm(self):
        with tempfile.TemporaryDirectory() as folder:
            file=pathlib.Path(folder)/"CatalogPagingSmoke.java"
            file.write_text(HARNESS,encoding="utf-8")
            subprocess.run(["javac","-encoding","UTF-8","-d",folder,
                str(JAVA/"CatalogPaging.java"),str(file)],
                check=True,capture_output=True,text=True)
            subprocess.run(["java","-cp",folder,"dev.infend.bplainfo.CatalogPagingSmoke"],
                           check=True,capture_output=True,text=True)

if __name__=="__main__":
    unittest.main()
