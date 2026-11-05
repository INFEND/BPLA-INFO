#!/usr/bin/env python3
"""Offline reviewed batch import. No network requests. Originals stay outside APK."""
import argparse
import copy
import hashlib
import io
import json
import os
import re
import sys
import tempfile
from pathlib import Path
from PIL import Image, UnidentifiedImageError

MAX_PREPARED_BYTES = 12_000_000
MAX_ORIGINAL_BYTES = 35_000_000
FORMATS = {".webp":"WEBP", ".png":"PNG", ".jpg":"JPEG", ".jpeg":"JPEG"}
MODEL_RE = re.compile(r"[a-z0-9][a-z0-9_-]{2,100}\Z")
ANGLE_RE = re.compile(r"[a-z0-9][a-z0-9_-]{2,60}\Z")
PHOTO_RE = re.compile(r"[a-z0-9][a-z0-9_-]{2,100}\.(?:webp|png|jpg|jpeg)\Z")
SHA_RE = re.compile(r"[a-f0-9]{64}\Z")
LICENSES = {"CC0 1.0", "CC BY 4.0", "CC BY-SA 4.0", "Public domain (U.S. Government)"}
STATES = {"verified_clean_original", "background_removed_verified", "verified_unmodified_original"}

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def clean_text(value, field, length=3):
    if not isinstance(value,str) or len(value.strip()) < length:
        raise ValueError("Missing "+field)
    if any(x in value.lower() for x in ("http://","https://","wikipedia","wikimedia")):
        raise ValueError("Prohibited URL/source in APK metadata: "+field)
    return value.strip()

def staging_file(root, value):
    if not isinstance(value,str) or "\\" in value or value.startswith("/"):
        raise ValueError("Unsafe staging file")
    if ".." in Path(value).parts or not value:
        raise ValueError("Unsafe staging path")
    path=(root/value).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        raise ValueError("Missing/unsafe staging file: "+value)
    return path

def image_data(path,limit,alpha_required=False):
    if path.suffix.lower() not in FORMATS:
        raise ValueError("Unsupported image suffix: "+path.name)
    size=path.stat().st_size
    if not 512 <= size <= limit:
        raise ValueError("Photo size invalid: "+path.name)
    data=path.read_bytes()
    Image.MAX_IMAGE_PIXELS=30_000_000
    try:
        with Image.open(io.BytesIO(data)) as im:
            if im.format != FORMATS[path.suffix.lower()]:
                raise ValueError("Image header/extension mismatch: "+path.name)
            if min(im.size) < 300 or max(im.size) < 500:
                raise ValueError("Image resolution too low: "+path.name)
            im.load()
            if alpha_required:
                if "A" not in im.getbands():
                    raise ValueError("Background removal has no alpha: "+path.name)
                low,high=im.getchannel("A").getextrema()
                if low != 0 or high != 255:
                    raise ValueError("Invalid transparency: "+path.name)
                bounds=im.getchannel("A").getbbox()
                if bounds is None or 0 in bounds[:2] or bounds[2]==im.width or bounds[3]==im.height:
                    raise ValueError("Cutout touches image edges; review complete aircraft outline: "+path.name)
    except (OSError,UnidentifiedImageError,Image.DecompressionBombError) as err:
        raise ValueError("Image decode failed: "+path.name) from err
    return data

def prepare(staging, repo):
    staging=Path(staging).resolve()
    repo=Path(repo).resolve()
    assets=repo/"app/src/main/assets"
    catalog=json.loads((assets/"catalog_lostarmour_2024_verified.json").read_text("utf-8"))
    models={x["id"]:x["name"] for x in catalog["entries"]}
    bulletin=assets/"bulletin30_2025.json"
    if bulletin.is_file():
        for row in json.loads(bulletin.read_text("utf-8"))["entries"]:
            if row["id"] in models and models[row["id"]]!=row["name"]:
                raise ValueError("Conflicting catalog model: "+row["id"])
            models[row["id"]]=row["name"]
    vsu=assets/"catalog_bpla_vsu_pdf.json"
    if vsu.is_file():
        for row in json.loads(vsu.read_text("utf-8"))["entries"]:
            if not row.get("targetId"):
                models[row["id"]]=row["name"]
    # Album-only aircraft are valid offline catalog cards too. Keep the same
    # reviewed alias and aircraft-only rules as photo_progress.py.
    import gzip
    album=repo/"research/source_catalogs/catalog_album_2026_original.dat"
    if album.is_file():
        source=repo/"app/src/main/java/dev/infend/bplainfo/DroneStore.java"
        aliases={}
        if source.is_file():
            code=source.read_text("utf-8")
            start=code.find("private static String albumAliasTarget(")
            end=code.find("private static void mergeAlbum(",start)
            if start>=0 and end>start:
                aliases={int(page):target for page,target in re.findall(
                    r'case (\d+):return "([^"]+)";',code[start:end])}
        for row in json.loads(gzip.decompress(album.read_bytes()))["entries"]:
            if row["type"] not in {"Самолётный","Коптерный","Барражирующий"}:
                continue
            if aliases.get(row["page"]) in models:
                continue
            if row["id"] not in models:
                models[row["id"]]=row["name"]
    manifest_file=assets/"bundled_photos.json"
    manifest=json.loads(manifest_file.read_text("utf-8"))
    if manifest.get("schema")!=2 or manifest.get("storage")!="apk-assets-only":
        raise ValueError("Wrong offline photo manifest")
    batch=json.loads((staging/"batch.json").read_text("utf-8"))
    if not isinstance(batch.get("photos"),list) or not batch["photos"]:
        raise ValueError("Empty photo batch")
    new=copy.deepcopy(manifest)
    used={x["sha256"] for g in new["items"].values() for x in g["files"]}
    originals={x["original_sha256"] for g in new["items"].values() for x in g["files"] if x.get("original_sha256")}
    paths={x["file"] for g in new["items"].values() for x in g["files"]}
    pending=[]
    angles_by_model={model:{str(x.get("angle_id","")).strip().lower() for x in group["files"] if x.get("angle_id")} for model,group in new["items"].items()}
    for entry in batch["photos"]:
        model=entry.get("model_id","")
        if not isinstance(model,str) or not MODEL_RE.fullmatch(model) or model not in models:
            raise ValueError("Unknown model: "+str(model))
        if entry.get("model_name")!=models[model]:
            raise ValueError("Model mismatch: "+model)
        name=entry.get("file_name","")
        if not isinstance(name,str) or not PHOTO_RE.fullmatch(name):
            raise ValueError("Invalid photo name")
        target="photos/"+name
        if target in paths:
            raise ValueError("Duplicate photo path: "+target)
        if (assets/target).exists():
            raise ValueError("Unindexed destination already exists: "+target)
        paths.add(target)
        status=entry.get("processing_status")
        if status not in STATES:
            raise ValueError("Only verified processing states are accepted")
        if entry.get("visual_review_approved") is not True:
            raise ValueError("Visual review not approved: "+target)
        model_note=None
        if status=="verified_unmodified_original":
            if entry.get("model_review_approved") is not True:
                raise ValueError("Exact model identity not approved: "+target)
            model_note=clean_text(entry.get("model_review_notes"),"model_review_notes",15)
        angle=entry.get("angle_id")
        if not isinstance(angle,str) or not ANGLE_RE.fullmatch(angle):
            raise ValueError("Explicit valid angle_id required: "+target)
        if entry.get("angle_review_approved") is not True:
            raise ValueError("Distinct angle visual review not approved: "+target)
        model_angles=angles_by_model.setdefault(model,set())
        if angle.lower() in model_angles:
            raise ValueError("Duplicate reviewed angle_id for model: "+model+" "+angle)
        model_angles.add(angle.lower())
        note=clean_text(entry.get("visual_review_notes"),"visual_review_notes",15)
        author=clean_text(entry.get("author"),"author")
        license_name=clean_text(entry.get("license"),"license")
        if license_name not in LICENSES:
            raise ValueError("License does not explicitly permit redistribution and edits")
        source=clean_text(entry.get("source"),"source",12)
        rights=clean_text(entry.get("rights_evidence"),"rights_evidence",30)
        if status!="verified_unmodified_original" and not any(x in rights.lower() for x in ("modify","modification","derivative","редакт")):
            raise ValueError("Rights for modifications not documented: "+target)
        original_path=staging_file(staging,entry.get("original"))
        prepared_path=staging_file(staging,entry.get("prepared"))
        if prepared_path.suffix.lower()!=Path(name).suffix.lower():
            raise ValueError("Prepared file extension mismatch")
        original=image_data(original_path,MAX_ORIGINAL_BYTES)
        prepared=image_data(prepared_path,MAX_PREPARED_BYTES,status=="background_removed_verified")
        original_sha=sha256(original)
        if not SHA_RE.fullmatch(entry.get("original_sha256","")) or entry["original_sha256"]!=original_sha:
            raise ValueError("Original SHA-256 mismatch: "+target)
        if original_sha in originals:
            raise ValueError("Same original reused as multiple photos or angles: "+target)
        originals.add(original_sha)
        digest=sha256(prepared)
        if status=="verified_unmodified_original" and digest!=original_sha:
            raise ValueError("Unmodified original bytes changed: "+target)
        expected_prepared=entry.get("prepared_sha256")
        if expected_prepared is not None and expected_prepared!=digest:
            raise ValueError("Prepared SHA-256 mismatch: "+target)
        if digest in used:
            raise ValueError("Duplicate photo bytes: "+target)
        used.add(digest)
        original_rel="photo_originals/"+model+"/"+original_sha+original_path.suffix.lower()
        for output in (assets/target,repo/original_rel):
            if not output.resolve().is_relative_to(repo) or output.is_symlink():
                raise ValueError("Output path escapes repository: "+str(output))
        gallery=new["items"].setdefault(model,{"name":models[model],"files":[]})["files"]
        if len(gallery)>=5:
            raise ValueError("Gallery photo limit exceeded: "+model)
        gallery.append({
            "file":target,"sha256":digest,"original_sha256":original_sha,
            "original_archive":original_rel,"author":author,"license":license_name,
            "source":source,"rights_evidence":rights,"processing_status":status,
            "visual_review_notes":note,"visual_review_approved":True,
            "angle_review_approved":True,"angle_id":angle
        })
        if model_note is not None:
            gallery[-1].update(model_review_approved=True,model_review_notes=model_note,
                               publication_status="verified",background_removed=False,
                               changes="None; source file embedded byte for byte")
        pending.append((assets/target,prepared,repo/original_rel,original))
    return manifest_file,new,pending

def apply_batch(manifest_path,manifest,pending,write=False):
    result={"mode":"apply" if write else "dry-run","photos":len(pending),
            "models":sorted({old.parent.name for _,_,old,_ in pending})}
    if not write:
        return result
    # Stage every file before replacing anything. A short write or failed replace
    # must never leave a partial photo, an orphan original or a truncated manifest.
    repo=manifest_path.resolve().parents[4]
    writes={}
    for photo_path,photo_data,original_path,original_data in pending:
        for path,data in ((photo_path,photo_data),(original_path,original_data)):
            if path in writes and writes[path]!=data:
                raise ValueError("Conflicting batch output: "+str(path))
            if path.exists() and path.read_bytes()!=data:
                raise ValueError("Existing file differs: "+str(path))
            writes[path]=data
    writes[manifest_path]=(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n").encode("utf-8")
    staged={}
    previous={}
    applied=[]
    try:
        for path,data in writes.items():
            if not path.resolve().is_relative_to(repo) or path.is_symlink():
                raise ValueError("Output path escapes repository: "+str(path))
            path.parent.mkdir(parents=True,exist_ok=True)
            previous[path]=path.read_bytes() if path.exists() else None
            with tempfile.NamedTemporaryFile(dir=path.parent,prefix=".photo-import-",delete=False) as temp:
                staged[path]=Path(temp.name)
                temp.write(data)
        for path,temp_path in staged.items():
            os.replace(temp_path,path)
            applied.append(path)
    except BaseException:
        for path in reversed(applied):
            if previous[path] is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(previous[path])
        raise
    finally:
        for temp_path in staged.values():
            temp_path.unlink(missing_ok=True)
    return result

def main():
    p=argparse.ArgumentParser(description="Offline batch photo import; defaults to dry-run")
    p.add_argument("--staging",required=True)
    p.add_argument("--repo",default=str(Path(__file__).resolve().parents[1]))
    p.add_argument("--apply",action="store_true")
    a=p.parse_args()
    try:
        manifest,photos,pending=prepare(a.staging,a.repo)
        print(json.dumps(apply_batch(manifest,photos,pending,a.apply),ensure_ascii=False,indent=2))
    except (ValueError,OSError,KeyError,json.JSONDecodeError) as err:
        raise SystemExit("FAIL: "+str(err))

if __name__=="__main__":
    main()
