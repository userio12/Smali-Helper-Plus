package com.riyadm.smalihelperplus.feature.tools.registers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.domain.tools.NoteLevel
import com.riyadm.smalihelperplus.domain.tools.RegisterCalculator
import com.riyadm.smalihelperplus.domain.tools.RegisterDeclaration
import com.riyadm.smalihelperplus.domain.tools.RegisterLayout
import com.riyadm.smalihelperplus.domain.tools.RegisterResult
import com.riyadm.smalihelperplus.domain.tools.RegisterRow
import com.riyadm.smalihelperplus.feature.tools.common.CodeInputField
import com.riyadm.smalihelperplus.feature.tools.common.ErrorText
import com.riyadm.smalihelperplus.feature.tools.common.NoteRow
import com.riyadm.smalihelperplus.feature.tools.common.PresetChips
import com.riyadm.smalihelperplus.feature.tools.common.ResultRow
import com.riyadm.smalihelperplus.feature.tools.common.ToolCard
import com.riyadm.smalihelperplus.feature.tools.common.ToolPage
import com.riyadm.smalihelperplus.feature.tools.common.caretDetail
import com.riyadm.smalihelperplus.navigation.AppRoute

private val PRESETS = listOf(
    "(II)I" to "(II)I",
    "(IJLjava/lang/String;)V" to "(IJLjava/lang/String;)V",
    "int a, long b, double c" to "int a, long b, double c",
    "Lcom/a/B;->m(DD)D" to "Lcom/a/B;->m(DD)D",
    "no parameters" to "()V",
)

@Composable
fun RegisterCalculatorScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    var signature by rememberSaveable { mutableStateOf("") }
    var isStatic by rememberSaveable { mutableStateOf(false) }
    var useRegisters by rememberSaveable { mutableStateOf(false) }
    var countText by rememberSaveable { mutableStateOf("2") }
    var declaringClass by rememberSaveable { mutableStateOf("") }

    val count = countText.trim().toIntOrNull()
    val countError = when {
        countText.isBlank() -> "Enter a number"
        count == null -> "Digits only"
        count < 0 -> "Cannot be negative"
        else -> null
    }
    val result: RegisterResult? = remember(signature, isStatic, useRegisters, count, declaringClass) {
        if (count == null || count < 0) null
        else RegisterCalculator.calculate(
            signature = signature,
            isStatic = isStatic,
            declaration = if (useRegisters) RegisterDeclaration.REGISTERS else RegisterDeclaration.LOCALS,
            count = count,
            declaringClass = declaringClass.takeIf { !isStatic && it.isNotBlank() },
        )
    }

    DetailScaffold(title = "Register calculator", onBack = onBack) { padding ->
        ToolPage(padding) {
            ToolCard(title = "Method") {
                CodeInputField(
                    value = signature,
                    onValueChange = { signature = it },
                    label = "Descriptor or Java parameters",
                    placeholder = "(IJLjava/lang/String;)V  or  int a, long b",
                    supportingText = "Prototype, member reference, Java signature or parameter list. Empty means no parameters.",
                    modifier = Modifier.testTag("regcalc_descriptor"),
                )
                PresetChips(PRESETS, onPick = { signature = it })
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isStatic, onClick = { isStatic = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("regcalc_static"),
                    ) { Text("Static") }
                    SegmentedButton(
                        selected = !isStatic, onClick = { isStatic = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("regcalc_instance"),
                    ) { Text("Instance") }
                }
                if (!isStatic) {
                    CodeInputField(
                        value = declaringClass,
                        onValueChange = { declaringClass = it },
                        label = "Declaring class (optional)",
                        placeholder = "Lcom/acme/demo/Foo;",
                    )
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useRegisters, onClick = { useRegisters = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(".locals") }
                    SegmentedButton(
                        selected = useRegisters, onClick = { useRegisters = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(".registers") }
                }
                CodeInputField(
                    value = countText,
                    onValueChange = { countText = it.filter { c -> c.isDigit() }.take(6) },
                    label = if (useRegisters) "Value of .registers" else "Value of .locals",
                    keyboardType = KeyboardType.Number,
                    isError = countError != null,
                    supportingText = countError,
                    modifier = Modifier.testTag("regcalc_locals"),
                )
            }

            when (result) {
                null -> if (countError != null) ErrorText("Fix the register count to see the layout.")
                is RegisterResult.Failure -> ToolCard {
                    ErrorText(result.message, detail = caretDetail(signature, result.position))
                }
                is RegisterResult.Success -> LayoutResults(result.layout)
            }
        }
    }
}

@Composable
private fun LayoutResults(layout: RegisterLayout) {
    ToolCard(title = "Frame summary") {
        ResultRow("Total registers (.registers)", layout.registersDirective)
        ResultRow("Local registers (.locals)", layout.localsDirective)
        ResultRow(
            "Parameter words",
            "${layout.parameterWords}" + if (!layout.isStatic) " (including this)" else "",
            copyText = layout.parameterWords.toString(),
        )
        if (layout.slots.isNotEmpty()) {
            val first = layout.slots.first().firstV
            val last = layout.totalRegisters - 1
            ResultRow("Parameter registers", if (first == last) "v$first" else "v$first .. v$last")
        }
    }

    if (layout.slots.isNotEmpty()) {
        ToolCard(title = "Parameters") {
            layout.slots.forEach { slot ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            slot.label + if (slot.isThis) "" else ": " + slot.type.java,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        val p = if (slot.isWide) "p${slot.firstP}/p${slot.firstP + 1}" else "p${slot.firstP}"
                        val v = if (slot.isWide) "v${slot.firstV}/v${slot.lastV}" else "v${slot.firstV}"
                        Text(
                            "$p  =  $v" + if (slot.isWide) "  (register pair)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        slot.beyond8Bit -> Flag(">v255")
                        slot.beyond4Bit -> Flag(">v15")
                    }
                }
            }
        }
    }

    ToolCard(title = "Register map") {
        Row(Modifier.fillMaxWidth()) {
            HeaderCell("vN", 64)
            HeaderCell("pN", 56)
            Text("Role", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            layout.rows.forEach { RegisterRowView(it) }
        }
        if (layout.locals > layout.rows.count { it.p == null }) {
            Text(
                "… ${layout.locals - layout.rows.count { it.p == null }} more local registers (v${layout.rows.count { it.p == null }}..v${layout.locals - 1})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    ToolCard(title = "Notes") {
        layout.notes.forEach { NoteRow(it.text, warning = it.level == NoteLevel.WARNING) }
    }
}

@Composable
private fun HeaderCell(text: String, widthDp: Int) {
    Text(
        text,
        modifier = Modifier.width(widthDp.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RegisterRowView(row: RegisterRow) {
    val bg = if (row.p != null) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (row.isWideLow || row.isWideHigh) 0.9f else 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.vName, Modifier.width(56.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text(row.pName ?: "–", Modifier.width(56.dp), fontFamily = FontFamily.Monospace)
        Text(row.role, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        if (!row.reachableBy8Bit) Flag(">v255") else if (!row.reachableBy4Bit) Flag(">v15")
    }
}

@Composable
private fun Flag(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}
