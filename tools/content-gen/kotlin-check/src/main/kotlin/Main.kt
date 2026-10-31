import kotlin.system.exitProcess

private const val USAGE = """Usage:
  kotlin-check smali-check --list-opcodes
  kotlin-check smali-check <file.smali>...
  kotlin-check asm-examples <apiLevel> <file.smali>..."""

fun main(args: Array<String>) {
    val rest = args.drop(1).toTypedArray()
    when (args.firstOrNull()) {
        "smali-check" -> smaliCheck(rest)
        "asm-examples" -> exitProcess(asmExamples(rest))
        else -> {
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}
