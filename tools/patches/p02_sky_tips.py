#!/usr/bin/env python3
"""Perun 1.2.1, patch 02. Sun and Moon: the tips of the day length change, of the Moon above the horizon
and of the phase explain the value in words, without repeating it.

Every text must be found exactly once. If anything differs, nothing is written."""
import sys
from pathlib import Path

KT = Path("app/src/main/java/sk/perun/weather/ui/SunMoonScreen.kt")
RES = Path("app/src/main/res")
EDITS = [
    ("dayChange>0->uiText(R.string.sky_day_longer, dayChangeSpan);dayChange<0->uiText(R.string.sky_day_shorter, dayChangeSpan);else->uiText(R.string.sky_day_same) }",
     "dayChange>0->uiText(if(date==today()) R.string.sky_tip_day_longer else R.string.sky_tip_day_longer_before);dayChange<0->uiText(if(date==today()) R.string.sky_tip_day_shorter else R.string.sky_tip_day_shorter_before);else->uiText(if(date==today()) R.string.sky_tip_day_same else R.string.sky_tip_day_same_before) }"),
    ("SkyTileRow(uiText(R.string.sky_moon_up, moonUpText))",
     "SkyTileRow(uiText(R.string.sky_tip_moon_up))"),
    ("SkyTileRow(SunMoonMath.phaseName(phasePoint.phase)+uiText(R.string.ui_osvetlenie_38b6bd7b)+skyNumber(phasePoint.illumination)+\" %\")",
     "SkyTileRow(uiText(R.string.sky_tip_phase, SunMoonMath.phaseName(phasePoint.phase)))"),
]
STRINGS = [
    ("sky_tip_day_longer", "Deň je dlhší než včera", "The day is longer than yesterday"),
    ("sky_tip_day_shorter", "Deň je kratší než včera", "The day is shorter than yesterday"),
    ("sky_tip_day_same", "Deň je rovnako dlhý ako včera", "The day is as long as yesterday"),
    ("sky_tip_day_longer_before", "Deň je dlhší než predchádzajúci deň", "The day is longer than the day before"),
    ("sky_tip_day_shorter_before", "Deň je kratší než predchádzajúci deň", "The day is shorter than the day before"),
    ("sky_tip_day_same_before", "Deň je rovnako dlhý ako predchádzajúci deň", "The day is as long as the day before"),
    ("sky_tip_moon_up", "Čas, počas ktorého je Mesiac nad obzorom", "Time the Moon is above the horizon"),
    ("sky_tip_phase", "%1$s, osvetlená časť disku", "%1$s, lit share of the disc"),
]


def xml(index):
    rows = "".join(f'    <string name="{s[0]}">{s[index]}</string>\n' for s in STRINGS)
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + rows + "</resources>\n"


problems = []
targets = {RES / "values/strings_sky_tips.xml": xml(1), RES / "values-en/strings_sky_tips.xml": xml(2)}
for path in targets:
    if path.exists():
        problems.append(f"{path} already exists")
    if not path.parent.is_dir():
        problems.append(f"{path.parent} not found")
text = ""
crlf = False
if not KT.is_file():
    problems.append(f"{KT} not found")
else:
    raw = KT.read_bytes().decode("utf-8")
    crlf = "\r\n" in raw
    text = raw.replace("\r\n", "\n")
    for old, new in EDITS:
        n = text.count(old)
        if n == 1:
            text = text.replace(old, new)
        else:
            problems.append(f"expected 1 match, found {n}: {old[:90]}")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
KT.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print(f"updated {KT}")
for path, content in targets.items():
    path.write_bytes(content.encode("utf-8"))
    print(f"created {path}")
