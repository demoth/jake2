# Asset Tools

Asset generators and authoring sources. Runtime exports go to `assets/` and
app-owned icon directories.

- [Branding and icons](branding/README.md)
- [UI artwork and bitmap fonts](ui/README.md)
- Viewer capture script: `automation/capture-viewer-ui.py --help`

Generate a bitmap font from the repository root:

```sh
./gradlew :cake:asset-tools:run --args="--ttf fonts/Share_Tech_Mono/ShareTechMono-Regular.ttf --out /tmp/cake-font --name share-tech-mono --size 21"
```

Font sources and their license are under `fonts/`. The runtime font license is
`assets/ui/ShareTechMono-OFL.txt`.
