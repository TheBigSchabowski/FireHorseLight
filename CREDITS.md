# Credits

FireHorseLight steht unter der [MIT-Lizenz](LICENSE). Es ist ein Ableger von
[FireHorse](https://github.com/TheBigSchabowski/FireHorse) (ebenfalls MIT) und übernimmt dessen Code:
Varianten, Puzzle, Spaced Repetition, Playlists, Archiv, Suche, Strg+B-Kürzel, Engine und lichess.
Ohne Zeichnen, Theorie-Lücken und Eröffnungsnotizen; dazu kommen Blind-Üben und Sprache.

## Was in FireHorseLight steckt

- **Schachfiguren:** „cburnett“-Satz von Colin M.L. Burnett, über [lichess](https://github.com/lichess-org/lila),
  Lizenz GPLv2+ (nicht MIT). Die PNGs in `shared/src/commonMain/composeResources/drawable` stammen aus
  FireHorse, das App-Icon zeigt den weißen Springer daraus.
- **Spaced Repetition:** FSRS-4.5 vom Projekt [open-spaced-repetition](https://github.com/open-spaced-repetition),
  in FireHorse in Kotlin umgesetzt.
- **Bibliotheken:** [Kotlin](https://kotlinlang.org) mit kotlinx.coroutines und kotlinx.serialization,
  [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (JetBrains), AndroidX,
  [Gradle](https://gradle.org) – alle Apache License 2.0.

## Dienste und Programme, mit denen FireHorseLight spricht

- **lichess-Eröffnungsdatenbank:** die [Opening Explorer API](https://lichess.org/api#tag/Opening-Explorer)
  von [lichess.org](https://lichess.org), abgefragt mit deinem eigenen API-Schlüssel.
  Die Partiedaten stammen aus der [lichess-Datenbank](https://database.lichess.org) (CC0).
- **Engines:** keine mitgeliefert; FireHorseLight spricht über UCI mit einer, die schon auf dem Computer ist –
  meist [Stockfish](https://stockfishchess.org) (GPLv3, eigenständiges Programm).
- **Sprachausgabe:** am Mac `say`, unter Linux [eSpeak NG](https://github.com/espeak-ng/espeak-ng)
  (GPLv3, eigenständiges Programm), auf Android die TextToSpeech-Engine des Geräts. Spracheingabe mit
  [voxtype](https://github.com/peteonrails/voxtype) (Whisper). Optional
  [Qwen-TTS](https://github.com/QwenLM) über `tools/qwen_tts_bridge.py`.
