package com.riyadm.smalihelperplus.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: AppRoute,
    val routeClass: KClass<out AppRoute>,
    val testTag: String,
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, HomeRoute, HomeRoute::class, "nav_home"),
    OPCODES("Opcodes", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, OpcodesRoute, OpcodesRoute::class, "nav_opcodes"),
    LEARN("Learn", Icons.Filled.School, Icons.Outlined.School, LearnRoute, LearnRoute::class, "nav_learn"),
    PLAYGROUND("Playground", Icons.Filled.Code, Icons.Outlined.Code, PlaygroundRoute, PlaygroundRoute::class, "nav_playground"),
    TOOLS("Tools", Icons.Filled.Build, Icons.Outlined.Build, ToolsRoute, ToolsRoute::class, "nav_tools"),
}
