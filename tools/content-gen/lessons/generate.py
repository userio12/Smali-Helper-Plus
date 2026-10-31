#!/usr/bin/env python3
"""Builds app/src/main/assets/content/lessons.json from the lesson source modules and validates it.

Usage (from anywhere):
    python3 -I tools/content-gen/lessons/generate.py            # validate + write the asset
    python3 -I tools/content-gen/lessons/generate.py --check    # validate only
    python3 -I tools/content-gen/lessons/generate.py --check --parts b1 b2   # validate some parts only

Lesson sources live in part_<name>.py modules (b1, b2 = beginner; i1, i2 = intermediate; a1, a2 = advanced),
each exporting LESSONS and FLASHCARDS lists built with the helpers in common.py.

Validation performed:
  * structure: unique ids, levels/orders, 4-8 sections, >= 2 exercises, 4-6 quiz questions, option sanity,
    relatedOpcodes are real Dalvik mnemonics, >= 60 flashcards (full build only);
  * every smali listing (code sections, smali-looking exercise starters/solutions, quiz code, flashcard code) is
    assembled with the smali assembler bundled in apktool.jar (see kotlin-check/, the `smali-check` command), after wrapping fragments in a
    class / method when needed;
  * a register lint: vN must be < the method's register count and pN < its parameter-word count.
"""
import argparse
import importlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import kotlin_tool  # noqa: E402
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "lessons.json")
# Bump CONTENT_VERSION whenever lesson content changes so installed apps re-seed the bundled lessons.
CONTENT_VERSION = 1
GENERATED_AT = "2026-10-30"
APKTOOL_JAR = os.environ.get("APKTOOL_JAR", "/usr/local/bin/apktool.jar")

LEVEL_ORDERS = {"beginner": range(1, 11), "intermediate": range(11, 20), "advanced": range(20, 29)}
SECTION_TYPES = {"text", "code", "tip", "warning", "java"}
FLASHCARD_TOPICS = {"Opcodes", "Descriptors", "Registers", "Formats", "Directives", "DEX", "Runtime"}
PAYLOAD_NAMES = {"packed-switch-payload", "sparse-switch-payload", "fill-array-data-payload"}
CLASS_LEVEL = (".field", ".method", ".annotation", ".implements", ".source", ".subannotation")


def load_opcode_names():
    out = subprocess.run(
        [kotlin_tool.launcher(APKTOOL_JAR), "smali-check", "--list-opcodes"],
        capture_output=True, text=True, check=True,
    ).stdout.split()
    names = {n for n in out if "-quick" not in n and n not in ("return-void-no-barrier", "array-payload")}
    return names | PAYLOAD_NAMES


def first_meaningful_line(src):
    for line in src.splitlines():
        s = line.strip()
        if s and not s.startswith("#"):
            return s
    return ""


def looks_like_smali(src, opcodes):
    """True when [src] is a smali listing rather than a prose answer: the first meaningful line is a directive or
    label, or starts with an opcode whose operands contain no ordinary words."""
    if not src:
        return False
    first = first_meaningful_line(src)
    if first.startswith((".", ":")):
        return True
    parts = first.split(None, 1)
    if not parts or parts[0] not in opcodes:
        return False
    rest = strip_noise(parts[1] if len(parts) > 1 else "")
    return not re.search(r"\b[a-z]{3,}\b", rest)


def wrap(src):
    """Returns a complete smali class for [src] (which may be a class, members, or an instruction fragment)."""
    lines = [l.strip() for l in src.splitlines() if l.strip() and not l.strip().startswith("#")]
    has = lambda d: any(l.startswith(d + " ") or l == d for l in lines)
    if has(".class"):
        return src
    if has(".super"):
        return ".class public LCheck;\n" + src + "\n"
    if lines and lines[0].startswith(CLASS_LEVEL):
        return ".class public LCheck;\n.super Ljava/lang/Object;\n\n" + src + "\n"
    header = ".class public LCheck;\n.super Ljava/lang/Object;\n\n.method public check(IIIIIIII)V\n"
    if not (has(".registers") or has(".locals")):
        header += "    .registers 16\n"
    return header + src + "\n.end method\n"


def param_words(desc, static):
    words = 0 if static else 1
    i = 0
    while i < len(desc):
        c = desc[i]
        if c in "JD":
            words += 2
            i += 1
        elif c == "L":
            words += 1
            i = desc.index(";", i) + 1
        elif c == "[":
            while desc[i] == "[":
                i += 1
            if desc[i] == "L":
                i = desc.index(";", i) + 1
            else:
                i += 1
            words += 1
        else:
            words += 1
            i += 1
    return words


def strip_noise(body):
    body = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)
    body = re.sub(r"'(?:\\.|[^'\\])*'", "''", body)
    body = re.sub(r"#.*", "", body)
    body = re.sub(r"->\S+", "", body)
    body = re.sub(r"L[^;\s]*;", "", body)
    body = re.sub(r":[\w$]+", "", body)
    return body


def lint_registers(src):
    errors = []
    for m in re.finditer(r"^\s*\.method\s+([^\n]*)\n(.*?)^\s*\.end method", src, re.S | re.M):
        header, body = m.group(1), m.group(2)
        mods = header.split()
        if "abstract" in mods or "native" in mods:
            continue
        proto = re.search(r"\(([^)]*)\)", header)
        if not proto:
            errors.append("cannot parse method header: " + header)
            continue
        words = param_words(proto.group(1), "static" in mods)
        regs = re.search(r"^\s*\.registers\s+(\d+)", body, re.M)
        locs = re.search(r"^\s*\.locals\s+(\d+)", body, re.M)
        if regs:
            total = int(regs.group(1))
            if total < words:
                errors.append(f"{header}: .registers {total} < {words} parameter words")
        elif locs:
            total = int(locs.group(1)) + words
        else:
            errors.append(f"{header}: missing .registers/.locals")
            continue
        clean = strip_noise(body)
        for v in re.findall(r"\bv(\d+)\b", clean):
            if int(v) >= total:
                errors.append(f"{header}: v{v} out of range (method has {total} registers)")
        for p in re.findall(r"\bp(\d+)\b", clean):
            if int(p) >= words:
                errors.append(f"{header}: p{p} out of range ({words} parameter words)")
    return errors


def collect_listings(bundle, opcodes):
    """Yields (location, source) for every smali listing that must assemble."""
    for l in bundle["lessons"]:
        for i, s in enumerate(l["sections"]):
            if s["type"] == "code" and s.get("_check", True):
                yield f"{l['id']}/section[{i}]", s["body"]
        for e in l["exercises"]:
            if e.get("starterCode") and e.get("_check_starter", True) and looks_like_smali(e["starterCode"], opcodes):
                yield f"{l['id']}/{e['id']}/starter", e["starterCode"]  # empty method bodies are allowed here
            if looks_like_smali(e["solution"], opcodes):
                yield f"{l['id']}/{e['id']}/solution", e["solution"]
        for q in l["quiz"]:
            if q.get("code") and looks_like_smali(q["code"], opcodes):
                yield f"{l['id']}/{q['id']}/code", q["code"]
    for c in bundle["flashcards"]:
        if c.get("code") and looks_like_smali(c["code"], opcodes):
            yield f"flashcard {c['id']}", c["code"]


def assemble_all(listings):
    errors = []
    tmp = tempfile.mkdtemp(prefix="lesson-smali-")
    try:
        files = {}
        for n, (loc, src) in enumerate(listings):
            full = wrap(src)
            for e in lint_registers(full):
                errors.append(f"{loc}: {e}")
            path = os.path.join(tmp, f"s{n}.smali")
            with open(path, "w") as f:
                f.write(full)
            files[path] = loc
        if not files:
            return errors
        res = subprocess.run(
            [kotlin_tool.launcher(APKTOOL_JAR), "smali-check", *files.keys()],
            capture_output=True, text=True,
        )
        diag = res.stderr
        for line in res.stdout.splitlines():
            status, path = line.split(" ", 1)
            if status == "ERR":
                with open(path) as f:
                    src_lines = f.read().splitlines()
                msgs = []
                for d in diag.splitlines():
                    if os.path.basename(path) not in d:
                        continue
                    m = re.search(r"\[(\d+),(\d+)\]\s*(.*)", d)
                    if m:
                        ln = int(m.group(1))
                        ctx = src_lines[ln - 1].strip() if 0 < ln <= len(src_lines) else ""
                        msgs.append(f"{m.group(3)}  <<{ctx}>>")
                    else:
                        msgs.append(d.split(": ", 1)[-1])
                loc = files[path]
                if loc.endswith("/starter") and all("at least 1 instruction" in x for x in msgs) and msgs:
                    continue
                errors.append(f"{loc}: does not assemble: {' | '.join(msgs)[:500]}")
        if res.returncode != 0 and not res.stdout:
            errors.append("smali-check failed: " + diag[:400])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return errors


def validate_structure(bundle, opcodes, full):
    errors = []
    seen = set()

    def uniq(kind, id_):
        if not id_ or not re.match(r"^[a-z0-9][a-z0-9-]*$", id_):
            errors.append(f"{kind} id '{id_}' must be lowercase kebab-case")
        if (kind, id_) in seen:
            errors.append(f"duplicate {kind} id {id_}")
        seen.add((kind, id_))

    orders = set()
    for l in bundle["lessons"]:
        lid = l["id"]
        uniq("lesson", lid)
        if l["level"] not in LEVEL_ORDERS:
            errors.append(f"{lid}: bad level {l['level']}")
        elif l["order"] not in LEVEL_ORDERS[l["level"]]:
            errors.append(f"{lid}: order {l['order']} outside {l['level']} range")
        if l["order"] in orders:
            errors.append(f"{lid}: duplicate order {l['order']}")
        orders.add(l["order"])
        if not (3 <= l["estimatedMinutes"] <= 40):
            errors.append(f"{lid}: estimatedMinutes {l['estimatedMinutes']}")
        if len(l["summary"]) < 40:
            errors.append(f"{lid}: summary too short")
        if not (4 <= len(l["sections"]) <= 8):
            errors.append(f"{lid}: {len(l['sections'])} sections (need 4-8)")
        types = {s["type"] for s in l["sections"]}
        for s in l["sections"]:
            if s["type"] not in SECTION_TYPES:
                errors.append(f"{lid}: bad section type {s['type']}")
            if not s["body"].strip():
                errors.append(f"{lid}: empty section")
        if "text" not in types or "code" not in types:
            errors.append(f"{lid}: needs at least one text and one code section")
        if len(l["exercises"]) < 2:
            errors.append(f"{lid}: needs >= 2 exercises")
        for e in l["exercises"]:
            uniq("exercise", e["id"])
            if not e["prompt"].strip() or not e["solution"].strip():
                errors.append(f"{lid}/{e['id']}: empty prompt/solution")
        if not (4 <= len(l["quiz"]) <= 6):
            errors.append(f"{lid}: {len(l['quiz'])} quiz questions (need 4-6)")
        for q in l["quiz"]:
            uniq("question", q["id"])
            if not (2 <= len(q["options"]) <= 5):
                errors.append(f"{lid}/{q['id']}: {len(q['options'])} options")
            if len(set(q["options"])) != len(q["options"]):
                errors.append(f"{lid}/{q['id']}: duplicate options")
            if not (0 <= q["correctIndex"] < len(q["options"])):
                errors.append(f"{lid}/{q['id']}: correctIndex out of range")
            if len(q["explanation"]) < 20:
                errors.append(f"{lid}/{q['id']}: explanation too short")
        if not l["relatedOpcodes"]:
            errors.append(f"{lid}: no relatedOpcodes")
        for op in l["relatedOpcodes"]:
            if op not in opcodes:
                errors.append(f"{lid}: unknown related opcode '{op}'")
    for c in bundle["flashcards"]:
        uniq("flashcard", c["id"])
        if c["topic"] not in FLASHCARD_TOPICS:
            errors.append(f"flashcard {c['id']}: topic '{c['topic']}' not in {sorted(FLASHCARD_TOPICS)}")
        if not c["front"].strip() or not c["back"].strip():
            errors.append(f"flashcard {c['id']}: empty side")
    if full:
        if len(bundle["lessons"]) != 28:
            errors.append(f"expected 28 lessons, got {len(bundle['lessons'])}")
        if sorted(orders) != list(range(1, 29)):
            errors.append("lesson orders must be exactly 1..28")
        if len(bundle["flashcards"]) < 60:
            errors.append(f"expected >= 60 flashcards, got {len(bundle['flashcards'])}")
    return errors


def strip_private(obj):
    if isinstance(obj, dict):
        return {k: strip_private(v) for k, v in obj.items() if not k.startswith("_") and v is not None}
    if isinstance(obj, list):
        return [strip_private(v) for v in obj]
    return obj


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="validate only, do not write the asset")
    ap.add_argument("--parts", nargs="*", default=None, help="validate only these part modules, e.g. b1 i2")
    args = ap.parse_args()
    all_parts = sorted(f[5:-3] for f in os.listdir(HERE) if f.startswith("part_") and f.endswith(".py"))
    parts = args.parts or all_parts
    full = args.parts is None

    lessons, cards = [], []
    for name in parts:
        mod = importlib.import_module("part_" + name)
        lessons += mod.LESSONS
        cards += getattr(mod, "FLASHCARDS", [])
    lessons.sort(key=lambda l: l["order"])
    bundle = {"schemaVersion": 1, "contentVersion": CONTENT_VERSION, "generatedAt": GENERATED_AT, "lessons": lessons, "flashcards": cards}

    opcodes = load_opcode_names()
    errors = validate_structure(bundle, opcodes, full)
    listings = list(collect_listings(bundle, opcodes))
    errors += assemble_all(listings)

    q = sum(len(l["quiz"]) for l in lessons)
    ex = sum(len(l["exercises"]) for l in lessons)
    print(f"{len(lessons)} lessons, {ex} exercises, {q} quiz questions, {len(cards)} flashcards, "
          f"{len(listings)} smali listings assembled")
    if errors:
        print(f"\n{len(errors)} problem(s):")
        for e in errors:
            print("  - " + e)
        sys.exit(1)
    if args.check:
        print("OK (check only)")
        return
    if not full:
        sys.exit("refusing to write a partial bundle; run without --parts")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(strip_private(bundle), f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("wrote " + os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
