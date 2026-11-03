# Batch import, photo originals and licensing

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
