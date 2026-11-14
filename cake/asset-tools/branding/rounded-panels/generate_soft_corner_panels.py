#!/usr/bin/env python3
"""Keep the three-shade frame face flat and fade the corner shade transitions.

Only top-right and bottom-left corner bands contain gradients. Each gradient
is constant near its endpoints, matching the unvarying straight edge pieces.
All geometry and nine-patch splits match the previous frame studies.
"""

import argparse
import json
import shutil
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

from generate_panels import document, assemble, point, CENTER
from generate_flat_panels import piece as flat_piece, ring, FACE, HIGHLIGHT, SHADOW


HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]


def gradient(name,x1,y1,x2,y2,stops):
    return (f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" '
            f'x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'+
            ''.join(f'<stop offset="{t}" stop-color="{color}"/>' for t,color in stops)+
            '</linearGradient>')


def piece(index,thickness,prefix):
    if index not in (2,6):
        return flat_piece(index,thickness)
    cx,cy,start=(0,24,270) if index==2 else (24,0,90)
    width=thickness*.20
    outer_radius=24-width/2
    inner_radius=24-thickness+width/2
    outer_id=f'{prefix}-outer-{index}'
    inner_id=f'{prefix}-inner-{index}'
    if index==2:
        outer_points=(0,24-outer_radius,outer_radius,24)
        inner_points=(0,24-inner_radius,inner_radius,24)
    else:
        outer_points=(24-outer_radius,0,24,outer_radius)
        inner_points=(24-inner_radius,0,24,inner_radius)
    definitions=gradient(outer_id,*outer_points,[(0,HIGHLIGHT),(.18,HIGHLIGHT),(.5,FACE),(.82,SHADOW),(1,SHADOW)])
    definitions+=gradient(inner_id,*inner_points,[(0,SHADOW),(.18,SHADOW),(.82,FACE),(1,FACE)])
    paths=[f'<path fill="{CENTER}" d="M{cx},{cy} L{point(cx,cy,24,start)} '
           f'A24,24 0 0 1 {point(cx,cy,24,start+90)} Z"/>',
           ring(cx,cy,24,24-thickness,start,start+90,FACE),
           ring(cx,cy,24,24-width,start,start+90,f'url(#{outer_id})'),
           ring(cx,cy,24-thickness+width,24-thickness,start,start+90,f'url(#{inner_id})')]
    return document(''.join(paths),definitions)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',action='store_true',
                        help='Render assets/ui/engine-panels.png (Inkscape and Pillow required)')
    args=parser.parse_args()
    ET.register_namespace('','http://www.w3.org/2000/svg')
    build=ROOT/'build/figma-ui/soft-corner-panels';build.mkdir(parents=True,exist_ok=True)
    manifest={'tileSize':72,'split':[24]*4,'cornerRadius':24,'center':CENTER,
              'rimColors':[HIGHLIGHT,FACE,SHADOW],'gradientCorners':[2,6],
              'sourceScale':3,'weights':{}}
    sheet=[]
    for name,t,padding,x in [('thick',12,24,1),('thin',6,12,75)]:
        slices=[piece(i,t,name) for i in range(9)]
        svg=assemble(slices,72,72)
        (HERE/f'soft-corner-panel-{name}.svg').write_text(svg+'\n')
        root=ET.fromstring(svg)
        sheet.append(f'<g transform="translate({x} 1)">'+''.join(ET.tostring(c,encoding='unicode') for c in root)+'</g>')
        manifest['weights'][name]={'rimThickness':t,'padding':[padding]*4,'slices':slices}
        (build/f'preview-{name}.svg').write_text(assemble(slices,640,200)+'\n')
    (HERE/'soft-corner-panels.svg').write_text(document(''.join(sheet),width=148,height=76)+'\n')
    (build/'manifest.json').write_text(json.dumps(manifest))
    if args.runtime:
        from PIL import Image
        renderer=shutil.which('inkscape')
        if not renderer:parser.error('Inkscape and Pillow are required for --runtime')
        packed=Image.new('RGBA',(148,76))
        for name,x in [('thick',1),('thin',75)]:
            svg=HERE/f'soft-corner-panel-{name}.svg'
            raster=build/f'_render-{name}.png'
            subprocess.run([renderer,str(svg.resolve()),'--export-type=png',
                            '--export-filename='+str(raster.resolve()),
                            '--export-width=288','--export-height=288'],check=True)
            with Image.open(raster) as image:
                tile=image.convert('RGBA').resize((72,72),Image.Resampling.LANCZOS)
            # Preserve exact tile alpha; do not resample the packed sheet.
            tile.save(build/f'panel-{name}.png')
            packed.paste(tile,(x,1))
            raster.unlink()
        shutil.copyfile(HERE/'soft-corner-panels.svg',HERE.parents[1]/'ui/engine-panels.svg')
        packed.save(ROOT/'assets/ui/engine-panels.png')
    print('Generated corner-only fades; flat faces, straight edges and fixed corners preserved.')


if __name__=='__main__':main()
