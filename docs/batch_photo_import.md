# Batch import, photo originals and licensing

## Current policy — confirmed 6 November 2026

This section supersedes older background-removal requirements below. Add **3–5 real photographs of the exact model without changing publisher image bytes**. Do not crop, upscale, enhance, remove backgrounds, re-encode, generate substitutes or synthesize views. Preserve publisher watermarks. The same image under two filenames is not two photographs.

Use `processing_status: verified_unmodified_original`. Set `original` and `prepared` to the same downloaded file. The importer enforces equality of the packaged SHA-256 and `original_sha256`, requires `model_review_approved: true` with meaningful `model_review_notes`, and keeps `background_removed: false`. Existing visual and distinct-angle review flags remain mandatory. Redistribution permission is required; editing permission is unnecessary when no edits are made.

This also applies during APK assembly. The build-time WebP packing introduced in v1.0.30 is disabled under the latest unchanged-byte requirement. `audit_apk_photos.py` rejects `optimized_from_verified_original` derivatives even when their archive and fidelity metrics are present. `optimize_apk_photos.py` is a historical utility, not part of release delivery.

Individually reviewed free photographer images under the **Unsplash License** and **Pexels License** are accepted alongside the licenses below. Verify the exact photo page and current official terms; do not include paid Unsplash+ or third-party sponsored images. The use is illustration in an informational aircraft catalog, not standalone image resale, a competing stock service, a wallpaper service, or an endorsement. Retain author, source, license evidence and exact published-rendition hashes in provenance. Images photographed *by* a drone do not depict that drone.

Inspect the actual image and the original publisher caption together. A caption mentioning an aircraft does not establish that the aircraft is visible: operators, control stations, launch tubes and unrelated aircraft in the same gallery do not replace model photos. Do not attach a generic-model photograph to an unconfirmed specific variant.

Put publisher and license links in `app/src/main/assets/photo_credits.json`, keyed by the packaged photo path. This is offline attribution data, not an image downloader or a request to add technical labels to the photo-only viewer. Keep download provenance outside Android assets in `research/photos/`.

The first batch is recorded in `research/photos/original_batch_2026_11_05.json`. The review of all 52 photos released in v1.0.27 is in `research/photos/original_policy_review_2026_11_05.json`; subsequent batches have numbered provenance files listed in `research/photos/README.md`. Previous rejection of an attempted cutout does not reject its untouched publisher photograph. Completed galleries do not complete the whole catalog.

`photo_progress.py` includes all aircraft cards from the base catalog, bulletin, VSU PDF and album. Its total is a card count, not a claim that every transliteration is a unique airframe. Explicit reviewed equivalents may share one gallery using a root `aliases` record with `name`, `target_id`, `model_review_approved: true` and meaningful `model_review_notes`. The target must be a canonical gallery in `items`; alias chains, unreviewed matches and variant substitutions are rejected. Aliases never add to physical photo totals. Recompute `research/photos/coverage_min3.json` after every import.

## Legacy processing modes and importer mechanics

This importer works only with **already downloaded, visually reviewed local files**. It does not download images or decide whether a UAV model or license is genuine. Never use Wikimedia/Wikipedia, runtime photo links or generated substitutes for real photographs.

Staging folder contains batch.json and original/prepared image files. Each record in photos[] requires: model_id, model_name (matching catalog), file_name, original, original_sha256, prepared, author, license, source (offline text), rights_evidence (explicit redistribution/modification permission), processing_status, visual_review_approved=true and visual_review_notes. Accepted licenses: CC0 1.0, CC BY 4.0, CC BY-SA 4.0, and verified U.S. Government public-domain work. New processing status is verified_clean_original (genuinely clean framing) or background_removed_verified (transparent alpha was visually reviewed). Do not claim either status without inspection. Per model 1 to 5 nonduplicate photos.

Run dry-run first:

    python3 scripts/batch_import_photos.py --staging /path/to/staging

Then, only after reviewing results:

    python3 scripts/batch_import_photos.py --staging /path/to/staging --apply
    python3 -m unittest discover -s tests -v

A complete batch adds the prepared bytes to app/src/main/assets/photos, archives untouched originals in photo_originals/<model_id>/ outside APK, and adds author, license, source, rights evidence, SHA-256 of both original and processed images, and visual-review status to bundled_photos.json.

Never add synthetic test fixtures to the real app. Never modify or erase user-selected local photos. Do not bump version or publish OTA without successful CI and confirmed new release content.

Original hashes must also be unique across the batch and existing galleries:
different crops or recompressed copies of one source are not distinct angles.
An optional `prepared_sha256` is checked against the exact prepared bytes.
Transparent cutouts need padding around the complete aircraft outline. Existing
unindexed destination files are never overwritten. Output paths cannot escape
the repository through symbolic links.

All writes are staged in the destination filesystem before any replacement.
Failed staging writes and failed replacements restore prior file contents and
remove new files and temporary fragments. This is process-failure recovery,
not a power-loss transaction.

Six unmodified downloaded source renditions and the next photo candidates are
documented in `research/photos/source_index.json` and `research/photos/README.md`.
They are research inputs, not new APK gallery photos.

## Mandatory reviewed angle identity

Every new photo must carry `angle_id` (3–61 lowercase ASCII letters, digits,
underscores or hyphens, starting with a letter/digit) and
`angle_review_approved: true`, in addition to `visual_review_approved: true`.
An angle ID represents a genuinely distinct verified camera viewpoint, **not** a
filename, crop, rotation, or duplicate original. Reusing the same angle ID for
the same model is rejected, including across existing and newly staged photos.
The importer preserves both review flags and the angle ID in the offline APK
manifest, allowing `scripts/photo_progress.py` to count qualifying photographs.
This metadata does not replace human inspection and must never be fabricated.
