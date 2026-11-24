from pathlib import Path
ui = Path('app/src/main/java/sk/perun/weather/ui')
edits = {}
def load(name):
    p = ui / name
    raw = p.read_bytes().decode('utf-8')
    return p, raw, '\r\n' in raw, raw.replace('\r\n', '\n')
def need(text, old, name):
    if text.count(old) != 1:
        raise SystemExit(f'{name}: unexpected text, nothing written: {old[:70]}')

# Expanded day of Forecast: the row of the pressure gets a gauge (circle with a needle) instead of the arrow.
p, raw, crlf, t = load('ForecastScreen.kt')
old = 'FV.Dew->DewPointGlyph(Color.White,22.dp)'
need(t, old, 'ForecastScreen.kt')
t = t.replace(old, old + '\n            FV.Pressure->PressureGaugeGlyph(Color.White,22.dp)')
t = t.rstrip('\n') + """

// The row icon of the pressure: a gauge, a circle with a needle and a hub. The arrow of the pressure stays for the trend.
@Composable internal fun PressureGaugeGlyph(color:Color,dimension:androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(dimension)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            drawCircle(color,10f,Offset(12f,12f),style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawLine(color,Offset(12f,12f),Offset(16.8f,7.2f),1.8f,StrokeCap.Round)
            drawCircle(color,1.7f,Offset(12f,12f))
        }
    }
}
"""
edits[p] = (t, crlf)

# Day by hours widget: the same gauge.
p, raw, crlf, t = load('DayWidget.kt')
old = ('            drawLine(color,Offset(6f,12f),Offset(18f,12f),1.8f,StrokeCap.Round)\n'
       '            drawLine(color,Offset(14f,8f),Offset(18f,12f),1.8f,StrokeCap.Round)\n'
       '            drawLine(color,Offset(14f,16f),Offset(18f,12f),1.8f,StrokeCap.Round)\n')
need(t, old, 'DayWidget.kt')
t = t.replace(old, '            drawLine(color,Offset(12f,12f),Offset(16.8f,7.2f),1.8f,StrokeCap.Round)\n            drawCircle(color,1.7f,Offset(12f,12f))\n')
edits[p] = (t, crlf)
for p, (t, crlf) in edits.items():
    p.write_bytes((t.replace('\n', '\r\n') if crlf else t).encode('utf-8'))
