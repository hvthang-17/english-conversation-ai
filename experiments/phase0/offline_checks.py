"""P0 contract checks with synthetic fixtures. No network calls or credentials."""

import json
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path


FIXTURE = Path(__file__).with_name("fixtures") / "ai-evaluation-cases.json"
SPEECH_TASKS = {
    "SCRIPTED": ("AccuracyScore", "FluencyScore", "PronScore", "CompletenessScore"),
    "UNSCRIPTED": ("AccuracyScore", "FluencyScore", "PronScore"),
}
RETENTION = {"audio": timedelta(hours=1), "draft": timedelta(minutes=30),
             "AI log": timedelta(days=30), "backup": timedelta(days=7)}


def classify_response(task, *, status=200, body="", timed_out=False):
    if timed_out:
        return "UNKNOWN_TIMEOUT"
    if status == 429:
        return "QUOTA"
    if status != 200:
        return "FAILED"
    try:
        payload = json.loads(body)
        if not isinstance(payload, dict):
            return "INVALID_OUTPUT"
        if task not in SPEECH_TASKS:
            field = {"CHAT": "reply", "TRANSCRIPTION": "transcript", "FEEDBACK": "summary"}[task]
            value = payload.get(field)
            return "OK" if isinstance(value, str) and value.strip() else "INVALID_OUTPUT"
        if payload.get("RecognitionStatus") == "NoMatch":
            return "INSUFFICIENT_EVIDENCE"
        result = payload["NBest"][0]["PronunciationAssessment"]
        keys = SPEECH_TASKS[task]
        if "ProsodyScore" in result:
            keys += ("ProsodyScore",)
        if task == "UNSCRIPTED" and "CompletenessScore" in result:
            return "INVALID_OUTPUT"
        if not all(type(result[key]) in (float, int) and 0 <= result[key] <= 100 for key in keys):
            return "INVALID_OUTPUT"
        return "OK"
    except (KeyError, IndexError, TypeError, ValueError):
        return "INVALID_OUTPUT"


class OfflineContractTest(unittest.TestCase):
    def test_gemini_fixture_outcomes(self):
        for task, field in (("CHAT", "reply"), ("TRANSCRIPTION", "transcript"), ("FEEDBACK", "summary")):
            with self.subTest(task=task):
                self.assertEqual("OK", classify_response(task, body=json.dumps({field: "Example"})))
                self.assertEqual("INVALID_OUTPUT", classify_response(task, body="{}"))
                self.assertEqual("INVALID_OUTPUT", classify_response(task, body="["))
                self.assertEqual("QUOTA", classify_response(task, status=429))
                self.assertEqual("UNKNOWN_TIMEOUT", classify_response(task, timed_out=True))

    def test_azure_modes_and_missing_evidence(self):
        scores = {"AccuracyScore": 87, "FluencyScore": 82, "PronScore": 84}
        for mode, result in (("SCRIPTED", {**scores, "CompletenessScore": 100, "ProsodyScore": 80}),
                             ("UNSCRIPTED", scores)):
            with self.subTest(mode=mode):
                valid = json.dumps({"NBest": [{"PronunciationAssessment": result}]})
                self.assertEqual("OK", classify_response(mode, body=valid))
                invalid = json.dumps({"NBest": [{"PronunciationAssessment": {**result, "AccuracyScore": 101}}]})
                self.assertEqual("INVALID_OUTPUT", classify_response(mode, body=invalid))
                self.assertEqual("INSUFFICIENT_EVIDENCE", classify_response(mode, body='{"RecognitionStatus":"NoMatch"}'))
                self.assertEqual("QUOTA", classify_response(mode, status=429))
                self.assertEqual("UNKNOWN_TIMEOUT", classify_response(mode, timed_out=True))
        missing = json.dumps({"NBest": [{"PronunciationAssessment": scores}]})
        self.assertEqual("INVALID_OUTPUT", classify_response("SCRIPTED", body=missing))

    def test_content_fixture_coverage(self):
        payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
        cases = payload["cases"]
        self.assertFalse(payload["personal_data"])
        self.assertEqual(len(cases), payload["case_count"])
        self.assertEqual(len(cases), len({case["case_id"] for case in cases}))
        self.assertEqual({"A2", "B1"}, {case["selected_level"] for case in cases})

    def test_retention_fixture_boundaries(self):
        # Illustrative clock fixtures for P0, independent of application cleanup code.
        created = datetime(2026, 11, 24, 8, tzinfo=timezone.utc)
        ready = created + timedelta(minutes=3)
        for name, start, expected in (("audio", created, "2026-11-24T09:00:00+00:00"),
                                      ("draft", ready, "2026-11-24T08:33:00+00:00"),
                                      ("AI log", created, "2026-12-24T08:00:00+00:00"),
                                      ("backup", created, "2026-12-01T08:00:00+00:00")):
            with self.subTest(name=name):
                expires = start + RETENTION[name]
                self.assertEqual(expected, expires.isoformat())
                self.assertLess(expires - timedelta(microseconds=1), expires)


if __name__ == "__main__":
    unittest.main(verbosity=2)
