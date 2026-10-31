# content-gen: offline Dalvik opcode database

Generates `app/src/main/assets/content/opcodes.json` (an `OpcodeBundleDto`, see
`app/src/main/java/com/riyadm/smalihelperplus/core/content/ContentModels.kt`), which `BundledContentSeeder` installs on first launch.

## Files

| Path | Purpose |
| --- | --- |
| `generate_opcodes.py` | Entry point. Parses the saved spec, cross-checks it, builds the entries, verifies them and writes the JSON. |
| `opcode_text.py` | Per-family text templates (binops across int/long/float/double in 23x, /2addr, /lit16 and /lit8 forms; aget/aput, iget/iput and sget/sput type variants; if-test and if-testz; invoke kinds and /range; conversions; and so on) plus hand-written entries for unique instructions. All prose is original. |
| `verify_examples.py` | Optional extra check that assembles every example with real smali (see below). |
| `kotlin-check/` | Standalone Kotlin/Gradle tool (`smali-check`, `asm-examples`) that drives the smali assembler bundled in `apktool.jar`. Used by `verify_examples.py` and `lessons/generate.py`; see below. |
| `kotlin_tool.py` | Small helper that builds `kotlin-check` on first use and returns its launcher path. |
| `spec/` | Saved copies of the sources, so generation is reproducible offline. |

### Saved sources (`spec/`)

| File | Source |
| --- | --- |
| `dalvik-bytecode.html` | https://source.android.com/docs/core/runtime/dalvik-bytecode (page "Last updated 2026-10-09 UTC") |
| `instruction-formats.html` | https://source.android.com/docs/core/runtime/instruction-formats |
| `dex-format.html` | https://source.android.com/docs/core/runtime/dex-format |
| `Opcode.java.txt`, `VersionMap.java.txt` | google-smali `dexlib2/src/main/java/com/android/tools/smali/dexlib2/` (refs/heads/main) |
| `smaliParser.g`, `smaliLexer.jflex` | google-smali grammar (`third_party/smali/src/main/antlr`, `smali/src/main/jflex`) |
| `MutableMethodImplementation.java.txt` | google-smali `dexlib2/.../builder/` (confirms goto widening, payload padding, one payload per switch) |

`Opcode.java.txt`, `VersionMap.java.txt` and `MutableMethodImplementation.java.txt` are upstream google-smali source
files (BSD-3-Clause; the license header is kept intact at the top of each file). They are kept only as reference
**data** that `generate_opcodes.py` parses as text: they are not compiled, not part of the app, and the `.txt`
extension keeps them out of the repository's language statistics.

All of these were fetched with `curl` on 2026-10-30. Gitiles files were fetched with `?format=TEXT` and base64-decoded.

## What is extracted vs. written

Extracted from the saved HTML at generation time, so the values cannot drift:

* the opcode value, mnemonic, format id and spec syntax of every row in "Summary of bytecode set", including the
  family rows such as `90..af 23x binop vAA, vBB, vCC`;
* the operand bit widths in the "Arguments" column (each `X: ... (N bits)` statement is checked against the
  generated operands);
* the dex version, from "Present in Dex files from version 0NN onwards";
* the payload layouts and code-unit formulas from the three payload format tables;
* the official format ids and bit layouts (for example `AA|op CC|BB`) from instruction-formats;
* the Android release that introduced each dex version, from the `DEX_FILE_MAGIC` notes in dex-format (8.0 for 038,
  9.0 for 039), mapped to API levels (26 and 28).

Written by hand: the explanations, examples, Java/Kotlin equivalents, register-usage notes and common mistakes.

## Regenerating

From the repository root:

```sh
python3 -I tools/content-gen/generate_opcodes.py           # regenerate + verify (fails without writing on any error)
python3 -I tools/content-gen/generate_opcodes.py --check   # verify only; fails if the JSON on disk is stale
python3 -I tools/content-gen/verify_examples.py            # optional: assemble every example with smali
```

The scripts use the Python 3 standard library only.

To refresh the spec, re-download the files listed above into `spec/` (for example
`curl -sSL -o spec/dalvik-bytecode.html 'https://source.android.com/docs/core/runtime/dalvik-bytecode?hl=en'`), run the
generator and review any verification failures. If the page gains new instructions, the generator stops with an
"entry set mismatch" error until templates for them have been added to `opcode_text.py`. Bump `CONTENT_VERSION` in
`generate_opcodes.py` whenever the published content changes, so installed apps re-seed the bundle.

## Built-in verification (`generate_opcodes.py`)

The script refuses to write the JSON unless all of these hold:

* every value 0x00..0xff appears exactly once in the spec table, either as an instruction or in an "(unused)" range.
  Unused ranges are not emitted as entries;
* the spec's mnemonics, opcode values and formats match google-smali's `Opcode.java.txt` (non-ODEX entries). Only the
  six dex 038/039 instructions may be version-gated there;
* names are unique, the entry set is exactly the spec instructions plus the three payloads, and each opcode value is
  used once (payloads use their idents 0x0100, 0x0200 and 0x0300);
* opcode, format and dex version of every entry equal the parsed spec. Operand bit widths agree with the
  spec's Arguments column, and register placeholders in `syntax` equal the spec's;
* formats are official format ids, categories are valid `OpcodeCategory` ids, every `related` name exists, every
  entry has non-empty text, an example, 2 to 4 common mistakes, tags and an HTTPS dalvik-bytecode reference.

It prints the per-category counts on success.

## Optional: assembling the examples (`verify_examples.py`)

This check uses the smali/dexlib2 copy bundled inside `apktool.jar`, so nothing extra is downloaded (default path
`/usr/local/bin/apktool.jar`; override it with `--apktool-jar` or `APKTOOL_JAR`). It needs a JDK 17+ (`JAVA_HOME`
or `java` on `PATH`) to build and run the Kotlin tool described in the next section.

* Each example must assemble and write a dex at the entry's documented `minApi`. This catches bad syntax, register
  fields out of range, literal ranges, missing labels and payload widths.
* Each entry with `minApi > 1` must fail to assemble at `minApi - 1`. smali's own opcode tables reject
  invoke-polymorphic, invoke-custom and their /range forms below API 26, and const-method-handle and
  const-method-type below API 28.

Snippets that are not complete methods are wrapped in a method with enough locals. Labels they reference but don't
define are appended.

## The Kotlin verification tool (`kotlin-check/`)

`kotlin-check/` is a small standalone Gradle build (Kotlin 2.4.21, JVM 17 target) with two subcommands:

* `kotlin-check smali-check [--list-opcodes | file.smali...]`: prints `OK <file>` / `ERR <file>` per file
  (diagnostics on stderr), or the sorted opcode names known to the assembler. Used by `lessons/generate.py`.
* `kotlin-check asm-examples <apiLevel> file.smali...`: assembles each file at that API level and writes a throw-away
  dex; prints `OK <file>` / `FAIL <file> <reason>` and exits with the failure count (capped at 100). Used by
  `verify_examples.py`.

It is not part of the app's `settings.gradle.kts`, so the app build and CI never depend on `apktool.jar`. The Python
scripts build it on demand with the project's Gradle wrapper (`kotlin_tool.py`); to build or run it by hand from the
repository root:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk            # any JDK 17+
./gradlew -p tools/content-gen/kotlin-check installDist   # APKTOOL_JAR=/path/to/apktool.jar to override the jar
tools/content-gen/kotlin-check/build/install/kotlin-check/bin/kotlin-check smali-check --list-opcodes
```

The apktool jar (default `/usr/local/bin/apktool.jar`, Gradle property or environment variable `APKTOOL_JAR`) is
copied into the install at build time; delete `kotlin-check/build` to rebuild against a different jar.

## Scope decisions

* The bundle covers every instruction currently listed in the spec table (224) plus the three data payload
  pseudo-instructions, 227 entries in total.
* The historical ODEX/"quick"/"jumbo" opcodes are excluded because they are not in the current spec. That covers
  `*-quick`, `*-volatile`, `execute-inline`, `invoke-*-quick`, `invoke-direct-empty`, `invoke-object-init/range`,
  `return-void-barrier`/`-no-barrier`, `throw-verification-error` and the Dalvik "jumbo" proposals.
  `const-string/jumbo` (0x1b) is a regular instruction and is included.
* The payloads use the spec's names (`packed-switch-payload`, `sparse-switch-payload`, `fill-array-data-payload`) as
  both name and format id, and are in category `advanced` (with `nop`), matching that category's description.
  dexlib2 calls the third one `array-payload`; in smali source they are written as the `.packed-switch`,
  `.sparse-switch` and `.array-data` directives.
* `move-exception` is in category `exception`; `neg-*` are `arithmetic`; `not-*` are `bitwise`.
* The 23x, /2addr, /lit16 and /lit8 forms of add/sub/mul/div/rem/rsub are `arithmetic`; the same forms of
  and/or/xor/shl/shr/ushr are `bitwise`.
