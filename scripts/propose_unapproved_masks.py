#!/usr/bin/env python3
"""Create UNAPPROVED alpha-mask candidates from local original photographs.

No AI synthesis, no RGB modification, no APK import. Manual review required.
"""
import argparse
import hashlib
import json
from pathlib import Path


def eligible_source(entry):
    """Only source-index records with redistribution provenance can enter QA."""
    required = ("archived_original", "sha256", "license", "author", "source_page")
    return all(isinstance(entry.get(key), str) and entry[key].strip() for key in required)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--index", default="research/photos/armyinform_review.json")
    parser.add_argument("--originals", default="research/photos/originals")
    parser.add_argument("--output", default="staging/mask_candidates")
    parser.add_argument("--limit", type=int, default=26)
    args = parser.parse_args()
    import cv2
    import numpy as np
    from PIL import Image
    data = json.loads(Path(args.index).read_text(encoding="utf-8"))
    print("Index top-level:", list(data) if isinstance(data,dict) else "list")
    # Only indexed, SHA-verified originals may become mask proposals.
    indexes = [data]
    supplemental = Path("research/photos/source_index.json")
    if supplemental.exists() and Path(args.index).resolve() != supplemental.resolve():
        indexes.append(json.loads(supplemental.read_text(encoding="utf-8")))
    candidates = {}
    for index in indexes:
        for entry in index.get("entries", []):
            archived, sha = entry.get("archived_original"), entry.get("sha256")
            if not eligible_source(entry):
                continue
            source = Path(archived)
            if source.parent.resolve() == Path(args.originals).resolve():
                candidates[source.name] = (source, sha, entry)
    files = [candidates[k] for k in sorted(candidates)][:args.limit]
    out = Path(args.output)
    out.mkdir(parents=True,exist_ok=True)
    manifest = []
    for src, expected_sha, entry in files:
        if not src.is_file() or src.suffix.lower() not in (".jpg",".jpeg",".webp",".png"):
            continue
        raw=src.read_bytes()
        actual_sha = hashlib.sha256(raw).hexdigest()
        if actual_sha != expected_sha:
            print("SKIP SHA256 MISMATCH", src.name)
            continue
        rgb=np.array(Image.open(src).convert("RGB"))
        h,w=rgb.shape[:2]
        if min(h,w)<64:
            continue
        # A rough foreground proposal. Never claim this is a verified drone silhouette.
        rect=(max(1,w//12),max(1,h//12),max(2,w-w//6),max(2,h-h//6))
        mask=np.zeros((h,w),np.uint8)
        bg=np.zeros((1,65),np.float64)
        fg=np.zeros((1,65),np.float64)
        try:
            cv2.grabCut(cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR),mask,rect,bg,fg,5,cv2.GC_INIT_WITH_RECT)
        except cv2.error as exc:
            print("SKIP",src.name,str(exc)[:100])
            continue
        alpha=np.where((mask==cv2.GC_FGD)|(mask==cv2.GC_PR_FGD),255,0).astype("uint8")
        rgba=np.dstack((rgb,alpha))
        name=src.stem
        Image.fromarray(alpha,"L").save(out/(name+"_mask.png"))
        Image.fromarray(rgba,"RGBA").save(out/(name+"_preview.png"))
        manifest.append({"original":str(src),"sha256":actual_sha,"model_name":entry.get("model_name", ""),"model_id":entry.get("model_id", ""),
                         "author":entry["author"],"license":entry["license"],"source_page":entry["source_page"],
                         "rights_evidence":entry.get("rights_evidence", ""),
                         "mask":name+"_mask.png","preview":name+"_preview.png","status":"UNAPPROVED_AUTOMATIC_PROPOSAL","manual_visual_review":False,"rgb_modified":False})
        print("PROPOSAL",src.name,w,h)
    (out/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("Unapproved proposals:",len(manifest))

if __name__=="__main__":
    main()
