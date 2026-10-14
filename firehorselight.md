# FireHorseLight – Complete Multiplatform Specification (macOS, Linux, Android)
**Target Agent:** Gemini 3.8 Flash (Antigravity CLI)
**Instructions:** Create the exact directory structure and write all files below completely, preserving every single comment and logic branch. Do NOT truncate or summarize any code.

## 1. Anweisungen für Gemini 3.8 Flash (STRIKT BEFOLGEN!)
1. Du befindest dich im Projektordner `FireHorseLight/`. Das Originalprojekt liegt unter `../FireHorse/`[cite: 1].
2. **Schritt 1:** Lege alle Build-Dateien (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `shared/build.gradle.kts`, `desktopApp/build.gradle.kts`, `androidApp/build.gradle.kts`) exakt wie unten angegeben an[cite: 1].
3. **Schritt 2:** Lege `tools/bootstrap-from-firehorse.sh` an, mache es ausführbar (`chmod +x tools/bootstrap-from-firehorse.sh`) und führe es aus. Es kopiert den bewährten Kern (`Chess.kt`, `Variants.kt`, `Exchange.kt`, `Explorer.kt`, `Store.kt`, `Fsrs.kt`, `Cloze.kt`, `Drawing.kt`, `Board.kt` sowie alle Figuren-PNGs) aus `../FireHorse` herüber[cite: 1].
4. **Schritt 3:** Erstelle alle neuen `FireHorseLight`-Dateien (`VoiceMove.kt`, `LineResponder.kt`, `VoiceIo.kt`, `BlindfoldViewModel.kt`, `BlindfoldScreen.kt`, `Main.kt`, `MainActivity.kt`, `VoiceMoveTest.kt` und `tools/qwen_tts_bridge.py`) exakt mit dem untenstehenden Code.
5. **Schritt 4:** Führe `./gradlew :shared:desktopTest` aus und stelle sicher, dass alle Tests für den englischen Sprach-Parser und den FireHorse-JSON-Import grün durchlaufen[cite: 1].

---

## 2. Projektstruktur (`FireHorseLight/`)

```text
FireHorseLight/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── tools/
│   ├── bootstrap-from-firehorse.sh   # Kopiert Core, JSON-Store, Board & Icons aus ../FireHorse
│   └── qwen_tts_bridge.py            # Lokale Qwen-TTS Bridge (macOS / Linux) mit eigenem Voice-Sample
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── composeResources/drawable/ # (wird per Skript aus ../FireHorse kopiert)
│       │   └── kotlin/repertoire/
│       │       ├── core/                  # (aus ../FireHorse: Chess, Variants, Exchange, Store, Explorer)
│       │       │   ├── VoiceMove.kt       # NEU: Englischer Phonetik-Parser ("bishop c4", "knight c3")
│       │       │   ├── LineResponder.kt   # NEU: Antwort-Logik (FireHorse-JSON-Varianten + Lichess API)
│       │       │   └── VoiceIo.kt         # NEU: Multiplatform TTS Interface (Desktop & Android)
│       │       └── ui/
│       │           ├── Board.kt           # (aus ../FireHorse kopiert)
│       │           ├── BlindfoldViewModel.kt # NEU: Voxtype-Flow, Double-Tap Peek, JSON-Loader
│       │           └── BlindfoldScreen.kt    # NEU: Blindfold UI für Desktop & Android
│       ├── jvmCommonMain/kotlin/repertoire/core/
│       │   └── (Http.jvm.kt, Platform.jvm.kt aus ../FireHorse)
│       ├── desktopMain/kotlin/repertoire/core/
│       │   ├── UserDirs.kt                # (aus ../FireHorse kopiert)
│       │   └── VoiceIo.desktop.kt         # NEU: Ruft Qwen-TTS (oder System-TTS) auf macOS & Linux auf
│       ├── androidMain/kotlin/repertoire/core/
│       │   └── VoiceIo.android.kt         # NEU: Nutzt Android TextToSpeech (Locale.US)
│       └── commonTest/kotlin/repertoire/
│           └── VoiceMoveTest.kt           # NEU: Tests für "bishop c4", "knight c3" & FireHorse-JSON
├── desktopApp/
│   ├── build.gradle.kts
│   └── src/main/kotlin/repertoire/desktop/
│       └── Main.kt                        # NEU: Desktop-Fenster (macOS & Linux)
└── androidApp/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── kotlin/repertoire/android/
            └── MainActivity.kt            # NEU: Android-App mit Voxtype/Diktat & Double-Tap
```

---

## 3. Bootstrap-Skript & Qwen-TTS Python-Bridge

### Datei: `tools/bootstrap-from-firehorse.sh`
Wir behalten das Package `repertoire.core` und `repertoire.ui` exakt wie in `FireHorse` bei[cite: 1] – dadurch funktionieren alle deine `library.json`-Dateien, der `FilePicker`-Import (`Exchange.kt`) und das `ChessBoard` (`Board.kt`) sofort ohne Reibungsverluste[cite: 1]!

```bash
#!/usr/bin/env bash
set -euo pipefail

SRC="../FireHorse"
if [ ! -d "$SRC" ]; then
  echo "Fehler: ../FireHorse wurde nicht gefunden!"
  exit 1
fi

mkdir -p shared/src/commonMain/kotlin/repertoire/core
mkdir -p shared/src/commonMain/kotlin/repertoire/ui
mkdir -p shared/src/jvmCommonMain/kotlin/repertoire/core
mkdir -p shared/src/desktopMain/kotlin/repertoire/core
mkdir -p shared/src/commonMain/composeResources/drawable
mkdir -p androidApp/src/main/res

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
cp "$SRC/grdle.properties" .
cp "$SRC/build.gradle.kts" .
cp "$SRC/settings.gradle.kts" .
cp "$SRC/shared/build.gradle.kts" "shared/build.gradle.kts"
cp "$SRC/shared/src/commonMain/composeResources/drawable/"*.png "shared/src/commonMain/composeResources/drawable/"
cp -r "$SRC/androidApp/src/main/res/"* "androidApp/src/main/res/"
cp "$SRC/androidApp/src/main/AndroidManifest.xml" "androidApp/src/main/AndroidManifest.xml"
cp "$SRC/desktopApp/icon.icns" "desktopApp/" 2>/dev/null || true
cp "$SRC/desktopApp/icon.png" "desktopApp/" 2>/dev/null || true

echo "Bootstrap fertig: Alle FireHorse Core- & JSON-Module sind bereit!"
```

---

### Datei: `tools/qwen_tts_bridge.py`
Dieses Skript läuft auf macOS und Linux im Hintergrund. Wenn du Qwen-TTS installiert und deine eigene Stimm-Referenz (`~/my_voice.wav`) hinterlegt hast, spricht die App die englischen Antwortzüge (*"Knight c 3"*, *"Bishop b 5"*) mit deiner eigenen Stimme aus. Solange Qwen-TTS noch nicht läuft, fällt das Skript automatisch auf die englische Systemstimme (`say -v Daniel` / `espeak-ng -v en`) zurück, damit du sofort trainieren kannst.

```python
#!/usr/bin/env python3
"""
Persistente TTS-Bridge für FireHorseLight (macOS & Linux).
Liest englische Schachzüge Zeile für Zeile von stdin (z. B. "SAY Knight c 3")
und gibt sie über Qwen-TTS (mit Voice Cloning deiner eigenen Stimme) aus.
"""

import os
import sys
import subprocess
import shutil

# Pfad zu deiner aufgenommenen Stimm-Referenz (3-10 Sekunden sauberes Audio von dir)
VOICE_SAMPLE = os.environ.get("FIREHORSE_VOICE_SAMPLE", os.path.expanduser("~/my_voice.wav"))
# Optionaler lokaler Qwen-TTS HTTP-Endpoint (falls du Qwen-TTS als Server laufen lässt)
QWEN_SERVER_URL = os.environ.get("QWEN_TTS_URL", "")

def play_wav(path: str):
    """Spielt eine erzeugte WAV-Datei plattformübergreifend auf macOS (afplay) oder Linux (aplay/paplay) ab."""
    if sys.platform == "darwin" and shutil.which("afplay"):
        subprocess.run(["afplay", path], check=False)
    elif shutil.which("paplay"):
        subprocess.run(["paplay", path], check=False)
    elif shutil.which("aplay"):
        subprocess.run(["aplay", "-q", path], check=False)

def speak_english(text: str):
    """
    1. Wenn QWEN_TTS_URL gesetzt ist und dein Voice-Sample existiert -> Qwen-TTS Voice Clone!
    2. Sonst Fallback auf englische Native-TTS (macOS 'say' oder Linux 'espeak-ng').
    """
    if QWEN_SERVER_URL and os.path.exists(VOICE_SAMPLE):
        out_wav = "/tmp/firehorse_qwen_out.wav"
        res = subprocess.run([
            "curl", "-s", "-X", "POST", QWEN_SERVER_URL,
            "-F", f"text={text}",
            "-F", f"language=en",
            "-F", f"speaker_wav=@{VOICE_SAMPLE}",
            "--output", out_wav
        ], check=False)
        if res.returncode == 0 and os.path.exists(out_wav):
            play_wav(out_wav)
            print("DONE", flush=True)
            return

    # Fallback, falls Qwen-TTS gerade noch installiert wird oder nicht läuft:
    if sys.platform == "darwin":
        # Englische Stimme für saubere Aussprache von "Knight c 3", "Bishop c 4"
        subprocess.run(["say", "-r", "200", text], check=False)
    elif shutil.which("espeak-ng"):
        subprocess.run(["espeak-ng", "-v", "en-us", "-s", "165", text], check=False)
    elif shutil.which("spd-say"):
        subprocess.run(["spd-say", "-l", "en", "-w", text], check=False)

    print("DONE", flush=True)

def main():
    print("READY", flush=True)
    for raw in sys.stdin:
        line = raw.strip()
        if line == "QUIT":
            break
        if line.startswith("SAY "):
            speak_english(line[4:].strip())

if __name__ == "__main__":
    main()
```

---

## 4. Neue Core-Dateien (Englische Sprachlogik & FireHorse-JSON-Responder)

### Datei: `shared/src/commonMain/kotlin/repertoire/core/VoiceMove.kt`
Hier ist die komplette englische Logik für **Voxtype**:
Wenn du in Voxtype *„bishop c4“* oder *„knight c3“* sagst, erkennt Whisper manchmal Homophone wie *„night see three“*, *„bishop sea four“*, *„queen takes d 4“* oder *„pawn to e 4“*. Dieser Parser löst all diese Fälle im Kontext der legalen Züge der aktuellen `Position` auf[cite: 1].

```kotlin
package repertoire.core

/**
 * Englischer Sprach-zu-Schachzug-Parser für Voxtype / Whisper und Sprechtext-Generator für Qwen-TTS.
 *
 * Unterstützt natürliche englische Ansagen wie:
 * - "bishop c4", "knight c3", "queen d4", "rook e1", "king f1"
 * - Typische Whisper-Homophone: "night c3", "knight see three", "bishop sea for", "rook to a ate"
 * - Schlagzüge: "c takes d4", "knight takes d4", "bishop captures f7"
 * - Rochaden: "castles", "castle kingside", "short castle", "castle queenside", "long castle"
 */
object VoiceMove {

    /**
     * Geordnete Ersetzungsliste für englische Spracheingaben aus Voxtype / Whisper.
     * Da "knight" und "night" im Englischen identisch klingen, werden beide sicher auf "N" gemappt.
     */
    private val ENGLISH_PHONETIC_RULES = listOf(
        // 1. Rochaden zuerst abfangen (lange Rochade vor kurzer!)
        "castle queenside" to "O-O-O",
        "castles queenside" to "O-O-O",
        "queen side castle" to "O-O-O",
        "queenside castle" to "O-O-O",
        "long castle" to "O-O-O",
        "long castling" to "O-O-O",
        "triple oh" to "O-O-O",
        "zero zero zero" to "O-O-O",

        "castle kingside" to "O-O",
        "castles kingside" to "O-O",
        "king side castle" to "O-O",
        "kingside castle" to "O-O",
        "short castle" to "O-O",
        "short castling" to "O-O",
        "castles" to "O-O",
        "castle" to "O-O",
        "double oh" to "O-O",
        "zero zero" to "O-O",

        // 2. Englische Figurennamen + häufige Whisper-Hörfehler ("night" statt "knight")
        "knight" to "N",
        "night" to "N",
        "horse" to "N",
        "bishop" to "B",
        "rook" to "R",
        "castle" to "R",
        "queen" to "Q",
        "king" to "K",
        "pawn" to "",

        // 3. Englische Verbindungs- und Schlagwörter
        "captures" to "x",
        "takes" to "x",
        "take" to "x",
        "by" to "x",
        "goes to" to "",
        "moves to" to "",
        "to" to "",
        "on" to "",
        "at" to "",
        "checkmate" to "#",
        "mate" to "#",
        "check" to "+",
        "promotes to" to "=",
        "equals" to "=",

        // 4. Englische Buchstaben-Homophone (wenn Whisper Buchstaben als Wörter ausschreibt)
        "alpha" to "a",
        "ay" to "a",
        "bravo" to "b",
        "bee" to "b",
        "be" to "b",
        "charlie" to "c",
        "see" to "c",
        "sea" to "c",
        "delta" to "d",
        "dee" to "d",
        "echo" to "e",
        "ee" to "e",
        "foxtrot" to "f",
        "ef" to "f",
        "golf" to "g",
        "gee" to "g",
        "hotel" to "h",
        "aitch" to "h",

        // 5. Englische Zahlen + Homophone ("for" -> 4, "ate" -> 8, "won" -> 1)
        "one" to "1",
        "won" to "1",
        "two" to "2",
        "too" to "2",
        "three" to "3",
        "tree" to "3",
        "four" to "4",
        "for" to "4",
        "fore" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "ate" to "8",
    )

    /**
     * Parst eine englische Spracheingabe (z. B. "bishop c4", "knight c3", "e4", "c takes d4")
     * im Kontext der aktuellen [position][cite: 1].
     * Gibt `null` zurück, wenn der Zug in dieser Stellung nicht legal ist[cite: 1].
     */
    fun parseSpokenEnglish(transcript: String, position: Position): Move? {
        val raw = transcript.trim().trimEnd('.', ',', '!', '?')
        if (raw.isEmpty()) return null

        // 1. Direkter Schnelltest: Falls Voxtype bereits "Bc4", "Nc3" oder "e4" geliefert hat
        position.parseSan(raw.replace(" ", ""))?.let { return it }

        // 2. Englische Phonetik-Regeln anwenden (Wortgrenzen beachten)
        var text = raw.lowercase()
        for ((spoken, replacement) in ENGLISH_PHONETIC_RULES) {
            text = text.replace(Regex("\\b$spoken\\b"), replacement)
        }

        // Sonderfall Rochade direkt prüfen
        if ("O-O-O" in text) return position.parseSan("O-O-O")
        if ("O-O" in text) return position.parseSan("O-O")

        // Alle Leerzeichen und Bindestriche entfernen (z. B. "N c 3" -> "Nc3", "b c 4" -> "bc4")
        val compact = text.replace(Regex("[\\s\\-_]+"), "")
        if (compact.isEmpty()) return null

        // Figuren-Großbuchstaben am Anfang wiederherstellen ("nc3" -> "Nc3", "bc4" -> "Bc4", aber "b4" bleibt Bauer!)
        val candidateSan = restoreEnglishPieceCase(compact)
        position.parseSan(candidateSan)?.let { return it }

        // 3. Wenn "bc4" sowohl "Bc4" (Läufer) als auch "bxc4" (b-Bauer schlägt c4) sein könnte:
        // Falls der erste Buchstabe 'B' war und parseSan("Bc4") fehlschlug, testen wir den b-Bauern ("bc4"):
        if (candidateSan.startsWith("B")) {
            position.parseSan("b" + candidateSan.drop(1))?.let { return it }
        }

        // 4. Kontext-Fallback über alle legalen Züge der Stellung:
        // Sucht das genannte Zielfeld ("a1".."h8") am Ende und gleicht den Figurentyp ab.
        return matchLegalByContext(candidateSan, position)
    }

    /**
     * Macht aus "nc3" -> "Nc3" und aus "bc4" -> "Bc4", lässt aber reine Bauernzüge wie "b4" oder "e4" klein.
     */
    private fun restoreEnglishPieceCase(compact: String): String {
        val first = compact.first()
        val second = compact.getOrNull(1)
        // Wenn es mit n, r, q, k anfängt, ist es immer eine Figur (keine Linie heißt n, r, q, k)
        if (first in "nrqkNRQK") {
            return first.uppercaseChar() + compact.drop(1)
        }
        // 'b' ist knifflig: "b4" ist ein Bauernzug, "bc4" (ohne 'x' gesprochen: "bishop c4") ist ein Läufer!
        if (first == 'b' || first == 'B') {
            val isSinglePawnPush = second != null && second in '1'..'8' && compact.length == 2
            return if (isSinglePawnPush) {
                "b$second"
            } else {
                "B" + compact.drop(1)
            }
        }
        return compact
    }

    /**
     * Falls der Nutzer z. B. "knight to f3" mit unklarer Disambiguierung sagt, schauen wir in
     * `position.legalMoves()` nach allen Zügen, die auf dem genannten Zielfeld enden[cite: 1].
     */
    private fun matchLegalByContext(candidate: String, position: Position): Move? {
        val sqMatch = Regex("([a-h][1-8])([qrbnQRBN])?$").find(candidate) ?: return null
        val toSquare = parseSquare(sqMatch.groupValues[1])
        val promo = sqMatch.groupValues[2].firstOrNull()?.lowercaseChar()

        val legalToTarget = position.legalMoves().filter {
            it.to == toSquare && (promo == null || it.promotion == promo || it.promotion == 'q')
        }
        if (legalToTarget.isEmpty()) return null
        if (legalToTarget.size == 1) return legalToTarget.single()

        // Nach Figurentyp filtern
        val pieceChar = candidate.first().let { if (it in "NBRQK") it else 'P' }
        val byPiece = legalToTarget.filter { position.pieceAt(it.from)?.uppercaseChar() == pieceChar }
        if (byPiece.size == 1) return byPiece.single()

        // Nach Startlinie ('a'..'h') filtern, z. B. bei "e takes d5" vs "c takes d5"
        val fileChar = candidate.drop(if (pieceChar == 'P') 0 else 1).firstOrNull { it in 'a'..'h' }
        if (fileChar != null) {
            val byFile = byPiece.filter { (it.from % 8) == (fileChar - 'a') }
            if (byFile.size == 1) return byFile.single()
        }
        return null
    }

    /**
     * Macht aus einem legalen [move] in [position] natürliches Englisch für Qwen-TTS[cite: 1]:
     * - `Nf3` -> `"Knight f 3"`
     * - `Bc4` -> `"Bishop c 4"`
     * - `cxd4` -> `"c takes d 4"`
     * - `O-O` -> `"Castles kingside"`
     */
    fun toSpokenEnglish(position: Position, move: Move): String {
        val san = position.san(move)
        if (san.startsWith("O-O-O")) return "Castles queenside"
        if (san.startsWith("O-O")) return "Castles kingside"

        val suffix = when {
            san.endsWith("#") -> ", checkmate"
            san.endsWith("+") -> ", check"
            else -> ""
        }
        val core = san.trimEnd('+', '#')

        val pieceWord = when (core.first()) {
            'N' -> "Knight "
            'B' -> "Bishop "
            'R' -> "Rook "
            'Q' -> "Queen "
            'K' -> "King "
            else -> ""
        }

        val rest = if (pieceWord.isNotEmpty()) core.drop(1) else core
        // Fügt Leerzeichen zwischen Linie und Reihe ein ("c4" -> "c 4"), damit TTS es klar ausspricht
        val formattedRest = rest
            .replace("x", " takes ")
            .replace("=Q", " promotes to Queen")
            .replace("=R", " promotes to Rook")
            .replace("=B", " promotes to Bishop")
            .replace("=N", " promotes to Knight")
            .replace(Regex("([a-h])([1-8])"), "$1 $2")

        return (pieceWord + formattedRest).trim().replace(Regex("\\s+"), " ") + suffix
    }
}
```

---

### Datei: `shared/src/commonMain/kotlin/repertoire/core/LineResponder.kt`
Arbeitet direkt mit den echten `Variant`-Objekten aus deiner `FireHorse`-`Library` (`library.json` oder importierten `.json`-Dateien)[cite: 1] sowie dem Lichess-Opening-Explorer[cite: 1].

```kotlin
package repertoire.core

import kotlin.random.Random

/** Woher die Antwortzüge kommen: Aus deinen FireHorse-Varianten oder live aus der Lichess-Datenbank[cite: 1]. */
enum class ResponseSource(val label: String) {
    FIREHORSE_LIBRARY("FireHorse Varianten"),
    LICHESS_MASTERS("Lichess Masters"),
    LICHESS_PLAYERS("Lichess Players"),
}

/** Wie der Computer bei Verzweigungen zieht. */
enum class ReplyStrategy(val label: String) {
    MAIN("1. Hauptlinie"),
    WEIGHTED("Zufällig gewichtet"),
    SIDELINES("2./3. Nebenvariante"),
}

/** Antwort des Computers inkl. Herkunfts-Info (z. B. "Sizilianisch › Scheveningen")[cite: 1]. */
data class LineReply(val move: Move, val label: String)

object LineResponder {

    /**
     * Durchsucht alle aktiven [variants] aus deiner FireHorse-Library (optional gefiltert auf eine
     * bestimmte Eröffnung [openingFilter]) nach der aktuellen Stellung (`position.key`)[cite: 1].
     * Erkennt automatisch auch Zugumstellungen (Transpositionen), genau wie `QuizBuilder` in FireHorse[cite: 1]!
     */
    fun replyFromVariants(
        position: Position,
        variants: List<Variant>,
        openingFilter: String? = null,
        strategy: ReplyStrategy = ReplyStrategy.MAIN,
    ): LineReply? {
        val pool = variants.filter { !it.archived && (openingFilter == null || Library.openingOf(it) == openingFilter) }
        val key = position.key

        // Alle Züge finden, die von `position.key` in deinen gespeicherten Varianten weiterführen
        val matches = mutableListOf<Pair<Move, Variant>>()
        for (v in pool) {
            val positions = v.positions
            for (i in v.moves.indices) {
                if (positions[i].key == key) {
                    val move = Move.fromUci(v.moves[i])
                    if (position.isLegal(move)) {
                        matches += move to v
                    }
                    break
                }
            }
        }

        if (matches.isEmpty()) return null

        val grouped = matches.groupBy({ it.first }, { it.second }).entries.toList()
        val chosen = when (strategy) {
            ReplyStrategy.MAIN -> grouped.first()
            ReplyStrategy.SIDELINES -> grouped.getOrElse(1) { grouped.first() }
            ReplyStrategy.WEIGHTED -> grouped.random()
        }
        val titles = chosen.value.take(2).joinToString(" / ") { QuizBuilder.title(it) }
        return LineReply(chosen.key, titles)
    }

    /** Wählt eine Antwort aus der Lichess Opening Explorer Datenbank[cite: 1]. */
    fun replyFromExplorer(
        position: Position,
        explorerResult: ExplorerResult,
        strategy: ReplyStrategy,
    ): LineReply? {
        val total = explorerResult.games.coerceAtLeast(1)
        val viable = explorerResult.moves
            .filter { it.games * 100 >= total || it == explorerResult.moves.firstOrNull() }
            .mapNotNull { em -> em.moveIn(position)?.let { it to em } }

        if (viable.isEmpty()) return null

        val (move, em) = when (strategy) {
            ReplyStrategy.MAIN -> viable.first()
            ReplyStrategy.SIDELINES -> viable.drop(1).take(2).randomOrNull() ?: viable.first()
            ReplyStrategy.WEIGHTED -> {
                val sum = viable.sumOf { it.second.games }.coerceAtLeast(1)
                var r = Random.nextLong(sum)
                viable.firstOrNull { (_, m) -> r -= m.games; r < 0 } ?: viable.first()
            }
        }
        val pct = (em.games * 100 / total).toInt()
        val openingName = explorerResult.opening?.name?.takeIf { it.isNotBlank() } ?: "Lichess"
        return LineReply(move, "$openingName ($pct\%,${Explorer.count(em.games)} games)")
    }
}
```

---

### Datei: `shared/src/commonMain/kotlin/repertoire/core/VoiceIo.kt`
```kotlin
package repertoire.core

/**
 * Plattformübergreifende Sprachausgabe für die englischen Schachzüge ("Knight c 3", "Bishop c 4"):
 * - Auf macOS & Linux: `tools/qwen_tts_bridge.py` (mit deiner geklonten Stimme über Qwen-TTS oder System-Voice).
 * - Auf Android: Native `android.speech.tts.TextToSpeech` (Locale.US).
 */
interface Speaker {
    suspend fun speakEnglish(text: String)
    fun close()
}

expect fun createSpeaker(): Speaker
```

### Datei: `shared/src/desktopMain/kotlin/repertoire/core/VoiceIo.desktop.kt`
```kotlin
package repertoire.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class DesktopQwenSpeaker : Speaker {
    private val mutex = Mutex()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private fun ensureBridge() {
        if (process?.isAlive == true) return
        val script = listOf(File("tools/qwen_tts_bridge.py"), File("../tools/qwen_tts_bridge.py"))
            .firstOrNull { it.exists() } ?: return
        val p = ProcessBuilder("python3", script.absolutePath).start()
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream))
        reader = BufferedReader(InputStreamReader(p.inputStream))
        reader?.readLine() // wartet auf "READY"
    }

    override suspend fun speakEnglish(text: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                ensureBridge()
                val w = writer
                val r = reader
                if (w != null && r != null) {
                    w.write("SAY $text\n")
                    w.flush()
                    r.readLine()
                } else {
                    fallbackSpeak(text)
                }
            }.onFailure {
                fallbackSpeak(text)
            }
        }
    }

    private fun fallbackSpeak(text: String) {
        val os = System.getProperty("os.name").lowercase()
        runCatching {
            if ("mac" in os) {
                ProcessBuilder("say", text).start().waitFor()
            } else {
                ProcessBuilder("espeak-ng", "-v", "en-us", text).start().waitFor()
            }
        }
    }

    override fun close() {
        runCatching {
            writer?.write("QUIT\n")
            writer?.flush()
            process?.destroy()
        }
    }
}

actual fun createSpeaker(): Speaker = DesktopQwenSpeaker()
```

### Datei: `shared/src/androidMain/kotlin/repertoire/core/VoiceIo.android.kt`
```kotlin
package repertoire.core

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Wird in `MainActivity.onCreate` gesetzt, damit Android TTS ohne Zusatz-Abhängigkeiten läuft. */
var appContext: Context? = null

class AndroidSpeaker : Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        appContext?.let { ctx ->
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    ready = true
                }
            }
        }
    }

    override suspend fun speakEnglish(text: String) = withContext(Dispatchers.Main) {
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fh_move")
        }
    }

    override fun close() {
        tts?.shutdown()
    }
}

actual fun createSpeaker(): Speaker = AndroidSpeaker()
```

---

## 5. ViewModel & UI (mit FireHorse-JSON-Import, Voxtype-Direktfeld & Double-Tap Board)

### Datei: `shared/src/commonMain/kotlin/repertoire/ui/BlindfoldViewModel.kt`
Lädt beim Start direkt deine `Store`-Library aus `FireHorse`[cite: 1] und bietet einen Button **„FireHorse JSON importieren“** (`Exchange.import`), mit dem du jederzeit exportierte `.json`-Dateien aus der großen App einspeisen kannst[cite: 1].

```kotlin
package repertoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import repertoire.core.Exchange
import repertoire.core.Explorer
import repertoire.core.ExplorerSource
import repertoire.core.FilePicker
import repertoire.core.Library
import repertoire.core.LineReply
import repertoire.core.LineResponder
import repertoire.core.Move
import repertoire.core.Position
import repertoire.core.ReplyStrategy
import repertoire.core.ResponseSource
import repertoire.core.Speaker
import repertoire.core.Store
import repertoire.core.VoiceMove
import repertoire.core.createSpeaker

data class BlindMoveEntry(
    val ply: Int,
    val san: String,
    val spokenEnglish: String,
    val byUser: Boolean,
    val info: String = "",
)

class BlindfoldViewModel(
    private val store: Store,
    private val files: FilePicker? = null,
    private val speaker: Speaker = createSpeaker(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    /** Deine komplette FireHorse-Library (geladen aus `library.json` oder per JSON-Import)[cite: 1]. */
    var library by mutableStateOf(store.loadLibrary())
        private set

    var progress by mutableStateOf(store.loadProgress())
        private set

    /** Aktuelle Stellung auf dem (standardmäßig versteckten) Brett[cite: 1]. */
    var position by mutableStateOf(Position.START)
        private set

    /** Letzter gespielter Zug (für die Hervorhebung, wenn das Brett per Doppeltipp gezeigt wird)[cite: 1]. */
    var lastMove by mutableStateOf<Move?>(null)
        private set

    /** Ist das Brett sichtbar? (Doppeltipp schaltet es jederzeit an/aus). */
    var showBoard by mutableStateOf(false)
        private set

    /** Spielst du Weiß oder Schwarz?[cite: 1] */
    var playWhite by mutableStateOf(true)
        private set

    /** Optionaler Filter auf eine bestimmte Eröffnung aus deiner FireHorse-Library (null = Alle)[cite: 1]. */
    var selectedOpening by mutableStateOf<String?>(null)

    var source by mutableStateOf(ResponseSource.FIREHORSE_LIBRARY)
    var strategy by mutableStateOf(ReplyStrategy.MAIN)

    /** Textfeld, in das Voxtype (per Hotkey) direkt deine englischen Züge ("bishop c4", "knight c3") tippt. */
    var voxtypeBuffer by mutableStateOf("")

    var status by mutableStateOf("Voxtype bereit · Sag z.B. „e4“, „knight f3“, „bishop c4“ · Doppeltipp = Brett")
    var history by mutableStateOf<List<BlindMoveEntry>>(emptyList())
        private set

    /** Alle Eröffnungsnamen aus deiner geladenen FireHorse-Library[cite: 1]. */
    val openings: List<String>
        get() = library.groups().map { it.opening }

    /** Doppeltipp auf die Fläche: Schachbrett ein- oder ausblenden. */
    fun toggleBoard() {
        showBoard = !showBoard
    }

    /**
     * 1x Tippen auf die Fläche oder Enter nach Voxtype-Diktat:
     * Liest `voxtypeBuffer` (z.B. "bishop c4" oder auch mehrere Züge auf einmal "e4 e5 knight f3"),
     * führt den Zug aus und lässt den Computer aus deinen Linien / Lichess antworten.
     */
    fun submitVoxtypeBuffer() {
        val text = voxtypeBuffer.trim()
        if (text.isEmpty()) return
        voxtypeBuffer = ""
        onSpokenEnglish(text)
    }

    /**
     * Verarbeitet deine englische Ansage ("bishop c4", "knight c3", "castles" oder "show board").
     */
    fun onSpokenEnglish(transcript: String) {
        val lower = transcript.lowercase().trim()
        if (lower in listOf("board", "show board", "hide board", "peek")) {
            toggleBoard()
            return
        }
        if (lower in listOf("reset", "new game", "restart")) {
            resetSession()
            return
        }
        if (lower in listOf("repeat", "again")) {
            history.lastOrNull()?.let { scope.launch { speaker.speakEnglish(it.spokenEnglish) } }
            return
        }

        val move = VoiceMove.parseSpokenEnglish(transcript, position)
        if (move == null) {
            status = "❌ Not legal here: \"$transcript\""
            scope.launch { speaker.speakEnglish("Illegal move") }
            return
        }

        playOnBoard(move, byUser = true, info = "Heard: \"$transcript\"")

        // Computer antwortet automatisch mit einem Zug aus deinen FireHorse-Linien oder Lichess!
        scope.launch {
            triggerBookReply()
        }
    }

    /** Erlaubt es auch, direkt auf dem aufgeklappten Brett zu ziehen[cite: 1]. */
    fun onBoardMove(move: Move) {
        if (!position.isLegal(move)) return
        playOnBoard(move, byUser = true, info = "Board move")
        scope.launch { triggerBookReply() }
    }

    private fun playOnBoard(move: Move, byUser: Boolean, info: String): BlindMoveEntry {
        val san = position.san(move)
        val spoken = VoiceMove.toSpokenEnglish(position, move)
        position = position.play(move)
        lastMove = move
        val entry = BlindMoveEntry(history.size + 1, san, spoken, byUser, info)
        history = history + entry
        return entry
    }

    private suspend fun triggerBookReply() {
        val reply: LineReply? = when (source) {
            ResponseSource.FIREHORSE_LIBRARY -> LineResponder.replyFromVariants(
                position = position,
                variants = library.active,
                openingFilter = selectedOpening,
                strategy = strategy,
            )
            ResponseSource.LICHESS_MASTERS, ResponseSource.LICHESS_PLAYERS -> {
                val token = library.settings.lichessToken
                if (token.isBlank()) {
                    status = "Kein Lichess-Token in library.json – nutze eigene FireHorse-Varianten."
                    LineResponder.replyFromVariants(position, library.active, selectedOpening, strategy)
                } else {
                    val explorerSrc = if (source == ResponseSource.LICHESS_MASTERS) ExplorerSource.MASTERS else ExplorerSource.LICHESS
                    runCatching {
                        val res = Explorer.fetch(explorerSrc, position.fen, token)
                        LineResponder.replyFromExplorer(position, res, strategy)
                    }.getOrNull()
                }
            }
        }

        if (reply == null) {
            status = "🏁 End of line reached after ${history.size} plies!"
            speaker.speakEnglish("End of line")
            return
        }

        val step = playOnBoard(reply.move, byUser = false, info = reply.label)
        status = "Reply: ${step.san} (${ step.spokenEnglish }) ·${reply.label}"
        speaker.speakEnglish(step.spokenEnglish)
    }

    fun resetSession(newPlayWhite: Boolean = playWhite) {
        playWhite = newPlayWhite
        position = Position.START
        lastMove = null
        history = emptyList()
        showBoard = false
        status = "New blindfold line (${if (playWhite) "White" else "Black"})."
        if (!playWhite) {
            scope.launch { triggerBookReply() }
        }
    }

    /**
     * Lädt eine aus FireHorse exportierte `.json`-Datei (`"format": "eroeffnungs-puzzle"`) ein[cite: 1]
     * und fügt alle darin enthaltenen Varianten zum lokalen Datenbestand hinzu[cite: 1].
     */
    fun importFireHorseJson() {
        val picker = files ?: return
        picker.open { text ->
            if (text == null) return@open
            runCatching {
                val res = Exchange.import(text, library, progress, replace = false)
                library = res.library
                progress = res.progress
                store.saveLibrary(library)
                store.saveProgress(progress)
                status = "✅ FireHorse JSON importiert: ${res.summary()} (Gesamt:${library.active.size} Varianten)"
            }.onFailure { err ->
                status = "Import-Fehler: ${err.message}"
            }
        }
    }
}
```

---

### Datei: `shared/src/commonMain/kotlin/repertoire/ui/BlindfoldScreen.kt`
```kotlin
package repertoire.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import repertoire.core.ReplyStrategy
import repertoire.core.ResponseSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlindfoldScreen(vm: BlindfoldViewModel, modifier: Modifier = Modifier) {
    val voxFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { voxFocus.requestFocus() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF111318)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top-Leiste: Titel + FireHorse JSON Import + Seitenwahl
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "FireHorse Light · English Voice (" + vm.library.active.size + " Varianten)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::importFireHorseJson) {
                            Text("📂 FireHorse JSON laden")
                        }
                        FilterChip(
                            selected = vm.playWhite,
                            onClick = { vm.resetSession(newPlayWhite = true) },
                            label = { Text("White") },
                        )
                        FilterChip(
                            selected = !vm.playWhite,
                            onClick = { vm.resetSession(newPlayWhite = false) },
                            label = { Text("Black") },
                        )
                        OutlinedButton(onClick = { vm.resetSession() }) {
                            Text("↺ Reset")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Quelle (FireHorse JSON vs. Lichess) + Eröffnungs-Filter + Zieh-Strategie
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (src in ResponseSource.entries) {
                        FilterChip(
                            selected = vm.source == src,
                            onClick = { vm.source = src },
                            label = { Text(src.label) },
                        )
                    }
                    for (strat in ReplyStrategy.entries) {
                        FilterChip(
                            selected = vm.strategy == strat,
                            onClick = { vm.strategy = strat },
                            label = { Text(strat.label) },
                        )
                    }
                }

                // Falls eigene Eröffnungen aus FireHorse vorhanden sind, als Schnell-Chips anzeigen:
                if (vm.openings.isNotEmpty() && vm.source == ResponseSource.FIREHORSE_LIBRARY) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = vm.selectedOpening == null,
                            onClick = { vm.selectedOpening = null },
                            label = { Text("Alle Eröffnungen") },
                        )
                        for (op in vm.openings) {
                            FilterChip(
                                selected = vm.selectedOpening == op,
                                onClick = { vm.selectedOpening = op },
                                label = { Text(op) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // GROSSE INTERAKTIONS-FLÄCHE:
                // 1x Tippen -> Führt den von Voxtype eingesprochenen Zug aus (und fokussiert das Voxtype-Feld)
                // 2x Tippen (Doppeltipp) -> Zeigt / Versteckt das echte FireHorse-Schachbrett!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF181B22), RoundedCornerShape(18.dp))
                        .border(2.dp, Color(0xFF2E3440), RoundedCornerShape(18.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    voxFocus.requestFocus()
                                    vm.submitVoxtypeBuffer()
                                },
                                onDoubleTap = { vm.toggleBoard() },
                            )
                        }
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.showBoard) {
                        // Das originale Schachbrett aus FireHorse (mit deinen cburnett-Figuren!)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ChessBoard(
                                position = vm.position,
                                whiteBottom = vm.playWhite,
                                lastMove = vm.lastMove,
                                marks = emptyMap(),
                                interactive = true,
                                onMove = vm::onBoardMove,
                                modifier = Modifier.sizeIn(maxWidth = 420.dp, maxHeight = 420.dp).aspectRatio(1f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Double-tap anywhere outside to hide board", color = Color.Gray)
                        }
                    } else {
                        // Blindfold Kreidetafel-Ansicht
                        val lastUser = vm.history.lastOrNull { it.byUser }
                        val lastReply = vm.history.lastOrNull { !it.byUser }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (vm.history.isEmpty()) {
                                    "Blindfold Ready\nSay e.g. \"e4\", \"knight f3\", \"bishop c4\""
                                } else {
                                    "${lastUser?.spokenEnglish ?: "…"}   →   ${lastReply?.spokenEnglish ?: "…"}"
                                },
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = vm.status,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF90CAF9),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Voxtype-Direkteingabefeld (hält dauerhaft den Fokus für deinen globalen Voxtype-Hotkey!)
                OutlinedTextField(
                    value = vm.voxtypeBuffer,
                    onValueChange = { typed ->
                        // Wenn Voxtype am Ende einen Zeilenumbruch schickt, sofort ausführen!
                        if ("\n" in typed) {
                            vm.voxtypeBuffer = typed.replace("\n", "").trim()
                            vm.submitVoxtypeBuffer()
                        } else {
                            vm.voxtypeBuffer = typed
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(voxFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                                vm.submitVoxtypeBuffer()
                                true
                            } else {
                                false
                            }
                        },
                    singleLine = true,
                    label = { Text("🎙️ Voxtype Input (English: \"bishop c4\", \"knight c3\", \"c takes d4\" + Enter/Tap)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.submitVoxtypeBuffer() }),
                )
            }
        }
    }
}
```

---

## 6. Desktop- & Android-Entrypoints + Unit-Tests

### Datei: `desktopApp/src/main/kotlin/repertoire/desktop/Main.kt`
Nutzt exakt denselben Datenordner (`~/Documents/EroeffnungsPuzzle` bzw. unter Linux `~/Dokumente/EroeffnungsPuzzle`) wie `FireHorse`[cite: 1] – deine Varianten sind also sofort da!

```kotlin
package repertoire.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.Store
import repertoire.core.UserDirs
import repertoire.ui.BlindfoldScreen
import repertoire.ui.BlindfoldViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val vm = remember {
        val dir = System.getProperty("repertoire.dataDir")?.let(::File)
            ?: File(UserDirs.documents(), "EroeffnungsPuzzle")
        BlindfoldViewModel(Store(DirectoryStore(dir)), DesktopJsonPicker)
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "FireHorse Light",
        state = rememberWindowState(width = 960.dp, height = 720.dp),
    ) {
        BlindfoldScreen(vm)
    }
}

private object DesktopJsonPicker : FilePicker {
    override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) = onDone(null)
    override fun open(onDone: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "FireHorse JSON importieren", FileDialog.LOAD)
        dialog.setFilenameFilter { _, n -> n.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val file = dialog.file?.let { File(dialog.directory, it) }
        onDone(file?.readText())
    }
}
```

### Datei: `androidApp/src/main/kotlin/repertoire/android/MainActivity.kt`
```kotlin
package repertoire.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.Store
import repertoire.core.appContext
import repertoire.ui.BlindfoldScreen
import repertoire.ui.BlindfoldViewModel

class MainActivity : ComponentActivity() {
    private var pendingOpen: ((String?) -> Unit)? = null

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = pendingOpen ?: return@registerForActivityResult
        pendingOpen = null
        cb(uri?.let { contentResolver.openInputStream(it)?.use { s -> s.readBytes().decodeToString() } })
    }

    private val picker = object : FilePicker {
        override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) = onDone(null)
        override fun open(onDone: (String?) -> Unit) {
            pendingOpen = onDone
            openDoc.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        enableEdgeToEdge()
        val vm = BlindfoldViewModel(Store(DirectoryStore(filesDir)), picker)
        setContent {
            BlindfoldScreen(vm, Modifier.safeDrawingPadding())
        }
    }
}
```

### Datei: `shared/src/commonTest/kotlin/repertoire/VoiceMoveTest.kt`
Prüft sowohl deine englischen Sprachbefehle (*„bishop c4“*, *„knight c3“*, inkl. Whisper-Hörfehlern wie *„night see three“*) als auch das Einlesen einer originalen `FireHorse`-JSON-Exportdatei[cite: 1]:

```kotlin
package repertoire

import repertoire.core.Exchange
import repertoire.core.Library
import repertoire.core.LineResponder
import repertoire.core.Position
import repertoire.core.Progress
import repertoire.core.VoiceMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VoiceMoveTest {

    @Test
    fun parsesEnglishSpokenMovesAndHomophones() {
        var pos = Position.START

        // 1. e4 ("pawn to e four")
        val m1 = VoiceMove.parseSpokenEnglish("pawn to e four", pos)
        assertNotNull(m1)
        assertEquals("e2e4", m1.uci)
        assertEquals("e 4", VoiceMove.toSpokenEnglish(pos, m1))
        pos = pos.play(m1)

        // 1... e5 ("ee five")
        val m2 = VoiceMove.parseSpokenEnglish("ee five", pos)
        assertNotNull(m2)
        assertEquals("e7e5", m2.uci)
        pos = pos.play(m2)

        // 2. Nf3 ("knight f3" oder Whisper-Homophon "night ef three")
        val m3 = VoiceMove.parseSpokenEnglish("night ef three", pos)
        assertNotNull(m3)
        assertEquals("g1f3", m3.uci)
        assertEquals("Knight f 3", VoiceMove.toSpokenEnglish(pos, m3))
        pos = pos.play(m3)

        // 2... Nc6 ("knight c6")
        val m4 = VoiceMove.parseSpokenEnglish("knight c6", pos)
        assertNotNull(m4)
        assertEquals("b8c6", m4.uci)
        pos = pos.play(m4)

        // 3. Bc4 ("bishop c4" oder "bishop sea for")
        val m5 = VoiceMove.parseSpokenEnglish("bishop c4", pos)
        assertNotNull(m5)
        assertEquals("f1c4", m5.uci)
        assertEquals("Bishop c 4", VoiceMove.toSpokenEnglish(pos, m5))
    }

    @Test
    fun importsFireHorseJsonAndRepliesAlongSavedLine() {
        val json = """
            {
              "format": "eroeffnungs-puzzle",
              "version": 1,
              "variants": [
                {
                  "opening": "Italian Game",
                  "name": "Giuoco Piano",
                  "playWhite": true,
                  "moves": "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5"
                }
              ]
            }
        """.trimIndent()

        val imported = Exchange.import(json, Library(), Progress())
        assertEquals(1, imported.added)

        // Wir spielen 1. e4 und prüfen, ob LineResponder automatisch 1... e5 aus der FireHorse-JSON antwortet
        val afterE4 = Position.START.play(VoiceMove.parseSpokenEnglish("e4", Position.START)!!)
        val reply = LineResponder.replyFromVariants(afterE4, imported.library.active)
        assertNotNull(reply)
        assertEquals("e7e5", reply.move.uci)
    }
}
```
