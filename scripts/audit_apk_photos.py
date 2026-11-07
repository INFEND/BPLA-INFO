#!/usr/bin/env python3
"""Verify physical APK photo contents, not just manifest source text."""
import hashlib
import io
import json
import re
import sys
import zipfile
from PIL import Image, UnidentifiedImageError

PHOTO_PATTERN = re.compile(r"photos/[a-z0-9][a-z0-9_-]{2,100}\.(?:webp|png|jpg|jpeg)\Z")
LEGACY = ("assets/photo_sources.json",)
MAX_SIZE = 12_000_000
Image.MAX_IMAGE_PIXELS = 30_000_000
# Publisher originals are preserved in the repository. The APK may contain
# a quality-gated WebP derivative with signed source/hash metadata.
VALID_PROCESSING = {"needs_background_review", "background_removed_verified",
                    "verified_clean_original", "verified_unmodified_original",
                    "optimized_from_verified_original"}

def valid_image_header(file, binary):
    if file.endswith(".webp"):
        return binary[:4] == b"RIFF" and binary[8:12] == b"WEBP"
    if file.endswith(".png"):
        return binary.startswith(bytes.fromhex("89504e470d0a1a0a"))
    return binary.startswith(bytes.fromhex("ffd8ff"))

def verify_decodable_image(file, binary, processing_status):
    expected = "WEBP" if file.endswith(".webp") else ("PNG" if file.endswith(".png") else "JPEG")
    try:
        with Image.open(io.BytesIO(binary)) as image:
            if image.format != expected:
                raise ValueError("Photo format mismatch: " + file)
            width, height = image.size
            if min(width, height) < 300 or max(width, height) < 500:
                raise ValueError("Photo dimensions too small: " + file)
            image.load()
            if processing_status == "background_removed_verified":
                if "A" not in image.getbands():
                    raise ValueError("Background-removed photo has no alpha channel: " + file)
                low, high = image.getchannel("A").getextrema()
                if low > 0 or high < 255:
                    raise ValueError("Background-removed photo has no usable transparency: " + file)
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as error:
        raise ValueError("Photo decoding failed: " + file) from error

def audit(path):
    with zipfile.ZipFile(path) as apk:
        names = set(apk.namelist())
        for forbidden in LEGACY:
            if forbidden in names:
                raise ValueError("Old photo sources still embedded: " + forbidden)
        raw = apk.read("assets/bundled_photos.json")
        manifest = json.loads(raw)
        if manifest.get("schema") != 2 or manifest.get("storage") != "apk-assets-only":
            raise ValueError("APK is missing offline-only photo manifest")
        if any(token in json.dumps(manifest.get("items", {}), ensure_ascii=False).lower() for token in ("wikimedia", "http://", "https://")):
            raise ValueError("Remote photo references are forbidden")
        referenced = set()
        for model_id, model in manifest["items"].items():
            photos = model["files"]
            if not 1 <= len(photos) <= 5:
                raise ValueError("Invalid photo count: " + model_id)
            for record in photos:
                file = record["file"]
                status = record.get("processing_status")
                if status not in VALID_PROCESSING:
                    raise ValueError("Missing or invalid photo processing status: " + file)
                if status != "needs_background_review" and not re.fullmatch(r"[a-f0-9]{64}", record.get("original_sha256", "")):
                    raise ValueError("Verified photo lacks original SHA-256: " + file)
                if status == "verified_unmodified_original":
                    if record["sha256"] != record["original_sha256"]:
                        raise ValueError("Unmodified original bytes changed: " + file)
                    if record.get("model_review_approved") is not True or record.get("visual_review_approved") is not True:
                        raise ValueError("Original model/photo identity not reviewed: " + file)
                if status == "optimized_from_verified_original":
                    optimization = record.get("optimization")
                    source_hash = record.get("original_sha256", "")
                    if (not isinstance(optimization, dict) or
                            record.get("model_review_approved") is not True or
                            record.get("visual_review_approved") is not True or
                            record.get("background_removed") is True or
                            not record.get("original_archive") or
                            file != "photos/packed_" + source_hash[:24] + ".webp" or
                            optimization.get("source_sha256") != source_hash or
                            optimization.get("algorithm") != "adaptive-webp-ssim-v1" or
                            not isinstance(optimization.get("quality"), int) or
                            optimization["quality"] not in (78, 84, 89, 93) or
                            not isinstance(optimization.get("source_bytes"), int) or
                            not isinstance(optimization.get("packed_bytes"), int) or
                            not isinstance(optimization.get("ssim_luma"), (int, float)) or
                            not isinstance(optimization.get("psnr_luma_db"), (int, float)) or
                            not 0.974 <= optimization["ssim_luma"] <= 1.00001 or
                            not 33.5 <= optimization["psnr_luma_db"] <= 99.01):
                        raise ValueError("Invalid verified WebP optimization provenance: " + file)
                if not PHOTO_PATTERN.fullmatch(file):
                    raise ValueError("Invalid embedded photo path: " + file)
                if file in referenced:
                    raise ValueError("Duplicate embedded photo: " + file)
                referenced.add(file)
                if not all(record.get(key) for key in ("author", "license", "source", "sha256")):
                    raise ValueError("Missing photo credits: " + file)
                asset = "assets/" + file
                if asset not in names:
                    raise ValueError("Missing photo binary in APK: " + asset)
                binary = apk.read(asset)
                if status == "optimized_from_verified_original":
                    if (len(binary) != optimization["packed_bytes"] or
                            optimization["source_bytes"] <= len(binary) / 0.92):
                        raise ValueError("Unverified compression size/ratio: " + file)
                if not 512 <= len(binary) <= MAX_SIZE:
                    raise ValueError("Invalid photo size: " + asset)
                if not valid_image_header(file, binary):
                    raise ValueError("Invalid image format: " + asset)
                verify_decodable_image(file, binary, status)
                if hashlib.sha256(binary).hexdigest() != record["sha256"]:
                    raise ValueError("Photo checksum mismatch: " + asset)
        actual = {name.removeprefix("assets/") for name in names
                  if name.startswith("assets/photos/") and not name.endswith("/")}
        if actual != referenced:
            raise ValueError("Unindexed photo binaries: " + repr(actual - referenced))
        for alias,review in manifest.get('aliases',{}).items():
            target=review.get('target_id')
            if (alias in manifest['items'] or target not in manifest['items'] or
                    alias==target or review.get('model_review_approved') is not True or
                    len(review.get('model_review_notes','').strip())<15):
                raise ValueError('Invalid or unreviewed photo gallery alias: '+alias)
        print(f"PASS: {path}: {len(manifest['items'])} model galleries, {len(referenced)} physical photos decoded with Pillow, no legacy photo manifest")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: audit_apk_photos.py APK")
    try:
        audit(sys.argv[1])
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        raise SystemExit("FAIL: " + str(error))
