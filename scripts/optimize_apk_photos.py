#!/usr/bin/env python3
"""Build-time photo packing. Never changes the committed publisher originals.

The working tree is disposable in CI: source assets are replaced only AFTER the
unmodified-source tests, and only optimized bytes and their derived manifest
are packaged in the APK. The research/photo_originals archives are untouched.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import tempfile

import cv2
import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MANIFEST = ASSETS / "bundled_photos.json"
SAFE = re.compile(r"photos/[a-z0-9][a-z0-9_-]{2,100}\.(?:jpg|jpeg|png|webp)\Z")
Image.MAX_IMAGE_PIXELS = 30_000_000

# Quality is measured against the source AFTER display-scale reduction. This
# prevents an oversized publisher file from forcing an oversized phone APK.
LONG_EDGE = 2560
MIN_EDGE = 320
QUALITIES = (78, 84, 89, 93)
MIN_SSIM = 0.974
MIN_PSNR = 33.5
MIN_SAVING = 0.08


def digest(data):
    return hashlib.sha256(data).hexdigest()


def expected_dimensions(width, height):
    short, long = min(width, height), max(width, height)
    if short < 300 or long < 500:
        raise ValueError("Original photograph has too few pixels")
    target = max(LONG_EDGE, round(MIN_EDGE * long / short))
    if long <= target:
        return width, height
    ratio = target / long
    return max(1, round(width * ratio)), max(1, round(height * ratio))


def preview_gray(image, longest=896):
    width, height = image.size
    scale = min(1.0, longest / max(width, height))
    rgb = np.asarray(image.convert("RGB"))
    if scale < 1:
        rgb = cv2.resize(rgb, (max(1, round(width * scale)),
                                max(1, round(height * scale))),
                         interpolation=cv2.INTER_AREA)
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32)


def fidelity(reference, candidate):
    """Local-luminance SSIM plus PSNR on the same display-sized image."""
    a = preview_gray(reference)
    b = preview_gray(candidate)
    if a.shape != b.shape:
        raise ValueError("Candidate dimensions changed unexpectedly")
    c1, c2 = 6.5025, 58.5225
    mu_a = cv2.GaussianBlur(a, (7, 7), 1.3)
    mu_b = cv2.GaussianBlur(b, (7, 7), 1.3)
    var_a = cv2.GaussianBlur(a * a, (7, 7), 1.3) - mu_a * mu_a
    var_b = cv2.GaussianBlur(b * b, (7, 7), 1.3) - mu_b * mu_b
    cov = cv2.GaussianBlur(a * b, (7, 7), 1.3) - mu_a * mu_b
    ssim = float(np.mean(((2 * mu_a * mu_b + c1) * (2 * cov + c2)) /
                         ((mu_a * mu_a + mu_b * mu_b + c1) *
                          (var_a + var_b + c2))))
    mse = float(np.mean((a - b) ** 2))
    psnr = 99.0 if mse == 0 else float(10 * np.log10(255.0 ** 2 / mse))
    return ssim, psnr


def optimize(data):
    with Image.open(io.BytesIO(data)) as source:
        if source.format not in {"JPEG", "WEBP", "PNG"}:
            raise ValueError("Unsupported publisher photo format")
        source.load()
        shown = ImageOps.exif_transpose(source)
        mode = "RGBA" if "A" in shown.getbands() else "RGB"
        visible = shown.convert(mode)
        dimensions = expected_dimensions(*visible.size)
        if visible.size != dimensions:
            visible = visible.resize(dimensions, Image.Resampling.LANCZOS)
        for quality in QUALITIES:
            out = io.BytesIO()
            visible.save(out, format="WEBP", quality=quality, method=5,
                         exact=(mode == "RGBA"))
            payload = out.getvalue()
            if len(payload) >= len(data) * (1 - MIN_SAVING):
                continue
            with Image.open(io.BytesIO(payload)) as check:
                if check.format != "WEBP" or check.size != visible.size:
                    raise ValueError("Compressed WebP failed decoding")
                check.load()
                ssim, psnr = fidelity(visible, check)
            if ssim >= MIN_SSIM and psnr >= MIN_PSNR:
                return payload, {"quality": quality, "width": dimensions[0],
                                 "height": dimensions[1],
                                 "ssim_luma": round(ssim, 5),
                                 "psnr_luma_db": round(psnr, 2)}
    return None, None


def pack(root=ROOT, assets=ASSETS):
    manifest_path = assets / "bundled_photos.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schema") != 2 or manifest.get("storage") != "apk-assets-only":
        raise ValueError("Unexpected APK photo manifest")
    replacements = {}
    originals = set()
    final_paths = set()
    count = saved = before = after = 0
    # First validate ALL original bytes. Any error aborts with no file changes.
    for model in manifest["items"].values():
        for photo in model["files"]:
            name = photo["file"]
            if not SAFE.fullmatch(name) or name in originals:
                raise ValueError("Unsafe/duplicate photo path: " + name)
            originals.add(name)
            original_path = root / photo.get("original_archive", "")
            if not original_path.is_file() or not original_path.resolve().is_relative_to(root.resolve()):
                raise ValueError("Missing safe original archive: " + name)
            archived = original_path.read_bytes()
            original = (assets / name).read_bytes()
            expected = photo["original_sha256"]
            if (digest(original) != photo["sha256"] or digest(archived) != expected or
                    original != archived or photo.get("processing_status") != "verified_unmodified_original"):
                raise ValueError("Unverified or changed publisher bytes: " + name)
            before += len(original)
            payload, details = optimize(original)
            if payload is not None:
                next_name = "photos/packed_" + expected[:24] + ".webp"
                if not SAFE.fullmatch(next_name) or next_name in final_paths:
                    raise ValueError("Duplicate optimized path: " + next_name)
                details.update({"algorithm": "adaptive-webp-ssim-v1",
                                "source_sha256": expected, "source_bytes": len(original),
                                "packed_bytes": len(payload)})
                photo["file"] = next_name
                photo["sha256"] = digest(payload)
                photo["processing_status"] = "optimized_from_verified_original"
                photo["optimization"] = details
                photo["changes"] = ("Display-size resize and quality-gated WebP compression; "
                                    "publisher original preserved in original_archive")
                replacements[next_name] = payload
                final_paths.add(next_name)
                count += 1
                saved += len(original) - len(payload)
                after += len(payload)
            else:
                if name in final_paths:
                    raise ValueError("Duplicate original path: " + name)
                final_paths.add(name)
                after += len(original)
    # Write only after every source and target has been checked.
    for name, payload in replacements.items():
        path = assets / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
        if digest(path.read_bytes()) != digest(payload):
            raise ValueError("Failed to write optimized photo " + name)
    for name in originals - final_paths:
        (assets / name).unlink()
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                             encoding="utf-8")
    return {"photos": len(originals), "optimized": count, "source_bytes": before,
            "packed_bytes": after, "saved_bytes": saved,
            "saved_percent": round(100 * saved / before, 2) if before else 0}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", default="dist/photo_optimization_report.json")
    args = parser.parse_args()
    summary = pack()
    path = Path(args.report)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")
    print("APK photo packing:", json.dumps(summary, ensure_ascii=False))
    if summary["photos"] == 0:
        raise SystemExit("No offline photos to optimize")


if __name__ == "__main__":
    main()
