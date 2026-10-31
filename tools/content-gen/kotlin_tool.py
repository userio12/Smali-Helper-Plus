"""Locates (and builds on demand) the Kotlin verification launcher in tools/content-gen/kotlin-check.

The launcher bundles the smali assembler from apktool.jar, so the jar is only needed when it is built.
Python 3 standard library only.
"""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
PROJECT_DIR = HERE / "kotlin-check"
LAUNCHER = PROJECT_DIR / "build" / "install" / "kotlin-check" / "bin" / "kotlin-check"
DEFAULT_APKTOOL_JAR = "/usr/local/bin/apktool.jar"


def _is_stale() -> bool:
    if not LAUNCHER.exists():
        return True
    built = LAUNCHER.stat().st_mtime
    inputs = [PROJECT_DIR / "build.gradle.kts", PROJECT_DIR / "settings.gradle.kts",
              *(PROJECT_DIR / "src").rglob("*.kt")]
    return any(p.stat().st_mtime > built for p in inputs)


def launcher(apktool_jar: str | os.PathLike | None = None) -> str:
    """Returns the path of the `kotlin-check` launcher, running `installDist` first if it is missing or stale.

    The jar the launcher was built against is fixed at build time; delete tools/content-gen/kotlin-check/build to
    rebuild against a different apktool.jar.
    """
    if _is_stale():
        jar = str(apktool_jar or os.environ.get("APKTOOL_JAR", DEFAULT_APKTOOL_JAR))
        if not os.path.exists(jar):
            raise SystemExit(f"apktool.jar not found at {jar}; set APKTOOL_JAR (or pass --apktool-jar)")
        cmd = [str(REPO_ROOT / "gradlew"), "-p", str(PROJECT_DIR), f"-PAPKTOOL_JAR={jar}",
               "installDist", "--console=plain", "-q"]
        print("building tools/content-gen/kotlin-check (first run only)...", file=sys.stderr)
        proc = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True)
        if proc.returncode != 0 or not LAUNCHER.exists():
            raise SystemExit("could not build kotlin-check:\n" + proc.stdout + proc.stderr)
    return str(LAUNCHER)
