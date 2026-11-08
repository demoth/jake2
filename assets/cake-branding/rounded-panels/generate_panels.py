#!/usr/bin/env python3
"""Generate neutral, rounded half-round nine-patch frame artwork.

Run from the repository root:
    python3 assets/cake-branding/rounded-panels/generate_panels.py --rasterize

The atlas is a review asset, not installed into the runtime skin. Both tiles
are 72x72, with 24px fixed corners. Thick/thin padding matches the existing
engine skin (24/12 source pixels). No palette tint is baked into the artwork.
"""

import argparse
import json
import math
import shutil
import subprocess
from pathlib import Path


HERE = Path(__file__).resolve().parent
TILE = 72
CELL = 24
CENTER = '#171717'
LIGHT_X = LIGHT_Y = -1 / math.sqrt(6)
LIGHT_Z = 2 / math.sqrt(6)
AMBIENT = 0.22
STEP = 0.5


def gray(light):
    value = round(255 * (AMBIENT + (1 - AMBIENT) * max(0, min(1, light))))
    return f'#{value:02x}{value:02x}{value:02x}'


def xy(x, y):
    return f'{x:.5f},{y:.5f}'


def point(cx, cy, r, angle):
    a = math.radians(angle)
    return xy(cx + r * math.cos(a), cy + r * math.sin(a))


def document(body, defs='', width=24, height=24):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}"><defs>{defs}</defs>{body}</svg>')


def piece(index, thickness, prefix):
    corners = {0: (24, 24, 180), 2: (0, 24, 270), 6: (24, 0, 90), 8: (0, 0, 0)}
    definitions, paths = [], []
    if index in corners:
        cx, cy, angle = corners[index]
        paths.append(f'<path fill="{CENTER}" d="M{cx},{cy} L{point(cx,cy,24,angle)} '
                     f'A24,24 0 0 1 {point(cx,cy,24,angle+90)} Z"/>')
    else:
        paths.append(f'<rect width="24" height="24" fill="{CENTER}"/>')
    if index == 4:
        return document(''.join(paths))
    for band in range(round(thickness / STEP)):
        d0, d1 = band * STEP, (band + 1) * STEP
        mid = (d0 + d1) / 2
        q = 1 - 2 * mid / thickness
        nz = math.sqrt(max(0, 1 - q*q))
        # Overlap by one complete band: adjacent antialiased boundaries must not
        # expose the dark center beneath the smooth rounded surface.
        d1 = min(thickness, d1 + STEP)
        if index in corners:
            cx, cy, angle = corners[index]
            ro, ri, rmid = 24 - d0, 24 - d1, 24 - mid
            mag = math.hypot(LIGHT_X, LIGHT_Y)
            ux, uy = LIGHT_X / mag, LIGHT_Y / mag
            g = f'{prefix}-{index}-{band}'
            a, b = nz * LIGHT_Z - q * mag, nz * LIGHT_Z + q * mag
            stops = [(0, gray(a)), (1, gray(b))]
            if min(a,b) < 0 < max(a,b):
                stops.insert(1, (-a/(b-a), gray(0)))
            definitions.append(f'<linearGradient id="{g}" gradientUnits="userSpaceOnUse" '
                               f'x1="{cx-rmid*ux:.5f}" y1="{cy-rmid*uy:.5f}" '
                               f'x2="{cx+rmid*ux:.5f}" y2="{cy+rmid*uy:.5f}">'+
                               ''.join(f'<stop offset="{t:.6f}" stop-color="{c}"/>' for t,c in stops)+
                               '</linearGradient>')
            paths.append(f'<path fill="url(#{g})" d="M{point(cx,cy,ro,angle)} '
                         f'A{ro:.5f},{ro:.5f} 0 0 1 {point(cx,cy,ro,angle+90)} '
                         f'L{point(cx,cy,ri,angle+90)} A{ri:.5f},{ri:.5f} 0 0 0 {point(cx,cy,ri,angle)} Z"/>')
        else:
            normal = {1:(0,-1),3:(-1,0),5:(1,0),7:(0,1)}[index]
            color = gray(nz * LIGHT_Z + q * (normal[0]*LIGHT_X + normal[1]*LIGHT_Y))
            if index in (1,7):
                y = d0 if index == 1 else CELL-d1
                paths.append(f'<rect x="0" y="{y:.5f}" width="24" height="{d1-d0:.5f}" fill="{color}"/>')
            else:
                x = d0 if index == 3 else CELL-d1
                paths.append(f'<rect x="{x:.5f}" y="0" width="{d1-d0:.5f}" height="24" fill="{color}"/>')
    return document(''.join(paths), ''.join(definitions))


def assemble(slices, width, height):
    """Compose fixed corners with stretchable straight edges and center."""
    import xml.etree.ElementTree as ET
    ns = {'s':'http://www.w3.org/2000/svg'}
    definitions, shapes = [], []
    xs, ys = [0,24,width-24], [0,24,height-24]
    widths, heights = [24,width-48,24], [24,height-48,24]
    for index, svg in enumerate(slices):
        root = ET.fromstring(svg)
        defs = root.find('s:defs',ns)
        definitions.extend(ET.tostring(child,encoding='unicode') for child in defs)
        content = ''.join(ET.tostring(n,encoding='unicode') for n in root if n is not defs)
        row,col = divmod(index,3)
        if widths[col] > 0 and heights[row] > 0:
            shapes.append(f'<g transform="translate({xs[col]} {ys[row]}) scale({widths[col]/24} {heights[row]/24})">{content}</g>')
    return document(''.join(shapes),''.join(definitions),width,height)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rasterize',action='store_true',help='Render PNG using Inkscape and Pillow')
    args=parser.parse_args()
    import xml.etree.ElementTree as ET
    ET.register_namespace('','http://www.w3.org/2000/svg')
    output=HERE
    build=Path('build/figma-ui/rounded-panels');build.mkdir(parents=True,exist_ok=True)
    manifest={'tileSize':72,'split':[24]*4,'cornerRadius':24,'center':CENTER,'sourceScale':3,'weights':{}}
    sheet_parts=[]
    for name,thickness,padding,x in [('thick',12,24,1),('thin',6,12,75)]:
        slices=[piece(i,thickness,name) for i in range(9)]
        tile=assemble(slices,72,72)
        (output/f'rounded-panel-{name}.svg').write_text(tile+'\n')
        root=ET.fromstring(tile)
        sheet_parts.append(f'<g transform="translate({x} 1)">'+''.join(ET.tostring(c,encoding='unicode') for c in root)+'</g>')
        manifest['weights'][name]={'rimThickness':thickness,'padding':[padding]*4,'slices':slices,'atlasXY':[x,1]}
        (build/f'preview-{name}.svg').write_text(assemble(slices,640,200))
    (output/'rounded-panels.svg').write_text(document(''.join(sheet_parts),width=148,height=76)+'\n')
    atlas=['rounded-panels.png','size: 148,76','format: RGBA8888','filter: Nearest,Nearest','repeat: none']
    for name,data in manifest['weights'].items():
        atlas.extend([f'rounded-panel-{name}','  rotate: false',f'  xy: {data["atlasXY"][0]}, 1','  size: 72, 72',
                      '  split: 24, 24, 24, 24','  pad: '+', '.join(map(str,data['padding'])),
                      '  orig: 72, 72','  offset: 0, 0','  index: -1'])
    (output/'rounded-panels.atlas').write_text('\n'.join(atlas)+'\n')
    (build/'manifest.json').write_text(json.dumps(manifest))
    if args.rasterize:
        from PIL import Image
        renderer=shutil.which('inkscape')
        if not renderer:parser.error('Inkscape and Pillow are required for --rasterize')
        for svg in [output/'rounded-panel-thick.svg',output/'rounded-panel-thin.svg',build/'preview-thick.svg',build/'preview-thin.svg']:
            # Supersample to retain clean edges in native 72px source artwork.
            size=(640,200) if svg.name.startswith('preview-') else (72,72)
            raster=build/('_render-'+svg.stem+'.png')
            subprocess.run([renderer,str(svg.resolve()),'--export-type=png',
                            '--export-filename='+str(raster.resolve()),
                            '--export-width='+str(size[0]*4),'--export-height='+str(size[1]*4)],check=True)
            with Image.open(raster) as image:
                image.convert('RGBA').resize(size,Image.Resampling.LANCZOS).save(svg.with_suffix('.png'))
            raster.unlink()
        # Pack the exact standalone pixels, rather than resampling the sheet
        # independently and changing edge alpha at the transparent gutters.
        sheet=Image.new('RGBA',(148,76))
        for name,x in [('thick',1),('thin',75)]:
            with Image.open(output/f'rounded-panel-{name}.png') as image:
                sheet.paste(image.convert('RGBA'),(x,1))
        sheet.save(output/'rounded-panels.png')
    print('Generated neutral thin/thick frames; 24px fixed corners; 24/12px content padding.')


if __name__=='__main__':main()
