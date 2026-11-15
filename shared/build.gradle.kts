import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=ru.allisighs.funpaytools.shared", "-Xadd-light-debug=enable", "-Xbinary=sourceInfoType=libbacktrace")
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
            "-opt-in=kotlinx.cinterop.BetaInteropApi",
            "-nowarn"
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("com.squareup.okio:okio:3.18.2")
            implementation("co.touchlab:stately-concurrent-collections:2.1.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.2")
            implementation("org.jetbrains.skiko:skiko:0.150.2")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.6.1")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-java:3.6.1")
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "ru.allisighs.funpaytools.DesktopMainKt"
    }
}

tasks.register<JavaExec>("snapshot") {
    dependsOn("jvmMainClasses")
    val jvmTarget = kotlin.targets.getByName("jvm")
    val comp = jvmTarget.compilations.getByName("main")
    classpath = files(comp.output.allOutputs, comp.runtimeDependencyFiles)
    mainClass.set("ru.allisighs.funpaytools.SnapshotKt")
    args = (project.findProperty("snapArgs") as String? ?: "root").split(",")
    jvmArgs("-Djava.awt.headless=true", "-Dsnap.dir=/tmp/snaps")
}
