#!/usr/bin/env python3
"""Create OTA manifest from the actual signed APK and version notes."""
import argparse,datetime,hashlib,json,pathlib,re,subprocess
from urllib.parse import quote
def parse_badging(s):
    m=re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",s,re.M)
    if not m:raise ValueError("APK badging unavailable")
    return m.group(1),int(m.group(2)),m.group(3)
def make(apk,info,cert,notes):
    package,code,version=info
    if package!="dev.infend.bplainfo" or code<1 or not re.fullmatch(r"[0-9]+(?:\.[0-9]+){2}",version):raise ValueError("Wrong APK identity/version")
    if not re.fullmatch(r"[a-fA-F0-9]{64}",cert):raise ValueError("Wrong certificate fingerprint")
    return {"schema":1,"packageName":package,"versionCode":code,"versionName":version,
      "apkUrl":"https://github.com/INFEND/BPLA-INFO/releases/download/"+quote("v"+version)+"/BPLA-INFO.apk",
      "sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),"certificateSha256":cert,
      "sizeBytes":apk.stat().st_size,"publishedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),"notes":notes}
def main():
    p=argparse.ArgumentParser()
    for name in ("apk","aapt","output","github-output"):p.add_argument("--"+name,required=True)
    a=p.parse_args();apk=pathlib.Path(a.apk)
    info=parse_badging(subprocess.check_output([a.aapt,"dump","badging",str(apk)],text=True))
    notes_path=pathlib.Path("release_notes")/("v"+info[2]+".txt")
    if not notes_path.is_file():raise SystemExit("Missing release notes: "+str(notes_path))
    notes=notes_path.read_text("utf-8").strip()
    if not notes:raise SystemExit("Empty release notes")
    cert=pathlib.Path("dist/certificate_sha256.txt").read_text("utf-8").strip()
    manifest=make(apk,info,cert,notes)
    output=pathlib.Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n","utf-8")
    with open(a.github_output,"a",encoding="utf-8") as out:
        out.write("version="+info[2]+"\ntag=v"+info[2]+"\n")
    print("Manifest:",info[2],manifest["sha256"])
if __name__=="__main__":main()
