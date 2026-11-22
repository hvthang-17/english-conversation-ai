"""Four-request Gemini smoke with fixed fictional data. Writes only sanitized evidence."""
import argparse
import datetime
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.request

MODEL = "gemini-3.5-flash-lite"
ROOT = Path(__file__).resolve().parents[2]
INSTRUCTION = (
    "English practice role-play in a fictional restaurant. Learner is a customer. "
    "You are the server. The menu has vegetable soup, chicken rice and tea. "
    "Use the specified English level, short lines and one question at a time. "
    "Never request personal details. Return plain English only."
)


def local_config():
    lines = (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines()
    return dict(line.split("=", 1) for line in lines if re.match(r"^GEMINI_(API_KEY|CHAT_MODEL)=", line))


def item(role, text):
    return {"role": role, "parts": [{"text": text}]}


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--live", action="store_true", required=True)
    args.parse_args()
    values = local_config()
    key = values.get("GEMINI_API_KEY", "").strip()
    if not key or not re.fullmatch(r"[A-Za-z0-9_-]+", key) or values.get("GEMINI_CHAT_MODEL", "").strip() != MODEL:
        raise SystemExit("Configure the local API key and exact supported smoke model first.")
    endpoint = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args):
            return None

    opener = urllib.request.build_opener(NoRedirect())
    opening = item("user", "Open the role-play in character and ask one relevant question.")
    history = [opening, item("model", "Welcome! What would you like to order?"),
               item("user", "I would like vegetable soup, please.")]
    cases = [
        ("opening-a2", "A2", [opening]),
        ("reply-a2", "A2", history),
        ("hint-a2", "A2", history + [item("model", "Would you like a drink?"),
                                     item("user", "As a coach, give one short English sentence starter.")]),
        ("opening-b1", "B1", [opening]),
    ]
    report = {"recordedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "model": MODEL, "maxRequests": len(cases), "cases": []}
    for name, level, contents in cases:
        task = "For this hint respond as a coach, outside the dialogue." if name.startswith("hint") else ""
        request_data = {"systemInstruction": {"parts": [{"text": f"{INSTRUCTION} Level: {level}. {task}"}]},
                        "contents": contents,
                        "generationConfig": {"candidateCount": 1, "maxOutputTokens": 512, "temperature": 0.6}}
        request = urllib.request.Request(endpoint, data=json.dumps(request_data).encode(),
                                         headers={"Content-Type": "application/json", "x-goog-api-key": key}, method="POST")
        started = time.monotonic()
        result = {"case": name, "level": level}
        try:
            with opener.open(request, timeout=30) as response:
                raw = response.read(65537)
            if len(raw) > 65536:
                raise ValueError("response too large")
            data = json.loads(raw)
            candidate = data["candidates"][0]
            text = "".join(part["text"] for part in candidate["content"]["parts"] if not part.get("thought")).strip()
            if candidate["finishReason"] != "STOP" or not 0 < len(text) <= 2000 or key in text:
                raise ValueError("invalid text output")
            usage = data.get("usageMetadata", {})
            result.update(status="PASS", inputTokens=usage.get("promptTokenCount"), outputTokens=usage.get("candidatesTokenCount"),
                          thoughtTokens=usage.get("thoughtsTokenCount"), sample=text)
        except urllib.error.HTTPError as failure:
            result.update(status="HTTP_ERROR", httpStatus=failure.code)
        except urllib.error.URLError:
            result.update(status="NETWORK_ERROR")
        except (TimeoutError, KeyError, IndexError, ValueError, TypeError):
            result.update(status="INVALID_OR_TIMED_OUT")
        result["latencyMs"] = round((time.monotonic() - started) * 1000)
        report["cases"].append(result)
        if result["status"] != "PASS":
            break
    path = ROOT / ".runtime" / "phase3" / "gemini-live.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True))
    return 0 if len(report["cases"]) == 4 and all(case["status"] == "PASS" for case in report["cases"]) else 1


if __name__ == "__main__":
    raise SystemExit(main())
