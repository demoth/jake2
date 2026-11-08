#!/usr/bin/env python3
"""Generate all desktop icons from the selected Cake geometry.

From the repository root: python3 assets/cake-branding/generate_app_icons.py
Requires Inkscape and Pillow. SVG rendering is a development dependency only.
"""

import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw
from generate_logo import generate


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SIZES = (16, 24, 32, 48, 64, 128, 256, 512, 1024)


def main():
    renderer = shutil.which('inkscape')
    if not renderer:
        raise SystemExit('Inkscape and Pillow are required to generate app icons')
    config = json.loads((HERE / 'app-icons.json').read_text())
    recipe = json.loads((HERE / config['recipe']).read_text())
    build = ROOT / 'build/captures/design-round-1/slice-3'
    build.mkdir(parents=True, exist_ok=True)
    ET.register_namespace('', 'http://www.w3.org/2000/svg')
    ns = '{http://www.w3.org/2000/svg}'
    sheet = Image.new('RGB', (900, 470), '#191412')
    labels = ImageDraw.Draw(sheet)
    for col, (app, colors) in enumerate(config['apps'].items()):
        parameters = dict(recipe['mark_parameters'])
        parameters.update({key: colors[key] for key in ('gear_color', 'rim_color')})
        svg = ET.fromstring(generate(parameters))
        # Runtime SVGs use explicit fills; the experimental CSS variables are unnecessary.
        svg.attrib.pop('style', None)
        svg.remove(svg.find(ns + 'style'))
        svg.find(ns + 'title').text = 'Cake ' + colors['label'] + ' app icon'
        metadata = svg.find(ns + 'metadata')
        metadata.text = json.dumps({'app': app, 'mark_parameters': parameters, 'tile': recipe['tile']}, sort_keys=True)
        tile = recipe['tile']
        background = ET.Element(ns + 'rect', {
            'id': 'icon-background', 'width': str(tile['size']), 'height': str(tile['size']),
            'rx': str(tile['corner_radius']), 'fill': tile['background'],
        })
        x, y = tile['mark_offset']
        mark = ET.Element(ns + 'g', {'id': 'mark',
            'transform': f'translate({x} {y}) scale({tile["mark_size"] / parameters["size"]})'})
        for child in list(svg):
            if child.tag == ns + 'g':
                svg.remove(child)
                mark.append(child)
        svg.extend([background, mark])
        output = ROOT / f'cake/cake-{app}/icons'
        output.mkdir(parents=True, exist_ok=True)
        source = output / 'logo.svg'
        source.write_text(ET.tostring(svg, encoding='unicode') + '\n')
        raster = build / f'{app}-render.png'
        subprocess.run([renderer, str(source), '--export-type=png',
                        '--export-filename=' + str(raster), '--export-width=2048', '--export-height=2048'], check=True)
        with Image.open(raster) as image:
            master = image.convert('RGBA')
        resources = ROOT / f'cake/cake-{app}/src/main/resources/icons'
        resources.mkdir(parents=True, exist_ok=True)
        images = {size: master.resize((size, size), Image.Resampling.LANCZOS) for size in SIZES}
        for size in (16, 32, 64, 128, 256):
            images[size].save(resources / f'icon-{size}.png')
        images[512].save(output / 'logo.png')
        images[256].save(output / 'logo.ico', sizes=[(s, s) for s in SIZES if s <= 256],
                         append_images=[images[s] for s in SIZES if s < 256])
        images[1024].save(output / 'logo.icns')
        raster.unlink()
        sheet.paste(images[256], (col * 300 + 22, 20), images[256])
        labels.text((col * 300 + 22, 292), colors['label'], fill='#FFF3E3')
        cursor = col * 300 + 22
        for size in (16, 32, 64, 128):
            # At native pixels: the small exports are part of the review.
            sheet.paste(images[size], (cursor, 318), images[size])
            cursor += size + 10
    sheet.save(build / 'app-icons.png')
    # Existing generic assets remain the Strawberry brand/Fullgame icon.
    fullgame = ROOT / 'cake/cake-fullgame/icons'
    legacy = ROOT / 'assets/icons'
    for ext in ('png', 'ico'):
        shutil.copyfile(fullgame / f'logo.{ext}', legacy / f'logo.{ext}')
    shutil.copyfile(ROOT / 'cake/cake-fullgame/src/main/resources/icons/icon-32.png', legacy / 'logo-32.png')
    print('Generated Fullgame/Viewer/Client window and PNG/ICO/ICNS packaging icons.')


if __name__ == '__main__':
    main()
