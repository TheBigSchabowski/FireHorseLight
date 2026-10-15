# FireHorseLight

Eröffnungen blind lernen, mit Stimme – für macOS, Linux und Android. Die leichte Ausgabe von
[FireHorse](https://github.com/TheBigSchabowski/FireHorse): dieselben Varianten, Puzzle, Spaced Repetition,
Playlists, Archiv, Suche, Strg+B-Kürzel, Engine und lichess-Datenbank – ohne Zeichnen, Theorie-Lücken und
Eröffnungsnotizen, dafür mit Blind-Üben und Sprache.

## Blind und Stimme

- **Blind** (`Strg+B m` oder „Blind“ auf der Startseite) verdeckt das Brett beim Üben und im Editor. Die App spielt
  die Gegenseite deiner Variante und sagt ihre Züge auf Englisch an; du spielst nur deine Farbe, aus dem Kopf.
  Falsch: „No“ und der richtige Zug. Am Ende wird das Brett aufgedeckt. `Strg+B n` oder Doppelklick deckt zwischendurch auf.
- **Züge sagen:** Leertaste (oder den 🎙-Knopf, am Handy nur den) halten, Zug sagen, loslassen – beim Üben und im Editor,
  auch ohne Blind. Kurz tippen bleibt „Gut“ / „Weiter“. `🔊 Ansage an/aus` schaltet die Ansagen ab; dann steht der
  gespielte Zug auf dem verdeckten Brett.
- **Die App lernt deine Stimme:** jeder erkannte Zug wird als Aufnahme gemerkt und beim nächsten Mal am Klang erkannt
  (MFCC + DTW, nur unter den gerade möglichen Zügen) – egal ob „knight f3“ oder „Springer f3“. Was sie nicht kennt,
  schreibt am Computer [voxtype](https://github.com/peteonrails/voxtype) (Whisper) auf, falls installiert; sonst
  verbindest du die Aufnahme einfach mit dem Zug, den du dann ziehst oder tippst. „Falsch erkannt“ löst eine falsche
  Zuordnung.
- **Graph** (`Strg+B g`): links die Aufnahmen, rechts die Züge, dazwischen Linien; ziehen (am Handy: antippen) verbindet neu,
  ▶ spielt ab, ✕ löscht.
- **🎙 Training** (Startseite, freiwillig): die App sagt die Züge deiner Varianten und die Kommandos vor, du sagst sie nach.
- **Kommandos:** `board`, `repeat`, `hint`, `next`, `restart`, `undo`.
- **Varianten blind anlegen:** im Editor mit Blind sagst du die Züge, die App sagt sie zurück. Abspielen sagt die Züge an.
- **Stimmprobe aufnehmen** (Einstellungen → „Blind und Stimme“): `Stimme/referenz.wav` + Text, für Qwen3-TTS.

## Download

Unter [Releases](../../releases/latest):

| Plattform | Datei | Hinweis |
|---|---|---|
| macOS | `FireHorseLight_<version>_macos-arm64.dmg` | Apple Silicon |
| Linux | `FireHorseLight_<version>_linux-amd64.deb` | Ubuntu 24.04+, Debian 13+, Mint 22+ (x86-64) |
| Linux | `FireHorseLight_<version>_linux-amd64.tar.gz` | jede andere Distribution, ohne Installation |
| Android | `FireHorseLight_<version>_android.apk` | Android 8.0+ |

**macOS:** DMG öffnen, die App in „Programme“ ziehen; nicht mit einem Apple-Zertifikat signiert, beim ersten Start
daher Rechtsklick → „Öffnen“. Beim ersten Sprechen das Mikrofon erlauben. Optional `brew install --cask voxtype`,
dann wird auch aufgeschrieben, was die App noch nicht am Klang kennt.

**Linux:** `sudo apt install ./FireHorseLight_<version>_linux-amd64.deb` installiert die App samt Java und Stimme
(`espeak-ng`). Ältere Systeme (Ubuntu 22.04, Debian 12) nehmen das tar.gz: entpacken, `FireHorseLight/bin/FireHorseLight`
starten; für die Stimme `espeak-ng` installieren. voxtype ist optional (wie am Mac).

**Android:** APK installieren („Unbekannte Apps installieren“ erlauben). Zum Sprechen den 🎙-Knopf halten (das Mikrofon wird
beim ersten Mal erfragt); erkannt wird am Klang deiner Aufnahmen. Tippen und das Mikrofon der Tastatur gehen auch.

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
