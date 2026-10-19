"""Create labeled review sheets; never approve, copy or rewrite reference images.

Usage: python tools/review_release_goldens.py --candidates <pulled screens> --output <build directory>
Requires Pillow. Every image listed in the existing manifest must be present.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser()
parser.add_argument('--candidates', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--manifest', type=Path, default=Path('docs/golden-reference-manifest.json'))
args = parser.parse_args()
entries = json.loads(args.manifest.read_text(encoding='utf-8-sig'))['imagesManifest']
missing = [entry['file'] for entry in entries if not (args.candidates / entry['file']).is_file()]
if missing:
    raise SystemExit(f'Missing {len(missing)} required candidates: {missing[:10]}')
args.output.mkdir(parents=True, exist_ok=True)
font = ImageFont.load_default(size=13)
index = []
for family in ('home', 'diary', 'digital', 'onboarding', 'settings', 'place_picker'):
    names = sorted(entry['file'] for entry in entries if entry['file'].startswith(family + '_'))
    for page, start in enumerate(range(0, len(names), 12), 1):
        sheet = Image.new('RGB', (1200, 2160), '#e8e8ed')
        draw = ImageDraw.Draw(sheet)
        selected = names[start:start + 12]
        for offset, name in enumerate(selected):
            x, y = (offset % 4) * 300, (offset // 4) * 720
            with Image.open(args.candidates / name) as source:
                original_size = source.size
                image = source.convert('RGB')
                image.thumbnail((296, 680), Image.Resampling.NEAREST)
                sheet.paste(image, (x + (300 - image.width) // 2, y + 32))
            label = name.removesuffix('.png').replace(family + '_', '', 1)
            draw.text((x + 4, y + 4), label, fill='black', font=font)
            index.append(dict(file=name, size=original_size, sheet=f'{family}-{page:02}.png'))
        sheet.save(args.output / f'{family}-{page:02}.png')
(args.output / 'review-index.json').write_text(json.dumps(index, indent=2) + '\n', encoding='utf-8')
print(f'{len(index)} candidates indexed for visual review; references unchanged.')
