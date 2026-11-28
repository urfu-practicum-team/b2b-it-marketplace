import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    frontend = json.loads(Path("frontend/coverage/coverage-summary.json").read_text())
    lines = frontend["total"]["lines"]
    backend = ET.parse("backend/target/site/jacoco/jacoco.xml")
    counter = backend.find("./counter[@type='LINE']")
    covered = int(counter.attrib["covered"])
    rows = [
        ("Фронтенд", lines["covered"], lines["total"]),
        ("Бэкенд", covered, covered + int(counter.attrib["missed"])),
    ]
    total_covered = sum(row[1] for row in rows)
    total_lines = sum(row[2] for row in rows)
    rows.append(("Итого", total_covered, total_lines))
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        print("| Часть | Покрытые строки | Исполняемые строки | Покрытие |", file=summary)
        print("| --- | ---: | ---: | ---: |", file=summary)
        for name, hits, total in rows:
            percent = f"{100 * hits / total:.2f}%" if total else "—"
            print(f"| {name} | {hits} | {total} | {percent} |", file=summary)
    if not total_lines or total_covered * 100 < total_lines * 70:
        raise SystemExit("Combined line coverage must be at least 70%")


if __name__ == "__main__":
    main()
