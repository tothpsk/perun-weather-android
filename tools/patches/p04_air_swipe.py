#!/usr/bin/env python3
"""Perun 1.2.1, patch 04. Air quality and pollen, expanded day: a swipe to the left or right over the header
opens the next or the previous day, as on Forecast. The tip of a pollen row names the plant without the word Pollen.

Every text must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

PATH = Path("app/src/main/java/sk/perun/weather/ui/AirQualityScreen.kt")
# (old, new, old is a regular expression)
EDITS = [
    ("AqRow(it.key,it.name,uiText(R.string.ui_pel_9ee1ac34)+it.name,AqKind.Pollen)",
     "AqRow(it.key,it.name,it.name,AqKind.Pollen)", False),
    ("@Composable private fun AqExpandedDay(day:AqDay,d:AirQualityData,mode:AqMode,air:AirPreferences,location:Location,onClose:()->Unit) {",
     "@Composable private fun AqExpandedDay(day:AqDay,d:AirQualityData,mode:AqMode,air:AirPreferences,location:Location,onStep:(Int)->Unit,onClose:()->Unit) {\n"
     "    val step by rememberUpdatedState(onStep)", False),
    (r"Row\(Modifier\.fillMaxWidth\(\),verticalAlignment=Alignment\.CenterVertically\) \{(?=\s*AqDayTitle\(day\.date,d\.zone,true,)",
     "// A swipe over the header opens the next day (to the left) or the previous one (to the right).\n"
     "            Row(Modifier.fillMaxWidth().pointerInput(day.date) { var total=0f;detectHorizontalDragGestures(onDragStart={ total=0f },onDragEnd={ if(total<-40.dp.toPx()) step(1) else if(total>40.dp.toPx()) step(-1) }) { c,amount->c.consume();total+=amount } },verticalAlignment=Alignment.CenterVertically) {", True),
    ("AqExpandedDay(expanded,d,mode,air,location) { open=null }",
     "AqExpandedDay(expanded,d,mode,air,location,{ step->days.getOrNull(days.indexOf(expanded)+step)?.let { open=it.date } }) { open=null }", False),
]

if not PATH.is_file():
    print(f"Nothing was changed: {PATH} not found")
    sys.exit(1)
raw = PATH.read_bytes().decode("utf-8")
crlf = "\r\n" in raw
text = raw.replace("\r\n", "\n")
problems = []
for old, new, regex in EDITS:
    pattern = old if regex else re.escape(old)
    n = len(re.findall(pattern, text))
    if n == 1:
        text = re.sub(pattern, lambda m: new, text)
    else:
        problems.append(f"expected 1 match, found {n}: {old[:90]}")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
PATH.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print(f"updated {PATH}")
