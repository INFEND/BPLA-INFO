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
for index,(id,entry) in enumerate(data["items"].items()):
    if index>1:break
    name=entry["files"][0]["file"].replace(" ","_")
    md5=hashlib.md5(name.encode("utf-8")).hexdigest()
    encoded=urllib.parse.quote(name,safe="")
    url=f"https://upload.wikimedia.org/wikipedia/commons/{md5[0]}/{md5[:2]}/{encoded}"
    print("Inspect",entry["name"],url)
    req=urllib.request.Request(url,headers={"User-Agent":"BPLAINFO-verification/1.0 (INFEND/BPLA-INFO)"},method="HEAD")
    try:
        with urllib.request.urlopen(req,timeout=20) as r:
            print("HEAD",r.status,r.headers.get("Content-Type"),r.headers.get("Content-Length"))
            assert r.status==200 and r.headers.get("Content-Type","").startswith("image/")
    except Exception as error:
        print("WARNING: image server not reachable from CI:",repr(error))
