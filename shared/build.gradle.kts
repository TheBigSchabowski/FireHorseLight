plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    android {
        namespace = "repertoire.shared"
        compileSdk = 36
        minSdk = 26
        androidResources { enable = true }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Desktop and Android both run on the JVM: file and date handling lives here once.
        val jvmCommonMain by creating { dependsOn(commonMain.get()) }
        named("desktopMain") { dependsOn(jvmCommonMain) }
        named("androidMain") { dependsOn(jvmCommonMain) }

        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            api("org.jetbrains.compose.foundation:foundation:1.11.1")
            api("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.components:components-resources:1.11.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // runTest for the engine session; the same version Compose brings along.
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        // Skia for the tests that feed taps into an off-screen Compose scene.
        named("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = "repertoire.shared.resources"
}
