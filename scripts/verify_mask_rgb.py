#!/usr/bin/env python3
"""Check that an unapproved RGBA mask proposal preserves every original RGB pixel.

This is a mechanical integrity check, NEVER visual approval or an APK import.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops


def verify(original: Path, preview: Path, expected_sha256: str):
    original_bytes = original.read_bytes()
    actual_sha = hashlib.sha256(original_bytes).hexdigest()
    if actual_sha != expected_sha256:
        return {"ok": False, "reason": "original_sha256_mismatch"}
    with Image.open(original) as src, Image.open(preview) as cutout:
        rgb = src.convert("RGB")
        rgba = cutout.convert("RGBA")
        if rgb.size != rgba.size:
            return {"ok": False, "reason": "dimensions_mismatch"}
        if ImageChops.difference(rgb, rgba.convert("RGB")).getbbox():
            return {"ok": False, "reason": "original_rgb_modified"}
        alpha = rgba.getchannel("A")
        if not alpha.getbbox():
            return {"ok": False, "reason": "empty_alpha"}
        if alpha.getextrema() == (255, 255):
            return {"ok": False, "reason": "no_background_removed"}
        return {"ok": True, "reason": "rgb_integrity_only_visual_review_required"}


def run(directory: Path):
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    results = []
    for item in manifest:
        try:
            result = verify(Path(item["original"]), directory / item["preview"], item["sha256"])
        except (OSError, ValueError, KeyError) as exc:
            result = {"ok": False, "reason": "unreadable_or_invalid_input", "detail": str(exc)[:160]}
        results.append({"original": item.get("original"), "preview": item.get("preview"),
                        **result, "visual_review_approved": False})
    return {"schema": 1, "review_only": True, "total": len(results),
            "rgb_integrity_pass": sum(x["ok"] for x in results),
            "rgb_integrity_fail": sum(not x["ok"] for x in results),
            "entries": results}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, default=Path("staging/mask_candidates"))
    args = parser.parse_args()
    report = run(args.directory)
    (args.directory / "rgb_integrity.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "entries"}, ensure_ascii=False))
    if report["rgb_integrity_fail"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
