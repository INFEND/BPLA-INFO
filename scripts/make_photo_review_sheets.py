#!/usr/bin/env python3
"""Prepare non-destructive contact sheets for original UAV photo inspection.

Never imports photos into an APK, never performs generative image editing,
and never counts originals as reviewed cutouts. Input images are read only.
"""
import argparse
import hashlib
import json
import math
import re
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps, UnidentifiedImageError

SOURCE_FILES = (
    'research/photos/source_index.json',
    'research/photos/armyinform_review.json',
)
TILE_WIDTH = 448
TILE_HEIGHT = 410
COLUMNS = 3
MODEL_ID = re.compile(r'[a-z0-9][a-z0-9_-]{2,100}\Z')
HASH = re.compile(r'[a-f0-9]{64}\Z')


def load_candidates(repo):
    repo = Path(repo).resolve()
    originals = (repo / 'research/photos/originals').resolve()
    if not originals.is_dir():
        raise ValueError('Originals directory missing')
    groups = defaultdict(list)
    seen_hashes = set()
    for source in SOURCE_FILES:
        path = repo / source
        if not path.is_file():
            raise ValueError('Missing source index: ' + source)
        doc = json.loads(path.read_text('utf-8'))
        for item in doc.get('entries', []):
            mid = item.get('model_id', '')
            if not isinstance(mid, str) or not MODEL_ID.fullmatch(mid):
                raise ValueError('Invalid model ID in ' + source)
            name = item.get('model_name')
            if not isinstance(name, str) or not name.strip():
                raise ValueError('Missing model name in ' + source)
            sha = item.get('sha256', '')
            if not isinstance(sha, str) or not HASH.fullmatch(sha):
                raise ValueError('Missing valid SHA256 for ' + str(item.get('source_id')))
            if sha in seen_hashes:
                raise ValueError('Repeated original image SHA256: ' + sha)
            seen_hashes.add(sha)
            rel = item.get('archived_original', '')
            if not isinstance(rel, str) or '\\' in rel or not rel.startswith('research/photos/originals/'):
                raise ValueError('Unsafe image path')
            file_path = (repo / rel).resolve()
            if not file_path.is_relative_to(originals) or not file_path.is_file():
                raise ValueError('Original missing or outside archive: ' + rel)
            if file_path.stat().st_size > 35_000_000:
                raise ValueError('Original too large: ' + rel)
            data = file_path.read_bytes()
            if hashlib.sha256(data).hexdigest() != sha:
                raise ValueError('Original checksum mismatch: ' + rel)
            try:
                with Image.open(file_path) as image:
                    dimensions = image.size
                    image.verify()
                expected = tuple(item.get('dimensions', []))
                if expected and dimensions != expected:
                    raise ValueError('Original dimensions mismatch: ' + rel)
                if min(dimensions) < 250:
                    raise ValueError('Original too small: ' + rel)
            except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as exc:
                raise ValueError('Original image invalid: ' + rel) from exc
            record = {
                'model_id': mid,
                'model_name': name,
                'source_id': item.get('source_id', ''),
                'view': item.get('view', 'not recorded'),
                'author': item.get('author', ''),
                'license': item.get('license', ''),
                'notes': item.get('visual_review_notes', item.get('visual_notes', '')),
                'publication_status': item.get('publication_status', 'not approved'),
                'sha256': sha,
                'file': rel,
                'size': list(dimensions),
            }
            groups[mid].append(record)
    return groups


def font(size):
    for candidate in (
        '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
        '/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf',
    ):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    return ImageFont.load_default()


def render(groups, repo, out):
    repo = Path(repo).resolve()
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    header_font, regular_font = font(19), font(14)
    manifest = {'schema': 1, 'purpose': 'Research review only; none of these is an approved APK photo', 'models': {}}
    for model_id, originals in sorted(groups.items()):
        if not originals:
            continue
        rows = math.ceil(len(originals) / COLUMNS)
        canvas = Image.new('RGB', (TILE_WIDTH * COLUMNS, TILE_HEIGHT * rows + 54), '#f4f5f8')
        draw = ImageDraw.Draw(canvas)
        draw.rectangle((0, 0, canvas.width, 54), fill='#17212e')
        title = originals[0]['model_name'] + ' | ORIGINALS, NOT PUBLISHED | ' + str(len(originals))
        draw.text((18, 15), title, fill='white', font=header_font)
        for i, item in enumerate(originals):
            ox = i % COLUMNS * TILE_WIDTH
            oy = (i // COLUMNS) * TILE_HEIGHT + 54
            draw.rectangle((ox + 9, oy + 8, ox + TILE_WIDTH - 9, oy + TILE_HEIGHT - 8), fill='#ffffff')
            source = repo / item['file']
            with Image.open(source) as image:
                sample = ImageOps.contain(image.convert('RGB'), (TILE_WIDTH - 38, 272), Image.Resampling.LANCZOS)
            px = ox + (TILE_WIDTH - sample.width) // 2
            py = oy + 18 + (272 - sample.height) // 2
            canvas.paste(sample, (px, py))
            def line(y, text):
                draw.text((ox + 20, oy + y), text[:47], fill='#17212e', font=regular_font)
            line(304, str(i + 1) + '. ' + str(item['source_id']))
            line(327, 'View: ' + str(item['view']))
            line(350, 'SHA256: ' + item['sha256'][:20] + '...')
            line(373, 'Status: NOT APPROVED')
        sheet = model_id + '.jpg'
        canvas.save(out / sheet, 'JPEG', quality=90, optimize=True)
        manifest['models'][model_id] = {'name': originals[0]['model_name'], 'sheet': sheet,
                                        'count_originals': len(originals), 'photos': originals}
    (out / 'index.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    total = sum(g['count_originals'] for g in manifest['models'].values())
    (out / 'SUMMARY.txt').write_text(
        'Original photo inspection only\nModels: ' + str(len(manifest['models'])) +
        '\nSource originals: ' + str(total) +
        '\nApproved cutouts from this tool: 0\nPhotos imported into APK by this tool: 0\n', 'utf-8')
    return {'models': len(manifest['models']), 'originals': total, 'sheets': len(manifest['models'])}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    result = render(load_candidates(args.repo), args.repo, args.out)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
