# Changelog

All notable changes to Perun Weather for Android. The format follows [Keep a Changelog](https://keepachangelog.com/) and versions follow semantic versioning.

## [1.1.0] - 2026-11-20

### Added

- Day by hours widget: the hours of one day as a chart with the rows of the expanded day on Forecast; the rows are chosen in the widget settings. Arrows switch the day, a line marks the current time and the header shows the official alert valid now. A tap opens Forecast with the shown day expanded.
- 6 days widget.
- Air and pollen widget: the air quality index or the pollen as the main value, chosen in the widget settings, for sizes from one cell up. Small sizes show a gauge with the rating and the place; wider ones add pollutants or the strongest kinds of pollen; the large sizes add a 24-hour band, the other value under it and up to six pollutants or kinds of pollen. A tap opens Air quality, on the pollen mode for a pollen widget.
- Sun and Moon widget: the chosen body for sizes from one cell to five cells by two rows, with the symbols of the Sun and Moon screen. A tap opens that screen.
- Meteogram widget: six more quantities.

### Changed

- Widgets draw the weather symbols and the quantity icons of the app.
- Compact widget: separate layouts for one row, two cells and three cells, a bold place with the alert beside it, a refresh button where it fits and the time of the update in the wide form.
- Meteogram widget: value axes with icons, a faint grid, days on the time axis and a cloud band that is always visible; usable at two cells.
- Wide widgets show a fourth value.
- Widget settings: unified widget names, the new widgets in the list, and the note about adding a widget behind the (i) button.

## [1.0.6] - 2026-11-17

### Added

- Locations: the list of saved places shows a mark in front of the remove button for every place that has an official alert. It uses the icon and the colour of the alert on Now, and shows +N when the place has more alerts. The list loads the alerts of the sources chosen for each place when it opens, without local estimates; when a source fails, the place has no mark.
- Sun and Moon: the change of the day length and the time the Moon is above the horizon.
- MeteoAlarm: a note that the alerts can be delayed against www.meteoalarm.org, and the origin of the warning area outlines in the data sources.

### Changed

- MeteoAlarm: a place gets the alerts of the warning areas it lies in. The position is checked against simplified outlines of the areas that are bundled with the app; names decide only where the position cannot, for example for a country without outlines. See NOTICE.txt in the app assets for the origin of the outlines.
- Sun and Moon: the Moon tile lists moonrise, time above the horizon, moonset, phase and trend, in this order.
- Sea level help: the same extreme markers as in the legend, and labels without the text triangles.
- The data sources line shows MeteoAlarm on the same line as Open-Meteo.

### Fixed

- A place that was saved without a country, for example when the coordinates were entered without a connection, gets its name and country when the lookup is repeated after the connection returns.

## [1.0.5] - 2026-11-15

### Added

- Now: a long press on the feels-like temperature, or on the dot in front of it, opens a help sheet with the current value on a colour scale and seven bands from freezing to hot. A tap on the dot shows the value with its band. The bands are a guide of this app, not an official standard.
- Sea level chart: a tap on a legend item hides or shows the markers of the model maximum or minimum.

### Changed

- Sea charts: the help sheet is shorter. It says that the values are modelled and explains the controls, including the Now button.
- Charts: the help of the visibility chart no longer lists bands that the chart does not show.
- Sun and Moon: a new solar noon icon, an arc above the horizon with the Sun at its top, which no longer resembles the UV icon.
- Moon help sheets on Now and on Sun and Moon: the four main phases are drawn next to their names instead of a text list. On Now they are drawn for the northern hemisphere.

### Fixed

- Now reloads after returning to the app when the shown data is older than 5 minutes. It used to keep the old values although the background refresh had already downloaded new ones.
- With sea data switched on, the sea tiles on Now appear together with the rest of the screen and no longer disappear for a moment during a refresh.
- MeteoAlarm: a location saved without a region, which could happen when it was added from the current position, received the alerts of the whole country. Such a location is now matched by its district or name.

## [1.0.4] - 2026-11-14

### Added

- A long press on the app icon offers shortcuts to Now, Forecast, Maps and Alerts. They appear after the first start of this version.
- Sea level chart: a legend for the model maximum and model minimum markers.

### Changed

- There is no preset location any more. Until the first location is added, the app only offers adding one. If you used only the preset Bratislava and never added or reordered a location, add it again after updating.
- Hourly forecast detail: the precipitation value at the cursor follows the type of precipitation. It shows the snow depth for snow, the amount in liquid units for rain, and both for rain with snow.

### Fixed

- Local alert estimates are computed in the same way on Now and on Alerts, so an estimate shown on Now is also listed on Alerts. Both screens use the forecast model selected for the location and the same validity windows; Now and notifications no longer report an estimate whose window has already passed.

## [1.0.3] - 2026-11-12

### Fixed

- Precipitation nowcast: the minutes in the headline (for example rain starts in 20 min) are counted from the current time, not from the last radar frame, so they are no longer too long by the age of that frame.
- A radar frame older than 20 minutes is no longer used; the app uses the Open-Meteo forecast instead.
- The radar nowcast is accepted when the service provides a horizon shorter than 60 minutes (at least 30 minutes), instead of silently switching to the Open-Meteo fallback.
- The headline says how long rain will last at least, and reports rain with breaks instead of claiming that it lasts at least an hour.

## [1.0.2] - 2026-11-12

First signed release build. Contains everything developed after 1.0.0.

### Added

- Four independent alert sources that can be combined freely: local (computed in the app), MeteoAlarm (Europe), LibreWXR (world) and the FOSS Public Alert Server (world, meteorological alerts only). Each source is shown separately under its own heading, without merging duplicates. The choice applies to Now, notifications and widgets.
- Info buttons that explain the alert sources and why European sources can show the same official alert more than once.
- The touch cursor on charts shows the titles of the alerts active at that time, or a note that there is no alert.
- Swipe navigation outside the graph area: on Now between saved locations, on Forecast and Air quality between parameters. Horizontal graph panning is unchanged.
- A notification rule can apply to several locations at once.
- Forecast: when the selected model has no precipitation probability, it is filled in from the Best match model and the forecast note says so.

### Changed

- Meteogram widget: temperature in red, with the highest and lowest value of every day; icons with values placed directly at the curves (thermometer, drop, umbrella for probability, separate icons for wind and gusts) and kept clear of curves, bars and each other where possible; the legend row and the inner grid were removed.
- Compact widget: the narrowest size shows only the icon and temperature, larger sizes show the location and the selected parameter in larger type.
- Current conditions and meteogram widgets show an alert symbol in the severity colour next to the location instead of alert text.
- New rain drop and snowflake icons for precipitation; other weather icons made consistent. Existing app icons are reused for probability, humidity and dew point.
- Alert source labels shortened to MeteoAlarm (Europe), LibreWXR (world) and FOSS Public Alert Server (world).
- Sun and Moon tiles show values in regular weight; empty chart states use the same bold style as alerts.
- New fan-shaped icon for the ensemble settings.

### Performance

- Alert responses are cached for 5 minutes like the other data; pull-to-refresh still reaches the network and stale alerts are never shown after a network failure.
- FOSS alert details are downloaded four at a time.
- Neighbouring saved locations are loaded in the background, so swiping to them is faster.
- Cached responses older than two days are removed at start.

## [1.0.1] - 2026-11-12

Internal version number only, no build was published.

## [1.0.0] - 2026-11-11

### Added

- English (British) localisation next to Slovak, selectable in Settings, Language, or following the system; the app restarts when the language changes.
- Translation checks run in the build, locale-aware dates and numbers, language note and updated README.

### Changed

- `versionCode` is the number of minutes since 2026-01-01 UTC at build time; the About page shows the version with the build number.

## 0.x

Pre-release development builds, not listed in detail.
