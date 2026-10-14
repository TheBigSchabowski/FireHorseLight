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
