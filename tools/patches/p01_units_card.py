#!/usr/bin/env python3
"""Perun 1.2.1, patch 01. Settings, units: the units and the air quality settings share one card, with a divider between them.

Every pattern must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

PATH = Path("app/src/main/java/sk/perun/weather/ui/AppSettingsScreen.kt")
EDITS = [
    (r"\n([ \t]*)\}\n[ \t]*SettingsCategory\(stringResource\(R\.string\.settings_air_pollen\)\) \{AirQualitySettings\(\)\}\n",
     "\n\\1    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))\n"
     "\\1    Text(stringResource(R.string.settings_air_pollen),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)\n"
     "\\1    AirQualitySettings()\n"
     "\\1}\n"),
]

if not PATH.is_file():
    print(f"Nothing was changed: {PATH} not found")
    sys.exit(1)
raw = PATH.read_bytes().decode("utf-8")
crlf = "\r\n" in raw
text = raw.replace("\r\n", "\n")
problems = []
for pattern, new in EDITS:
    n = len(re.findall(pattern, text))
    if n == 1:
        text = re.sub(pattern, new, text)
    else:
        problems.append(f"expected 1 match, found {n}: {pattern[:90]}")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
PATH.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print(f"updated {PATH}")
