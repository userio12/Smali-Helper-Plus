package com.riyadm.smalihelperplus.core.smali

import com.riyadm.smalihelperplus.core.smali.diagnostics.Diagnostic
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity

/** Wraps a method body into a minimal, otherwise valid class. */
fun classWith(
    body: String,
    signature: String = "test()V",
    flags: String = "public static",
    frame: String = ".registers 4",
): String = buildString {
    append(".class public Lcom/acme/demo/T;\n")
    append(".super Ljava/lang/Object;\n\n")
    append(".method $flags $signature\n")
    if (frame.isNotEmpty()) append("    $frame\n")
    body.trimIndent().lines().forEach { append("    ").append(it).append('\n') }
    append(".end method\n")
}

fun diagnostics(code: String, minApi: Int = 26): List<Diagnostic> = SmaliAnalyzer.analyze(code, AnalysisOptions(minApi = minApi)).diagnostics

fun List<Diagnostic>.errors() = filter { it.severity == Severity.ERROR }

fun List<Diagnostic>.codes(): Set<DiagnosticCode> = map { it.code }.toSet()

fun List<Diagnostic>.withCode(code: DiagnosticCode) = filter { it.code == code }
