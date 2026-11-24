from pathlib import Path
p = Path('app/src/main/java/sk/perun/weather/ui/DayWidget.kt')
s = p.read_bytes()
a = b'"direction"->compassGlyph(Color.White,(gs*1.15f).roundToInt())'
b = b'"direction"->dayDirectionRowArrow(gs)'
if s.count(a) != 1 or b'dayDirectionRowArrow(' in s:
    raise SystemExit('Unexpected DayWidget.kt; nothing written')
f = '''
private fun dayDirectionRowArrow(size:Int):android.graphics.Bitmap {
    val n=size.coerceAtLeast(1)
    val bitmap=android.graphics.Bitmap.createBitmap(n,n,android.graphics.Bitmap.Config.ARGB_8888)
    val canvas=android.graphics.Canvas(bitmap)
    canvas.scale(n/20f,n/20f)
    canvas.rotate(45f,10f,10f)
    val pen=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color=android.graphics.Color.WHITE;strokeWidth=1.5f;strokeCap=android.graphics.Paint.Cap.ROUND
    }
    canvas.drawLine(10f,19.25f,10f,.75f,pen)
    canvas.drawLine(10f,.75f,6.04f,6.04f,pen)
    canvas.drawLine(10f,.75f,13.96f,6.04f,pen)
    return bitmap
}
'''.encode()
p.write_bytes(s.replace(a,b).rstrip(b'\r\n') + (b'\r\n'+f.replace(b'\n',b'\r\n') if b'\r\n' in s else b'\n'+f))
