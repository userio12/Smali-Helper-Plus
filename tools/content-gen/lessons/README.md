# Lesson content generator

Source for `app/src/main/assets/content/lessons.json` (the Interactive Learning Academy: 28 lessons and the
flashcard deck). The JSON matches `LessonBundleDto` in
`app/src/main/java/com/riyadm/smalihelperplus/core/content/ContentModels.kt`.

## Layout

| File | Contents |
| --- | --- |
| `common.py` | Helpers (`lesson`, `text`, `code`, `java`, `tip`, `warning`, `exercise`, `question`, `card`). They dedent code and re-flow prose paragraphs. |
| `part_b1.py`, `part_b2.py` | Beginner lessons (orders 1-5 and 6-10) and their flashcards |
| `part_i1.py`, `part_i2.py` | Intermediate lessons (11-15 and 16-19) |
| `part_a1.py`, `part_a2.py` | Advanced lessons (20-24 and 25-28) |
| `generate.py` | Builds the bundle, validates it, and writes the asset |
| `../kotlin-check/` | Kotlin tool (`smali-check` subcommand) that assembles `.smali` files with the smali assembler that ships inside `apktool.jar`; built on demand by `generate.py` |

## Usage

```sh
python3 -I tools/content-gen/lessons/generate.py                 # validate everything, then write the asset
python3 -I tools/content-gen/lessons/generate.py --check         # validate only
python3 -I tools/content-gen/lessons/generate.py --check --parts a1   # validate one part while editing
```

You need a JDK 17+ and an `apktool.jar` that bundles smali 3.x (default `/usr/local/bin/apktool.jar`; override with
the `APKTOOL_JAR` environment variable). The first run builds `../kotlin-check` with the project's Gradle wrapper (see
`../README.md`).

## What gets validated

- Structure: unique lesson, exercise, question and flashcard ids; levels and orders (beginner 1-10, intermediate
  11-19, advanced 20-28); 4-8 sections including text and code; at least 2 exercises; 4-6 quiz questions with
  2-5 unique options and an explanation. Every `relatedOpcodes` entry must be a real Dalvik mnemonic or payload
  pseudo-instruction. A full build also needs 28 lessons and at least 60 flashcards.
- Smali: the real assembler checks every `code` section, every smali-looking exercise starter or solution, and every
  smali-looking quiz or flashcard snippet. Fragments are wrapped first. Instruction-only snippets go into
  `.method public check(IIIIIIII)V` with `.registers 16`, so p0-p8 map to v7-v15. Member-level snippets go into a
  class. The assembler rejects literals that are out of range, registers that don't fit the instruction format,
  unknown mnemonics and syntax errors.
- Register lint: each `vN` must be lower than the method's register count, and each `pN` lower than its
  parameter-word count. The assembler does not check this.

`code(..., check=False)` and `exercise(..., check_starter=False)` skip assembly. Use them only for listings that are
invalid on purpose and labelled that way in the text.

After you change any lesson text, bump `CONTENT_VERSION` in `generate.py` so existing installs re-seed. Then run
`./gradlew :app:testDebugUnitTest --tests '*BundledLessonsTest*'`.
