package com.riyadm.smalihelperplus.feature.ai

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.data.ai.AiMessageParser
import com.riyadm.smalihelperplus.data.ai.AiQuickAction
import com.riyadm.smalihelperplus.data.ai.AiSegment
import com.riyadm.smalihelperplus.data.ai.ChatRole
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.SettingsRoute

@Composable
fun AiAssistantScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: AiAssistantViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val configured = state.settings.isConfigured

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(configured) {
        if (configured) viewModel.consumeHandoff()
    }

    DetailScaffold(
        title = "AI assistant",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (configured && state.messages.isNotEmpty()) {
                IconButton(onClick = viewModel::clearChat, modifier = Modifier.testTag("ai_clear")) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear chat")
                }
            }
            IconButton(onClick = { onNavigate(SettingsRoute) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "AI settings")
            }
        },
    ) { padding ->
        when {
            !state.settingsLoaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !configured -> NotConfigured(state.settings.enabled, padding, onSetup = { onNavigate(SettingsRoute) })
            else -> Chat(
                state = state,
                draft = draft,
                padding = padding,
                viewModel = viewModel,
            )
        }
    }

    state.consent?.let { consent ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConsent,
            icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
            title = { Text("Send to ${consent.host}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (consent.containsCode) {
                            "Your message includes code (${consent.codeLines} line${if (consent.codeLines == 1) "" else "s"} attached or pasted). " +
                                "It will be sent over HTTPS to ${consent.host}, together with the earlier messages of this chat."
                        } else {
                            "Your message and the earlier messages of this chat will be sent over HTTPS to ${consent.host}."
                        },
                    )
                    Text(
                        "That server is operated by a third party you configured. Only send code you are allowed to share. " +
                            "Nothing is sent unless you confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmConsent, modifier = Modifier.testTag("ai_consent_send")) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConsent, modifier = Modifier.testTag("ai_consent_cancel")) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NotConfigured(enabled: Boolean, padding: PaddingValues, onSetup: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Outlined.AutoAwesome,
            title = if (enabled) "Finish the AI setup" else "AI assistant is optional and off",
            message = "Connect any OpenAI-compatible service (HTTPS endpoint, model name and your own API key) in Settings " +
                "to ask questions about Smali code. Nothing is sent anywhere until you set this up and confirm a message.",
            actionLabel = "Set up in Settings",
            onAction = onSetup,
            modifier = Modifier.testTag("ai_setup"),
        )
        Text(
            "Everything else in Smali Helper Plus works fully offline - opcodes, lessons, the Playground and all tools " +
                "need no account, no key and no network.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun Chat(state: AiChatState, draft: String, padding: PaddingValues, viewModel: AiAssistantViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.loading) {
        val count = state.messages.size + (if (state.loading) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        Text(
            "AI answers can be wrong - check them against the opcode reference. Messages go to ${state.settings.host ?: "your configured server"} only when you send them.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("ai_messages"),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Text(
                        "Ask about an opcode, paste or attach code, or pick a quick action below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message, canRetry = message.isError && !state.loading, onRetry = viewModel::retry)
            }
            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Waiting for the answer" }, strokeWidth = 2.dp)
                        Text("Thinking...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AiQuickAction.entries.toList(), key = { it.name }) { action ->
                        FilterChip(
                            selected = state.selectedAction == action,
                            onClick = { viewModel.selectAction(action) },
                            label = { Text(action.label) },
                            modifier = Modifier.testTag("ai_action_${action.name.lowercase()}"),
                        )
                    }
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val attached = state.attachedCode
                    if (attached != null) {
                        AssistChip(
                            onClick = viewModel::removeAttachment,
                            label = { Text("Code attached (${attached.lines().size} lines)") },
                            leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Remove attached code", modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("ai_attachment"),
                        )
                    } else {
                        AssistChip(
                            onClick = viewModel::attachPlaygroundCode,
                            label = { Text("Attach Playground code") },
                            leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("ai_attach_playground"),
                        )
                        AssistChip(
                            onClick = {
                                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                viewModel.onClipboardText(clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString())
                            },
                            label = { Text("Paste code") },
                            leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("ai_paste"),
                        )
                    }
                }
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = viewModel::onDraftChange,
                        placeholder = {
                            Text(state.selectedAction?.let { "${it.label}: add a question (optional)" } ?: "Ask about Smali or Dalvik")
                        },
                        maxLines = 5,
                        modifier = Modifier.weight(1f).testTag("ai_input"),
                    )
                    if (state.loading) {
                        IconButton(onClick = viewModel::cancel, modifier = Modifier.testTag("ai_cancel")) {
                            Icon(Icons.Outlined.Stop, contentDescription = "Cancel request", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(
                            onClick = viewModel::send,
                            enabled = draft.isNotBlank() || state.attachedCode != null,
                            modifier = Modifier.testTag("ai_send"),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send message")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AiUiMessage, canRetry: Boolean, onRetry: () -> Unit) {
    val isUser = message.role == ChatRole.USER
    val container = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(container)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isUser) {
                if (message.text.isNotBlank()) Text(message.text, color = content, style = MaterialTheme.typography.bodyMedium)
                message.code?.let { code ->
                    val lines = code.lines()
                    CodeBlock(
                        code = lines.take(PREVIEW_LINES).joinToString("\n") + if (lines.size > PREVIEW_LINES) "\n... (${lines.size - PREVIEW_LINES} more lines sent)" else "",
                        label = "Attached code",
                        showCopy = false,
                    )
                }
            } else if (message.isError) {
                Text(message.text, color = content, style = MaterialTheme.typography.bodyMedium)
                if (canRetry) {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("ai_retry")) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Try again")
                    }
                }
            } else {
                AiMessageContent(message.text, content)
            }
        }
    }
}

@Composable
private fun AiMessageContent(text: String, textColor: androidx.compose.ui.graphics.Color) {
    val segments = remember(text) { AiMessageParser.parse(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is AiSegment.Text -> SelectionContainer {
                    Text(segment.text, color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                is AiSegment.Code -> CodeBlock(
                    code = segment.code,
                    language = if (segment.language.isBlank() || segment.language == "smali") "smali" else segment.language,
                    label = segment.language.ifBlank { "smali" },
                )
            }
        }
    }
}

private const val PREVIEW_LINES = 12
