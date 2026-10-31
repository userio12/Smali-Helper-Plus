import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// --- Release signing ---------------------------------------------------------------------------------------------
// CI decodes the ANDROID_KEYSTORE_BASE64 secret into a temporary file and exports its path as ANDROID_KEYSTORE_PATH,
// together with ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD. Without them the release build
// is produced unsigned; debug builds never need secrets.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val releaseKeystorePath = env("ANDROID_KEYSTORE_PATH")
val requireSigning = env("SMALI_HELPER_REQUIRE_SIGNING") == "true"

// Build metadata. Values come from the CI environment so local builds stay reproducible and cacheable.
// SMALI_HELPER_GIT_SHA lets the release workflow stamp the exact released commit (GITHUB_* cannot be overridden).
val gitSha = (env("SMALI_HELPER_GIT_SHA") ?: env("GITHUB_SHA"))?.take(12) ?: "local"
val buildNumber = env("GITHUB_RUN_NUMBER") ?: "0"

// --- Versioning --------------------------------------------------------------------------------------------------
// versionName = <major>.<minor>.<commitCount - 1>, versionCode = <commitCount>, so the first commit is 1.0.0 (code 1)
// and every new commit on main produces a higher version automatically. major/minor live in version.properties.
// CI must check out the full history (fetch-depth: 0). Overrides: -PsmaliHelper.versionCode / .versionName or the
// SMALI_HELPER_VERSION_CODE / SMALI_HELPER_VERSION_NAME environment variables.
val versionProps = Properties().apply {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText
    load(StringReader(checkNotNull(text.orNull) { "version.properties is missing" }))
}
val versionMajor = versionProps.getProperty("major").trim().toInt()
val versionMinor = versionProps.getProperty("minor").trim().toInt()

fun versionOverride(property: String, envName: String): String? =
    providers.gradleProperty(property).orNull?.trim()?.takeIf { it.isNotEmpty() } ?: env(envName)

val gitCommitCount: Int? = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toInt()
}.getOrNull()?.takeIf { it > 0 }

val appVersionCode: Int = versionOverride("smaliHelper.versionCode", "SMALI_HELPER_VERSION_CODE")?.toInt()
    ?: gitCommitCount ?: 1
val appVersionName: String = versionOverride("smaliHelper.versionName", "SMALI_HELPER_VERSION_NAME")
    ?: "$versionMajor.$versionMinor.${appVersionCode - 1}"

tasks.register("printVersion") {
    group = "help"
    description = "Prints the versionName and versionCode used by this build."
    val line = "versionName=$appVersionName versionCode=$appVersionCode"
    doLast { println(line) }
}

android {
    namespace = "com.riyadm.smalihelperplus"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.riyadm.smalihelperplus"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "com.riyadm.smalihelperplus.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "CI_BUILD_NUMBER", "\"$buildNumber\"")
        // Default endpoint for documentation updates. Only validated JSON is ever downloaded from it (HTTPS only).
        buildConfigField(
            "String",
            "DEFAULT_CONTENT_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/riyadmondol2006/Smali-Helper-Plus/main/content/manifest.json\"",
        )
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = env("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = env("ANDROID_KEY_ALIAS")
                keyPassword = env("ANDROID_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        if (requireSigning) {
            val release = findByName("release")
            check(
                release != null && release.storeFile?.isFile == true &&
                    !release.storePassword.isNullOrEmpty() && !release.keyAlias.isNullOrEmpty() &&
                    !release.keyPassword.isNullOrEmpty(),
            ) { "SMALI_HELPER_REQUIRE_SIGNING is set but the release signing configuration is incomplete" }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        localeFilters += listOf("en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        warningsAsErrors = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
            "/DebugProbesKt.bin",
        )
    }

    sourceSets {
        getByName("test").assets.directories.add("$projectDir/schemas")
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
