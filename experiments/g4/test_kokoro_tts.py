import contextlib
import importlib.util
import io
import tempfile
import unittest
import wave
from pathlib import Path

spec = importlib.util.spec_from_file_location("kokoro_probe", Path(__file__).with_name("kokoro-tts.py"))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class Audio:
    def tolist(self):
        return [0.0, 0.5, -0.5] * 80


class ProbeTest(unittest.TestCase):
    def test_pcm_rejects_empty_nonfinite_and_out_of_range(self):
        for values in ([], [float("nan")], [float("inf")], [1.01]):
            with self.subTest(values=values), self.assertRaises(ValueError):
                probe.pcm16(values)

    def test_wav_format_turn_order_and_separator(self):
        voices = []

        def pipeline(text, *, voice, speed):
            voices.append(voice)
            yield text, "", Audio()

        with tempfile.TemporaryDirectory() as parent, contextlib.redirect_stdout(io.StringIO()):
            folder = Path(parent) / "probe"
            probe.generate(pipeline, folder)
            for name, frames in (("a2", 240), ("b1", 480 + 6000)):
                with wave.open(str(folder / f"{name}.wav"), "rb") as audio:
                    self.assertEqual((1, 2, 24000, frames),
                                     (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getnframes()))
            self.assertEqual(["af_heart", "am_michael", "af_heart"], voices)

    def test_failure_removes_previous_sample_and_partial_directory(self):
        count = 0

        def pipeline(text, **kwargs):
            nonlocal count
            count += 1
            if count == 2:
                raise RuntimeError("Synthetic model failure")
            yield text, "", Audio()

        with tempfile.TemporaryDirectory() as parent, contextlib.redirect_stdout(io.StringIO()):
            folder = Path(parent) / "probe"
            with self.assertRaises(RuntimeError):
                probe.generate(pipeline, folder)
            self.assertFalse(folder.exists())


if __name__ == "__main__":
    unittest.main()
