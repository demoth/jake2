# Engine Tools

Standalone desktop tooling for engine asset generation.

## Bitmap Font Tool

From the repository root, generate a libGDX bitmap font (`.fnt` + `.png`) from a TTF:

- `./gradlew :cake:engine-tools:run --args="--ttf /absolute/path/MyFont.ttf --out /absolute/output/dir --name font --size 21"`

Notes:

- Uses libGDX default glyph set: `FreeTypeFontGenerator.DEFAULT_CHARS`.
- Output is intended for manual copy into engine assets.
