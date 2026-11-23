#!/usr/bin/env python3
"""Perun 1.2.1, patch 09. Forecast, expanded day: the lines and bars of a row start at the bottom of the row, as in
the Day by hours widget. Only the precipitation row of a day with snow keeps the room under the bars for the marks.
Forecast, list of days: the footer names what was filled in from another model, the precipitation probability
from Best match and the UV index from GFS.

Every text must be found as often as stated. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

PATH = Path("app/src/main/java/sk/perun/weather/ui/ForecastScreen.kt")
RES = "app/src/main/res/"
UV = "if(filled>0) note+=uiText(R.string.ui_chybajuce_uv_doplnene_z_gfs_bez_predlzenia_hlavneho_horizo_649c0e6f)"
# (old, new, old is a regular expression, every occurrence is replaced and at least one must exist)
EDITS = [
    ("private data class ForecastBundle(val days:List<ForecastDay>,val zone:ZoneId,val note:String)",
     "private data class ForecastBundle(val days:List<ForecastDay>,val zone:ZoneId,val note:String,val filledNote:String?=null)", False, False),
    ("var note=uiText(R.string.ui_rozsah_podla_dostupnej_teplotnej_predpovede_zvoleneho_mode_b54cf840)",
     "var note=uiText(R.string.ui_rozsah_podla_dostupnej_teplotnej_predpovede_zvoleneho_mode_b54cf840);var fromBestMatch:String?=null;var fromGfs:String?=null", False, False),
    (UV, "if(filled>0) { " + UV[len("if(filled>0) "):] + ";fromGfs=uiText(R.string.fallback_gfs_note,FV.Uv.label) }", False, False),
    ("if(filledProb>0) note+=uiText(R.string.forecast_prob_filled)",
     "if(filledProb>0) { note+=uiText(R.string.forecast_prob_filled);fromBestMatch=uiText(R.string.long_fallback_note,FV.Prob.label) }", False, False),
    ("ForecastBundle(days,z,note)", 'ForecastBundle(days,z,note,listOfNotNull(fromBestMatch,fromGfs).joinToString("\\n").ifEmpty { null })', False, False),
    ("item { AttributionFooter() }", "item { AttributionFooter(bundle?.filledNote) }", False, False),
    (r"([ \t]*)fun y\(a:Double\)=",
     lambda m: m.group(1) + "// The scale starts at the bottom of the row; the precipitation row of a day with snow keeps the room for the marks under the bars.\n"
     + m.group(1) + 'val base=(if(v==FV.Rain && day.hours.any { it.v("snowfall")>0 }) 15 else 3).dp.toPx()\n'
     + m.group(1) + "fun y(a:Double)=", True, False),
    (r"y1-15\.dp\.toPx\(\)", "y1-base", True, True),
    (r"rh-31\.dp\.toPx\(\)", "rh-base-16.dp.toPx()", True, True),
]


def xml(text):
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="fallback_gfs_note">"' + text + '"</string>\n</resources>\n'


NEW = {RES + "values/strings_fallback_gfs.xml": xml("Doplnené z GFS: %1$s"), RES + "values-en/strings_fallback_gfs.xml": xml("Filled in from GFS: %1$s")}

problems = []
for name in NEW:
    if Path(name).exists():
        problems.append(f"{name} already exists")
text = ""
crlf = False
if not PATH.is_file():
    problems.append(f"{PATH} not found")
else:
    raw = PATH.read_bytes().decode("utf-8")
    crlf = "\r\n" in raw
    text = raw.replace("\r\n", "\n")
    for old, new, regex, every in EDITS:
        pattern = old if regex else re.escape(old)
        n = len(re.findall(pattern, text))
        if n == 1 or (every and n > 1):
            text = re.sub(pattern, new if callable(new) else (lambda m, s=new: s), text)
            print(f"{n} x {old[:70]}")
        else:
            problems.append(f"expected {'at least 1 match' if every else '1 match'}, found {n}: {old[:90]}")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
PATH.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print(f"updated {PATH}")
for name, content in NEW.items():
    Path(name).write_bytes(content.encode("utf-8"))
    print(f"created {name}")
