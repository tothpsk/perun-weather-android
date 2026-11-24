import re
from pathlib import Path
ui = Path('app/src/main/java/sk/perun/weather/ui')
files = {}
def get(name):
    if name not in files:
        raw = (ui / name).read_bytes().decode('utf-8')
        files[name] = ['\r\n' in raw, raw.replace('\r\n', '\n')]
    return files[name]
def once(name, old, new):
    f = get(name)
    if f[1].count(old) != 1:
        raise SystemExit(f'{name}: unexpected text, nothing written: {old[:80]}')
    f[1] = f[1].replace(old, new)

# 1. Forecast, cloud cover: the title keeps the place and the size of the other titles; the (i) of the settings floats over its right end.
f = get('ForecastScreen.kt')
pat = re.compile(r'if\(mode!=FV\.Cloud\) (Text\(mode\.label,modifier=Modifier\.fillMaxWidth\(\)[^\n]*?Bold\)) else Row\(.*?\n        \}', re.S)
if len(pat.findall(f[1])) != 1:
    raise SystemExit('ForecastScreen.kt: unexpected title of the cloud cover, nothing written')
f[1] = pat.sub(lambda m: ('Box(Modifier.fillMaxWidth()) {\n            ' + m.group(1) + '\n'
    '            // The visible circle of the (i) ends at the edge of the cards: the 36 dp button reaches 9 dp beyond it and does not change the height of the title.\n'
    '            if(mode==FV.Cloud) Box(Modifier.matchParentSize(),contentAlignment=Alignment.CenterEnd) { Box(Modifier.requiredSize(36.dp).offset(x=9.dp,y=2.dp)) { SettingsInfo(uiText(R.string.cloud_median_note)) } }\n'
    '        }'), f[1])

# 2. The note about the median is the last item of the help of the cloud cover tile.
once('OverviewHelp.kt', '            HelpText(uiText(R.string.cloud_median_note))\n', '')
once('OverviewHelp.kt', '            HelpRow(Color(0xFF4B586C),uiText(R.string.ui_zamracene_829159bb),uiText(R.string.ui_viac_ako_87_5_105ed338))\n',
     '            HelpRow(Color(0xFF4B586C),uiText(R.string.ui_zamracene_829159bb),uiText(R.string.ui_viac_ako_87_5_105ed338))\n            HelpText(uiText(R.string.cloud_median_note))\n')

# 3. Widgets: the gauge for the quantity, the trend arrow for the cells of the widgets with the change of the pressure.
once('WidgetIcons.kt',
     '                    drawLine(c,Offset(6f,12f),Offset(18f,12f),1.8f,StrokeCap.Round)\n'
     '                    drawLine(c,Offset(14f,8f),Offset(18f,12f),1.8f,StrokeCap.Round)\n'
     '                    drawLine(c,Offset(14f,16f),Offset(18f,12f),1.8f,StrokeCap.Round)\n',
     '                    drawLine(c,Offset(12f,12f),Offset(16.8f,7.2f),1.8f,StrokeCap.Round)\n'
     '                    drawCircle(c,1.7f,Offset(12f,12f))\n')
w = get('WidgetIcons.kt')
w[1] = w[1].rstrip('\n') + """

/** The icon of a value in a cell of a widget: the arrow of the trend for the pressure when its change is known, otherwise the icon of the quantity. */
internal fun widgetParamIcon(key:String,color:Int,size:Int,pressureChange:Double):Bitmap =
    if(key=="pressure" && pressureChange.isFinite()) widgetPressureTrendBitmap(pressureChange,color,size) else widgetIconBitmap(key,color,size)

/** The trend of the pressure as on the Now screen (TkTrendGraphic): a ring and an arrow turned up by a rise and down by a fall, 60 degrees at 3 hPa in 3 hours. */
internal fun widgetPressureTrendBitmap(change:Double,color:Int,size:Int):Bitmap {
    val px=size.coerceAtLeast(1);val c=Color(color)
    val image=ImageBitmap(px,px)
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(image),Size(px.toFloat(),px.toFloat())) {
        scale(px/24f,px/24f,Offset.Zero) {
            drawCircle(c.copy(alpha=.6f),10.5f,Offset(12f,12f),style=Stroke(1.4f))
            val angle=(-change/3.0*60.0).coerceIn(-60.0,60.0).toFloat()
            rotate(angle,Offset(12f,12f)) { drawPath(iconPath("M5 12h14M14 7l5 5-5 5"),c,style=Stroke(2f,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
        }
    }
    return image.asAndroidBitmap()
}
"""
once('Widgets.kt', 'widgetIconBitmap(key,pal.fg,size.roundToInt().coerceAtLeast(1))', 'widgetParamIcon(key,pal.fg,size.roundToInt().coerceAtLeast(1),pressureChange)')
once('Widgets.kt', 'updated:String,pal:Palette):Pair<Bitmap,Boolean> {', 'updated:String,pal:Palette,pressureChange:Double=Double.NaN):Pair<Bitmap,Boolean> {')
once('Widgets.kt', 'a?.level,uiText(R.string.wg_updated,d.time.format(Hm)),pal)', 'a?.level,uiText(R.string.wg_updated,d.time.format(Hm)),pal,ex?.pressureChange ?: Double.NaN)')
once('Widgets.kt', 'widgetIconBitmap(if(key=="rain" && snowing(d)) "snow" else key,pal.fg,48)', 'widgetParamIcon(if(key=="rain" && snowing(d)) "snow" else key,pal.fg,48,ex?.pressureChange ?: Double.NaN)')
once('SixDayWidget.kt', 'widgetIconBitmap(if(key=="rain" && snowing) "snow" else key,fg,48)', 'widgetParamIcon(if(key=="rain" && snowing) "snow" else key,fg,48,ex?.pressureChange ?: Double.NaN)')
for name,(crlf,text) in files.items():
    (ui / name).write_bytes((text.replace('\n', '\r\n') if crlf else text).encode('utf-8'))
