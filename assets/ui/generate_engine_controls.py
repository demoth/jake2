#!/usr/bin/env python3
"""Generate neutral scrollbar pills and a circular knob for the engine skin.

Requires Inkscape and Pillow. Run from any working directory. The position knob
is a solid 24px circle, matching the existing 24px native control height.
"""

from pathlib import Path
import shutil
import subprocess
import tempfile

from PIL import Image


HERE = Path(__file__).resolve().parent
SVG = '''<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="0 0 56 56">
  <rect id="scroll-thumb-horizontal" x="1" y="1" width="25" height="24" rx="12" fill="#ffffff"/>
  <rect id="scroll-thumb-vertical" x="28" y="1" width="24" height="25" rx="12" fill="#ffffff"/>
  <circle id="slider-circle" cx="13" cy="40" r="12" fill="#ffffff"/>
</svg>
'''


def main():
    renderer = shutil.which('inkscape')
    if not renderer:
        raise SystemExit('Inkscape is required to render engine controls.')
    source = HERE / 'engine-controls.svg'
    source.write_text(SVG)
    # Snap-packaged Inkscape has a private /tmp; use a directory beside the source.
    with tempfile.TemporaryDirectory(prefix='.engine-controls-', dir=HERE) as temp:
        raster = Path(temp) / 'controls.png'
        subprocess.run([renderer, str(source), '--export-type=png',
                        '--export-filename=' + str(raster),
                        '--export-width=448', '--export-height=448'], check=True)
        with Image.open(raster) as image:
            image.convert('RGBA').resize((56, 56), Image.Resampling.LANCZOS).save(HERE / 'engine-controls.png')
    print('Generated engine-controls.svg and engine-controls.png.')


if __name__ == '__main__':
    main()
