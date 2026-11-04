"""Physical APK catalog checks: copied bytes and missing assets are caught."""
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('audit_bulletin', ROOT/'scripts/audit_apk_bulletin.py')
audit=importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class ApkBulletinTests(unittest.TestCase):
    def apk(self,folder,changed=None,omitted=None):
        path=folder/'test.apk'
        with zipfile.ZipFile(path,'w') as archive:
            for filename in audit.FILES:
                if filename==omitted:continue
                data=(audit.ASSETS/filename).read_bytes()
                if filename==changed:data=data+b'corrupted'
                archive.writestr('assets/'+filename,data)
        return path

    def test_correct_assets_are_present(self):
        with tempfile.TemporaryDirectory() as d:
            result=audit.audit_apk(self.apk(Path(d)))
            self.assertEqual(result['supplemental_source_models'],374)

    def test_missing_supplemental_asset_fails(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(ValueError,'Missing APK asset'):
                audit.audit_apk(self.apk(Path(d),omitted='bulletin30_2025.json'))

    def test_tampered_supplemental_asset_fails(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(ValueError,'Different or truncated'):
                audit.audit_apk(self.apk(Path(d),changed='bulletin30_2025.json'))

if __name__=='__main__':unittest.main()
