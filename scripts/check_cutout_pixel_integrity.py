#!/usr/bin/env python3
"""Reject altered UAV pixels in a proposed transparent cutout.

This is a conservative QA gate, not visual approval. Run on equally sized
original and cutout PNGs before human inspection. No image is modified.
"""
import argparse
import json
from pathlib import Path
from PIL import Image


def inspect(original_path, cutout_path, tolerance=3):
    with Image.open(original_path) as source, Image.open(cutout_path) as prepared:
        source.load()
        prepared.load()
        if source.size != prepared.size:
            raise ValueError("Dimensions differ: pixel preservation cannot be established")
        if prepared.mode != "RGBA":
            raise ValueError("Prepared image must have an explicit RGBA alpha channel")
        src = source.convert("RGB")
        out = prepared.convert("RGBA")
        pixels = out.getdata()
        original = src.getdata()
        opaque = 0
        transparent = 0
        partial = 0
        changed = 0
        bounds = [out.width, out.height, -1, -1]
        for i, (a, b) in enumerate(zip(original, pixels)):
            alpha = b[3]
            if alpha == 0:
                transparent += 1
                continue
            x, y = i % out.width, i // out.width
            bounds = [min(bounds[0], x), min(bounds[1], y),
                      max(bounds[2], x), max(bounds[3], y)]
            if alpha == 255:
                opaque += 1
                if any(abs(a[c] - b[c]) > tolerance for c in range(3)):
                    changed += 1
            else:
                partial += 1
        if opaque == 0 or transparent == 0:
            raise ValueError("Missing opaque subject or transparent background")
        border_contact = (bounds[0] == 0 or bounds[1] == 0 or
                          bounds[2] == out.width - 1 or bounds[3] == out.height - 1)
        return {"size": list(out.size), "opaque_pixels": opaque,
                "transparent_pixels": transparent, "partial_alpha_pixels": partial,
                "changed_opaque_pixels": changed, "touches_border": border_contact,
                "pixel_preservation_pass": changed == 0 and not border_contact,
                "visual_review_approved": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("cutout", type=Path)
    parser.add_argument("--tolerance", type=int, default=3)
    args = parser.parse_args()
    if not 0 <= args.tolerance <= 255:
        parser.error("Tolerance must be 0..255")
    result = inspect(args.original, args.cutout, args.tolerance)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result["pixel_preservation_pass"] else 1)


if __name__ == "__main__":
    main()
