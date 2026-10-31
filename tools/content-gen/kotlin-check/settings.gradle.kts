// Standalone build for the offline content-verification tool. It is deliberately NOT part of the app's
// settings.gradle.kts, so the app build and CI never depend on apktool.jar.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-check"
