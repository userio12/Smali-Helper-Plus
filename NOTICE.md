# Third-party notices

Smali Helper Plus is licensed under the Apache License 2.0 (see [LICENSE](LICENSE)). Copyright 2026 riyadmondol2006.

This file lists third-party material the project relies on or draws facts from.

## Android Open Source Project documentation

Facts about Dalvik bytecode, instruction formats and the dex file format follow the public Android documentation:

- https://source.android.com/docs/core/runtime/dalvik-bytecode
- https://source.android.com/docs/core/runtime/instruction-formats
- https://source.android.com/docs/core/runtime/dex-format

Per https://source.android.com/license, the documentation content of source.android.com, including code shown in it,
is licensed under the **Apache License 2.0**; all other content on that site is licensed under the **Creative
Commons Attribution 4.0** license (CC BY 4.0, https://creativecommons.org/licenses/by/4.0/). Some documentation
extracted from source code modules can carry the license of that module. Google trademarks and brand features are
not covered by these licenses.

All explanatory text, examples and quiz material in this app are written independently. The app states facts
(opcode names, formats, semantics) and credits the official pages as its source; it does not reproduce the
documentation pages. Educational pseudocode in the app is labelled as pseudocode, not as decompiler output.

"Android" and "Pixel" are trademarks of Google LLC. This project is not affiliated with or endorsed by Google.

## smali / baksmali (reference only)

The [smali](https://github.com/google/smali) assembler and baksmali disassembler, created by Ben Gruver (JesusFreke)
and maintained by Google, are released under a BSD-style three-clause license (copyright 2010 Ben Gruver, with
later changes copyright Google LLC; see the `LICENSE` file in that repository). Smali Helper Plus **does not bundle
or link** smali or dexlib2. The in-app engine is an independent Kotlin parser/validator. smali is credited here
because the app teaches the syntax that smali defines.

## Libraries bundled in the app

| Library | License | Project |
|---|---|---|
| AndroidX (Core, Activity, Lifecycle, Navigation, Room, DataStore, WorkManager, DocumentFile, Hilt extensions, Compose, Material 3, Material Icons) | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Material icons (vector icons from `material-icons-extended`) | Apache-2.0 | https://fonts.google.com/icons |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | https://kotlinlang.org |
| Dagger / Hilt | Apache-2.0 | https://dagger.dev |
| OkHttp, Okio | Apache-2.0 | https://square.github.io/okhttp/ |

Build and test only (not shipped): JUnit 4 (Eclipse Public License 1.0), Truth (Apache-2.0), Turbine (Apache-2.0),
AndroidX Test and Espresso (Apache-2.0), KSP (Apache-2.0).

The full license text of each library is available from its project page and in its published artifacts.

## The original Smali Helper

The idea for a smali learning companion on Android comes from the original **Smali Helper** by Zylern
(https://github.com/Zylern/Smali-Helper). That repository carries no license, so **no code, text, data or assets
were taken from it**; only the general idea of a smali reference app was kept. Smali Helper Plus is an independent
implementation with original content.
