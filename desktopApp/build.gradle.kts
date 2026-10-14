import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main on the desktop (the view model answers moves on it); Android has its own.
    // The same version Compose brings along.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

// jpackage lives in a full JDK; the JDK 21 that Gradle provisions may be a JBR without it.
// Only macOS has java_home; elsewhere (the Linux packages) Gradle's own JVM does.
fun systemJdk(): String {
    val javaHome = File("/usr/libexec/java_home")
    if (!javaHome.canExecute()) return System.getProperty("java.home")
    val process = ProcessBuilder(javaHome.path).start()
    return process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
}

compose.desktop {
    application {
        mainClass = "repertoire.desktop.MainKt"
        javaHome = systemJdk()
        // ./gradlew :desktopApp:run -PdataDir=/some/folder keeps test runs away from the real progress.
        (findProperty("dataDir") as String?)?.let { jvmArgs += "-Drepertoire.dataDir=$it" }
        (findProperty("windowSize") as String?)?.let { jvmArgs += "-Dwindow.size=$it" }

        nativeDistributions {
            // Each format can only be built on its own operating system: the DMG on the Mac, the DEB on Linux.
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "FireHorseLight"
            packageVersion = property("app.version") as String
            description = "FireHorse Light - Blindfold Chess with Voice"
            vendor = "TheBigSchabowski"
            macOS {
                bundleID = "de.privat.firehorselight"
                iconFile.set(project.file("icon.icns"))
                // Without it macOS does not let the app record: Space held records a move to say.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>FireHorse Light nimmt auf, solange du die Leertaste hältst, damit du Züge ansagen kannst.</string>
                    """.trimIndent()
                }
            }
            linux {
                iconFile.set(project.file("icon.png"))
                packageName = "firehorselight"
                debMaintainer = "TheBigSchabowski@users.noreply.github.com"
                appCategory = "games"
                menuGroup = "Game;BoardGame;"
            }
        }
    }
}

// jpackage works out the .deb dependencies from the libraries installed on the build machine. A server or
// container without the X11 libraries AWT loads would leave them out, and the app would not start on a desktop without them.
// espeak-ng says the moves on Linux (VoiceIo.desktop.kt).
// No spaces in the list: jpackage reads its arguments from a file that splits at them.
tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Deb) freeArgs.addAll("--linux-package-deps", "libxtst6,libxi6,libxrandr2,espeak-ng")
}

// The same icon.png is the window icon on Linux and Windows (see Main.kt).
tasks.processResources { from("icon.png") }

tasks.register<JavaExec>("renderScreens") {
    description = "Renders the main screens at phone and desktop size into build/screens."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "repertoire.desktop.RenderScreensKt"
    workingDir = projectDir
}
