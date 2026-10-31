import brut.androlib.mod.SmaliMod
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import java.io.File

private const val MAX_EXIT_CODE = 100

/**
 * Assembles each given .smali file with the smali/dexlib2 copy bundled in apktool.jar and writes a throw-away dex,
 * so syntax, register-field ranges, literal ranges, labels and payload alignment are all checked.
 *
 * Usage: kotlin-check asm-examples <apiLevel> <file.smali>...
 *
 * Prints "OK <file>" or "FAIL <file> <reason>" per file. Returns the process exit code: the number of failures,
 * capped at 100.
 */
fun asmExamples(args: Array<String>): Int {
    val api = args.first().toInt()
    var failures = 0
    for (path in args.drop(1)) {
        val file = File(path)
        var reason = ""
        val ok = try {
            // Equivalent of Opcodes.forApi(api) (NO_VERSION = -1); apktool's minified dexlib2 lacks forApi.
            val builder = DexBuilder(Opcodes(api, -1))
            SmaliMod.assembleSmaliFile(file, builder, api).also { assembled ->
                if (assembled) writeThrowAwayDex(builder)
            }
        } catch (t: Throwable) {
            reason = t.toString()
            false
        }
        if (!ok) failures++
        println((if (ok) "OK " else "FAIL ") + file.name + (if (reason.isEmpty()) "" else " $reason"))
    }
    return minOf(failures, MAX_EXIT_CODE)
}

private fun writeThrowAwayDex(builder: DexBuilder) {
    val out = File.createTempFile("example", ".dex")
    try {
        builder.writeTo(FileDataStore(out))
    } finally {
        out.delete()
    }
}
