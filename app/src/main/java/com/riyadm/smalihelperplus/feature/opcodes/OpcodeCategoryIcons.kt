package com.riyadm.smalihelperplus.feature.opcodes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.ui.graphics.vector.ImageVector
import com.riyadm.smalihelperplus.domain.model.OpcodeCategory

fun OpcodeCategory.icon(): ImageVector = when (this) {
    OpcodeCategory.MOVE -> Icons.Outlined.SwapHoriz
    OpcodeCategory.RETURN -> Icons.AutoMirrored.Outlined.KeyboardReturn
    OpcodeCategory.CONSTANT -> Icons.Outlined.Tag
    OpcodeCategory.MONITOR -> Icons.Outlined.Lock
    OpcodeCategory.TYPE -> Icons.Outlined.Category
    OpcodeCategory.OBJECT -> Icons.Outlined.DataObject
    OpcodeCategory.ARRAY -> Icons.Outlined.DataArray
    OpcodeCategory.FIELD -> Icons.AutoMirrored.Outlined.ViewList
    OpcodeCategory.INVOKE -> Icons.Outlined.Functions
    OpcodeCategory.ARITHMETIC -> Icons.Outlined.Calculate
    OpcodeCategory.BITWISE -> Icons.Outlined.Numbers
    OpcodeCategory.COMPARISON -> Icons.Outlined.Compare
    OpcodeCategory.BRANCH -> Icons.AutoMirrored.Outlined.CallSplit
    OpcodeCategory.JUMP -> Icons.AutoMirrored.Outlined.Redo
    OpcodeCategory.SWITCH -> Icons.AutoMirrored.Outlined.Rule
    OpcodeCategory.CONVERSION -> Icons.Outlined.Transform
    OpcodeCategory.EXCEPTION -> Icons.Outlined.BugReport
    OpcodeCategory.METHOD_HANDLE -> Icons.Outlined.Link
    OpcodeCategory.ADVANCED -> Icons.Outlined.Tune
}
