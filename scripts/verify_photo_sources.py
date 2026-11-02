#!/usr/bin/env python3
"""Check authenticity metadata and Wikimedia media access for curated UAV photos."""
import hashlib,json,pathlib,urllib.parse,urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
catalog=json.loads((root/"app/src/main/assets/catalog_lostarmour_2024_verified.json").read_text("utf-8"))
data=json.loads((root/"app/src/main/assets/photo_sources.json").read_text("utf-8"))
names={item["id"]:item["name"] for item in catalog["entries"]}
photos=0
for id,entry in data["items"].items():
    assert id in names,(id,"not in catalog")
    assert entry["name"]==names[id],(id,"model mismatch")
    files=[r["file"] for r in entry["files"]]
    assert 1<=len(files)<=5,(id,"count")
    assert len(set(files))==len(files),(id,"duplicate files")
    assert all("." in file and "/" not in file for file in files)
    photos+=len(files)
print("Curated model count:",len(data["items"]),"verified-file references:",photos,"of target",5*len(names))
passed=0
missing=[]
blocked=0
for id,entry in data["items"].items():
    for record in entry["files"]:
        name=record["file"].replace(" ","_")
        md5=hashlib.md5(name.encode("utf-8")).hexdigest()
        encoded=urllib.parse.quote(name,safe="")
        url=f"https://upload.wikimedia.org/wikipedia/commons/{md5[0]}/{md5[:2]}/{encoded}"
        req=urllib.request.Request(url,headers={"User-Agent":"BPLAINFO-verification/1.0 (INFEND/BPLA-INFO)"},method="HEAD")
        try:
            with urllib.request.urlopen(req,timeout=16) as r:
                if r.status==200 and r.headers.get("Content-Type","").startswith("image/"):
                    passed+=1
                else:missing.append((id,name,r.status))
        except urllib.error.HTTPError as error:
            if error.code in (429,403,503):
                blocked+=1
            else:missing.append((id,name,error.code))
        except Exception as error:
            blocked+=1
        if (passed+len(missing)+blocked)%10==0:
            print("CHECK",passed,"confirmed",len(missing),"missing",blocked,"blocked",flush=True)
print("FINAL:",passed,"of",photos,"accessible;",
      blocked,"rate-limited/unavailable;",len(missing),"missing")
for row in missing:
    print("MISSING:",row)
if missing:
    raise SystemExit("Manifest contains incorrect Commons filenames")
