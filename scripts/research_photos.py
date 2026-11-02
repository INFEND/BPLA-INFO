#!/usr/bin/env python3
"""Find Creative Commons photographs of exact UAV models for manual verification.

Results are *candidates*, not automatically approved photographs. No photos are
invented, relabelled, or copied without license metadata.
"""
import argparse
import html
import json
import os
import pathlib
import re
import time
import urllib.parse
import urllib.request

ROOT=pathlib.Path(__file__).resolve().parents[1]
USER_AGENT="BPLAINFO/1.1 (github.com/INFEND/BPLA-INFO; reference-gallery research)"
API="https://commons.wikimedia.org/w/api.php"
SEEDS={
    "DELTAQUAD PRO":"DeltaQuad Pro",
    "МАРА-2М":"Mara-2M",
    "A1-СМ ФУРИЯ":"A1-SM Furia",
    "FLYEYE":"FlyEye UAV",
    "BAYRAKTAR TB2":"Bayraktar TB2",
    "LELEKA-100 (BASIC)":"Leleka-100 UAV",
    "PUMA AE RQ-20":"RQ-20 Puma UAV",
    "WARMATE (2.0)":"Warmate loitering munition",
    "UJ-22 AIRBORNE":"UJ-22 Airborne",
    "ONE 150":"Primoco One 150",
    "VECTOR":"Quantum Systems Vector UAV",
    "MUGIN-5 PRO":"Mugin 5 Pro UAV",
    "PD-2":"PD-2 UAV",
    "SHARK":"Shark UAV",
}
AMBIGUOUS={"FURY","ELF","RAM","THOR","COBRA","SPARROW","VECTOR","SKIF","SKIPPER","R-15","GALKA","SIRKO","SIRKO 2"}
def clean(value):
    return re.sub(r"<[^>]+>","",html.unescape(str(value or ""))).strip()
def query(params):
    url=API+"?"+urllib.parse.urlencode(params)
    req=urllib.request.Request(url,headers={"User-Agent":USER_AGENT,"Accept":"application/json"})
    with urllib.request.urlopen(req,timeout=30) as response:
        return json.load(response)
def candidates(term):
    terms=re.findall(r"[a-zA-Z0-9]+",term.lower())
    if not terms:return []
    result=query({
        "action":"query","generator":"search","gsrsearch":term,"gsrnamespace":"6",
        "gsrlimit":"40","prop":"imageinfo","iiprop":"url|size|extmetadata",
        "iiurlwidth":"1400","format":"json","formatversion":"2"
    })
    records=[]
    for page in result.get("query",{}).get("pages",[]):
        title=page.get("title","")
        normalized=title.casefold()
        if any(t not in normalized for t in terms):
            continue
        info=(page.get("imageinfo") or [None])[0]
        if not info:continue
        ext=info.get("extmetadata") or {}
        lic=clean(ext.get("LicenseShortName",{}).get("value",""))
        if not (lic.lower().startswith("cc") or "public domain" in lic.lower()):continue
        width=int(info.get("width",0));height=int(info.get("height",0))
        if width<800 or height<480:continue
        mime=info.get("mime","")
        if mime not in ("image/jpeg","image/png","image/webp"):continue
        url=info.get("thumburl") or info.get("url")
        if not url or not url.startswith("https://"):continue
        records.append({
            "title":title,"url":url,
            "sourceUrl":info.get("descriptionurl",""),
            "license":lic,"artist":clean(ext.get("Artist",{}).get("value",""))[:150],
            "width":width,"height":height,"mime":mime,
        })
    records.sort(key=lambda r:(-(min(r["width"],2800)*min(r["height"],2800)),r["title"]))
    return records[:20]
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--full",action="store_true")
    parser.add_argument("--output",default="photo_candidates.json")
    args=parser.parse_args()
    data=json.loads((ROOT/"app/src/main/assets/catalog_lostarmour_2024_verified.json").read_text("utf-8"))
    entries=data["entries"]
    selected=entries if args.full else [v for v in entries if v["name"] in SEEDS]
    output={}
    for entry in selected:
        model=entry["name"];searchterm=SEEDS.get(model,model)
        if model in AMBIGUOUS and model not in SEEDS:continue
        try:
            photos=candidates(searchterm)
            output[entry["id"]]={"model":model,"query":searchterm,"candidates":photos}
            print(f"{model}: {len(photos)} licensed exact-title candidates",flush=True)
        except Exception as exc:
            output[entry["id"]]={"model":model,"query":searchterm,"error":str(exc)}
            print(f"{model}: lookup failed {type(exc).__name__}: {exc}",flush=True)
        time.sleep(0.25)
    pathlib.Path(args.output).write_text(json.dumps(output,ensure_ascii=False,indent=2),"utf-8")
    print("Coverage",sum(bool(x.get("candidates")) for x in output.values()),"of",len(output))
if __name__=="__main__":
    main()
