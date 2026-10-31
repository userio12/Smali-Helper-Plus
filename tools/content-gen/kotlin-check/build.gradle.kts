plugins {
    kotlin("jvm") version "2.4.21"
    application
}

// Path of the apktool jar whose bundled smali/dexlib2 assembler the tool drives:
// -PAPKTOOL_JAR=... or the APKTOOL_JAR environment variable (default /usr/local/bin/apktool.jar).
val apktoolJar: String = providers.gradleProperty("APKTOOL_JAR")
    .orElse(providers.environmentVariable("APKTOOL_JAR"))
    .getOrElse("/usr/local/bin/apktool.jar")

dependencies {
    implementation(files(apktoolJar))
}

// Compile for Java 17 bytecode with whichever JDK runs Gradle (17 or newer); no toolchain download is needed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("MainKt")
    applicationName = "kotlin-check"
}
