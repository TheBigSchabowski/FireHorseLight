# Credits

FireHorseLight steht unter der [MIT-Lizenz](LICENSE). Es ist ein Ableger von
[FireHorse](https://github.com/TheBigSchabowski/FireHorse) (ebenfalls MIT) und übernimmt dessen Kern:
Schachregeln, Varianten-Speicher, JSON-Import, lichess-Anbindung und das Brett.

## Was in FireHorseLight steckt

- **Schachfiguren:** „cburnett“-Satz von Colin M.L. Burnett, über [lichess](https://github.com/lichess-org/lila),
  Lizenz GPLv2+ (nicht MIT). Die PNGs in `shared/src/commonMain/composeResources/drawable` stammen aus
  FireHorse, das App-Icon zeigt den weißen Springer daraus.
- **Bibliotheken:** [Kotlin](https://kotlinlang.org) mit kotlinx.coroutines und kotlinx.serialization,
  [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (JetBrains), AndroidX,
  [Gradle](https://gradle.org) – alle Apache License 2.0.

## Dienste und Programme, mit denen FireHorseLight spricht

- **lichess-Eröffnungsdatenbank:** die [Opening Explorer API](https://lichess.org/api#tag/Opening-Explorer)
  von [lichess.org](https://lichess.org), abgefragt mit dem API-Schlüssel aus deiner FireHorse-`library.json`.
  Die Partiedaten stammen aus der [lichess-Datenbank](https://database.lichess.org) (CC0).
- **Sprachausgabe:** am Mac `say`, unter Linux [eSpeak NG](https://github.com/espeak-ng/espeak-ng)
  (GPLv3, eigenständiges Programm), auf Android die TextToSpeech-Engine des Geräts. Optional
  [Qwen-TTS](https://github.com/QwenLM) über `tools/qwen_tts_bridge.py`.
