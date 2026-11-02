import hashlib,pathlib,sys,tempfile,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"scripts"))
from make_manifest import parse_badging,make
class TestRelease(unittest.TestCase):
 def test_parse(self):
  self.assertEqual(parse_badging("package: name='dev.infend.bplainfo' versionCode='4' versionName='1.0.4'"),("dev.infend.bplainfo",4,"1.0.4"))
 def test_hash_and_size(self):
  with tempfile.TemporaryDirectory() as tmp:
   f=pathlib.Path(tmp)/"app.apk";f.write_bytes(b"test")
   manifest=make(f,("dev.infend.bplainfo",1,"1.0.0"),"f"*64,"notes")
   self.assertEqual(manifest["sha256"],hashlib.sha256(b"test").hexdigest())
   self.assertEqual(manifest["sizeBytes"],4)
 def test_bad_package(self):
  with tempfile.TemporaryDirectory() as tmp:
   f=pathlib.Path(tmp)/"app.apk";f.write_bytes(b"test")
   with self.assertRaises(ValueError):make(f,("different",1,"1.0.0"),"f"*64,"notes")
 def test_bad_cert(self):
  with tempfile.TemporaryDirectory() as tmp:
   f=pathlib.Path(tmp)/"app.apk";f.write_bytes(b"test")
   with self.assertRaises(ValueError):make(f,("dev.infend.bplainfo",1,"1.0.0"),"bad","notes")
if __name__=="__main__":unittest.main()
