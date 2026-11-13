"""Validate and archive jpackage images without losing Unix modes or symlinks."""

import argparse
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile


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
