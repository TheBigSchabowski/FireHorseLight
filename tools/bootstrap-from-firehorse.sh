#!/usr/bin/env bash
set -euo pipefail

SRC="../FireHorse"
if [ ! -d "$SRC" ]; then
  echo "Fehler: ../FireHorse wurde nicht gefunden!"
  exit 1
fi

mkdir -p tools
mkdir -p shared/src/commonMain/kotlin/repertoire/core
mkdir -p shared/src/commonMain/kotlin/repertoire/ui
mkdir -p shared/src/jvmCommonMain/kotlin/repertoire/core
mkdir -p shared/src/desktopMain/kotlin/repertoire/core
mkdir -p shared/src/commonMain/composeResources/drawable
mkdir -p androidApp/src/main/res
mkdir -p desktopApp

# 1. Core-Logik inkl. JSON-Exchange, Store, FSRS & Lichess-Explorer 1:1 übernehmen
for f in Chess.kt Cloze.kt Drawing.kt Exchange.kt Explorer.kt Fsrs.kt Pgn.kt Platform.kt Search.kt Store.kt Uci.kt Variants.kt; do
  cp "$SRC/shared/src/commonMain/kotlin/repertoire/core/$f" "shared/src/commonMain/kotlin/repertoire/core/$f"
done

# 2. JVM & Desktop Basis-Dateien (lädt automatisch ~/Documents/EroeffnungsPuzzle/library.json!)
for f in Http.jvm.kt Platform.jvm.kt Uci.jvm.kt; do
  cp "$SRC/shared/src/jvmCommonMain/kotlin/repertoire/core/$f" "shared/src/jvmCommonMain/kotlin/repertoire/core/$f"
done
cp "$SRC/shared/src/desktopMain/kotlin/repertoire/core/UserDirs.kt" "shared/src/desktopMain/kotlin/repertoire/core/UserDirs.kt"

# 3. Das echte Schachbrett + Kreidetafel aus FireHorse übernehmen
cp "$SRC/shared/src/commonMain/kotlin/repertoire/ui/Board.kt" "shared/src/commonMain/kotlin/repertoire/ui/Board.kt"
cp "$SRC/shared/src/commonMain/kotlin/repertoire/ui/Chalkboard.kt" "shared/src/commonMain/kotlin/repertoire/ui/Chalkboard.kt"

# 4. Figuren-Assets, Icons & Gradle-Wrapper kopieren
cp -r "$SRC/gradle" .
cp "$SRC/gradlew" .
chmod +x gradlew
cp "$SRC/gradle.properties" .
cp "$SRC/build.gradle.kts" .
cp "$SRC/settings.gradle.kts" .
cp "$SRC/shared/build.gradle.kts" "shared/build.gradle.kts"
cp "$SRC/desktopApp/build.gradle.kts" "desktopApp/build.gradle.kts"
cp "$SRC/androidApp/build.gradle.kts" "androidApp/build.gradle.kts"
cp "$SRC/shared/src/commonMain/composeResources/drawable/"*.png "shared/src/commonMain/composeResources/drawable/"
cp -r "$SRC/androidApp/src/main/res/"* "androidApp/src/main/res/"
cp "$SRC/androidApp/src/main/AndroidManifest.xml" "androidApp/src/main/AndroidManifest.xml"
cp "$SRC/desktopApp/icon.icns" "desktopApp/" 2>/dev/null || true
cp "$SRC/desktopApp/icon.png" "desktopApp/" 2>/dev/null || true

# Rename project in settings.gradle.kts to FireHorseLight
sed -i '' 's/rootProject.name = "FireHorse"/rootProject.name = "FireHorseLight"/' settings.gradle.kts || sed -i 's/rootProject.name = "FireHorse"/rootProject.name = "FireHorseLight"/' settings.gradle.kts

echo "Bootstrap fertig: Alle FireHorse Core- & JSON-Module sind bereit!"
