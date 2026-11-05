"""Real PDF preview regression: preserve units and reveal 19 genuine unprocessed frames."""
import hashlib
import json
import pathlib
import subprocess
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo"
ASSETS=ROOT/"app/src/main/assets"
HARNESS=r'''package dev.infend.bplainfo;
import java.util.*;
public final class PdfPreviewSmoke {
  private static void eq(String actual,String expected){
    if(!expected.equals(actual))throw new AssertionError("expected <"+expected+"> got <"+actual+">");
  }
  public static void main(String... args){
    List<String[]> copter=Arrays.asList(
      new String[]{"Тактический радиус, км","16"},
      new String[]{"Время полета, мин","15"},
      new String[]{"Максимальная скорость, км/ч","120"});
    eq(PreviewFormatter.endurance("15",copter),"15 мин");
    eq(PreviewFormatter.speed("120",copter),"120 км/ч");
    eq(PreviewFormatter.range("16",copter),"16 км");
    eq(PreviewFormatter.endurance("24…720",Collections.singletonList(
      new String[]{"Время полета, ч","24…720"})),"24–720 ч");
    eq(PreviewFormatter.speed("120",null),"—");
    eq(PreviewFormatter.endurance("15",null),"—");
    eq(PreviewFormatter.range("",copter),"—");
    eq(PreviewFormatter.endurance("до 2 часов (120 минут)",null),"до 2 ч");
    eq(CardPresentation.engine("электро"),"Электрический");
    eq(CardPresentation.engine("ЭЛЕКТРО"),"Электрический");
    eq(CardPresentation.engine("ЭЛЕКТРОДВИГАТЕЛЬ"),"Электрический");
    eq(CardPresentation.engine("electric"),"Электрический");
    eq(CardPresentation.engine("ДВС"),"ДВС");
    eq(CardPresentation.name("1-20пс ночная"),"1-20ПС «Ночная»");
  }
}
'''
class PdfPreviewRegression(unittest.TestCase):
    def test_jvm_unit_formatter_and_engine(self):
        with tempfile.TemporaryDirectory() as directory:
            p=pathlib.Path(directory)/"PdfPreviewSmoke.java"
            p.write_text(HARNESS,encoding="utf-8")
            compiled=subprocess.run(["javac","-encoding","UTF-8","-d",directory,
              str(JAVA/"SpecFormatter.java"),str(JAVA/"PreviewFormatter.java"),
              str(JAVA/"CardPresentation.java"),str(p)],
              capture_output=True,text=True)
            self.assertEqual(compiled.returncode,0,compiled.stdout+compiled.stderr)
            result=subprocess.run(["java","-cp",directory,"dev.infend.bplainfo.PdfPreviewSmoke"],
              capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
    def test_bulletin_contains_exact_source_of_units(self):
        data=json.loads((ASSETS/"bulletin30_2025.json").read_text("utf-8"))
        entries={r["name"].lower():r for r in data["entries"]}
        for key in ("1-20та","1-20пс ночная"):
            rows=entries[key]["specRows"]
            self.assertIn(["Время полета, мин","15"],rows)
            self.assertIn(["Максимальная скорость, км/ч","120"],rows)
            self.assertIn(["Тактический радиус, км","16"],rows)
        self.assertIn(["Время полета, ч","24…720"],entries["ab12dmn"]["specRows"])
    def test_verified_photos_are_real_unmodified_source_bytes(self):
        manifest=json.loads((ASSETS/"bundled_photos.json").read_text("utf-8"))
        photos=[r for x in manifest["items"].values() for r in x["files"]]
        self.assertGreaterEqual(len(photos),21)
        originals=[r for r in photos if r.get("processing_status")=="verified_unmodified_original"]
        self.assertEqual(len(originals),len(photos))
        for photo in originals:
            packaged=ASSETS/photo["file"]
            original=ROOT/photo["original_archive"]
            self.assertEqual(hashlib.sha256(packaged.read_bytes()).hexdigest(),photo["sha256"])
            self.assertEqual(packaged.read_bytes(),original.read_bytes())
            self.assertTrue(photo["model_review_approved"])
            self.assertFalse(photo["background_removed"])
    def test_fullscreen_status_describes_original_and_retains_identity_warning(self):
        ui=(JAVA/"MainActivity.java").read_text("utf-8")
        lib=(JAVA/"PhotoLibrary.java").read_text("utf-8")
        self.assertIn("ИСТОЧНИК ТРЕБУЕТ ПРОВЕРКИ",ui)
        self.assertIn("ОРИГИНАЛЬНАЯ ФОТОГРАФИЯ · БЕЗ ИЗМЕНЕНИЙ",ui)
        self.assertNotIn("ФОН НЕ УДАЛЁН",ui)
        self.assertIn("public boolean needsReview()",lib)
if __name__=="__main__":unittest.main()
