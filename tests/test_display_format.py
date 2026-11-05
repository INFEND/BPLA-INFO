import pathlib
import subprocess
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo/SpecFormatter.java"
ACTIVITY=(ROOT/"app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
ICON=(ROOT/"app/src/main/java/dev/infend/bplainfo/SettingsIconView.java").read_text("utf-8")
SAMPLES={
    "160 км/ч (публикация 2026 г.)":"~160 км/ч",
    "200 км/ч (по публикациям)":"~200 км/ч",
    "100 Км/Ч":"100 км/ч",
    "1.5 км":"1,5 км",
    "2,5 часа":"2,5 ч",
    "65 минут":"65 мин",
    "до 1,2 кг (без дополнительной батареи)":"до 1,2 кг (без дополнительной батареи)",
    "140–160 км/ч (зависит от конфигурации)":"~140–160 км/ч",
    "12 м":"12 м",
    "1,4 г":"1,4 г",
    "н/д":""
}

class DisplayRegression(unittest.TestCase):
    def test_code_and_icons(self):
        self.assertIn('titleBar("БПЛА  ИНФО","Каталог БПЛА",null)',ACTIVITY)
        self.assertNotIn("АВИАЦИОННЫЙ АРХИВ",ACTIVITY)
        self.assertIn('private String activeSettingsSection=""',ACTIVITY)
        self.assertIn("settingsBodies.put(id,body)",ACTIVITY)
        self.assertIn("CardPresentation.rows(d.values,d.specRows)",ACTIVITY)
        self.assertIn('new SettingsIconView(this,id,',ACTIVITY)
        self.assertNotIn("glyphs={",ACTIVITY)
        for icon in ('"dark"','"light"','"appearance"','"ota"','"about"','"motion"'):
            self.assertIn('case '+icon+':',ICON)
    def test_formatter_compilation_and_outputs(self):
        java_source='''import dev.infend.bplainfo.SpecFormatter;
public final class TestDisplay {
    public static void main(String[] a) {
        for(String input:a)System.out.println(SpecFormatter.display(input));
    }
}
'''
        with tempfile.TemporaryDirectory() as folder:
            directory=pathlib.Path(folder)
            path=directory/"TestDisplay.java"
            path.write_text(java_source,"utf-8")
            subprocess.run(["javac","-encoding","UTF-8","-d",folder,str(JAVA),str(path)],check=True,capture_output=True,text=True)
            tests=list(SAMPLES)
            result=subprocess.run(["java","-cp",folder,"TestDisplay",*tests],check=True,capture_output=True,text=True)
            self.assertEqual(result.stdout.splitlines(),list(SAMPLES.values()))
if __name__=="__main__":unittest.main()
