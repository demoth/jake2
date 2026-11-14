#!/usr/bin/env python3
"""Generate a three-shade prototype beside the smooth rounded frame study.

Run from the repository root. Same 72px tiles, 24px fixed corners, and 12/6px
rims as the smooth study. White/light gray/dark gray are the three rim paints;
the flat dark center is separate. Only ordinary edge antialiasing adds shades.
"""

import json
from pathlib import Path
import xml.etree.ElementTree as ET

from generate_panels import document, assemble, point, CENTER


HERE=Path(__file__).resolve().parent
FACE='#C8C8C8'
HIGHLIGHT='#FFFFFF'
SHADOW='#707070'


def ring(cx,cy,outer,inner,start,end,color):
    return (f'<path fill="{color}" d="M{point(cx,cy,outer,start)} '
            f'A{outer},{outer} 0 0 1 {point(cx,cy,outer,end)} '
            f'L{point(cx,cy,inner,end)} A{inner},{inner} 0 0 0 {point(cx,cy,inner,start)} Z"/>')


def piece(index,thickness):
    corners={0:(24,24,180),2:(0,24,270),6:(24,0,90),8:(0,0,0)}
    # The broad face dominates. Highlight and shadow occupy small, unequal
    # directional areas, rather than three equal concentric decorative rings.
    highlight_width=thickness*.20
    return_width=thickness*.20
    paths=[]
    if index in corners:
        cx,cy,start=corners[index]
        paths.append(f'<path fill="{CENTER}" d="M{cx},{cy} L{point(cx,cy,24,start)} '
                     f'A24,24 0 0 1 {point(cx,cy,24,start+90)} Z"/>')
        paths.append(ring(cx,cy,24,24-thickness,start,start+90,FACE))
        if index==0:
            paths.append(ring(cx,cy,24,24-highlight_width,180,270,HIGHLIGHT))
            paths.append(ring(cx,cy,24-thickness+return_width,24-thickness,180,270,SHADOW))
        elif index==8:
            paths.append(ring(cx,cy,24,24-highlight_width,0,90,SHADOW))
        elif index==2:
            paths.append(ring(cx,cy,24,24-highlight_width,270,315,HIGHLIGHT))
            paths.append(ring(cx,cy,24,24-highlight_width,315,360,SHADOW))
            paths.append(ring(cx,cy,24-thickness+return_width,24-thickness,270,315,SHADOW))
        else:
            paths.append(ring(cx,cy,24,24-highlight_width,90,135,SHADOW))
            paths.append(ring(cx,cy,24,24-highlight_width,135,180,HIGHLIGHT))
            paths.append(ring(cx,cy,24-thickness+return_width,24-thickness,135,180,SHADOW))
    else:
        paths.append(f'<rect width="24" height="24" fill="{CENTER}"/>')
        if index in (1,7):
            y=0 if index==1 else 24-thickness
            paths.append(f'<rect y="{y}" width="24" height="{thickness}" fill="{FACE}"/>')
            y=0 if index==1 else 24-highlight_width
            color=HIGHLIGHT if index==1 else SHADOW
            paths.append(f'<rect y="{y}" width="24" height="{highlight_width}" fill="{color}"/>')
            if index==1:
                paths.append(f'<rect y="{thickness-return_width}" width="24" height="{return_width}" fill="{SHADOW}"/>')
        elif index in (3,5):
            x=0 if index==3 else 24-thickness
            paths.append(f'<rect x="{x}" width="{thickness}" height="24" fill="{FACE}"/>')
            x=0 if index==3 else 24-highlight_width
            color=HIGHLIGHT if index==3 else SHADOW
            paths.append(f'<rect x="{x}" width="{highlight_width}" height="24" fill="{color}"/>')
            if index==3:
                paths.append(f'<rect x="{thickness-return_width}" width="{return_width}" height="24" fill="{SHADOW}"/>')
    return document(''.join(paths))


def main():
    ET.register_namespace('','http://www.w3.org/2000/svg')
    build=Path('build/figma-ui/flat-panels');build.mkdir(parents=True,exist_ok=True)
    manifest={'tileSize':72,'split':[24]*4,'cornerRadius':24,'center':CENTER,
              'rimColors':[HIGHLIGHT,FACE,SHADOW],'sourceScale':3,'weights':{}}
    sheet=[]
    for name,t,padding,x in [('thick',12,24,1),('thin',6,12,75)]:
        slices=[piece(i,t) for i in range(9)]
        svg=assemble(slices,72,72)
        (HERE/f'flat-panel-{name}.svg').write_text(svg+'\n')
        root=ET.fromstring(svg)
        sheet.append(f'<g transform="translate({x} 1)">'+''.join(ET.tostring(c,encoding='unicode') for c in root)+'</g>')
        manifest['weights'][name]={'rimThickness':t,'padding':[padding]*4,'slices':slices}
        (build/f'preview-{name}.svg').write_text(assemble(slices,640,200)+'\n')
    (HERE/'flat-panels.svg').write_text(document(''.join(sheet),width=148,height=76)+'\n')
    (build/'manifest.json').write_text(json.dumps(manifest))
    print('Generated thin/thick three-shade prototypes; smooth frame sources unchanged.')


if __name__=='__main__':main()
