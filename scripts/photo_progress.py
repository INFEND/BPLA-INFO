#!/usr/bin/env python3
"""Honest photo completion report: only verified real distinct angles count."""
import argparse
import hashlib
import json
from pathlib import Path

REQUIRED = 3
APPROVED = {"background_removed_verified"}
REVIEWED = {"needs_background_review"}

def audit(root):
    root = Path(root)
    assets = root / "app/src/main/assets"
    catalog = json.loads((assets / "catalog_lostarmour_2024_verified.json").read_text("utf-8"))
    manifest = json.loads((assets / "bundled_photos.json").read_text("utf-8"))
    models = {row["id"]: row["name"] for row in catalog["entries"]}
    if len(models) != len(catalog["entries"]):
        raise ValueError("Duplicate catalog model IDs")
    files_seen = set()
    hashes_seen = set()
    complete = []
    rows = []
    total_embedded = 0
    approved_count = 0
    for model_id, model_name in models.items():
        records = manifest.get("items", {}).get(model_id, {}).get("files", [])
        if not isinstance(records, list):
            raise ValueError("Invalid gallery: " + model_id)
        accepted = 0
        reviewed_angles = set()
        for record in records:
            rel = record["file"]
            path = (assets / rel).resolve()
            if not path.is_relative_to(assets.resolve()) or not path.is_file():
                raise ValueError("Missing/unsafe APK photo: " + str(rel))
            if rel in files_seen:
                raise ValueError("Repeated APK path: " + rel)
            files_seen.add(rel)
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if digest != record["sha256"]:
                raise ValueError("APK photo SHA mismatch: " + rel)
            if digest in hashes_seen:
                raise ValueError("Repeated APK bytes: " + rel)
            hashes_seen.add(digest)
            total_embedded += 1
            if record.get("processing_status") in APPROVED and record.get("visual_review_approved") is True and record.get("angle_review_approved") is True:
                angle = record.get('angle_id')
                if not isinstance(angle, str) or not angle.strip():
                    continue  # Flags alone do not establish a distinct camera angle.
                angle = angle.strip().lower()
                if angle in reviewed_angles:
                    continue  # Repeated camera angle cannot count twice.
                reviewed_angles.add(angle)
                accepted += 1
                approved_count += 1
        if accepted >= REQUIRED:
            complete.append(model_id)
        rows.append({"id": model_id, "name": model_name, "embedded": len(records),
                     "verified_distinct_processed": accepted,
                     "remaining": max(0, REQUIRED - accepted),
                     "complete": accepted >= REQUIRED})
    unknown = set(manifest.get("items", {})) - set(models)
    if unknown:
        raise ValueError("Unknown model IDs in photo manifest: " + ", ".join(sorted(unknown)))
    total_required = len(models) * REQUIRED
    return {
        "models_total": len(models), "minimum_verified_photos_required": total_required,
        "models_complete": len(complete), "models_incomplete": len(models) - len(complete),
        "photos_embedded": total_embedded, "photos_verified_processed_distinct": approved_count,
        "minimum_additional_verified_photos": sum(r["remaining"] for r in rows),
        "completion_percent": round(100 * len(complete) / len(models), 2) if models else 0,
        "models": rows,
        "definition": "Complete means >=3 embedded real photos with distinct reviewed angles, verified background removal and visual approval. A hash check alone never proves model identity or visual quality."
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    report = audit(args.repo)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        for key in ("models_total", "minimum_verified_photos_required", "models_complete",
                    "models_incomplete", "photos_embedded", "photos_verified_processed_distinct",
                    "minimum_additional_verified_photos", "completion_percent"):
            print(f"{key}: {report[key]}")
        for row in report["models"]:
            if row["embedded"]:
                print(f'{row["id"]} {row["name"]}: {row["embedded"]} embedded, '
                      f'{row["verified_distinct_processed"]} verified, {row["remaining"]} needed')

if __name__ == "__main__":
    main()
