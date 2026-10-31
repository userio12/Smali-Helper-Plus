import brut.androlib.mod.SmaliMod
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import java.io.File

private const val API = 30

/**
 * Assembles .smali files with the smali assembler bundled inside apktool.jar and reports which ones fail.
 *
 * Usage:
 *   kotlin-check smali-check --list-opcodes
 *   kotlin-check smali-check file1.smali file2.smali ...
 *
 * Output lines: "OK <file>" or "ERR <file>" (assembler diagnostics go to stderr, prefixed with the file name).
 */
fun smaliCheck(args: Array<String>) {
    if (args.singleOrNull() == "--list-opcodes") {
        Opcodes(API, -1).opcodesByName.keys.map { it.toString() }.toSortedSet().forEach(::println)
        return
    }
    for (path in args) {
        val ok = try {
            SmaliMod.assembleSmaliFile(File(path), DexBuilder(Opcodes(API, -1)), API)
        } catch (t: Throwable) {
            System.err.println("$path: $t")
            false
        }
        println((if (ok) "OK " else "ERR ") + path)
    }
}
