#!/usr/bin/env python3
"""Rebuild the Cake logo draft as clean, layered SVG geometry.

Edit logo-parameters.json, then run this script. CLI overrides allow quick
experiments, for example:

    python3 cake/asset-tools/branding/generate_logo.py --teeth 10 --cog-thickness 32 \
        --rim-thickness 16 --output /tmp/cake-logo-variant.svg

All dimensions use the 512-unit drawing coordinates. Gear and slice groups
share the center pivot (256, 256); the SVG has no painted background.
"""

import argparse
import json
import math
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr


HERE = Path(__file__).resolve().parent


def point(radius, angle, center):
    angle = math.radians(angle)
    return center + radius * math.cos(angle), center + radius * math.sin(angle)


def xy(p):
    return f"{p[0]:.5f},{p[1]:.5f}"


def gear_path(p):
    center = p["size"] / 2
    root = p["gear_root_radius"]
    tip = root + p["tooth_height"]
    inner = root - p["cog_thickness"]
    pitch = 360 / p["teeth"]
    root_half = pitch * p["tooth_root_fraction"] / 2
    tip_half = pitch * p["tooth_tip_fraction"] / 2
    d = [f"M{xy(point(root, -90 - root_half, center))}"]
    for tooth in range(p["teeth"]):
        angle = -90 + tooth * pitch
        d.extend([
            f"L{xy(point(tip, angle - tip_half, center))}",
            f"L{xy(point(tip, angle + tip_half, center))}",
            f"L{xy(point(root, angle + root_half, center))}",
            f"A{root},{root} 0 0 1 {xy(point(root, angle + pitch - root_half, center))}",
        ])
    d.extend([
        "Z",
        f"M{center + inner},{center}",
        f"A{inner},{inner} 0 1 0 {center - inner},{center}",
        f"A{inner},{inner} 0 1 0 {center + inner},{center} Z",
    ])
    return " ".join(d)


def slice_paths(p, start, end):
    center = p["size"] / 2
    outer = p["cake_radius"]
    inner = outer - p["rim_thickness"]
    half_gap = p["slice_gap"] / 2
    outer_inset = math.degrees(math.asin(half_gap / outer))
    inner_inset = math.degrees(math.asin(half_gap / inner))
    hub_distance = half_gap / math.sin(math.radians((end - start) / 2))
    hub = point(hub_distance, (start + end) / 2, center)
    outer_start = point(outer, start + outer_inset, center)
    outer_end = point(outer, end - outer_inset, center)
    inner_start = point(inner, start + inner_inset, center)
    inner_end = point(inner, end - inner_inset, center)
    body = (f"M{xy(hub)} L{xy(inner_start)} "
            f"A{inner},{inner} 0 0 1 {xy(inner_end)} Z")
    rim = (f"M{xy(outer_start)} A{outer},{outer} 0 0 1 {xy(outer_end)} "
           f"L{xy(inner_end)} A{inner},{inner} 0 0 0 {xy(inner_start)} Z")
    return body, rim


def validate(p):
    numeric = [key for key in p if not key.endswith("_color")]
    if any(not isinstance(p[k], (int, float)) or not math.isfinite(p[k]) for k in numeric):
        raise ValueError("Geometry parameters must be finite numbers")
    if p["teeth"] != int(p["teeth"]) or not 6 <= p["teeth"] <= 48:
        raise ValueError("teeth must be an integer between 6 and 48")
    p["teeth"] = int(p["teeth"])
    if not 0 < p["tooth_tip_fraction"] < p["tooth_root_fraction"] < 1:
        raise ValueError("Tooth fractions must satisfy 0 < tip < root < 1")
    if not 0 < p["tooth_height"] < p["size"] / 2 - p["gear_root_radius"]:
        raise ValueError("Gear tips must fit inside the drawing with positive margins")
    inner_gear = p["gear_root_radius"] - p["cog_thickness"]
    inner_cake = p["cake_radius"] - p["rim_thickness"]
    if not 0 < p["cog_thickness"] < p["gear_root_radius"]:
        raise ValueError("Cog thickness must be positive and smaller than its radius")
    if not 0 < p["cake_radius"] < inner_gear:
        raise ValueError("Cake must fit inside the cog with positive clearance")
    if not 0 < p["rim_thickness"] < p["cake_radius"]:
        raise ValueError("Rim thickness must be positive and smaller than the cake radius")
    if not 0 < p["slice_gap"] < inner_cake:
        raise ValueError("Slice gap must be positive and smaller than the inner cake radius")
    for key in ("gear_color", "cake_color", "rim_color"):
        color = p[key]
        if len(color) != 7 or not color.startswith("#"):
            raise ValueError(f"{key} must be a six-digit hex color")
        int(color[1:], 16)


def generate(p):
    validate(p)
    size = p["size"]
    center = size / 2
    cake_color_css = "var(--cake-logo-source-a)" if p["cake_color"] == p["gear_color"] else p["cake_color"]
    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}" '
        f'style="--cake-logo-source-a:{p["gear_color"]};--cake-logo-source-b:{p["rim_color"]};'
        f'--cake-logo-cog:var(--cake-logo-source-a);--cake-logo-cake:{cake_color_css};--cake-logo-rim:var(--cake-logo-source-b)">',
        '  <title>Cake logo draft — geometric reconstruction</title>',
        '  <desc>Cog surrounding five cake slices. Colors and geometry are provisional. Transparent gaps; shared center pivot.</desc>',
        f'  <metadata id="cake-logo-parameters">{escape(json.dumps(p, sort_keys=True))}</metadata>',
        '  <style>@supports (fill: var(--cake-logo-cog)) { #cogwheel { fill: var(--cake-logo-cog); } [id^="cake-"] { fill: var(--cake-logo-cake); } [id^="rim-"] { fill: var(--cake-logo-rim); } }</style>',
        f'  <g id="gear" data-pivot="{center},{center}">',
        f'    <path id="cogwheel" fill={quoteattr(p["gear_color"])} fill-rule="evenodd" d="{gear_path(p)}"/>',
        '  </g>',
        f'  <g id="cake" data-pivot="{center},{center}">',
    ]
    # The sixth sector, centered on the right, is deliberately absent.
    for index, (start, end) in enumerate([(-90, -30), (30, 90), (90, 150), (150, 210), (210, 270)], 1):
        body, rim = slice_paths(p, start, end)
        lines.extend([
            f'    <g id="slice-{index:02d}" data-pivot="{center},{center}">',
            f'      <path id="rim-{index:02d}" fill={quoteattr(p["rim_color"])} d="{rim}"/>',
            f'      <path id="cake-{index:02d}" fill={quoteattr(p["cake_color"])} d="{body}"/>',
            '    </g>',
        ])
    lines.extend(['  </g>', '</svg>', ''])
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--parameters", type=Path, default=HERE / "logo-parameters.json")
    parser.add_argument("--output", type=Path, default=HERE / "cake-logo-draft.svg")
    for name in ["teeth", "cog-thickness", "rim-thickness", "slice-gap", "cake-radius", "tooth-height"]:
        parser.add_argument("--" + name, type=int if name == "teeth" else float)
    for name in ["gear-color", "cake-color", "rim-color"]:
        parser.add_argument("--" + name)
    args = parser.parse_args()
    parameters = json.loads(args.parameters.read_text())
    for name in parameters:
        value = getattr(args, name, None)
        if value is not None:
            parameters[name] = value
    try:
        svg = generate(parameters)
    except (ValueError, TypeError) as error:
        parser.error(str(error))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(svg)
    print(args.output)


if __name__ == "__main__":
    main()
