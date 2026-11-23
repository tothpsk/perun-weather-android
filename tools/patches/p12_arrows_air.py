#!/usr/bin/env python3
"""Perun 1.2.1, patch 12. Now, wind tile and sea tile of the waves: an arrow instead of the compass, drawn as the
arrow of the wind direction in the hourly chart of an expanded day. It is as tall as the two rows of text and has the
colour of the dot of the tile: the Beaufort colour of the wind, the colour of the height of the waves. Air quality and
pollen, expanded day: the lines of a row start at the bottom of the row, as on Forecast.

Every text must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

UI = "app/src/main/java/sk/perun/weather/ui/"
KIT = UI + "OverviewTileKit.kt"
NOW = UI + "Overview.kt"
SEA = UI + "MarineOverview.kt"
AIR = UI + "AirQualityScreen.kt"
# (file, old, new, old is a regular expression)
EDITS = [
    (NOW, "WindCompass(w.direction)", "WindArrow(w.direction,windBeaufortColor(w.wind))", False),
    (NOW, r"// Compass of the wind tile\..*?\n}\n",
     "// Arrow of the wind tile: as tall as the two rows of text, it points where the wind blows.\n"
     "@Composable private fun WindArrow(direction:Double,color:Color) {\n"
     "    TkDirectionArrow(direction,color,rememberTileTextBox().height,uiText(R.string.ui_vietor_fuka_zo_smeru_value_sipka_ukazuje_kam_fuka_sever_je_983405a4, cardinal(direction)))\n"
     "}\n", True),
    # The compass keeps its only form again.
    (KIT, "// northY and northSize: the centre and the size of the N in the 100 unit box of the compass.\n"
     "@Composable internal fun TkCompass(direction:Double,color:Color,modifier:Modifier=Modifier.size(62.dp),northY:Float=8f,northSize:Float=16f) {",
     "@Composable internal fun TkCompass(direction:Double,color:Color,modifier:Modifier=Modifier.size(62.dp)) {", False),
    (KIT, "tkText(measurer,uiText(R.string.ui_s_8de0b3c4),50f*k,northY*k,(northSize*k).toSp(),Color(0xFFE8EAF0),FontWeight.Bold)",
     "tkText(measurer,uiText(R.string.ui_s_8de0b3c4),50f*k,8f*k,(16f*k).toSp(),Color(0xFFE8EAF0),FontWeight.Bold)", False),
    # The other sea graphics stand 3 dp above the bottom of the text; the arrow stands on the baseline of the second row.
    (SEA, 'TkFlush(62.dp,62.dp,8.06.dp,3.1.dp) {TkCompass(d.at("wave_direction",i),levelTone,Modifier.size(62.dp))}',
     '{val box=rememberTileTextBox();Box(Modifier.padding(bottom=(box.descent-3.dp).coerceAtLeast(0.dp))) {TkDirectionArrow(d.at("wave_direction",i),levelTone,box.height,uiText(R.string.ui_smer_sirenia_e0efaa7c)+cardinal(bearing))}}', False),
    (AIR, "val span=(rh-31.dp.toPx()).coerceAtLeast(1f)",
     "// The scale starts at the bottom of the row, as in the expanded day of Forecast.\n"
     "                val span=(rh-19.dp.toPx()).coerceAtLeast(1f)", False),
    (AIR, "fun ya(v:Double)=y0+rh-15.dp.toPx()-(v/high*span).toFloat()", "fun ya(v:Double)=y0+rh-3.dp.toPx()-(v/high*span).toFloat()", False),
]
FUNCTIONS = """
// The room beside the two rows of text of a tile. height: from the top of the digits of the value to the baseline of
// the second row. descent: from that baseline to the bottom of the text.
internal class TileTextBox(val height:Dp,val descent:Dp)

@Composable internal fun rememberTileTextBox():TileTextBox {
    val measurer=rememberTextMeasurer()
    val density=androidx.compose.ui.platform.LocalDensity.current
    val base=LocalTextStyle.current
    return remember(base,density) {
        val value=measurer.measure("0",base.merge(TextStyle(fontSize=24.sp,fontWeight=FontWeight.SemiBold)))
        val sub=measurer.measure("0",base.merge(TextStyle(fontSize=12.sp)))
        // The digits are about .71 of the font size tall; 2 dp is the gap between the two rows.
        with(density) { TileTextBox((value.size.height-value.firstBaseline+24.sp.toPx()*.71f+2.dp.toPx()+sub.firstBaseline).toDp(),(sub.size.height-sub.firstBaseline).toDp()) }
    }
}

// Arrow of a direction with the shape of the arrow in the hourly chart of an expanded day. direction is where the wind
// or the waves come from; the arrow points where they go, north is up.
@Composable internal fun TkDirectionArrow(direction:Double,color:Color,length:Dp,description:String) {
    Canvas(Modifier.size(length).semantics { contentDescription=description }) {
        val angle=(direction+180)*PI/180
        val v=Offset(sin(angle).toFloat(),-cos(angle).toFloat())
        val stroke=size.width*.075f
        val half=(size.width-stroke)/2
        val tip=center+v*half
        val base=tip-v*(half*4f/7f)
        val side=Offset(-v.y,v.x)*(half*3f/7f)
        drawLine(color,center-v*half,tip,stroke,StrokeCap.Round)
        drawLine(color,tip,base+side,stroke,StrokeCap.Round)
        drawLine(color,tip,base-side,stroke,StrokeCap.Round)
    }
}
"""

problems = []
texts = {}
crlf = {}
for name, old, new, regex in EDITS:
    if name not in texts:
        if not Path(name).is_file():
            problems.append(f"{name} not found")
            continue
        raw = Path(name).read_bytes().decode("utf-8")
        crlf[name] = "\r\n" in raw
        texts[name] = raw.replace("\r\n", "\n")
    pattern = old if regex else re.escape(old)
    n = len(re.findall(pattern, texts[name], flags=re.S))
    if n == 1:
        texts[name] = re.sub(pattern, lambda m, s=new: s, texts[name], flags=re.S)
    else:
        problems.append(f"{name}: expected 1 match, found {n}: {old[:90]}")
if KIT in texts and ("fun TkDirectionArrow(" in texts[KIT] or "class TileTextBox(" in texts[KIT]):
    problems.append("TkDirectionArrow or TileTextBox is already defined")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
texts[KIT] = texts[KIT].rstrip("\n") + "\n" + FUNCTIONS
for name, text in texts.items():
    Path(name).write_bytes((text.replace("\n", "\r\n") if crlf[name] else text).encode("utf-8"))
    print(f"updated {name}")
