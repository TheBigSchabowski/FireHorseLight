# FireHorseLight

Eröffnungen blind lernen, mit Stimme – für macOS, Linux und Android. Die leichte Ausgabe von
[FireHorse](https://github.com/TheBigSchabowski/FireHorse): dieselben Varianten, Puzzle, Spaced Repetition,
Playlists, Archiv, Suche, Strg+B-Kürzel, Engine und lichess-Datenbank – ohne Zeichnen, Theorie-Lücken und
Eröffnungsnotizen, dafür mit Blind-Üben und Sprache.

## Blind und Stimme

- **Blind** (`Strg+B m` oder „Blind“ auf der Startseite) verdeckt das Brett beim Üben und im Editor. Die App spielt
  die Gegenseite deiner Variante und sagt ihre Züge auf Englisch an; du spielst nur deine Farbe, aus dem Kopf.
  Falsch: „No“ und der richtige Zug. Am Ende wird das Brett aufgedeckt. `Strg+B n` oder Doppelklick deckt zwischendurch auf.
- **Züge sagen:** Leertaste halten, „knight f3“ sagen, loslassen – erkannt mit [voxtype](https://github.com/peteonrails/voxtype)
  (Whisper), auch ohne Blind. Kurz tippen bleibt „Gut“ / „Weiter“. Das Feld unter dem verdeckten Brett nimmt Züge auch
  getippt oder diktiert (voxtypes eigene Taste, am Handy das Mikrofon der Tastatur).
- **Kommandos:** `board`, `repeat`, `hint`, `next`, `restart`, `undo`.
- **Varianten blind anlegen:** im Editor mit Blind sagst du die Züge, die App sagt sie zurück. Abspielen sagt die Züge an.
- **Spracherkennung testen** und **Stimmprobe aufnehmen** unter Einstellungen → „Blind und Stimme“. Der Test speichert
  jede Aufnahme mit dem, was voxtype daraus gemacht hat (`Spracherkennung/` im Datenordner); die Stimmprobe
  (`Stimme/referenz.wav` + Text) ist für Qwen3-TTS, damit die Ansagen in deiner eigenen Stimme kommen.

## Download

Unter [Releases](../../releases/latest):

| Plattform | Datei | Hinweis |
|---|---|---|
| macOS | `FireHorseLight_<version>_macos-arm64.dmg` | Apple Silicon |
| Linux | `FireHorseLight_<version>_linux-amd64.deb` | Ubuntu 24.04+, Debian 13+, Mint 22+ (x86-64) |
| Linux | `FireHorseLight_<version>_linux-amd64.tar.gz` | jede andere Distribution, ohne Installation |
| Android | `FireHorseLight_<version>_android.apk` | Android 8.0+ |

**macOS:** DMG öffnen, die App in „Programme“ ziehen; nicht mit einem Apple-Zertifikat signiert, beim ersten Start
daher Rechtsklick → „Öffnen“. Für die Leertaste: `brew install --cask voxtype` und beim ersten Sprechen das Mikrofon erlauben.

**Linux:** `sudo apt install ./FireHorseLight_<version>_linux-amd64.deb` installiert die App samt Java und Stimme
(`espeak-ng`). Ältere Systeme (Ubuntu 22.04, Debian 12) nehmen das tar.gz: entpacken, `FireHorseLight/bin/FireHorseLight`
starten; für die Stimme `espeak-ng` installieren. Für die Leertaste voxtype installieren.

**Android:** APK installieren („Unbekannte Apps installieren“ erlauben). Züge sagst du über das Mikrofon der Tastatur.

## Daten

Ein eigener Ordner, getrennt von FireHorse: am Mac `~/Documents/FireHorseLight`, unter Linux im Dokumente-Ordner
(`XDG_DOCUMENTS_DIR`), auf Android im App-Speicher. Varianten aus FireHorse holst du mit FireHorse → „Alle exportieren“
und hier Einstellungen → Daten → „Importieren“. Theorie und Zeichnungen darin bleiben erhalten, werden hier nur nicht gezeigt.

## Bauen

```
./gradlew :shared:desktopTest        # Tests
./gradlew :desktopApp:run            # Desktop-App starten (-PdataDir=/ordner für einen Testordner)
./tools/build-release.sh             # DMG + signiertes APK nach dist/releases (am Mac)
./tools/build-linux.sh               # DEB + tar.gz nach dist/linux (unter Linux, braucht fakeroot)
```

Das APK wird mit dem Schlüssel aus `~/.android/` signiert (siehe `tools/android-sign.sh`).

## Lizenz

MIT, siehe [LICENSE](LICENSE). Die Schachfiguren stehen unter GPLv2+, Details in [CREDITS.md](CREDITS.md).
