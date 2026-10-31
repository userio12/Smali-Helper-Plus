package com.riyadm.smalihelperplus.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Root UI: adaptive navigation (bottom bar on phones, rail on tablets) around the navigation host. */
@Composable
fun SmaliHelperAppUi() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTop = TopLevelDestination.entries.firstOrNull { top ->
        destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true
    }

    val navigate: (AppRoute) -> Unit = { route ->
        val top = TopLevelDestination.entries.firstOrNull { it.route == route }
        if (top != null) {
            navController.navigate(top.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            // Single-top would reuse the current entry (and its ViewModel) when opening the same screen with other
            // arguments, e.g. a related opcode from an opcode's detail, so the screen would not change.
            val sameScreen = navController.currentDestination?.hasRoute(route::class) == true
            navController.navigate(route) { launchSingleTop = !sameScreen }
        }
    }

    val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    // Secondary screens on phones get the full height; tablets keep the navigation rail visible everywhere.
    val layoutType = if (currentTop == null && adaptiveType == NavigationSuiteType.NavigationBar) {
        NavigationSuiteType.None
    } else {
        adaptiveType
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { top ->
                val selected = top == currentTop
                item(
                    selected = selected,
                    onClick = { navigate(top.route) },
                    icon = { Icon(if (selected) top.selectedIcon else top.unselectedIcon, contentDescription = null) },
                    label = { NavLabel(top.label) },
                    modifier = Modifier.testTag(top.testTag),
                )
            }
        },
    ) {
        AppNavHost(navController = navController, onNavigate = navigate, modifier = Modifier.fillMaxSize())
    }
}

/** One line at the theme's label size; shrinks only when it does not fit (e.g. "Playground" at large font scales). */
@Composable
private fun NavLabel(text: String) {
    val max = LocalTextStyle.current.fontSize.takeIf { it.isSp } ?: 12.sp
    Text(text, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = max, stepSize = 0.5.sp))
}
