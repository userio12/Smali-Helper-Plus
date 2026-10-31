package com.riyadm.smalihelperplus.feature.tools.numbers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.domain.tools.ConstOption
import com.riyadm.smalihelperplus.domain.tools.FloatingAnalysis
import com.riyadm.smalihelperplus.domain.tools.FloatingDetails
import com.riyadm.smalihelperplus.domain.tools.IntegerAnalysis
import com.riyadm.smalihelperplus.domain.tools.NumberAnalysis
import com.riyadm.smalihelperplus.domain.tools.NumberConverter
import com.riyadm.smalihelperplus.domain.tools.NumberView
import com.riyadm.smalihelperplus.domain.tools.OctalRule
import com.riyadm.smalihelperplus.feature.tools.common.CodeInputField
import com.riyadm.smalihelperplus.feature.tools.common.ErrorText
import com.riyadm.smalihelperplus.feature.tools.common.NoteRow
import com.riyadm.smalihelperplus.feature.tools.common.PresetChips
import com.riyadm.smalihelperplus.feature.tools.common.ResultRow
import com.riyadm.smalihelperplus.feature.tools.common.SmaliResultLine
import com.riyadm.smalihelperplus.feature.tools.common.ToolCard
import com.riyadm.smalihelperplus.feature.tools.common.ToolPage
import com.riyadm.smalihelperplus.feature.tools.common.caretDetail
import com.riyadm.smalihelperplus.navigation.AppRoute

private val PRESETS = listOf(
    "255" to "255",
    "-1" to "-1",
    "0x7fffffff" to "0x7fffffff",
    "0xFFFFFFFF" to "0xFFFFFFFF",
    "0x7fffffffffffffffL" to "0x7fffffffffffffffL",
    "0b1010" to "0b1010",
    "0o777" to "0o777",
    "-0x80t" to "-0x80t",
    "10.0f" to "10.0f",
    "3.14159" to "3.14159",
    "0x41200000" to "0x41200000",
)

private val REGISTER_REGEX = Regex("^[vp]\\d{1,5}$")

@Composable
fun NumberConverterScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var leadingZeroOctal by rememberSaveable { mutableStateOf(true) }
    var register by rememberSaveable { mutableStateOf("v0") }

    val registerValid = REGISTER_REGEX.matches(register.trim())
    val analysis: NumberAnalysis? = remember(input, leadingZeroOctal, register, registerValid) {
        if (input.isBlank()) null
        else NumberConverter.analyze(
            input,
            if (leadingZeroOctal) OctalRule.LEADING_ZERO else OctalRule.PREFIX_ONLY,
            if (registerValid) register.trim() else "v0",
        )
    }

    DetailScaffold(title = "Number converter", onBack = onBack) { padding ->
        ToolPage(padding) {
            ToolCard(title = "Value") {
                CodeInputField(
                    value = input,
                    onValueChange = { input = it },
                    label = "Number or smali literal",
                    placeholder = "0x41200000, -42, 0b1010, 7L, 1.5f",
                    supportingText = "Decimal, 0x hex, 0b binary, 0o octal; suffix L (long), t (byte), s (short); floats like 1.5f.",
                    modifier = Modifier.testTag("numconv_input"),
                )
                PresetChips(PRESETS, onPick = { input = it })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Leading 0 means octal", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (leadingZeroOctal) "010 is 8, as smali and Java read it" else "010 is decimal 10 (use 0o10 for octal)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = leadingZeroOctal, onCheckedChange = { leadingZeroOctal = it }, modifier = Modifier.testTag("numconv_octal"))
                }
                CodeInputField(
                    value = register,
                    onValueChange = { register = it.take(6) },
                    label = "Register for generated instructions",
                    isError = !registerValid,
                    supportingText = if (registerValid) null else "Use v0, v1, p0 ...",
                )
            }

            when (analysis) {
                null -> ToolCard {
                    Text(
                        "Type a number to see it in every base and width, its IEEE 754 form and the const instruction that fits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is NumberAnalysis.Failure -> ToolCard {
                    ErrorText(analysis.message, detail = caretDetail(input, analysis.position))
                }
                is NumberAnalysis.IntegerResult -> IntegerResults(analysis.analysis)
                is NumberAnalysis.FloatingResult -> FloatingResults(analysis.analysis)
            }
        }
    }
}

@Composable
private fun IntegerResults(a: IntegerAnalysis) {
    val primary = a.primary
    ToolCard(title = "Interpreted as ${primary.width}-bit${a.parsed.suffix?.let { " (suffix ${it.char})" } ?: ""}") {
        ResultRow("Signed decimal", primary.signed.toString())
        ResultRow("Unsigned decimal", primary.unsigned.toString())
        ResultRow("Hex", primary.hex)
        ResultRow("Binary", primary.binary)
        ResultRow("Octal", primary.octal)
        ResultRow("Smali literal", primary.smali)
        primary.asFloatingText?.let { ResultRow(if (primary.width == 32) "Bits read as float" else "Bits read as double", it) }
        a.notes.forEach { NoteRow(it, warning = true) }
    }

    ToolCard(title = "Other widths (two's complement)") {
        a.views.forEach { v ->
            WidthBlock(v)
            if (v !== a.views.last()) HorizontalDivider()
        }
    }

    ConstCard(a.constOptions, wide = a.primaryWidth == 64)
}

@Composable
private fun WidthBlock(v: NumberView) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${v.width}-bit", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (!v.fits) {
                Text("does not fit - truncated", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        ResultRow("Signed / unsigned", "${v.signed}  /  ${v.unsigned}", copyText = v.signed.toString())
        ResultRow("Hex", v.hex)
        ResultRow("Smali literal", v.smali)
    }
}

@Composable
private fun ConstCard(options: List<ConstOption>, wide: Boolean) {
    ToolCard(title = if (wide) "Which const-wide instruction fits" else "Which const instruction fits") {
        options.forEach { o ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (o.fits) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (o.fits) "Fits" else "Does not fit",
                        tint = if (o.fits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        o.opcode,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (o.recommended) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${o.codeUnits} unit${if (o.codeUnits == 1) "" else "s"}" + if (o.recommended) " - smallest" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (o.recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(o.rule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp))
                if (o.smali != null) SmaliResultLine(o.smali, Modifier.padding(start = 32.dp))
            }
        }
    }
}

@Composable
private fun FloatingResults(a: FloatingAnalysis) {
    ToolCard(title = "Float (32-bit) bit pattern") {
        FloatingBlock(a.float)
    }
    ToolCard(title = "Double (64-bit) bit pattern") {
        FloatingBlock(a.double)
    }
    ConstCard(
        if (a.preferDouble) a.doubleConstOptions else a.floatConstOptions,
        wide = a.preferDouble,
    )
    ToolCard(title = if (a.preferDouble) "As float instead" else "As double instead") {
        val others = if (a.preferDouble) a.floatConstOptions else a.doubleConstOptions
        others.filter { it.recommended }.forEach { o -> o.smali?.let { SmaliResultLine(it) } }
        a.notes.forEach { NoteRow(it) }
    }
}

@Composable
private fun FloatingBlock(d: FloatingDetails) {
    ResultRow("Hex bits", d.hex)
    ResultRow("Value", d.text)
    val expBits = if (d.bits == 32) 8 else 11
    val mantBits = if (d.bits == 32) 23 else 52
    ResultRow("Sign bit", if (d.sign == 0) "0 (positive)" else "1 (negative)")
    ResultRow("Exponent ($expBits bits)", "${d.exponentRaw}" + (d.exponentUnbiased?.let { " (2^$it)" } ?: ""))
    ResultRow("Mantissa ($mantBits bits)", "0x" + d.mantissaBits.toString(16))
    ResultRow("Class", d.classification, mono = false)
}
