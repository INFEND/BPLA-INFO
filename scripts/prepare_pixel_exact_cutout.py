#!/usr/bin/env python3
"""Non-generative cutout preparation. A human-provided grayscale mask is mandatory.

Never sets approval flags, edits source originals, or writes into Android assets.
Foreground RGB values are copied exactly from the archived original.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageOps

def prepare(original, mask, output):
    original, mask, output = map(Path, (original, mask, output))
    if original.resolve() == output.resolve() or mask.resolve() == output.resolve():
        raise ValueError("Output must not overwrite an input")
    if output.suffix.lower() != ".png":
        raise ValueError("Review cutout must be PNG")
    source_hash = hashlib.sha256(original.read_bytes()).hexdigest()
    with Image.open(original) as im, Image.open(mask) as m:
        if im.mode not in ("RGB", "RGBA"):
            raise ValueError("Original must be RGB/RGBA")
        if m.mode != "L" or m.size != im.size:
            raise ValueError("Mask must be L mode and match original dimensions")
        rgb = im.convert("RGB")
        alpha = m.copy()
        lo, hi = alpha.getextrema()
        if lo != 0 or hi != 255:
            raise ValueError("Mask must include transparent and opaque pixels")
        if alpha.getbbox() is None:
            raise ValueError("Mask contains no subject")
        rgba = rgb.copy()
        rgba.putalpha(alpha)
        output.parent.mkdir(parents=True, exist_ok=True)
        rgba.save(output, format="PNG", optimize=True)
        with Image.open(output) as check:
            check.load()
            if check.mode != "RGBA" or check.size != rgb.size:
                raise ValueError("Output decoding failed")
            if ImageChops.difference(check.convert("RGB"), rgb).getbbox():
                raise ValueError("Foreground pixel values changed")
            if ImageChops.difference(check.getchannel("A"), alpha).getbbox():
                raise ValueError("Alpha mask changed")
    result = {
        "original_sha256": source_hash,
        "mask_sha256": hashlib.sha256(mask.read_bytes()).hexdigest(),
        "cutout_sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "visual_review_approved": False,
        "background_removal_verified": False,
        "apk_imported": False,
        "note": "Technical pixel-preservation only. Human edge/identity/rights review required."
    }
    output.with_suffix(".review.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--original", required=True)
    p.add_argument("--mask", required=True)
    p.add_argument("--out", required=True)
    a = p.parse_args()
    print(json.dumps(prepare(a.original, a.mask, a.out), indent=2))
