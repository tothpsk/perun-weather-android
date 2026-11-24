from pathlib import Path
ui = Path('app/src/main/java/sk/perun/weather/ui')
changes = {}
for name, old, new in [
    ('Overview.kt', r'+"\n"+uiText(R.string.cloud_median_note),"cloud"', ',"cloud"'),
    ('OverviewHelp.kt', 'HelpText(uiText(R.string.ui_celkova_modelova_oblacnost_v_percentach_oblak_sa_naplna_zd_83740492))', 'HelpText(uiText(R.string.ui_celkova_modelova_oblacnost_v_percentach_oblak_sa_naplna_zd_83740492))\n            HelpText(uiText(R.string.cloud_median_note))'),
]:
    p = ui / name
    data = p.read_bytes()
    if data.count(old.encode()) != 1:
        raise SystemExit(f'{name}: unexpected cloud help; nothing written')
    changes[p] = data.replace(old.encode(), new.replace('\n', '\r\n' if b'\r\n' in data else '\n').encode())
for p, data in changes.items():
    p.write_bytes(data)
