from pathlib import Path
p = Path('app/src/main/java/sk/perun/weather/ui/ForecastScreen.kt')
raw = p.read_bytes().decode('utf-8')
crlf = '\r\n' in raw
text = raw.replace('\r\n', '\n')
old = ('drawLine(color,Offset(6f,12f),Offset(18f,12f),1.8f,StrokeCap.Round);'
       'drawLine(color,Offset(14f,8f),Offset(18f,12f),1.8f,StrokeCap.Round);'
       'drawLine(color,Offset(14f,16f),Offset(18f,12f),1.8f,StrokeCap.Round)')
if text.count(old) != 1:
    raise SystemExit('ForecastScreen.kt: unexpected pressure mode glyph, nothing written')
text = text.replace(old, 'drawLine(color,Offset(12f,12f),Offset(16.8f,7.2f),1.8f,StrokeCap.Round);drawCircle(color,1.7f,Offset(12f,12f))')
p.write_bytes((text.replace('\n', '\r\n') if crlf else text).encode('utf-8'))
