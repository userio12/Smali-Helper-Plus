# Smali analysis engine (`core/smali`)

Pure Kotlin (no Android dependencies) static analysis of smali source: lexing, parsing, validation, formatting,
explanations, educational pseudocode, register usage and control flow. **Nothing is ever assembled, compiled or
executed** — the app only analyses text.

## Decision: self-written parser/validator instead of the Google smali assembler

Evaluated: `com.android.tools.smali:smali` 3.0.10 (latest on Google Maven, `group-index.xml` lists 3.0.0–3.0.10;
source: https://android.googlesource.com/platform/external/google-smali/).

Evidence gathered on 2026-10-30 from the published POMs/JARs:

| Artifact (compile scope of `smali` 3.0.10) | JAR size | Classes | Declared methods |
|---|---:|---:|---:|
| smali 3.0.10 | 313 KB | 155 | 742 |
| smali-dexlib2 3.0.10 | 1.20 MB | 992 | 5,322 |
| smali-util 3.0.10 | 33 KB | – | – |
| guava 31.1-android | 2.79 MB | 1,941 | 15,167 |
| antlr-runtime 3.5.2 | 168 KB | 117 | 1,264 |
| antlr 3.5.2 (the full ANTLR *tool*, declared as a compile dependency) | 1.15 MB | – | – |
| ↳ ST4 4.0.8, stringtemplate 3.2.1, antlr 2.7.7 (pulled by the ANTLR tool) | 240 KB + 149 KB + 445 KB | – | – |
| jcommander 1.85 (CLI parsing), jsr305 | 94 KB + small | – | – |

(Method counts were measured with `javap -p` over every class; "–" = not measured.)

Findings:

- **Size**: roughly 6.5 MB of JARs and more than 22,000 declared methods before shrinking, most of it Guava, dexlib2
  and the ANTLR tool chain. That is far larger than this whole app needs for syntax analysis.
- **Android compatibility**: the class files target Java 8 (class version 52), so they could be dexed, but the
  library is built as a JVM command-line tool (jcommander entry points, file-based I/O, the ANTLR *tool* rather than
  only its runtime on the compile classpath). It would need manual exclusions and keep rules, and nothing in the
  project is designed or tested for running it on a device.
- **Wrong job**: smali's purpose is to *assemble* dex files. This app must never assemble or execute user code. Its
  diagnostics are ANTLR recognition errors plus exceptions thrown from the tree walker, which stop at the first
  semantic problem; the app needs many positioned, educational diagnostics at once (register frame, format widths,
  return types, move-result placement, API levels...) with quick-fix suggestions, and must keep working on partial,
  half-typed code in the editor.

**Decision**: implement a properly scoped lexer/parser/validator in Kotlin. It recognises the smali grammar needed
for analysis (class/field/method structure, all instruction operand shapes, labels, catch directives, payloads,
debug directives; annotation bodies are skipped but their structure is kept) and validates against the official
specification. It is not an assembler and makes no claim of producing or verifying dex output.

## Sources

- Opcode table, formats, operand widths and min API levels:
  https://source.android.com/docs/core/runtime/dalvik-bytecode and
  https://source.android.com/docs/core/runtime/instruction-formats
  (dex 038 → API 26 for `invoke-polymorphic*`/`invoke-custom*`; dex 039 → API 28 for `const-method-handle` and
  `const-method-type`). Odex-only formats (20bc, 22cs, 35mi, 35ms, 3rmi, 3rms) are not modelled.

## Packages

| Package | Contents |
|---|---|
| `opcodes` | `DalvikOpcodeTable` – all 224 assigned opcodes with format, operand shapes, wide-ness, flow kind, min API |
| `lexer` | `SmaliLexer` – line-oriented tokens with line/column/offset |
| `parser`, `ast` | `SmaliParser` → `SmaliFile` (FILE mode, or LENIENT mode for bare instruction snippets) |
| `analysis` | `SmaliValidator` (diagnostics), `RegisterFrame`, `MethodLayout`, `ControlFlowAnalyzer`, `RegisterAnalyzer` |
| `format` | `SmaliFormatter` – whitespace-only canonical formatting; idempotent; refuses unbalanced input |
| `explain` | `InstructionExplainer` (plain-English per instruction), `PseudocodeGenerator` (educational pseudocode) |
| `structure` | `StructureAnalyzer` – class/field/method outline with Java-style signatures |
| root | `SmaliAnalyzer` facade, `SmaliSamples` |

## Known limits

- Literal ranges follow smali's conventions (hex/octal literals may spell two's-complement bit patterns; `const/4`
  is always -8..7). Branch offset ranges (e.g. `goto` ±127 code units) are not checked.
- Types are not inferred beyond what instructions state (no verifier-style type propagation).
- The pseudocode generator recognises only straight-line if/else shapes; loops stay as labelled `goto`s and
  unsupported instructions are emitted as `/* untranslated: ... */`.
