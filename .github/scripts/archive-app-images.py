"""Validate and archive jpackage images without losing Unix modes or symlinks."""

import argparse
from fnmatch import fnmatchcase
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tarfile
import zipfile


def check_bundle(app, directory, target):
    classifier = {"linux-x64": "linux", "windows-x64": "windows",
                  "macos-arm64": "macos-arm64", "macos-x64": "macos"}[target]
    native_modules = set()
    for jar in directory.glob("*.jar"):
        native = re.fullmatch(r"(lwjgl.*?)-[\d.]+-natives-(.+)\.jar", jar.name)
        if native:
            if native[2] != classifier:
                raise RuntimeError(f"Foreign native library in bundle: {jar.name}")
            native_modules.add(native[1])
        if jar.name.startswith(("gdx-controllers-", "jamepad-", "gdx-freetype-", "ktx-freetype-")):
            raise RuntimeError(f"Unused dependency in bundle: {jar.name}")
    required = {"lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb"}
    if missing := required - native_modules:
        raise RuntimeError(f"Missing {classifier} native libraries: {sorted(missing)}")

    app_jars = list(directory.glob(f"{app}-*.jar"))
    if len(app_jars) != 1:
        raise RuntimeError(f"Expected one application jar for {app}, found {app_jars}")
    excluded = ("*.py", "*.svg", "*.md", "*.ttf", "cake-branding/*",
                "cake-logo-draft.png", "libgdx.png", "triangloid.png", "icons/logo*")
    with zipfile.ZipFile(app_jars[0]) as archive:
        leftovers = [name for name in archive.namelist()
                     if any(fnmatchcase(name, pattern) for pattern in excluded)]
        if leftovers:
            raise RuntimeError(f"Unused resources in {app}: {leftovers}")
        if "ui/ShareTechMono-OFL.txt" not in archive.namelist():
            raise RuntimeError(f"Missing bitmap font license in {app}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, choices=(
        "linux-x64", "windows-x64", "macos-arm64", "macos-x64"))
    args = parser.parse_args()
    system, architecture = args.target.split("-")
    expected_system = {"linux": "Linux", "windows": "Windows", "macos": "Darwin"}[system]
    expected_architectures = {"x64": {"x86_64", "amd64"}, "arm64": {"aarch64", "arm64"}}
    if platform.system() != expected_system or platform.machine().lower() not in expected_architectures[architecture]:
        parser.error("Target must match the host OS and architecture")

    root = Path(__file__).resolve().parents[2]
    output = root / "build/packages"
    output.mkdir(parents=True, exist_ok=True)
    for app in ("cake-client", "cake-fullgame", "cake-viewer"):
        image = root / "cake" / app / "build/jpackage" / (app + (".app" if system == "macos" else ""))
        if system == "macos":
            launcher = image / "Contents/MacOS" / app
            config = image / "Contents/app" / (app + ".cfg")
            java = image / "Contents/runtime/Contents/Home/bin/java"
        elif system == "windows":
            launcher = image / (app + ".exe")
            config = image / "app" / (app + ".cfg")
            java = image / "runtime/bin/java.exe"
        else:
            launcher = image / "bin" / app
            config = image / "lib/app" / (app + ".cfg")
            java = image / "lib/runtime/bin/java"
        for required in (launcher, config, java):
            if not required.is_file():
                raise FileNotFoundError(required)
        options = config.read_text(encoding="utf-8")
        if "--enable-native-access=ALL-UNNAMED" not in options:
            raise RuntimeError(f"Missing native-access option in {config}")
        if system == "macos" and "-XstartOnFirstThread" not in options:
            raise RuntimeError(f"Missing GLFW first-thread option in {config}")
        check_bundle(app, config.parent, args.target)
        subprocess.run([str(java), "-version"], check=True, timeout=60)
        if app == "cake-viewer":
            subprocess.run([str(launcher), "--help"], check=True, timeout=60)

        archive = output / f"{app}-{args.target}"
        if system == "windows":
            shutil.make_archive(str(archive), "zip", root_dir=image.parent, base_dir=image.name)
        else:
            # The complete .app/runtime includes symlinks and executable files.
            with tarfile.open(str(archive) + ".tar.gz", "w:gz") as bundle:
                bundle.add(image, arcname=image.name)
        print(f"Packaged {archive}", flush=True)


if __name__ == "__main__":
    main()
