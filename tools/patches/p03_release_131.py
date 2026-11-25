from pathlib import Path
def sub(path,old,new):
    p=Path(path); s=p.read_text(encoding='utf-8')
    if s.count(old)!=1: raise SystemExit(f'{path}: expected one match for {old[:40]!r}, got {s.count(old)}')
    p.write_text(s.replace(old,new),encoding='utf-8')
sub('app/build.gradle.kts','versionName = "1.3.0"','versionName = "1.3.1"')
sub('README.md','**Current version:** 1.3.0','**Current version:** 1.3.1')
sub('README.md','- Meteogram: a chart of the coming days.\n','- Meteogram: a chart of the coming days, with precipitation bars coloured by kind. A tap opens Now for the place of the widget.\n')
sub('README.md','listed with their severity, issuer and period of validity.','listed with their severity, issuer and period of validity. The chart marks every alert with the icon of its hazard, and the name of the issuer links to the page of the warning when the source gives one.')
notes='\n'.join([
'## [1.3.1] - 2026-11-25',
'',
'### Added',
'',
'- Meteogram widget: the precipitation bars are coloured by the kind of precipitation: rain, showers, snow and rain with snow.',
'- Alerts, details of a MeteoAlarm warning: the issuer is shown. Its name is a link to the page of the warning when MeteoAlarm gives one, and plain text when it does not.',
'- Now, wave tile: the direction of the waves shows the degrees beside the point of the compass, for example NW \u00b7 322\u00b0.',
'',
'### Changed',
'',
'- Alerts, chart: the icon of the hazard stands above a bar instead of text.',
'- Meteogram widget: a tap opens Now for the place of the widget, not Forecast. The refresh control is separate.',
'',
'### Fixed',
'',
'- Alert icons: an alert of the shared snow and ice group is drawn by its own name. An icing warning no longer gets the snowflake because its general headline says \"Snow-ice\". A headline alone does not decide between snow and ice; it is used only when the name of the alert does not say.',
'',
''])
sub('CHANGELOG.md','## [1.3.0] - 2026-11-24\n',notes+'## [1.3.0] - 2026-11-24\n')
