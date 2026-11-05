import importlib.util
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("audit_apk_photos",ROOT/"scripts/audit_apk_photos.py")
audit=importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class ApkPhotoAuditTests(unittest.TestCase):
    def make_apk(self, path, manifest, extra=None):
        with zipfile.ZipFile(path,"w") as z:
            z.writestr("assets/bundled_photos.json",json.dumps(manifest))
            for name,content in (extra or {}).items():
                z.writestr(name,content)
    def test_empty_offline_manifest_is_valid(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"test.apk"
            self.make_apk(path,{"schema":2,"storage":"apk-assets-only","items":{}})
            audit.audit(path)
    def test_old_manifest_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"test.apk"
            self.make_apk(path,{"schema":2,"storage":"apk-assets-only","items":{}},
                          {"assets/photo_sources.json":"{}"})
            with self.assertRaisesRegex(ValueError,"Old photo sources"):
                audit.audit(path)
    def test_missing_photo_binary_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"test.apk"
            manifest={"schema":2,"storage":"apk-assets-only","items":{"model":{"files":[{
                "file":"photos/model_front.webp","sha256":"0"*64,
                "author":"A","license":"CC0","source":"archive", "processing_status":"needs_background_review"
            }]}}}
            self.make_apk(path,manifest)
            with self.assertRaisesRegex(ValueError,"Missing photo binary"):
                audit.audit(path)

    def test_rejects_false_processing_claim(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"test.apk"
            manifest={"schema":2,"storage":"apk-assets-only","items":{"test":{"files":[{
                "file":"photos/test_front.webp", "sha256":"0"*64,
                "author":"A", "license":"CC0", "source":"archive",
                "processing_status":"background_removed_verified"
            }]}}}
            self.make_apk(path,manifest)
            with self.assertRaisesRegex(ValueError,"original SHA-256"):
                audit.audit(path)

    def test_reviewed_alias_uses_one_physical_photo(self):
        # Alias cards must share the audited original, rather than introduce
        # duplicate binaries or bypass the exact-model review requirement.
        image=io.BytesIO()
        Image.new('RGB',(600,400),(18,68,95)).save(image,format='JPEG')
        binary=image.getvalue()
        digest=hashlib.sha256(binary).hexdigest()
        manifest={'schema':2,'storage':'apk-assets-only','items':{'canonical':{'files':[{
            'file':'photos/original_test.jpg','sha256':digest,'original_sha256':digest,
            'author':'Test author','license':'CC0 1.0','source':'Fixture photograph',
            'processing_status':'verified_unmodified_original',
            'model_review_approved':True,'visual_review_approved':True
        }]}},'aliases':{'alternate-name':{
            'target_id':'canonical','model_review_approved':True,
            'model_review_notes':'Reviewed alternate spelling of the same aircraft model.'
        }}}
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'test.apk'
            def write():self.make_apk(path,manifest,{'assets/photos/original_test.jpg':binary})
            write()
            audit.audit(path)
            manifest['aliases']['alternate-name']['model_review_approved']=False
            write()
            with self.assertRaisesRegex(ValueError,'unreviewed photo gallery alias'):
                audit.audit(path)
            manifest['aliases']['alternate-name']['model_review_approved']=True
            manifest['aliases']['alternate-name']['target_id']='missing-model'
            write()
            with self.assertRaisesRegex(ValueError,'unreviewed photo gallery alias'):
                audit.audit(path)

if __name__=="__main__":
    unittest.main()
