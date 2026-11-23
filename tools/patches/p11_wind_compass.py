#!/usr/bin/env python3
"""Perun 1.2.1, patch 11. Now, wind tile: the circle of the compass is as tall as the two rows of text. Its top is
level with the top of the digits of the wind speed, its bottom stays on the baseline of the second row, and the N
sits close above it.

Every text must be found exactly once. If anything differs, nothing is written."""
import sys
from pathlib import Path

UI = "app/src/main/java/sk/perun/weather/ui/"
EDITS = [
    (UI + "Overview.kt",
     "Box(Modifier.height(40.dp).wrapContentHeight(Alignment.Bottom,unbounded=true)) { TkFlush(62.dp,62.dp,62.dp.inset(13f,100f),62.dp.inset(5f,100f)) { TkCompass(w.direction,ForecastGreen,Modifier.size(62.dp)) } }",
     "WindCompass(w.direction)"),
    (UI + "OverviewTileKit.kt",
     "@Composable internal fun TkCompass(direction:Double,color:Color,modifier:Modifier=Modifier.size(62.dp)) {",
     "// northY and northSize: the centre and the size of the N in the 100 unit box of the compass.\n"
     "@Composable internal fun TkCompass(direction:Double,color:Color,modifier:Modifier=Modifier.size(62.dp),northY:Float=8f,northSize:Float=16f) {"),
    (UI + "OverviewTileKit.kt",
     "tkText(measurer,uiText(R.string.ui_s_8de0b3c4),50f*k,8f*k,(16f*k).toSp(),Color(0xFFE8EAF0),FontWeight.Bold)",
     "tkText(measurer,uiText(R.string.ui_s_8de0b3c4),50f*k,northY*k,(northSize*k).toSp(),Color(0xFFE8EAF0),FontWeight.Bold)"),
]
FUNCTION = """
// Compass of the wind tile. The circle is as tall as the two rows of text: from the top of the digits of the value
// to the baseline of the second row. The N stands above it and does not make the tile taller.
@Composable private fun WindCompass(direction:Double) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val base=LocalTextStyle.current
    val circle=remember(base,density) {
        val value=measurer.measure("0",base.merge(TextStyle(fontSize=24.sp,fontWeight=FontWeight.SemiBold)))
        val sub=measurer.measure("0",base.merge(TextStyle(fontSize=12.sp)))
        // The digits are about .71 of the font size tall; 2 dp is the gap between the two rows.
        with(density) { (value.size.height-value.firstBaseline+24.sp.toPx()*.71f+2.dp.toPx()+sub.firstBaseline).toDp() }
    }
    // The circle with its stroke takes .74 of the side of the compass.
    val side=circle/.74f
    Box(Modifier.height(circle).wrapContentHeight(Alignment.Bottom,unbounded=true)) {
        TkFlush(side,side,side.inset(13f,100f),side.inset(5f,100f)) { TkCompass(direction,ForecastGreen,Modifier.size(side),12f,18f) }
    }
}
"""
APPEND = UI + "Overview.kt"

problems = []
texts = {}
crlf = {}
for name, old, new in EDITS:
    if name not in texts:
        if not Path(name).is_file():
            problems.append(f"{name} not found")
            continue
        raw = Path(name).read_bytes().decode("utf-8")
        crlf[name] = "\r\n" in raw
        texts[name] = raw.replace("\r\n", "\n")
    n = texts[name].count(old)
    if n == 1:
        texts[name] = texts[name].replace(old, new)
    else:
        problems.append(f"{name}: expected 1 match, found {n}: {old[:90]}")
if APPEND in texts and "fun WindCompass(" in texts[APPEND]:
    problems.append("WindCompass is already defined")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
texts[APPEND] = texts[APPEND].rstrip("\n") + "\n" + FUNCTION
for name, text in texts.items():
    Path(name).write_bytes((text.replace("\n", "\r\n") if crlf[name] else text).encode("utf-8"))
    print(f"updated {name}")
