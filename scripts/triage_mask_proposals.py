#!/usr/bin/env python3
"""Conservative automatic triage of GrabCut proposals. Never grants visual approval."""
import argparse
import json
from pathlib import Path
from PIL import Image
import cv2
import numpy as np

def inspect_entry(root, entry):
    mask_path = root / entry["mask"]
    preview_path = root / entry["preview"]
    with Image.open(mask_path) as m, Image.open(preview_path) as p:
        mask = m.convert("L")
        rgba = p.convert("RGBA")
        if mask.size != rgba.size:
            return {"status": "REJECT", "reasons": ["size_mismatch"]}
        w, h = mask.size
        a = rgba.getchannel("A")
        if a.tobytes() != mask.tobytes():
            return {"status": "REJECT", "reasons": ["mask_alpha_mismatch"]}
        hist = a.histogram()
        fg = sum(hist[1:])
        total = w * h
        bounds = a.getbbox()
        reasons = []
        if not bounds:
            reasons.append("empty_subject")
        else:
            if bounds[0] == 0 or bounds[1] == 0 or bounds[2] == w or bounds[3] == h:
                reasons.append("subject_touches_border")
        if fg < total * 0.015:
            reasons.append("foreground_too_small")
        if fg > total * 0.85:
            reasons.append("foreground_too_large")
        # Reject masks dominated by disconnected islands or stray foreground pixels.
        binary = np.asarray(a, dtype=np.uint8) > 127
        count, labels, stats, _ = cv2.connectedComponentsWithStats(binary.astype(np.uint8), 8)
        areas = sorted((int(stats[i, cv2.CC_STAT_AREA]) for i in range(1, count)), reverse=True)
        if areas and sum(areas[1:]) > sum(areas) * 0.08:
            reasons.append("fragmented_foreground")
        if len([area for area in areas if area >= max(20, total * 0.002)]) > 3:
            reasons.append("multiple_large_components")
        return {"status": "REJECT" if reasons else "NEEDS_MANUAL_VISUAL_REVIEW",
                "reasons": reasons, "foreground_fraction": round(fg / total, 5),
                "size": [w, h], "visual_review_approved": False}

def triage(root):
    data = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    result = []
    for item in data:
        try:
            qa = inspect_entry(root, item)
        except (OSError, ValueError, KeyError) as exc:
            qa = {"status": "REJECT", "reasons": ["unreadable_or_invalid_mask"],
                  "error": str(exc)[:160], "visual_review_approved": False}
        result.append({"original": item.get("original"), "sha256": item.get("sha256"),
                       **qa})
    return {"schema": 1, "review_only": True, "total": len(result),
            "needs_manual_visual_review": sum(x["status"] == "NEEDS_MANUAL_VISUAL_REVIEW" for x in result),
            "rejected": sum(x["status"] == "REJECT" for x in result),
            "entries": result}

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--directory", type=Path, default=Path("staging/mask_candidates"))
    args = p.parse_args()
    result = triage(args.directory)
    target = args.directory / "qa_report.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "entries"}, ensure_ascii=False))

if __name__ == "__main__":
    main()
