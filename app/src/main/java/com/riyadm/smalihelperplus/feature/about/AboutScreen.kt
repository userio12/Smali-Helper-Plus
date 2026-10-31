package com.riyadm.smalihelperplus.feature.about

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.BuildConfig
import com.riyadm.smalihelperplus.R
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.navigation.AppRoute
import kotlinx.coroutines.launch

private data class Credit(val name: String, val license: String, val url: String, val note: String)

private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

private val LIBRARIES = listOf(
    Credit("AndroidX (Core, Activity, Lifecycle, Navigation, Room, DataStore, WorkManager, Hilt)", "Apache License 2.0", "https://developer.android.com/jetpack/androidx", "Google / Android Open Source Project"),
    Credit("Jetpack Compose and Material 3", "Apache License 2.0", "https://developer.android.com/jetpack/compose", "Google / Android Open Source Project"),
    Credit("Dagger and Hilt", "Apache License 2.0", "https://dagger.dev", "Google"),
    Credit("OkHttp and Okio", "Apache License 2.0", "https://square.github.io/okhttp/", "Square, Inc."),
    Credit("Kotlin, kotlinx.coroutines and kotlinx.serialization", "Apache License 2.0", "https://kotlinlang.org", "JetBrains s.r.o. and contributors"),
    Credit("Material Symbols and Icons", "Apache License 2.0", "https://fonts.google.com/icons", "Google"),
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun AboutScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openLink: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("No browser found to open $url") }
        }
    }

    DetailScaffold(title = "About", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("about_list"),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header() }
            item {
                InfoCard {
                    InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "about_version")
                    InfoRow("Build", BuildConfig.GIT_SHA, "about_build")
                    InfoRow("Package", BuildConfig.APPLICATION_ID, "about_package")
                }
            }
            item {
                NoteCard(
                    icon = Icons.Outlined.CloudOff,
                    title = "Offline by design",
                    text = "Opcode reference, lessons, quizzes, the Playground and every tool work without an account, a key or " +
                        "a network connection. The network is only used if you check for documentation updates or turn on the optional AI assistant.",
                )
            }
            item {
                NoteCard(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy",
                    text = "No analytics, no tracking, no ads and no crash reporting. Your bookmarks, snippets, progress and the code " +
                        "you write stay on this device. The only network requests are (1) downloading documentation updates from " +
                        "the manifest address shown in Documentation updates, and (2) requests to the AI service you configure yourself, " +
                        "sent only when you confirm them. Your AI key is stored encrypted with the Android Keystore and is excluded from backups.",
                )
            }
            item {
                NoteCard(
                    icon = Icons.Outlined.Security,
                    title = "Responsible use",
                    text = "Smali Helper Plus is an educational reference. Only analyse, modify or repackage apps that you own or " +
                        "that you are explicitly authorised to inspect, and respect licences, terms of service and the law.",
                )
            }
            item { SectionTitle("Credits") }
            item {
                InfoCard {
                    CreditBlock(
                        title = "Original inspiration: Smali Helper by Zylern",
                        body = "This app was inspired by the idea of the Smali Helper project. That repository has no license file, " +
                            "so no code, text, data or assets from it were used. Everything in this app was written from scratch; " +
                            "only the idea of a handy Smali reference was borrowed.",
                        linkLabel = "github.com/Zylern/Smali-Helper",
                        onLink = { openLink("https://github.com/Zylern/Smali-Helper") },
                    )
                    HorizontalDivider()
                    CreditBlock(
                        title = "Dalvik bytecode documentation - Android Open Source Project",
                        body = "Facts about opcodes and instruction formats follow the Dalvik executable documentation of the Android Open Source Project. " +
                            "Per source.android.com/license, that documentation (including code shown in it) is licensed under the Apache License 2.0, " +
                            "and other content of that site under Creative Commons Attribution 4.0. All explanations in this app are written independently. " +
                            "Android is a trademark of Google LLC; this app is not affiliated with or endorsed by Google.",
                        linkLabel = "source.android.com/license",
                        onLink = { openLink("https://source.android.com/license") },
                    )
                    HorizontalDivider()
                    CreditBlock(
                        title = "smali / baksmali",
                        body = "The assembler syntax taught here is that of smali and baksmali by Ben Gruver (JesusFreke) and Google, " +
                            "released under the BSD 3-Clause license. No smali or baksmali code is included in this app.",
                        linkLabel = "github.com/google/smali",
                        onLink = { openLink("https://github.com/google/smali") },
                    )
                }
            }
            item { SectionTitle("Open-source libraries") }
            item {
                InfoCard {
                    LIBRARIES.forEachIndexed { index, credit ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { openLink(credit.url) }.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(credit.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${credit.license} - ${credit.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                    LinkRow("Read the Apache License 2.0", "about_license_link") { openLink(APACHE_URL) }
                }
            }
            item {
                Text(
                    "Thank you for learning with Smali Helper Plus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Smali Helper Plus app icon",
                modifier = Modifier.size(96.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Smali Helper Plus", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Learn and look up Dalvik bytecode, offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun NoteCard(icon: ImageVector, title: String, text: String) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun CreditBlock(title: String, body: String, linkLabel: String, onLink: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        LinkRow(linkLabel, "about_link_${linkLabel.substringBefore('/').replace('.', '_')}", onLink)
    }
}

@Composable
private fun LinkRow(label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Opens in your browser", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}
