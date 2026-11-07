"""Full OTA history, legacy notes and responsive startup regression tests."""
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"scripts"))
from ota_history import build_history,clean_notes

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo"
HARNESS=r'''package dev.infend.bplainfo;
import java.util.*;
public final class OtaHistorySmoke {
  private static OtaHistory.Entry e(int c,String s){
    return new OtaHistory.Entry(c,"1.0."+s,"[+] Изменение "+s);
  }
  private static void check(boolean value){if(!value)throw new AssertionError("Invalid history");}
  public static void main(String[] args){
    List<OtaHistory.Entry> history=new ArrayList<>();
    for(int i=0;i<16;i++)history.add(e(i+1,""+i));
    String all=OtaHistory.merge(history,14,16,"[+] Изменение 15");
    check(all.contains("Версия 1.0.14")&&all.contains("Версия 1.0.15"));
    check(!all.contains("Версия 1.0.13"));
    check(OtaHistory.merge(history,0,16,"[+] Latest").contains("Версия 1.0.0"));
    check(OtaHistory.merge(history,15,16,"[+] Latest").contains("Версия 1.0.15"));
    history.remove(14);
    String fallback=OtaHistory.merge(history,14,16,"[+] Latest");
    check("[+] Latest".equals(fallback));
    history.add(e(15,"14"));
    history.add(e(16,"15"));
    check("[+] Latest".equals(OtaHistory.merge(history,14,16,"[+] Latest")));
  }
}
'''

class CumulativeOtaTests(unittest.TestCase):
    def test_jvm_merge_exact_versions_and_gaps(self):
        with tempfile.TemporaryDirectory() as tmp:
            harness=pathlib.Path(tmp)/"OtaHistorySmoke.java"
            harness.write_text(HARNESS,encoding="utf-8")
            subprocess.run(["javac","-encoding","UTF-8","-d",tmp,
                            str(JAVA/"OtaHistory.java"),str(JAVA/"ReleaseNotes.java"),
                            str(harness)],check=True,capture_output=True,text=True)
            subprocess.run(["java","-cp",tmp,"dev.infend.bplainfo.OtaHistorySmoke"],
                           check=True,capture_output=True,text=True)

    def test_legacy_notes_are_visible(self):
        original=(ROOT/"release_notes/v1.0.14.txt").read_text("utf-8")
        shown=clean_notes(original)
        self.assertIn("374 карточки",shown)
        self.assertIn("3516 строк",shown)
        self.assertIn("[+] ",shown)
        self.assertNotIn("Проверки:",shown)
        self.assertNotIn("github",shown.casefold())

    def test_actual_history_includes_all_prior_release_files(self):
        for n in range(15):
            self.assertTrue((ROOT/f"release_notes/v1.0.{n}.txt").is_file())
        with patch("ota_history.read_historical_code",side_effect=lambda tag:int(tag.split(".")[-1])+1):
            history=build_history("1.0.15",16,"[+] Полный журнал")
        self.assertEqual(len(history),16)
        self.assertEqual([h["versionCode"] for h in history],list(range(1,17)))
        self.assertEqual(history[14]["versionName"],"1.0.14")
        self.assertIn("374 карточки",history[14]["notes"])

    def test_missing_or_duplicate_releases_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            d=pathlib.Path(tmp)
            (d/"v1.0.0.txt").write_text("[+] First",encoding="utf-8")
            (d/"v1.0.1.txt").write_text("[+] Second",encoding="utf-8")
            with patch("ota_history.read_historical_code",return_value=1):
                with self.assertRaisesRegex(ValueError,"Incomplete"):
                    build_history("1.0.2",3,"[+] Third",notes_dir=d)
            (d/"v1.0.1.txt").unlink()
            with self.assertRaisesRegex(ValueError,"Missing previous"):
                build_history("1.0.2",3,"[+] Third",notes_dir=d)

    def test_android_ui_and_manifest_use_cumulative_history(self):
        manifest=(ROOT/"scripts/make_manifest.py").read_text("utf-8")
        updater=(JAVA/"OtaUpdater.java").read_text("utf-8")
        dialog=(JAVA/"OtaProgressDialog.java").read_text("utf-8")
        self.assertIn("history=build_history",manifest)
        self.assertIn('output["history"]=history',manifest)
        self.assertIn("notesFor(j,local)",updater)
        self.assertIn("notesFor(j,0)",updater)
        self.assertIn("progress.current(installed.versionName,complete)",updater)
        self.assertNotIn("actionStart=4",dialog)
        self.assertIn("for(int[] span:strong)",dialog)

    def test_startup_no_longer_builds_full_catalog_before_first_frame(self):
        main=(JAVA/"MainActivity.java").read_text("utf-8")
        self.assertIn('setup();',main)
        self.assertIn('"bpla-catalog-load"',main)
        self.assertIn("CARD_BATCH_SIZE=18",main)
        self.assertIn("CatalogPaging.end(offset,found.size(),chunk)",main)
        self.assertIn("Math.max(CARD_BATCH_SIZE,catalogVisibleCount)",main)
        self.assertNotIn("target.postDelayed(()->renderCardBatch(",main)
        self.assertIn("renderCardBatch(target,found",main)
        self.assertNotIn("records.addAll(DroneStore.load(this));",main)
        self.assertIn("PhotoLibrary.purgeLegacyCache(app)",main)

if __name__=="__main__":
    unittest.main()
