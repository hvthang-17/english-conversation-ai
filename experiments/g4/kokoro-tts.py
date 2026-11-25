"""G4 synthetic TTS probe. Default: no imports of ML libraries or network calls."""

import argparse
import json
import math
import struct
import time
import uuid
import wave
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RATE = 24000
SAMPLES = {
    "a2": [("af_heart", "Excuse me, where is the train station?")],
    "b1": [
        ("am_michael", "Is breakfast included in the room price?"),
        ("af_heart", "Yes, but this rate is non-refundable. If your plans might change, I can offer a flexible rate."),
    ],
}


def pcm16(values):
    """Validate model audio before writing little-endian mono PCM."""
    result = bytearray()
    for value in values:
        value = float(value)
        if not math.isfinite(value) or not -1 <= value <= 1:
            raise ValueError("Invalid normalized audio sample")
        result.extend(struct.pack("<h", round(value * 32767)))
    if not result:
        raise ValueError("Model returned empty audio")
    return result


def generate(pipeline, folder):
    """Injected pipeline permits offline validation of output and failure cleanup."""
    folder.mkdir(parents=True, exist_ok=False)
    try:
        for name, turns in SAMPLES.items():
            started = time.monotonic()
            frames = bytearray()
            for index, (voice, text) in enumerate(turns):
                turn = bytearray()
                for _, _, audio in pipeline(text, voice=voice, speed=1):
                    if audio is None:
                        raise ValueError("Model returned no audio")
                    turn.extend(pcm16(audio.tolist()))
                    if len(frames) + len(turn) > RATE * 2 * 60:
                        raise ValueError("Synthetic sample exceeded 60 seconds")
                if not turn:
                    raise ValueError("Model returned no chunks")
                if index:
                    frames.extend(bytes(int(RATE * 0.25) * 2))
                frames.extend(turn)
            path = folder / f"{name}.wav"
            with wave.open(str(path), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(RATE)
                output.writeframes(frames)
            print(json.dumps({"sample": name, "elapsedMs": round((time.monotonic() - started) * 1000),
                              "bytes": path.stat().st_size, "file": str(path)}))
    except BaseException:
        # Only files created inside this unique probe directory are removed.
        for name in SAMPLES:
            (folder / f"{name}.wav").unlink(missing_ok=True)
        folder.rmdir()
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true", help="Run CPU inference; first run may download model assets")
    args = parser.parse_args()
    if not args.generate:
        print(json.dumps({"mode": "offline", "samples": len(SAMPLES), "provider": "KOKORO_LOCAL",
                          "format": "wav-pcm16-mono-24000", "apiRequests": 0,
                          "modelLoaded": False}))
        return
    try:
        from kokoro import KPipeline
    except ImportError:
        raise SystemExit("Install experiments/g4/requirements-kokoro.txt in a Python 3.11 virtual environment first.") from None
    pipeline = KPipeline(lang_code="a", repo_id="hexgrad/Kokoro-82M", device="cpu")
    generate(pipeline, ROOT / ".runtime" / f"g4-kokoro-tts-{uuid.uuid4().hex}")


if __name__ == "__main__":
    main()
