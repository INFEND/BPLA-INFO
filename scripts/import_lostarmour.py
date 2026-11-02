#!/usr/bin/env python3
"""Read public, factual data from LostArmour's 2024 87-model PDF classifier.
No copyrighted pictures or layout are copied. This is NOT the live /catalog/uav feed.
"""
import argparse
import io
import json
import pathlib
import re
import sys
import urllib.request

PDF_URL = "https://lostarmour.info/books/ua-uav-classification.pdf"
SOURCE_PAGE = "https://lostarmour.info/articles/ua-uav-classification"
REQUEST_HEADERS = {"User-Agent": "BPLA-INFO-data-import/1.0 (public catalogue reference)"}

def download(destination):
    request = urllib.request.Request(PDF_URL, headers=REQUEST_HEADERS)
    with urllib.request.urlopen(request, timeout=40) as response:
        data = response.read(35_000_001)
        if len(data)>35_000_000:raise ValueError("PDF too large")
        if not data.startswith(b"%PDF"):raise ValueError("Not a PDF")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    return len(data)

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--pdf", default="/tmp/lostarmour-uav-2024.pdf")
    p.add_argument("--probe",action="store_true")
    p.add_argument("--out",default="app/src/main/assets/lostarmour_uav_2024.json")
    args=p.parse_args()
    import fitz
    filename=pathlib.Path(args.pdf)
    if not filename.exists():print("Downloaded",download(filename),"bytes")
    doc=fitz.open(filename)
    print("Pages",len(doc),"metadata",doc.metadata)
    if args.probe:
        for pi in range(min(len(doc),3)):
            page=doc[pi]
            print("PAGE",pi+1,"SIZE",page.rect)
            blocks=page.get_text("blocks",sort=True)
            for block in blocks[:140]:
                x0,y0,x1,y1,txt,*_=block
                clean=re.sub(r"\s+"," ",txt).strip()
                if clean:print(f"P{pi+1} x={x0:.0f}:{x1:.0f} y={y0:.0f}:{y1:.0f}: {clean[:340]}")
        return
    raise SystemExit("Run with --probe first to validate positions before publishing")

if __name__=="__main__":main()
