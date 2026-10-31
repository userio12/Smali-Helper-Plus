package com.riyadm.smalihelperplus.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.riyadm.smalihelperplus.feature.about.AboutScreen
import com.riyadm.smalihelperplus.feature.ai.AiAssistantScreen
import com.riyadm.smalihelperplus.feature.analysis.ControlFlowScreen
import com.riyadm.smalihelperplus.feature.analysis.MethodExplorerScreen
import com.riyadm.smalihelperplus.feature.explainer.RegisterVisualizerScreen
import com.riyadm.smalihelperplus.feature.explainer.SmaliExplainerScreen
import com.riyadm.smalihelperplus.feature.home.HomeScreen
import com.riyadm.smalihelperplus.feature.learn.FlashcardsScreen
import com.riyadm.smalihelperplus.feature.learn.LearnScreen
import com.riyadm.smalihelperplus.feature.learn.LearningProgressScreen
import com.riyadm.smalihelperplus.feature.learn.LessonScreen
import com.riyadm.smalihelperplus.feature.learn.QuizScreen
import com.riyadm.smalihelperplus.feature.library.BookmarksScreen
import com.riyadm.smalihelperplus.feature.library.HistoryScreen
import com.riyadm.smalihelperplus.feature.opcodes.OpcodeCategoriesScreen
import com.riyadm.smalihelperplus.feature.opcodes.OpcodeDetailScreen
import com.riyadm.smalihelperplus.feature.opcodes.OpcodeListScreen
import com.riyadm.smalihelperplus.feature.playground.PlaygroundScreen
import com.riyadm.smalihelperplus.feature.search.SearchScreen
import com.riyadm.smalihelperplus.feature.settings.BackupScreen
import com.riyadm.smalihelperplus.feature.settings.SettingsScreen
import com.riyadm.smalihelperplus.feature.snippets.SnippetLibraryScreen
import com.riyadm.smalihelperplus.feature.tools.ToolsScreen
import com.riyadm.smalihelperplus.feature.tools.cheatsheets.CheatSheetsScreen
import com.riyadm.smalihelperplus.feature.tools.compare.CodeComparisonScreen
import com.riyadm.smalihelperplus.feature.tools.compatibility.CompatibilityCheckerScreen
import com.riyadm.smalihelperplus.feature.tools.descriptors.DescriptorConverterScreen
import com.riyadm.smalihelperplus.feature.tools.formats.FormatExplorerScreen
import com.riyadm.smalihelperplus.feature.tools.numbers.NumberConverterScreen
import com.riyadm.smalihelperplus.feature.tools.registers.RegisterCalculatorScreen
import com.riyadm.smalihelperplus.feature.updates.UpdatesScreen

private const val DURATION = 300

@Composable
fun AppNavHost(
    navController: NavHostController,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(DURATION)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(DURATION)) { it / 8 }
        },
        exitTransition = { fadeOut(tween(DURATION / 2)) },
        popEnterTransition = { fadeIn(tween(DURATION)) },
        popExitTransition = {
            fadeOut(tween(DURATION / 2)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(DURATION)) { it / 8 }
        },
    ) {
        // Top-level destinations
        composable<HomeRoute> { HomeScreen(onNavigate) }
        composable<OpcodesRoute> { OpcodeCategoriesScreen(onNavigate) }
        composable<LearnRoute> { LearnScreen(onNavigate) }
        composable<PlaygroundRoute> { PlaygroundScreen(onNavigate) }
        composable<ToolsRoute> { ToolsScreen(onNavigate) }

        // Opcode reference
        composable<OpcodeListRoute> { OpcodeListScreen(onNavigate, back) }
        composable<OpcodeDetailRoute> { OpcodeDetailScreen(onNavigate, back) }
        composable<SearchRoute> { SearchScreen(onNavigate, back) }

        // Learning
        composable<LessonRoute> { LessonScreen(onNavigate, back) }
        composable<QuizRoute> { QuizScreen(onNavigate, back) }
        composable<FlashcardsRoute> { FlashcardsScreen(onNavigate, back) }
        composable<LearningProgressRoute> { LearningProgressScreen(onNavigate, back) }

        // Playground & tools
        composable<SnippetLibraryRoute> { SnippetLibraryScreen(onNavigate, back) }
        composable<SmaliExplainerRoute> { SmaliExplainerScreen(onNavigate, back) }
        composable<RegisterVisualizerRoute> { RegisterVisualizerScreen(onNavigate, back) }
        composable<MethodExplorerRoute> { MethodExplorerScreen(onNavigate, back) }
        composable<ControlFlowRoute> { ControlFlowScreen(onNavigate, back) }
        composable<RegisterCalculatorRoute> { RegisterCalculatorScreen(onNavigate, back) }
        composable<NumberConverterRoute> { NumberConverterScreen(onNavigate, back) }
        composable<DescriptorConverterRoute> { DescriptorConverterScreen(onNavigate, back) }
        composable<CodeComparisonRoute> { CodeComparisonScreen(onNavigate, back) }
        composable<CompatibilityCheckerRoute> { CompatibilityCheckerScreen(onNavigate, back) }
        composable<FormatExplorerRoute> { FormatExplorerScreen(onNavigate, back) }
        composable<CheatSheetsRoute> { CheatSheetsScreen(onNavigate, back) }

        // Library & app
        composable<BookmarksRoute> { BookmarksScreen(onNavigate, back) }
        composable<HistoryRoute> { HistoryScreen(onNavigate, back) }
        composable<UpdatesRoute> { UpdatesScreen(onNavigate, back) }
        composable<SettingsRoute> { SettingsScreen(onNavigate, back) }
        composable<BackupRoute> { BackupScreen(onNavigate, back) }
        composable<AboutRoute> { AboutScreen(onNavigate, back) }
        composable<AiAssistantRoute> { AiAssistantScreen(onNavigate, back) }
    }
}
