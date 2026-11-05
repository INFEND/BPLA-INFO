"""Check protection against the 1.0.20 startup crash loop without using a device."""
import pathlib
import re
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/dev/infend/bplainfo"
STORE=(JAVA/"DroneStore.java").read_text("utf-8")
MAIN=(JAVA/"MainActivity.java").read_text("utf-8")
GUARD=(JAVA/"StartupGuard.java").read_text("utf-8")
THEME=(JAVA/"ThemePrefs.java").read_text("utf-8")

class StartupRecoveryTests(unittest.TestCase):
    def test_crash_loop_circuit_breaker_before_migration(self):
        self.assertIn("StartupGuard.wasInterrupted(this)",MAIN)
        self.assertIn("showRecoveryScreen();",MAIN)
        self.assertIn("StartupGuard.begin(this)",MAIN)
        self.assertLess(MAIN.index("StartupGuard.wasInterrupted(this)"),
                        MAIN.index("DroneStore.load(app)"))
        self.assertIn('startup_catalog_pending_v2',GUARD)
        self.assertIn("getNoBackupFilesDir()",GUARD)
        self.assertIn("output.getFD().sync()",GUARD)

    def test_marker_cleared_only_when_list_rendered(self):
        self.assertIn("target.post(()->StartupGuard.completed(getApplicationContext()))",MAIN)
        self.assertIn("StartupGuard.completed(this);return;",MAIN)
        self.assertIn('if(offset==0)',MAIN)
        self.assertNotIn("StartupGuard.completed(this);\n        setup();",MAIN)

    def test_unsafe_migration_never_overwrites_local_copy(self):
        self.assertIn("checkpointBeforeMigration(c,raw)",STORE)
        self.assertIn("catalog_before_1_0_21.json",STORE)
        self.assertIn("android.util.AtomicFile",STORE)
        self.assertIn("if(destination.isFile())return;",STORE)
        self.assertIn("atomic.finishWrite(stream)",STORE)
        self.assertIn("throw new IllegalStateException",STORE)
        self.assertIn("NON_AIR_ARCHIVE",STORE)
        self.assertIn("pruneNonAir(c,out)",STORE)

    def test_safe_preview_is_read_only(self):
        preview=STORE[STORE.index("public static ArrayList<String> readOnlyPreview"):STORE.index("private static final int SEED_REVISION")]
        self.assertIn("JSONArray stored=new JSONArray(data)",preview)
        self.assertIn("CardPresentation.isAir",preview)
        self.assertNotIn("save(",preview)
        self.assertNotIn(".clear()",preview)
        self.assertIn("showRecoveryPreview",MAIN)
        self.assertIn("Только чтение",MAIN)
        self.assertIn("СКОПИРОВАТЬ ОТЧЁТ",MAIN)

    def test_exceptions_and_fatal_errors_become_local_report(self):
        self.assertIn('catch(Throwable failure)',MAIN)
        self.assertIn('catch(Throwable uiError)',MAIN)
        self.assertIn("StartupGuard.capture",MAIN)
        self.assertIn("Thread.setDefaultUncaughtExceptionHandler",GUARD)
        self.assertIn("last_startup_error.txt",GUARD)
        self.assertNotIn('getSharedPreferences("bpla_store",0).edit().clear()',MAIN)

    def test_corrupt_theme_values_do_not_crash_startup(self):
        self.assertIn('catch(ClassCastException bad)',THEME)
        self.assertIn('safeInt(p,"mode",0)',THEME)
        self.assertIn('safeInt(p,"accent",0)',THEME)
        self.assertIn('safeBoolean(p,"animations",true)',THEME)

if __name__=="__main__":
    unittest.main()
