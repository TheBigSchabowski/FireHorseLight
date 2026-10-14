plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release key lives outside the project (see tools/android-sign.sh). The same key must sign every
// version, otherwise Android refuses to install an update over the old one.
val keyDir = File(System.getProperty("user.home"), ".android")
val releaseKey = File(keyDir, "eroeffnungspuzzle-release.jks")
val releasePass = File(keyDir, "eroeffnungspuzzle-release.pass")

android {
    namespace = "repertoire.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.privat.firehorselight"
        minSdk = 26
        targetSdk = 36
        versionCode = (property("app.versionCode") as String).toInt()
        versionName = property("app.version") as String
    }

    signingConfigs {
        if (releaseKey.exists() && releasePass.exists()) {
            create("release") {
                val pass = releasePass.readText().trim()
                storeFile = releaseKey
                storePassword = pass
                keyAlias = "eroeffnungspuzzle"
                keyPassword = pass
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.10.1")
}
