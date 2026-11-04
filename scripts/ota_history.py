"""Build complete, versionCode-indexed release notes for the Android OTA screen."""
from pathlib import Path
import re
import subprocess

EXCLUDE = ("scoped storage", "jvm", "github", "chatgpt", "openai",
           "регрессион", "тестирован", "тесты", "аппаратн",
           "ci success", "versioncode", "не проверен", "лимит 30000")
PREFIXES = ("[+] ", "[=] ", "[-] ", "[–] ")


def clean_notes(source):
    """Normalize old bullet-formatted notes without fabricating updates."""
    entries = []
    category = "="
    for raw in source.splitlines():
        line = raw.strip()
        if not line or line.startswith(("БПЛА ИНФО", "BPLA INFO")):
            continue
        lower = line.casefold()
        if lower in ("добавлено:", "улучшено:", "изменено:"):
            category = "+"
            continue
        if lower in ("важно:", "примечания:"):
            category = "="
            continue
        if lower in ("проверки:", "технические проверки:"):
            category = None
            continue
        if line.startswith(PREFIXES):
            changed = line
        elif line.startswith(("[!] ", "[i] ")):
            changed = "[=] " + line[4:]
        elif line.startswith(("• ", "- ")):
            if category is None:
                continue
            changed = "[" + category + "] " + line[2:].strip()
        else:
            continue
        if not 5 <= len(changed) <= 260:
            continue
        folded = changed.casefold()
        if any(banned in folded for banned in EXCLUDE) or "https://" in folded or "http://" in folded:
            continue
        if changed not in entries:
            entries.append(changed)
    return "\n".join(entries)


def read_historical_code(tag):
    proc = subprocess.run(["git", "show", tag + ":app/build.gradle"],
                          capture_output=True, text=True)
    if proc.returncode != 0:
        raise ValueError("Missing published tag: " + tag)
    version_code = re.search(r"\bversionCode\s*(?:=\s*)?(\d+)\b", proc.stdout)
    version_name = re.search(r"\bversionName\s*(?:=\s*)?['\"]([^'\"]+)", proc.stdout)
    if not version_code or not version_name or version_name.group(1) != tag[1:]:
        raise ValueError("Historical APK version mismatch: " + tag)
    return int(version_code.group(1))


def build_history(version, latest_code, notes, notes_dir=Path("release_notes")):
    if not re.fullmatch(r"1\.0\.\d+", version):
        raise ValueError("Unsupported OTA version: " + version)
    patch = int(version.rsplit(".", 1)[1])
    history = []
    notes_dir = Path(notes_dir)
    for n in range(patch):
        name = "1.0." + str(n)
        file = notes_dir / ("v" + name + ".txt")
        if not file.is_file():
            raise ValueError("Missing previous release notes: " + str(file))
        visible = clean_notes(file.read_text(encoding="utf-8"))
        if not visible:
            raise ValueError("Previous release has no user changes: " + name)
        history.append({"versionCode": read_historical_code("v" + name),
                        "versionName": name, "notes": visible})
    latest = clean_notes(notes)
    if not latest:
        raise ValueError("Latest release has no user-facing changes")
    history.append({"versionCode": int(latest_code),
                    "versionName": version, "notes": latest})
    codes = [e["versionCode"] for e in history]
    if codes != list(range(1, int(latest_code) + 1)):
        raise ValueError("Incomplete or duplicate versionCode history")
    if sum(len(e["notes"]) for e in history) > 1_000_000:
        raise ValueError("Cumulative changelog exceeds size limit")
    return history
