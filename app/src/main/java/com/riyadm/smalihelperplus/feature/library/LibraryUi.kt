package com.riyadm.smalihelperplus.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType

fun typeIcon(type: String): ImageVector = when (type) {
    BookmarkType.OPCODE -> Icons.Outlined.DataObject
    BookmarkType.LESSON -> Icons.Outlined.School
    else -> Icons.Outlined.Code
}

fun typeLabel(type: String): String = when (type) {
    BookmarkType.OPCODE -> "Opcode"
    BookmarkType.LESSON -> "Lesson"
    BookmarkType.SNIPPET -> "Snippet"
    else -> type.replaceFirstChar { it.uppercase() }
}

/** Round tonal badge with the icon of a library item type. */
@Composable
fun TypeBadge(type: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(typeIcon(type), contentDescription = typeLabel(type), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
