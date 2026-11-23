#!/usr/bin/env python3
"""Perun 1.2.1, patch 05. Sun and Moon: the tip of the phase row is the name of the phase only.
Now, thunderstorm tile: the help explains the dots of the storm cells on the radar and their colours.

Every text must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

UI = "app/src/main/java/sk/perun/weather/ui/"
RES = "app/src/main/res/"
LIGHTNING = "HelpRow(Color(0xFFFF7043),uiText(R.string.ui_blesky_do_25_km_ce2c4ebd),uiText(R.string.ui_burka_je_pri_lokalite_17b2f702))"
PAD = "\n                        "
CELLS = [("0xFF10E81D", "weak"), ("0xFFFFE80D", "moderate"), ("0xFFFF8B00", "heavy"), ("0xFFEF0F02", "intense"), ("0xFFE400B1", "extreme")]
# (file, old, new, old is a regular expression)
EDITS = [
    (UI + "SunMoonScreen.kt",
     "SkyTileRow(uiText(R.string.sky_tip_phase, SunMoonMath.phaseName(phasePoint.phase)))",
     "SkyTileRow(SunMoonMath.phaseName(phasePoint.phase))", False),
    (RES + "values/strings_sky_tips.xml", r'[ \t]*<string name="sky_tip_phase">[^\n]*</string>\n', "", True),
    (RES + "values-en/strings_sky_tips.xml", r'[ \t]*<string name="sky_tip_phase">[^\n]*</string>\n', "", True),
    (UI + "OverviewNowcast.kt", LIGHTNING,
     LIGHTNING + PAD + "// The dots of the storm cells on the radar take the colour of the radar palette for their highest reflectivity."
     + PAD + "HelpText(uiText(R.string.storm_help_cells))"
     + "".join(PAD + f"HelpRow(Color({c}),uiText(R.string.storm_cell_{k}),uiText(R.string.storm_cell_{k}_note))" for c, k in CELLS), False),
]
STRINGS = [
    ("storm_help_cells",
     "Bodky na radare sú búrkové bunky; väčšia je bunka, ktorú dlaždica opisuje. Farba ukazuje najvyššiu radarovú odrazivosť bunky. Červená bodka s bielym okrajom v strede je vaša poloha.",
     "The dots on the radar are storm cells; the larger one is the cell that the tile describes. The colour shows the highest radar reflectivity of the cell. The red dot with a white edge in the middle is your location."),
    ("storm_cell_weak", "Zelená bunka", "Green cell"),
    ("storm_cell_weak_note", "do 30 dBZ, slabé zrážky", "below 30 dBZ, light precipitation"),
    ("storm_cell_moderate", "Žltá bunka", "Yellow cell"),
    ("storm_cell_moderate_note", "30 až 39 dBZ, mierne zrážky", "30 to 39 dBZ, moderate precipitation"),
    ("storm_cell_heavy", "Oranžová bunka", "Orange cell"),
    ("storm_cell_heavy_note", "40 až 49 dBZ, silné zrážky", "40 to 49 dBZ, heavy precipitation"),
    ("storm_cell_intense", "Červená bunka", "Red cell"),
    ("storm_cell_intense_note", "50 až 55 dBZ, veľmi silné zrážky", "50 to 55 dBZ, very heavy precipitation"),
    ("storm_cell_extreme", "Fialová bunka", "Purple cell"),
    ("storm_cell_extreme_note", "nad 55 dBZ, mimoriadne silné zrážky", "above 55 dBZ, extreme precipitation"),
]


def xml(index):
    rows = "".join(f'    <string name="{s[0]}" formatted="false">"{s[index]}"</string>\n' for s in STRINGS)
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + rows + "</resources>\n"


NEW = {RES + "values/strings_storm_cells.xml": xml(1), RES + "values-en/strings_storm_cells.xml": xml(2)}

problems = []
texts = {}
crlf = {}
for name in NEW:
    if Path(name).exists():
        problems.append(f"{name} already exists")
for name, old, new, regex in EDITS:
    if name not in texts:
        if not Path(name).is_file():
            problems.append(f"{name} not found")
            continue
        raw = Path(name).read_bytes().decode("utf-8")
        crlf[name] = "\r\n" in raw
        texts[name] = raw.replace("\r\n", "\n")
    pattern = old if regex else re.escape(old)
    n = len(re.findall(pattern, texts[name]))
    if n == 1:
        texts[name] = re.sub(pattern, lambda m: new, texts[name])
    else:
        problems.append(f"{name}: expected 1 match, found {n}: {old[:90]}")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
for name, text in texts.items():
    Path(name).write_bytes((text.replace("\n", "\r\n") if crlf[name] else text).encode("utf-8"))
    print(f"updated {name}")
for name, content in NEW.items():
    Path(name).write_bytes(content.encode("utf-8"))
    print(f"created {name}")
