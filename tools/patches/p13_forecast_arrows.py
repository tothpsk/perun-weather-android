from pathlib import Path
p = Path('app/src/main/java/sk/perun/weather/ui/ForecastScreen.kt')
s = p.read_bytes()
for old, new in [
    ('FV.Wind->TkCompass(direction,ForecastGreen,Modifier.size(52.dp))', 'FV.Wind->if(direction.isFinite() && wind>0) TkDirectionArrow(direction,windBeaufortColor(wind),38.dp,uiText(R.string.ui_vietor_fuka_zo_smeru_value_sipka_ukazuje_kam_fuka_sever_je_983405a4,cardinal(direction))) else Text("—",color=ForecastMuted)'),
    ('FV.Direction->TkCompass(0.0,Color.White,Modifier.size(26.dp))', 'FV.Direction->TkDirectionArrow(225.0,Color.White,20.dp,row.label)'),
]:
    if s.count(old.encode()) != 1:
        raise SystemExit('Unexpected ForecastScreen.kt; nothing written: ' + old)
    s = s.replace(old.encode(), new.encode())
p.write_bytes(s)
