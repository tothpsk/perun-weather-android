#!/usr/bin/env python3
"""Perun 1.2.1, patch 08. Now, precipitation tile: with rain and snow together the snow stands beside the rain,
after a comma, on one line. The line starts at the size of a single value and shrinks until it fits the tile.

Every pattern must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

PATH = Path("app/src/main/java/sk/perun/weather/ui/Overview.kt")
OLD = (r"ValueUnit\(u\.precipitation\.fromMillimetres\(liquid\),u\.precipitation\.liquidSymbol,tint,liquidDecimals,20\.sp\)\s*"
       r"ValueUnit\(u\.precipitation\.fromSnowCentimetres\(snow\),u\.precipitation\.snowSymbol,OverviewText,snowDecimals,16\.sp\)")
NEW = ("PrecipPair(weatherUnitNumber(u.precipitation.fromMillimetres(liquid),liquidDecimals),u.precipitation.liquidSymbol,tint,"
       "weatherUnitNumber(u.precipitation.fromSnowCentimetres(snow),snowDecimals),u.precipitation.snowSymbol)")
FUNCTION = """
// Rain and snow of the precipitation tile on one line, separated by a comma.
// The line starts at the size of a single value and shrinks until it fits the tile.
@Composable private fun PrecipPair(liquid:String,liquidUnit:String,tint:Color,snow:String,snowUnit:String) {
    val size=androidx.compose.runtime.remember(liquid,snow) { androidx.compose.runtime.mutableStateOf(20f) }
    val bold=androidx.compose.ui.text.SpanStyle(fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
    val small=androidx.compose.ui.text.SpanStyle(color=ForecastMuted,fontSize=androidx.compose.ui.unit.TextUnit(.62f,androidx.compose.ui.unit.TextUnitType.Em))
    val text=androidx.compose.ui.text.AnnotatedString.Builder().apply {
        pushStyle(bold.copy(color=tint));append(liquid);pop()
        pushStyle(small);append(" "+liquidUnit);pop()
        append(", ")
        pushStyle(bold);append(snow);pop()
        pushStyle(small);append(" "+snowUnit);pop()
    }.toAnnotatedString()
    Text(text,color=OverviewText,fontSize=size.value.sp,maxLines=1,softWrap=false,onTextLayout={ if(it.hasVisualOverflow && size.value>12f) size.value-=1f })
}
"""

if not PATH.is_file():
    print(f"Nothing was changed: {PATH} not found")
    sys.exit(1)
raw = PATH.read_bytes().decode("utf-8")
crlf = "\r\n" in raw
text = raw.replace("\r\n", "\n")
problems = []
n = len(re.findall(OLD, text))
if n != 1:
    problems.append(f"expected 1 match, found {n}: {OLD[:90]}")
if "fun PrecipPair(" in text:
    problems.append("PrecipPair is already defined")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
text = re.sub(OLD, lambda m: NEW, text).rstrip("\n") + "\n" + FUNCTION
PATH.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print(f"updated {PATH}")
