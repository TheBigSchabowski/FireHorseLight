# FireHorseLight

Blindschach mit Stimme, für macOS, Linux und Android. Ein Ableger von
[FireHorse](https://github.com/TheBigSchabowski/FireHorse).

Du sagst deinen Zug auf Englisch („e4“, „knight f3“, „bishop c4“, „c takes d4“, „castle kingside“),
die App antwortet mit einem Zug aus deinen FireHorse-Varianten oder aus der lichess-Datenbank und
spricht ihn laut aus. Das Brett bleibt verdeckt, ein Doppeltipp blendet es ein und wieder aus.

Die Spracheingabe kommt von einem Diktierprogramm deiner Wahl (z. B. Voxtype/Whisper, die Diktierfunktion
von macOS oder die Android-Tastatur), das den Text ins Eingabefeld schreibt. Typische Hörfehler wie
„night c3“ oder „bishop sea for“ werden verstanden.

## Download

Unter [Releases](../../releases/latest):

| Plattform | Datei | Hinweis |
|---|---|---|
| macOS | `FireHorseLight_<version>_macos-arm64.dmg` | Apple Silicon |
| Linux | `FireHorseLight_<version>_linux-amd64.deb` | Ubuntu 24.04+, Debian 13+, Mint 22+ (x86-64) |
| Linux | `FireHorseLight_<version>_linux-amd64.tar.gz` | jede andere Distribution, ohne Installation |
| Android | `FireHorseLight_<version>_android.apk` | Android 8.0+ |

**macOS:** DMG öffnen und die App in „Programme“ ziehen. Die App ist nicht mit einem
Apple-Entwicklerzertifikat signiert; beim ersten Start daher Rechtsklick → „Öffnen“.

**Linux:** `sudo apt install ./FireHorseLight_<version>_linux-amd64.deb` installiert die App samt Java
und der Stimme (`espeak-ng`) und legt „FireHorseLight“ ins Anwendungsmenü (unter „Spiele“); gestartet wird
auch mit `/opt/firehorselight/bin/FireHorseLight`. Ältere Systeme (Ubuntu 22.04, Debian 12) nehmen das tar.gz:
entpacken, `FireHorseLight/bin/FireHorseLight` starten. Damit die App spricht, muss dann `espeak-ng`
installiert sein. Gebraucht wird eine Desktop-Umgebung mit X11 oder Wayland (XWayland) und OpenGL.

**Android:** APK herunterladen und installieren („Unbekannte Apps installieren“ erlauben).

## Varianten

FireHorseLight liest denselben Datenordner wie FireHorse: am Mac `~/Documents/EroeffnungsPuzzle`,
unter Linux den Dokumente-Ordner (`~/Dokumente/EroeffnungsPuzzle` bzw. `~/Documents/...`, maßgeblich ist
`XDG_DOCUMENTS_DIR`). Deine Varianten sind also sofort da. Auf Android, oder um eine andere Sammlung zu laden,
gibt es „FireHorse JSON laden“ für einen Export aus FireHorse.

Für „Lichess Masters“ und „Lichess Players“ braucht es den lichess-API-Schlüssel, den du in FireHorse
eingetragen hast. Ohne ihn antwortet die App aus deinen eigenen Varianten.

## Eigene Stimme (optional)

Startest du die App aus dem Projektordner (`./gradlew :desktopApp:run`), spricht sie über
`tools/qwen_tts_bridge.py`. Ist `QWEN_TTS_URL` gesetzt und liegt eine Stimmprobe unter `~/my_voice.wav`
(oder `FIREHORSE_VOICE_SAMPLE`), klingt das mit deiner eigenen Stimme über Qwen-TTS; sonst mit der Systemstimme.
Die installierte App nutzt immer die Systemstimme.

## Bauen

```
./gradlew :shared:desktopTest        # Tests
./gradlew :desktopApp:run            # Desktop-App starten
./tools/build-release.sh             # DMG + signiertes APK nach dist/releases (am Mac)
./tools/build-linux.sh               # DEB + tar.gz nach dist/linux (unter Linux, braucht fakeroot)
```

Voraussetzungen: ein JDK (Gradle besorgt sich JDK 21 selbst; für das DEB ein volles JDK mit `jpackage`,
unter Ubuntu `openjdk-21-jdk`), für Android das Android SDK unter `~/Library/Android/sdk` oder in
`local.properties`. `-PdataDir=/ordner` startet die Desktop-App mit einem anderen Datenordner.

Das APK wird mit dem Schlüssel aus `~/.android/` signiert (siehe `tools/android-sign.sh`). Ohne denselben
Schlüssel lässt sich ein Update nicht über die installierte App spielen.

## Lizenz

MIT, siehe [LICENSE](LICENSE). Die Schachfiguren stehen unter GPLv2+, Details in [CREDITS.md](CREDITS.md).
