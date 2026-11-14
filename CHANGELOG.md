# Changelog

All notable changes to Perun Weather for Android. The format follows [Keep a Changelog](https://keepachangelog.com/) and versions follow semantic versioning.

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
