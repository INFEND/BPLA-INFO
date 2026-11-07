#!/usr/bin/env python3
"""Honest photo completion report: only verified real distinct angles count."""
import argparse
import hashlib
import json
from pathlib import Path

REQUIRED = 3
APPROVED = {"background_removed_verified", "verified_unmodified_original"}
REVIEWED = {"needs_background_review"}


def _bulletin_union_models(assets, catalog):
    """Combine independent model catalogs without claiming ambiguous variants match."""
    import re
    def norm(s):
        return re.sub(r'[^\w]+', '', (s or '').upper().replace('Ё','Е'), flags=re.UNICODE)
    models = {row["id"]:row["name"] for row in catalog["entries"]}
    if len(models)!=len(catalog["entries"]):
        raise ValueError("Duplicate catalog model IDs")
    model_types={row["id"]:row.get("type","") for row in catalog["entries"]}
    extra_file=assets/"bulletin30_2025.json"
    if extra_file.is_file():
        bulletin=json.loads(extra_file.read_text("utf-8"))
        if bulletin.get("schema")!=1:
            raise ValueError("Unsupported bulletin schema")
        for row in bulletin["entries"]:
            new_type=row["type"]
            new_name=norm(row["name"])
            duplicate=any(norm(name)==new_name and (
                not model_types.get(mid) or model_types[mid].casefold()==new_type.casefold()
            ) for mid,name in models.items())
            if duplicate:
                continue
            if row["id"] in models:
                raise ValueError("Duplicate cross-catalog ID: "+row["id"])
            models[row["id"]]=row["name"]
            model_types[row["id"]]=new_type
    # The later PDF and album imports also introduce independent aircraft.
    # Apply their explicit target IDs and the app's reviewed album aliases.
    vsu_file=assets/"catalog_bpla_vsu_pdf.json"
    if vsu_file.is_file():
        for row in json.loads(vsu_file.read_text("utf-8"))["entries"]:
            if row.get("targetId") in models:
                continue
            if row["id"] not in models:
                models[row["id"]]=row["name"]
                model_types[row["id"]]=row.get("type","")
    repo=assets.parents[3]
    album_file=repo/"research/source_catalogs/catalog_album_2026_original.dat"
    if album_file.is_file():
        import gzip
        source=repo/"app/src/main/java/dev/infend/bplainfo/DroneStore.java"
        aliases={}
        if source.is_file():
            code=source.read_text("utf-8")
            start=code.find("private static String albumAliasTarget(")
            end=code.find("private static void mergeAlbum(",start)
            if start>=0 and end>start:
                aliases={int(page):target for page,target in re.findall(
                    r'case (\d+):return "([^"]+)";',code[start:end])}
        document=json.loads(gzip.decompress(album_file.read_bytes()))
        for row in document["entries"]:
            if row["type"] not in {"Самолётный","Коптерный","Барражирующий"}:
                continue
            target=aliases.get(row["page"])
            if target in models:
                continue
            if not target and any(norm(name)==norm(row["name"]) and
                    model_types.get(mid,"").casefold()==row["type"].casefold()
                    for mid,name in models.items()):
                continue
            if row["id"] not in models:
                models[row["id"]]=row["name"]
                model_types[row["id"]]=row["type"]
    return models

def audit(root):
    root = Path(root)
    assets = root / "app/src/main/assets"
    catalog = json.loads((assets / "catalog_lostarmour_2024_verified.json").read_text("utf-8"))
    manifest = json.loads((assets / "bundled_photos.json").read_text("utf-8"))
    models = _bulletin_union_models(assets, catalog)
    files_seen = set()
    hashes_seen = set()
    complete = []
    rows = []
    total_embedded = 0
    approved_count = 0
    original_count = 0
    processed_count = 0
    for model_id, model_name in models.items():
        records = manifest.get("items", {}).get(model_id, {}).get("files", [])
        if not isinstance(records, list):
            raise ValueError("Invalid gallery: " + model_id)
        accepted = 0
        accepted_originals = 0
        accepted_processed = 0
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
            if record.get("processing_status") == "verified_unmodified_original":
                if record.get("original_sha256") != digest:
                    raise ValueError("Unmodified original bytes changed: " + rel)
                if record.get("model_review_approved") is not True:
                    continue
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
                if record.get("processing_status") == "verified_unmodified_original":
                    original_count += 1
                    accepted_originals += 1
                else:
                    processed_count += 1
                    accepted_processed += 1
        if accepted >= REQUIRED:
            complete.append(model_id)
        rows.append({"id": model_id, "name": model_name, "embedded": len(records),
                     "verified_distinct_processed": accepted_processed, "verified_distinct_originals": accepted_originals,
                     "verified_distinct_total": accepted, "verified_distinct_background_removed": accepted_processed,
                     "remaining": max(0, REQUIRED - accepted),
                     "complete": accepted >= REQUIRED})
    unknown = set(manifest.get("items", {})) - set(models)
    if unknown:
        raise ValueError("Unknown model IDs in photo manifest: " + ", ".join(sorted(unknown)))
    by_id={row['id']:row for row in rows}
    aliases=manifest.get('aliases',{})
    for alias,review in aliases.items():
        target=review.get('target_id')
        if (alias not in by_id or target not in manifest.get('items',{}) or
                alias in manifest.get('items',{}) or alias==target or
                review.get('model_review_approved') is not True or
                len(review.get('model_review_notes','').strip())<15):
            raise ValueError('Invalid or unreviewed gallery alias: '+alias)
        original=by_id[target]
        row=by_id[alias]
        row.update(embedded=original['embedded'],
                   verified_distinct_processed=original['verified_distinct_processed'],
                   verified_distinct_originals=original['verified_distinct_originals'],
                   verified_distinct_total=original['verified_distinct_total'],
                   verified_distinct_background_removed=original['verified_distinct_background_removed'],
                   remaining=original['remaining'],complete=original['complete'],
                   shared_gallery=target)
        if row['complete']:
            complete.append(alias)
    total_required = len(models) * REQUIRED
    return {
        "models_total": len(models), "minimum_verified_photos_required": total_required,
        "models_complete": len(complete), "models_incomplete": len(models) - len(complete),
        "photos_embedded": total_embedded, "photos_verified_processed_distinct": processed_count,
        "photos_verified_distinct_originals": original_count,
        "photos_verified_distinct_total": approved_count,
        "photos_verified_distinct_background_removed": processed_count,
        "physical_model_galleries": len(manifest.get('items',{})),
        "reviewed_shared_galleries": len(aliases),
        "minimum_additional_verified_photos": sum(r["remaining"] for r in rows),
        "completion_percent": round(100 * len(complete) / len(models), 2) if models else 0,
        "models": rows,
        "definition": "Counts cover catalog cards from base, bulletin, VSU PDF and the aircraft-only album, including explicit source aliases. Complete means >=3 genuine photos with reviewed model identity and distinct useful views. Explicitly reviewed equivalent cards may share one physical gallery; physical photo totals count each binary once. Required/remaining photo counts are card-level slots. Unmodified originals preserve publisher bytes; background removal is not required."
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
                    "photos_verified_distinct_originals", "photos_verified_distinct_background_removed", "minimum_additional_verified_photos", "completion_percent"):
            print(f"{key}: {report[key]}")
        for row in report["models"]:
            if row["embedded"]:
                print(f'{row["id"]} {row["name"]}: {row["embedded"]} embedded, '
                      f'{row["verified_distinct_total"]} verified ({row["verified_distinct_originals"]} originals, {row["verified_distinct_processed"]} processed), {row["remaining"]} needed')

if __name__ == "__main__":
    main()
