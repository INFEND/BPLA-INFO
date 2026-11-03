#!/usr/bin/env python3
"""Verify permanent key identity and sign the Android release APK."""
import os,base64,hashlib,hmac,io,json,pathlib,subprocess,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
# Pinned to the certificate used by published v1.0.13 (CI run 151).
EXPECTED_SIGNING_CERT_SHA256="6ba5b3c9cccdd4abed63e39685493312b75240e40d212b9bca44dc6708775153"
def verify_signing_identity(actual, declared):
    if not hmac.compare_digest(actual.lower(), declared.lower()):
        raise ValueError("Key certificate does not match signing.json")
    if not hmac.compare_digest(actual.lower(), EXPECTED_SIGNING_CERT_SHA256):
        raise ValueError("Signing certificate differs from published v1.0.13; OTA blocked")
ENV_KEYS=("BPLA_INFO_SIGNING_BUNDLE","INFEND_SIGNING_BUNDLE","ANDROID_SIGNING_BUNDLE","QR_LINK_SIGNING_BUNDLE")
def run(args,env=None):return subprocess.run(args,check=True,capture_output=True,text=True,env=env)
def main():
    secret=next((os.getenv(k,"").strip() for k in ENV_KEYS if os.getenv(k,"").strip()),"")
    if not secret:raise SystemExit("Missing persistent signing bundle; refusing to publish unsigned APK")
    try:
        clean="".join(secret.split()).replace("-","+").replace("_","/")
        clean += "="*((4-len(clean)%4)%4)
        data=base64.b64decode(clean,validate=True)
        if len(data)>100000:raise ValueError("Signing bundle too large")
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            info=json.loads(z.read("signing.json"))
            keys=[n for n in z.namelist() if n.endswith(".p12") and "/" not in n]
            if len(keys)!=1:raise ValueError("Expected one PKCS12 key file in ZIP")
            key_data=z.read(keys[0])
        for k in ("alias","keystore_password","key_password","certificate_sha256"):
            if not isinstance(info.get(k),str) or not info[k]:raise ValueError("Missing signing.json field "+k)
    except Exception as e:raise SystemExit("Invalid signing ZIP: expected Base64-encoded ZIP with signing.json and one .p12; reason: "+str(e)) from e
    sdk=pathlib.Path(os.environ["ANDROID_SDK_ROOT"])/"build-tools"/"35.0.0"
    files=list((ROOT/"app/build/outputs/apk/release").glob("*.apk"))
    if len(files)!=1:raise SystemExit("Exactly one release APK required")
    dist=ROOT/"dist";dist.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="bpla-sign-") as temp:
        folder=pathlib.Path(temp);key=folder/"key.p12";key.write_bytes(key_data);key.chmod(0o600)
        cert=folder/"certificate.der";env=os.environ.copy()
        env["BPLA_STORE_PASS"]=info["keystore_password"];env["BPLA_KEY_PASS"]=info["key_password"]
        run(["keytool","-exportcert","-alias",info["alias"],"-keystore",str(key),"-storetype","PKCS12","-storepass:env","BPLA_STORE_PASS","-file",str(cert)],env)
        actual=hashlib.sha256(cert.read_bytes()).hexdigest()
        verify_signing_identity(actual, info["certificate_sha256"])
        aligned=folder/"aligned.apk"
        run([str(sdk/"zipalign"),"-f","-p","4",str(files[0]),str(aligned)])
        out=dist/"BPLA-INFO.apk"
        run([str(sdk/"apksigner"),"sign","--ks",str(key),"--ks-type","PKCS12","--ks-key-alias",info["alias"],"--ks-pass","env:BPLA_STORE_PASS","--key-pass","env:BPLA_KEY_PASS","--out",str(out),str(aligned)],env)
        run([str(sdk/"apksigner"),"verify","--verbose","--print-certs",str(out)])
        run([str(sdk/"zipalign"),"-c","-p","4",str(out)])
        (dist/"certificate_sha256.txt").write_text(actual+"\n")
    print("Verified signed APK with certificate: "+actual)
if __name__=="__main__":main()
