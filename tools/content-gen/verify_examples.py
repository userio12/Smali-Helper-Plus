#!/usr/bin/env python3
"""Optional check: assemble every `example` in opcodes.json with a real smali assembler.

Uses the smali/dexlib2 copy bundled in apktool.jar (no extra downloads) through the Kotlin tool in
kotlin-check/ (`asm-examples` subcommand), which is built with Gradle on first use. For each opcode:
  * the example must assemble at the entry's documented minApi;
  * entries with minApi > 1 must FAIL to assemble at minApi - 1 (smali's own opcode tables reject them),
    which double-checks the minApi values against google-smali.

Snippets without a .method are wrapped in a method with enough locals; labels they reference but don't define
are appended so the snippet can assemble. Python 3 standard library only.

Usage (from the repository root):
    python3 -I tools/content-gen/verify_examples.py [--apktool-jar /usr/local/bin/apktool.jar]
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import kotlin_tool  # noqa: E402

REPO_ROOT = HERE.parent.parent
DEFAULT_JSON = REPO_ROOT / "app" / "src" / "main" / "assets" / "content" / "opcodes.json"

LABEL_DEF = re.compile(r"^\s*(:[A-Za-z_]\w*)\s*(?:#.*)?$")
LABEL_REF = re.compile(r"(?<![\w>])(:[A-Za-z_]\w*)")


def wrap(index: int, example: str) -> str:
    header = f".class public Lcom/acme/demo/gen/Example{index};\n.super Ljava/lang/Object;\n\n"
    if ".method" in example:
        return header + example + "\n"
    lines = example.splitlines()
    code = [ln for ln in lines]
    v_regs = [int(x) for x in re.findall(r"\bv(\d+)\b", example)]
    p_regs = [int(x) for x in re.findall(r"\bp(\d+)\b", example)]
    locals_ = max(v_regs) + 1 if v_regs else 0
    n_params = max(p_regs) + 1 if p_regs else 1          # p0 is `this`
    sig = "(" + "Ljava/lang/Object;" * (n_params - 1) + ")V"
    stripped = [re.sub(r'"(?:\\.|[^"\\])*"', '""', ln.split("#")[0]) for ln in code]
    defined = {m.group(1) for ln in stripped for m in [LABEL_DEF.match(ln)] if m}
    referenced = {r for ln in stripped if not LABEL_DEF.match(ln) for r in LABEL_REF.findall(ln)}
    missing = sorted(referenced - defined)
    body = "\n".join("    " + ln.strip() if ln.strip() else "" for ln in code)
    tail = "".join(f"\n    {lbl}\n    nop" for lbl in missing)
    return (header + f".method public test{sig}\n    .locals {locals_}\n{body}{tail}\n    return-void\n"
            f".end method\n")


def run_batch(tool, api, files):
    cmd = [tool, "asm-examples", str(api)] + [str(f) for f in files]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    results = {}
    for line in proc.stdout.splitlines():
        m = re.match(r"^(OK|FAIL) (\S+)(?: (.*))?$", line)
        if m:
            results[m.group(2)] = (m.group(1) == "OK", m.group(3) or "")
    if not results:
        sys.stderr.write(proc.stdout + proc.stderr)
    return results


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--json", type=Path, default=DEFAULT_JSON)
    ap.add_argument("--apktool-jar", type=Path, default=Path(os.environ.get("APKTOOL_JAR", "/usr/local/bin/apktool.jar")))
    ap.add_argument("--keep", type=Path, help="write the wrapped .smali files here instead of a temp dir")
    a = ap.parse_args(argv)
    if not a.apktool_jar.exists() and not kotlin_tool.LAUNCHER.exists():
        print(f"apktool.jar not found at {a.apktool_jar}; pass --apktool-jar", file=sys.stderr)
        return 2
    tool = kotlin_tool.launcher(a.apktool_jar)

    entries = json.loads(a.json.read_text(encoding="utf-8"))["opcodes"]
    with tempfile.TemporaryDirectory() as tmp:
        out_dir = a.keep or Path(tmp)
        out_dir.mkdir(parents=True, exist_ok=True)
        by_api = collections.defaultdict(list)
        names = {}
        for i, e in enumerate(entries):
            f = out_dir / f"Example{i}.smali"
            f.write_text(wrap(i, e["example"]), encoding="utf-8")
            names[f.name] = e["name"]
            by_api[e["minApi"]].append(f)
            if e["minApi"] > 1:
                by_api[-(e["minApi"] - 1)].append(f)   # negative key = expected failure at that api

        failures = []
        checked = 0
        for key, files in sorted(by_api.items()):
            api, expect_ok = (key, True) if key > 0 else (-key, False)
            res = run_batch(tool, api, files)
            for f in files:
                ok, reason = res.get(f.name, (None, "no result"))
                checked += 1
                if ok is None or ok != expect_ok:
                    failures.append(f"{names[f.name]} @api {api}: expected {'success' if expect_ok else 'failure'}, "
                                    f"got {'success' if ok else 'failure'} {reason}")
    for line in failures:
        print("  - " + line)
    print(f"{checked} assemblies checked, {len(failures)} unexpected results")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
