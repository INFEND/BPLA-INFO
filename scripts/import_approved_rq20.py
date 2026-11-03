#!/usr/bin/env python3
"""One-off offline asset import. No image URLs or downloads are used by Android.

Sources verified as public-domain US government images on DVIDS:
- RQ-20A Puma in flight, Daniel Amburg, Fort Dix, 15 Apr 2024, DVIDS image 8347955
- RQ-20 Puma in flight, LCpl Jose Gonzalez, Honduras, 14 Sep 2020, DVIDS image 6381574

This script must be removed from the repository after import, leaving only
the actual WebP files plus textual attribution in bundled_photos.json.
"""
import hashlib, io, json, pathlib, urllib.request
from PIL import Image, ImageOps

ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSETS=ROOT/"app/src/main/assets"
DEST=ASSETS/"photos"
DEST.mkdir(parents=True,exist_ok=True)
TARGET="lostarmour-pdf-2024-041"
SPEC=[
  {
    "file":"photos/rq20_puma_inflight_fortdix.webp",
    "url":"https://d1ldvf68ux039x.cloudfront.net/thumbs/photos/2404/8347955/1000w_q95.jpg",
    "crop":(160,120,780,580),
    "author":"Daniel Amburg, U.S. Army",
    "source":"DVIDS photo 8347955; 15.04.2024; RQ-20A Puma in flight",
    "license":"Public domain (U.S. Government)"
  },
  {
    "file":"photos/rq20_puma_inflight_honduras.webp",
    "url":"https://d1ldvf68ux039x.cloudfront.net/thumbs/photos/2010/6381574/1000w_q95.jpg",
    "crop":(90,35,670,400),
    "author":"LCpl Jose Gonzalez, U.S. Marine Corps",
    "source":"DVIDS photo 6381574; 14.09.2020; RQ-20 Puma in flight",
    "license":"Public domain (U.S. Government)"
  }
]
def fetch(url):
    req=urllib.request.Request(url,headers={"User-Agent":"INFEND-BPLA-INFO-asset-import/1.0"})
    with urllib.request.urlopen(req,timeout=30) as response:
        if response.status!=200 or not response.headers.get("Content-Type","").startswith("image/"):
            raise RuntimeError("Not an image response")
        data=response.read(6_000_001)
        if len(data)>6_000_000:raise RuntimeError("Oversized source")
    return data
def main():
    manifest=json.loads((ASSETS/"bundled_photos.json").read_text("utf-8"))
    assert manifest["schema"]==2 and manifest["storage"]=="apk-assets-only"
    catalog=json.loads((ASSETS/"catalog_lostarmour_2024_verified.json").read_text("utf-8"))
    model=next((x["name"] for x in catalog["entries"] if x["id"]==TARGET),None)
    assert model=="PUMA AE RQ-20",model
    records=[]
    for spec in SPEC:
        data=fetch(spec["url"])
        with Image.open(io.BytesIO(data)) as source:
            image=ImageOps.exif_transpose(source).convert("RGB")
        x1,y1,x2,y2=spec["crop"]
        if image.width<950 or image.height<550:raise RuntimeError("Unexpected source dimensions")
        image=image.crop((x1,y1,x2,y2))
        image.thumbnail((1600,1200),Image.Resampling.LANCZOS)
        if image.width<500 or image.height<300:raise RuntimeError("Image after crop too small")
        destination=ASSETS/spec["file"]
        image.save(destination,"WEBP",quality=90,method=6)
        binary=destination.read_bytes()
        if len(binary)<512:raise RuntimeError("Output too small")
        sha=hashlib.sha256(binary).hexdigest()
        print(spec["file"],image.size,len(binary),sha,flush=True)
        records.append({
          "file":spec["file"],"sha256":sha,"author":spec["author"],
          "license":spec["license"],"source":spec["source"]
        })
    manifest["items"][TARGET]={"name":model,"files":records}
    (ASSETS/"bundled_photos.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n","utf-8")
if __name__=="__main__":main()
