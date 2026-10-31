package com.riyadm.smalihelperplus.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes. Every screen receives `onNavigate: (AppRoute) -> Unit` and, for secondary screens,
 * `onBack: () -> Unit`. Large payloads (code) are passed through [com.riyadm.smalihelperplus.core.common.CodeHandoff],
 * never through route arguments.
 */
@Serializable sealed interface AppRoute

// Top-level destinations
@Serializable data object HomeRoute : AppRoute
@Serializable data object OpcodesRoute : AppRoute
@Serializable data object LearnRoute : AppRoute
@Serializable data object PlaygroundRoute : AppRoute
@Serializable data object ToolsRoute : AppRoute

// Opcode reference
/** [category] is an OpcodeCategory id, or null for all opcodes. */
@Serializable data class OpcodeListRoute(val category: String? = null) : AppRoute
@Serializable data class OpcodeDetailRoute(val name: String) : AppRoute
@Serializable data class SearchRoute(val query: String = "") : AppRoute

// Learning
@Serializable data class LessonRoute(val lessonId: String) : AppRoute
/** [lessonId] is a lesson id, or [QuizRoute.DAILY] / [QuizRoute.MIXED] for practice sessions. */
@Serializable data class QuizRoute(val lessonId: String) : AppRoute {
    companion object {
        const val DAILY = "daily"
        const val MIXED = "mixed"
    }
}
@Serializable data object FlashcardsRoute : AppRoute
@Serializable data object LearningProgressRoute : AppRoute

// Playground & tools
@Serializable data object SnippetLibraryRoute : AppRoute
@Serializable data object RegisterCalculatorRoute : AppRoute
@Serializable data object NumberConverterRoute : AppRoute
@Serializable data object DescriptorConverterRoute : AppRoute
@Serializable data object MethodExplorerRoute : AppRoute
@Serializable data object CodeComparisonRoute : AppRoute
@Serializable data object ControlFlowRoute : AppRoute
@Serializable data object SmaliExplainerRoute : AppRoute
@Serializable data object RegisterVisualizerRoute : AppRoute
@Serializable data object CompatibilityCheckerRoute : AppRoute
@Serializable data object FormatExplorerRoute : AppRoute
@Serializable data object CheatSheetsRoute : AppRoute

// Library & app
@Serializable data object BookmarksRoute : AppRoute
@Serializable data object HistoryRoute : AppRoute
@Serializable data object UpdatesRoute : AppRoute
@Serializable data object SettingsRoute : AppRoute
@Serializable data object BackupRoute : AppRoute
@Serializable data object AboutRoute : AppRoute
@Serializable data object AiAssistantRoute : AppRoute
