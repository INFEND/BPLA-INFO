"""JVM regression coverage for single presentation layout and non-destructive filtering."""
import pathlib
import subprocess
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo"
HARNESS=r'''package dev.infend.bplainfo;
import java.util.*;
public class PresentationSmoke {
  static void eq(String value,String expected){if(!value.equals(expected))
      throw new AssertionError("expected "+expected+" got "+value);}
  static void yes(boolean condition){if(!condition)throw new AssertionError("validation failed");}
  public static void main(String[] ignored){
    eq(CardPresentation.name("AQ-400 Коса"),"AQ-400 «Коса»");
    eq(CardPresentation.name("А1-СМ Фурия"),"А1-СМ «Фурия»");
    eq(CardPresentation.name("RQ-20 Puma AE"),"RQ-20 Puma AE");
    eq(CardPresentation.name("AQ-400 «Коса»"),"AQ-400 «Коса»");
    eq(CardPresentation.manufacturer("UAVTEK"),"«UAVTEK»");
    eq(CardPresentation.manufacturer("«UAVTEK»"),"«UAVTEK»");
    eq(CardPresentation.engine("ЭЛЕКТРИЧЕСКИЙ"),"Электрический");
    eq(CardPresentation.engine("ДВС"),"ДВС");
    yes(CardPresentation.isAir("Самолётный",""));
    yes(!CardPresentation.isAir("Морской",""));
    yes(!CardPresentation.isAir("","Наземный"));
    yes(!CardPresentation.isAir("Подводный аппарат",""));
    String[] base=new String[16];
    Arrays.fill(base,"");
    base[2]="UAVTEK"; base[3]="1,2 м"; base[5]="120 км/ч";
    base[11]="1 ч"; base[13]="ЭЛЕКТРИЧЕСКИЙ";
    List<String[]> extra=Arrays.asList(
      new String[]{"МАКСИМАЛЬНАЯ СКОРОСТЬ","120 КМ/Ч"},
      new String[]{"ВРЕМЯ ПОЛЕТА","60 минут"},
      new String[]{"РАЗМАХ КРЫЛЬЕВ","1,2 м"});
    List<String[]> normalized=CardPresentation.rows(base,extra);
    eq(normalized.get(0)[0],"РАЗМАХ КРЫЛЬЕВ");
    eq(normalized.get(0)[1],"1,2 м");
    yes(normalized.stream().filter(e->e[0].equals("АВТОНОМНОСТЬ")).count()==1);
    yes(normalized.stream().filter(e->e[0].equals("РАЗМАХ КРЫЛЬЕВ")).count()==1);
    yes(normalized.stream().anyMatch(e->e[0].equals("ПРОИЗВОДИТЕЛЬ")&&e[1].equals("«UAVTEK»")));
    yes(normalized.stream().anyMatch(e->e[0].equals("ТИП ДВИГАТЕЛЯ")&&e[1].equals("Электрический")));
    eq(SpecFormatter.display("120 КМ/Ч"),"120 км/ч");
    eq(SpecFormatter.display("1 Ч"),"1 ч");
  }
}
''';
class CardPresentationTests(unittest.TestCase):
    def test_jvm_card_presentation(self):
        with tempfile.TemporaryDirectory() as folder:
            file=pathlib.Path(folder)/"PresentationSmoke.java"
            file.write_text(HARNESS,encoding="utf-8")
            subprocess.run(["javac","-encoding","UTF-8","-d",folder,
                str(JAVA/"SpecFormatter.java"),str(JAVA/"CardPresentation.java"),str(file)],
                check=True,capture_output=True,text=True)
            subprocess.run(["java","-cp",folder,"dev.infend.bplainfo.PresentationSmoke"],
                check=True,capture_output=True,text=True)
    def test_marine_ground_hidden_without_deleting_user_data(self):
        ui=(JAVA/"MainActivity.java").read_text("utf-8")
        store=(JAVA/"DroneStore.java").read_text("utf-8")
        self.assertNotIn('category.equals("Морской")',ui)
        self.assertNotIn('category.equals("Наземный")',ui)
        self.assertIn("CardPresentation.isAir(d.type(),d.sourceGroup)",ui)
        self.assertIn('if(type.equals("Морской")||type.equals("Наземный"))continue;',store)
        self.assertNotIn("d.galleryUris.clear()",store)
        self.assertNotIn("d.favorite=false",store)
        self.assertIn("NON_AIR_ARCHIVE",store)
        self.assertIn("putString(NON_AIR_ARCHIVE",store)
        self.assertIn("pruneNonAir(c,out)",store)
        self.assertIn("CardPresentation.isAir(d.type(),d.sourceGroup)",store)
if __name__=="__main__":
    unittest.main()
