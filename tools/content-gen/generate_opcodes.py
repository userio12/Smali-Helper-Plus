#!/usr/bin/env python3
"""Generate the bundled offline Dalvik opcode database.

Output: app/src/main/assets/content/opcodes.json (an OpcodeBundleDto, see core/content/ContentModels.kt).

Ground truth is the official documentation saved under tools/content-gen/spec/:
  * dalvik-bytecode.html      https://source.android.com/docs/core/runtime/dalvik-bytecode
  * instruction-formats.html  https://source.android.com/docs/core/runtime/instruction-formats
  * dex-format.html           https://source.android.com/docs/core/runtime/dex-format
and, for smali mnemonics / version gating, the google-smali sources (Opcode.java.txt, VersionMap.java.txt,
smaliParser.g, smaliLexer.jflex, MutableMethodImplementation.java.txt).

Opcode values, mnemonics, formats, operand bit widths and dex versions are extracted from the saved HTML at run
time, so they cannot drift from the spec. Explanatory text is generated from per-family templates in
opcode_text.py plus hand-written entries for unique instructions.

Python 3 standard library only. Usage (from the repository root):
    python3 -I tools/content-gen/generate_opcodes.py            # generate + verify
    python3 -I tools/content-gen/generate_opcodes.py --check    # verify the existing JSON matches, don't write
"""
from __future__ import annotations

import argparse
import collections
import html
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC_DIR = HERE / "spec"
REPO_ROOT = HERE.parent.parent
DEFAULT_OUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "content" / "opcodes.json"

sys.path.insert(0, str(HERE))
import opcode_text  # noqa: E402  (sibling module, same directory)

SCHEMA_VERSION = 1
CONTENT_VERSION = 1
GENERATED_AT = "2026-10-30"

# Category ids, must match domain/model/OpcodeCategory.kt.
CATEGORIES = [
    "move", "return", "constant", "monitor", "type", "object", "array", "field", "invoke", "arithmetic",
    "bitwise", "comparison", "branch", "jump", "switch", "conversion", "exception", "method_handle", "advanced",
]

# Android release -> API level, used to turn the dex-format page's "added in the Android X release" notes into
# minApi values. (Platform version table: https://developer.android.com/tools/releases/platforms)
ANDROID_RELEASE_TO_API = {"7.0": 24, "8.0": 26, "9.0": 28, "10.0": 29, "10": 29, "16": 36}

PAYLOADS = {
    # name: (ident, format id used in the bundle)
    "packed-switch-payload": (0x0100, "packed-switch-payload"),
    "sparse-switch-payload": (0x0200, "sparse-switch-payload"),
    "fill-array-data-payload": (0x0300, "fill-array-data-payload"),
}


# ----------------------------------------------------------------------------------------------------------------
# HTML helpers
# ----------------------------------------------------------------------------------------------------------------

def clean(fragment: str) -> str:
    s = re.sub(r"<br\s*/?>", "\n", fragment)
    s = re.sub(r"</p>|<p[^>]*>", "\n", s)
    s = re.sub(r"<li[^>]*>", "\n- ", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s).replace("\xa0", " ")
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r" *\n *", "\n", s)
    s = re.sub(r"\n{2,}", "\n", s)
    return s.strip()


def flat(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


# ----------------------------------------------------------------------------------------------------------------
# dalvik-bytecode.html: the "Summary of bytecode set" table
# ----------------------------------------------------------------------------------------------------------------

class SpecOp:
    def __init__(self, opcode, name, fmt, spec_syntax, args, note, description):
        self.opcode = opcode
        self.name = name
        self.fmt = fmt
        self.spec_syntax = spec_syntax      # e.g. "add-int vAA, vBB, vCC"
        self.args = args                    # list of (letters, text, bits|None)
        self.note = note                    # member annotation, e.g. "lt bias"
        self.description = description      # cleaned description cell (used for facts like dex version)

    @property
    def dex_version(self) -> str:
        m = re.search(r"Present in Dex files from version (\d{3}) onwards", flat(self.description))
        return m.group(1) if m else "035"


ARG_RE = re.compile(r"^([A-Z](?:\.\.[A-Z])?): (.+)$")
BITS_RE = re.compile(r"\((\d+)[- ]bits?(?: each)?\)")


def parse_args_cell(text: str):
    out = []
    for line in text.split("\n"):
        line = line.strip()
        if not line:
            continue
        m = ARG_RE.match(line)
        if m:
            out.append([m.group(1), m.group(2)])
        elif line.startswith("N = "):
            continue
        elif out:
            out[-1][1] += " " + line
    result = []
    for letters, desc in out:
        b = BITS_RE.search(desc)
        result.append((letters, desc, int(b.group(1)) if b else None))
    return result


def parse_bytecode_table(path: Path):
    src = path.read_text(encoding="utf-8")
    start = src.index('<table class="instruc">')
    end = src.index("</table>", start)
    table = src[start:end]
    ops, unused = [], []
    for row in re.findall(r"<tr>(.*?)</tr>", table, re.S):
        cells = [clean(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)]
        if not cells:
            continue
        if len(cells) != 4:
            raise SystemExit(f"Unexpected bytecode row shape: {cells[:1]}")
        head, syn, args, desc = cells
        m = re.fullmatch(r"([0-9a-f]{2})(?:\.\.([0-9a-f]{2}))? (\w+)", head)
        if not m:
            raise SystemExit(f"Cannot parse op/format cell {head!r}")
        lo, hi, fmt = int(m.group(1), 16), int(m.group(2) or m.group(1), 16), m.group(3)
        if syn.startswith("(unused)"):
            unused.append((lo, hi))
            continue
        lines = syn.split("\n")
        template = re.sub(r"\s*\(with supplemental data.*$", "", lines[0]).strip()
        members = []
        for line in lines[1:]:
            mm = re.match(r"^([0-9a-f]{2}): (\S+)(?: \((.*)\))?$", line.strip())
            if mm:
                members.append((int(mm.group(1), 16), mm.group(2), mm.group(3)))
        if not members:
            if lo != hi:
                raise SystemExit(f"Range row without members: {head}")
            members = [(lo, template.split()[0], None)]
        if [op for op, _, _ in members] != list(range(lo, hi + 1)):
            raise SystemExit(f"Row {head}: member opcodes do not cover the range")
        parsed_args = parse_args_cell(args)
        rest = template[len(template.split()[0]):]
        for op, name, note in members:
            ops.append(SpecOp(op, name, fmt, name + rest, parsed_args, note, desc))
    return ops, unused


def parse_payload_tables(path: Path):
    """Returns {payload name: {'fields': [(name, type)], 'units': formula text}}"""
    src = path.read_text(encoding="utf-8")
    result = {}
    for anchor, name in (("packed-switch", "packed-switch-payload"), ("sparse-switch", "sparse-switch-payload"),
                         ("fill-array", "fill-array-data-payload")):
        i = src.index(f'id="{anchor}"')
        t_start = src.index('<table class="supplement">', i)
        t_end = src.index("</table>", t_start)
        rows = re.findall(r"<tr>(.*?)</tr>", src[t_start:t_end], re.S)
        fields = []
        for r in rows:
            cells = [clean(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", r, re.S)]
            if cells:
                fields.append((cells[0], cells[1], flat(cells[2])))
        note = flat(clean(src[t_end:src.index("</p>", t_end)]))
        m = re.search(r"table is (.+?)\.$", note)
        result[name] = {"fields": fields, "units": m.group(1) if m else None}
    return result


# ----------------------------------------------------------------------------------------------------------------
# instruction-formats.html: official format ids and their bit layouts
# ----------------------------------------------------------------------------------------------------------------

FORMAT_ID_RE = re.compile(r"^[0-9][0-9r][a-z]{1,3}$")


def parse_formats(path: Path):
    src = path.read_text(encoding="utf-8")
    start = src.index('<table class="format">')
    end = src.index("</table>", start)
    body = src[start:end]
    body = body[body.index("<tbody>"):]
    layouts = {}
    current_layout = None
    for row in re.findall(r"<tr>(.*?)</tr>", body, re.S):
        cells = [clean(re.sub(r"</td>.*", "", c, flags=re.S)) for c in re.split(r"<td[^>]*>", row)[1:]]
        if not cells:
            continue
        if FORMAT_ID_RE.match(cells[0]):
            fid = cells[0]
        else:
            current_layout = flat(cells[0])
            fid = cells[1]
        if not FORMAT_ID_RE.match(fid):
            raise SystemExit(f"Cannot parse format id from {cells[:2]}")
        layouts[fid] = current_layout if current_layout not in ("N/A",) else None
    return layouts


# ----------------------------------------------------------------------------------------------------------------
# dex-format.html: dex versions and the Android releases that introduced them
# ----------------------------------------------------------------------------------------------------------------

def parse_dex_versions(path: Path):
    text = flat(clean(path.read_text(encoding="utf-8")))
    versions = {}
    for m in re.finditer(r"Support for version (\d{3}) of the format (?:was added in|is experimental in) the "
                         r"Android ([0-9.]+) release", text):
        versions[m.group(1)] = m.group(2)
    for needed in ("037", "038", "039", "040", "041"):
        if needed not in versions:
            raise SystemExit(f"dex-format.html: no release note found for version {needed}")
    # Sanity: the notes must still attribute the bytecodes we gate on these versions.
    for phrase in ("Version 038 added new bytecodes (invoke-polymorphic and invoke-custom)",
                   "which introduced two new bytecodes, const-method-handle and const-method-type"):
        if phrase not in text:
            raise SystemExit(f"dex-format.html no longer contains: {phrase!r}")
    return versions


# ----------------------------------------------------------------------------------------------------------------
# google-smali dexlib2 Opcode.java (saved as spec/Opcode.java.txt, parsed as text): mnemonics, formats and version constraints
# ----------------------------------------------------------------------------------------------------------------

def parse_dexlib2(path: Path):
    entries = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r'^\s+[A-Z0-9_]+\((.+?), "([a-z0-9/\-]+)", .*?Format\.(\w+)', line)
        if not m:
            continue
        value_expr, name, fmt = m.groups()
        odex = "ODEX_ONLY" in line
        vm = re.fullmatch(r"0x([0-9a-f]+)", value_expr)
        constrained = vm is None
        if not vm:
            vm = re.search(r"0x([0-9a-f]+)", value_expr)
        entries[name] = {"opcode": int(vm.group(1), 16), "format": fmt, "odex": odex, "constrained": constrained,
                         "expr": value_expr}
    return entries


DEXLIB2_FORMAT = {"Format21ih": "21h", "Format21lh": "21h"}


def dexlib2_format(fmt: str) -> str:
    return DEXLIB2_FORMAT.get(fmt, fmt.replace("Format", ""))


# ----------------------------------------------------------------------------------------------------------------
# Build
# ----------------------------------------------------------------------------------------------------------------

def build():
    errors = []
    spec_ops, unused = parse_bytecode_table(SPEC_DIR / "dalvik-bytecode.html")
    payload_tables = parse_payload_tables(SPEC_DIR / "dalvik-bytecode.html")
    formats = parse_formats(SPEC_DIR / "instruction-formats.html")
    dex_versions = parse_dex_versions(SPEC_DIR / "dex-format.html")
    dexlib2 = parse_dexlib2(SPEC_DIR / "Opcode.java.txt")

    # --- spec table coverage: every opcode 0x00..0xff is either an instruction or listed as unused, exactly once.
    seen_values = collections.Counter(op.opcode for op in spec_ops)
    unused_values = [v for lo, hi in unused for v in range(lo, hi + 1)]
    for v in range(0x100):
        n = seen_values[v] + unused_values.count(v)
        if n != 1:
            errors.append(f"spec value 0x{v:02x} appears {n} times (instructions + unused)")

    # --- cross-check with dexlib2 (smali's opcode table).
    non_odex = {n: e for n, e in dexlib2.items() if not e["odex"] and e["opcode"] < 0x100}
    spec_names = {op.name for op in spec_ops}
    if set(non_odex) != spec_names:
        errors.append(f"dexlib2 vs spec mnemonic mismatch: only-dexlib2={sorted(set(non_odex) - spec_names)} "
                      f"only-spec={sorted(spec_names - set(non_odex))}")
    for op in spec_ops:
        d = non_odex.get(op.name)
        if d is None:
            continue
        if d["opcode"] != op.opcode:
            errors.append(f"{op.name}: dexlib2 opcode 0x{d['opcode']:02x} != spec 0x{op.opcode:02x}")
        if dexlib2_format(d["format"]) != op.fmt:
            errors.append(f"{op.name}: dexlib2 format {d['format']} != spec {op.fmt}")
        # Only the dex 038/039 instructions may be version-gated in dexlib2.
        if d["constrained"] != (op.dex_version != "035"):
            errors.append(f"{op.name}: dexlib2 version gating ({d['expr']}) disagrees with spec dex {op.dex_version}")
    for pname, ident in (("packed-switch-payload", 0x100), ("sparse-switch-payload", 0x200), ("array-payload", 0x300)):
        if dexlib2.get(pname, {}).get("opcode") != ident:
            errors.append(f"dexlib2 payload {pname} ident mismatch")

    # --- formats: every instruction format must be an official format id.
    for op in spec_ops:
        if op.fmt not in formats:
            errors.append(f"{op.name}: format {op.fmt} not in instruction-formats.html")

    spec_by_name = {op.name: op for op in spec_ops}
    ctx = opcode_text.Context(spec_by_name=spec_by_name, formats=formats, payload_tables=payload_tables,
                              dex_versions=dex_versions, release_to_api=ANDROID_RELEASE_TO_API)
    entries = opcode_text.build_all(ctx)

    # --- verify the generated entries
    names = [e["name"] for e in entries]
    dupes = [n for n, c in collections.Counter(names).items() if c > 1]
    if dupes:
        errors.append(f"duplicate names: {dupes}")
    expected = spec_names | set(PAYLOADS)
    if set(names) != expected:
        errors.append(f"entry set mismatch: missing={sorted(expected - set(names))} extra={sorted(set(names) - expected)}")
    by_value = collections.Counter(e["opcode"] for e in entries)
    for v, c in by_value.items():
        if c != 1:
            errors.append(f"opcode value 0x{v:x} used by {c} entries")
    all_names = set(names)
    for e in entries:
        n = e["name"]
        if n in spec_by_name:
            s = spec_by_name[n]
            if e["opcode"] != s.opcode or e["format"] != s.fmt:
                errors.append(f"{n}: opcode/format drifted from spec")
            if e["dexVersion"] != s.dex_version:
                errors.append(f"{n}: dexVersion {e['dexVersion']} != spec {s.dex_version}")
            check_operand_bits(e, s, errors)
            check_syntax_registers(e, s, errors)
        else:
            ident, fmt = PAYLOADS[n]
            if e["opcode"] != ident or e["format"] != fmt:
                errors.append(f"{n}: payload ident/format wrong")
        if e["category"] not in CATEGORIES:
            errors.append(f"{n}: unknown category {e['category']}")
        for key in ("syntax", "summary", "description", "technical", "example", "exampleExplanation", "registerUsage",
                    "compatibility"):
            if not isinstance(e[key], str) or not e[key].strip():
                errors.append(f"{n}: empty {key}")
        if "\n" in e["summary"]:
            errors.append(f"{n}: summary must be one line")
        if not 2 <= len(e["commonMistakes"]) <= 4:
            errors.append(f"{n}: needs 2-4 common mistakes, has {len(e['commonMistakes'])}")
        if not e["related"]:
            errors.append(f"{n}: no related entries")
        for r in e["related"]:
            if r not in all_names:
                errors.append(f"{n}: related {r} does not exist")
            if r == n:
                errors.append(f"{n}: related to itself")
        if not any(ref["url"].startswith(opcode_text.URL_BC) for ref in e["references"]):
            errors.append(f"{n}: missing dalvik-bytecode reference")
        for ref in e["references"]:
            if not ref["url"].startswith("https://"):
                errors.append(f"{n}: non-https reference")
        if not e["operands"] and e["format"] not in ("10x",) and n not in PAYLOADS:
            errors.append(f"{n}: no operands")
        if not e["tags"]:
            errors.append(f"{n}: no tags")
        if e["minApi"] < 1:
            errors.append(f"{n}: bad minApi")
    return entries, errors, unused


def check_operand_bits(entry, spec_op, errors):
    """Every 'X: ... (N bits)' statement in the spec must match an operand of the generated entry."""
    ours = {}
    for o in entry["operands"]:
        m = re.match(r"^([A-Z]+) \((\d+) bits", o["description"])
        if m:
            ours.setdefault(m.group(1)[0], set()).add(int(m.group(2)))
    for letters, desc, bits in spec_op.args:
        if bits is None:
            continue
        expanded = [letters] if ".." not in letters else [chr(c) for c in range(ord(letters[0]), ord(letters[-1]) + 1)]
        for letter in expanded:
            if bits not in ours.get(letter, set()):
                errors.append(f"{entry['name']}: spec says {letter} is {bits} bits; operands say {ours.get(letter)}")


REG_TOKEN = re.compile(r"\bv[A-Z]+\b")


def check_syntax_registers(entry, spec_op, errors):
    """Register placeholders in our smali syntax must be exactly the spec's (same widths, same order)."""
    if REG_TOKEN.findall(entry["syntax"]) != REG_TOKEN.findall(spec_op.spec_syntax):
        errors.append(f"{entry['name']}: syntax registers {entry['syntax']!r} vs spec {spec_op.spec_syntax!r}")
    if not entry["syntax"].startswith(entry["name"] + " ") and entry["syntax"] != entry["name"]:
        errors.append(f"{entry['name']}: syntax must start with the mnemonic")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--check", action="store_true", help="verify only; fail if the JSON on disk differs")
    a = ap.parse_args(argv)

    entries, errors, unused = build()
    if errors:
        print("VERIFICATION FAILED:", file=sys.stderr)
        for e in errors:
            print("  - " + e, file=sys.stderr)
        return 1

    bundle = {"schemaVersion": SCHEMA_VERSION, "contentVersion": CONTENT_VERSION, "generatedAt": GENERATED_AT,
              "opcodes": entries}
    text = json.dumps(bundle, ensure_ascii=False, indent=1) + "\n"

    counts = collections.Counter(e["category"] for e in entries)
    print(f"{len(entries)} entries ({len(entries) - len(PAYLOADS)} instructions + {len(PAYLOADS)} payloads); "
          f"unused spec ranges skipped: {', '.join(f'0x{lo:02x}..0x{hi:02x}' for lo, hi in unused)}")
    for c in CATEGORIES:
        print(f"  {c:14s} {counts.get(c, 0)}")

    if a.check:
        current = a.out.read_text(encoding="utf-8") if a.out.exists() else ""
        if current != text:
            print(f"{a.out} is out of date; re-run without --check", file=sys.stderr)
            return 1
        print(f"{a.out} is up to date ({len(text.encode('utf-8'))} bytes)")
        return 0
    a.out.parent.mkdir(parents=True, exist_ok=True)
    a.out.write_text(text, encoding="utf-8")
    print(f"wrote {a.out} ({len(text.encode('utf-8'))} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
