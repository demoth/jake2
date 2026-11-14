#!/usr/bin/env python3
"""Capture standalone Viewer resource screens using local Quake II data.

Build first: ./gradlew :cake:cake-viewer:installDist
Requires a graphics display and Pillow. Game data and outputs stay under build/.
"""

import argparse
import json
from pathlib import Path
import struct
import subprocess

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]


def pak_entries(file):
    with file.open('rb') as source:
        magic, offset, length = struct.unpack('<4sii', source.read(12))
        if magic != b'PACK' or length % 64:
            raise ValueError(f'Invalid PAK directory: {file}')
        source.seek(offset)
        result = {}
        for _ in range(length // 64):
            name, start, size = struct.unpack('<56sii', source.read(64))
            result[name.split(b'\0', 1)[0].decode('ascii').lower()] = (start, size)
        return result


def read_entry(file, entries, name):
    offset, size = entries[name.lower()]
    with file.open('rb') as source:
        source.seek(offset)
        data = source.read(size)
    if len(data) != size:
        raise ValueError(f'Truncated PAK entry: {name}')
    return data


def sample_pak(file, entries):
    directory = []
    with file.open('wb') as target:
        target.write(b'\0' * 12)
        for name, data in entries:
            directory.append(struct.pack('<56sii', name.encode('ascii'), target.tell(), len(data)))
            target.write(data)
        offset = target.tell()
        target.write(b''.join(directory))
        target.seek(0)
        target.write(struct.pack('<4sii', b'PACK', offset, len(directory) * 64))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir', type=Path, required=True, help='Local baseq2 directory containing pak0.pak and players/')
    parser.add_argument('--cin', type=Path, help='Optional CIN file; defaults to baseq2/video/ntro.cin if present')
    parser.add_argument('--viewer', type=Path, default=ROOT / 'cake/cake-viewer/build/install/cake-viewer/bin/cake-viewer')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/captures/design-round-1/slice-4/viewer')
    args = parser.parse_args()
    game = args.game_dir.resolve()
    viewer = args.viewer.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    resources = output / 'resources/baseq2'
    resources.mkdir(parents=True, exist_ok=True)
    pak = game / 'pak0.pak'
    entries = pak_entries(pak)
    # The resolver discovers this baseq2 root and reads map/sprite dependencies
    # from the original archive; only the opened fixture files are extracted.
    link = resources / 'pak0.pak'
    if link.is_symlink():
        link.unlink()
    if link.exists():
        raise ValueError(f'Refusing to replace a non-symlink: {link}')
    link.symlink_to(pak)
    paths = {}
    for kind, name in {
        'map': 'maps/base1.bsp', 'image': 'pics/inventory.pcx',
        'texture': 'textures/e1u1/metal2_1.wal', 'sprite': 'sprites/s_explod.sp2',
        'sound': 'sound/weapons/blastf1a.wav', 'text': 'default.cfg',
    }.items():
        target = resources / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(read_entry(pak, entries, name))
        paths[kind] = target
    sample = resources / 'review-samples.pak'
    sample_pak(sample, [
        ('inventory.pcx', paths['image'].read_bytes()),
        ('metal-wall.wal', paths['texture'].read_bytes()),
        ('a-long-config-filename-for-label-wrapping.cfg', paths['text'].read_bytes()),
        ('blaster.wav', paths['sound'].read_bytes()),
        ('notes/default.cfg', paths['text'].read_bytes()),
    ])
    cases = [('empty', None, (1024, 768)), ('archive', pak, (1024, 768)),
             ('archive-files', sample, (1024, 768)),
             ('model', game / 'players/male/tris.md2', (1024, 768))]
    cases += [(kind, path, (1024, 768)) for kind, path in paths.items()]
    cin = args.cin.resolve() if args.cin else game / 'video/ntro.cin'
    skipped = []
    if cin.is_file():
        cases.append(('cinematic', cin, (1024, 768)))
    elif args.cin:
        raise FileNotFoundError(cin)
    else:
        skipped.append('Cinematic: no local video/ntro.cin; pass --cin to include it.')
    cases += [('model-narrow', game / 'players/male/tris.md2', (800, 600)),
              ('archive-wide', pak, (1440, 900))]
    manifest = {'viewer': str(viewer), 'gameDirectory': str(game), 'captures': [], 'skipped': skipped,
                'scope': 'Standalone engine UI; initial states. Error/hover/focus states require interactive follow-up.'}
    for name, input_file, size in cases:
        target = output / f'{name}.png'
        command = [str(viewer)] + ([str(input_file)] if input_file else [])
        command += ['--capture', str(target), '--size', f'{size[0]}x{size[1]}']
        result = subprocess.run(command, cwd=resources.parent, capture_output=True, text=True, timeout=45)
        (output / f'{name}.log').write_text(result.stdout + result.stderr)
        if result.returncode:
            raise RuntimeError(f'{name}: exit {result.returncode}; see {output / (name + ".log")}')
        if 'using engine checkerboard' in result.stdout + result.stderr:
            raise RuntimeError(f'{name}: missing texture dependencies; see {output / (name + ".log")}')
        with Image.open(target) as screenshot:
            if screenshot.size != size:
                raise ValueError(f'{name}: expected {size}, got {screenshot.size}')
        manifest['captures'].append({'name': name, 'input': str(input_file) if input_file else None,
                                     'size': list(size), 'image': target.name, 'command': command,
                                     'workingDirectory': str(resources.parent)})
        print(f'{name}: {size[0]}x{size[1]}', flush=True)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    rows = (len(cases) + 2) // 3
    sheet = Image.new('RGB', (1200, rows * 330), '#191412')
    draw = ImageDraw.Draw(sheet)
    for index, (name, _, size) in enumerate(cases):
        x, y = (index % 3) * 400, (index // 3) * 330
        draw.text((x + 10, y + 8), f'{name} / {size[0]}x{size[1]}', fill='#FFF3E3')
        with Image.open(output / f'{name}.png') as screenshot:
            screenshot.thumbnail((390, 296), Image.Resampling.LANCZOS)
            sheet.paste(screenshot, (x + 5, y + 28))
    sheet.save(output / 'overview.png')
    print('Overview and capture manifest:', output)


if __name__ == '__main__':
    main()
