#!/usr/bin/env python3
"""Perun 1.2.1, patch 10. Now, wind tile: the compass no longer makes the tile taller than its two rows of text,
so the rows and the picture sit as far from the bottom as in the other tiles. Now, precipitation tile: rain with
snow on one line uses the size of the values of the other tiles (24 sp, units 13 sp) before it shrinks to fit.

Every text must be found exactly once. If anything differs, nothing is written."""
import sys
from pathlib import Path

PATH = Path("app/src/main/java/sk/perun/weather/ui/Overview.kt")
COMPASS = "TkFlush(62.dp,62.dp,62.dp.inset(13f,100f),62.dp.inset(5f,100f)) { TkCompass(w.direction,ForecastGreen,Modifier.size(62.dp)) }"
EDITS = [
    # The compass is taller than the two rows of text. It keeps its size and its bottom line and reaches up beside the title instead.
    (COMPASS, "Box(Modifier.height(40.dp).wrapContentHeight(Alignment.Bottom,unbounded=true)) { " + COMPASS + " }"),
    ("androidx.compose.runtime.mutableStateOf(20f) }", "androidx.compose.runtime.mutableStateOf(24f) }"),
    ("androidx.compose.ui.unit.TextUnit(.62f,androidx.compose.ui.unit.TextUnitType.Em)", "androidx.compose.ui.unit.TextUnit(.54f,androidx.compose.ui.unit.TextUnitType.Em)"),
]

if not PATH.is_file():
    print(f"Nothing was changed: {PATH} not found")
    sys.exit(1)
raw = PATH.read_bytes().decode("utf-8")
crlf = "\r\n" in raw
text = raw.replace("\r\n", "\n")
problems = []
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
PATH.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print(f"updated {PATH}")
