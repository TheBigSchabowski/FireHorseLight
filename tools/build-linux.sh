#!/bin/bash
# Builds the Linux packages, a .deb and a portable .tar.gz, and puts both, renamed for a
# GitHub release, into dist/linux together with SHA256 checksums. jpackage builds for the
# system it runs on, so this only works on Linux; the .deb needs dpkg-deb and fakeroot.
#
#   ./tools/build-linux.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[ "$(uname -s)" = Linux ] || { echo "Dieses Skript baut nur unter Linux." >&2; exit 1; }

VERSION="$(grep '^app.version=' gradle.properties | cut -d= -f2)"
ARCH="$(dpkg --print-architecture 2>/dev/null || uname -m)"
OUT_DIR="dist/linux"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "==> Tests, DEB und portables Paket..."
./gradlew :shared:desktopTest :desktopApp:packageDeb :desktopApp:createDistributable

DEB="$(ls desktopApp/build/compose/binaries/main/deb/*.deb 2>/dev/null | head -n 1)"
APP_DIR="desktopApp/build/compose/binaries/main/app"
for artifact in "$DEB" "$APP_DIR/FireHorseLight/bin/FireHorseLight"; do
  [ -s "$artifact" ] || { echo "Build-Artefakt fehlt: $artifact" >&2; exit 1; }
done

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*
cp "$DEB" "$OUT_DIR/FireHorseLight_${VERSION}_linux-${ARCH}.deb"
tar -C "$APP_DIR" -czf "$OUT_DIR/FireHorseLight_${VERSION}_linux-${ARCH}.tar.gz" FireHorseLight
(cd "$OUT_DIR" && sha256sum *.deb *.tar.gz > SHA256SUMS.txt)
echo "==> Fertig:"
ls -la "$OUT_DIR"
