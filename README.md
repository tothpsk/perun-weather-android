# Perun Weather for Android

A personal weather app for Android, built with Kotlin and Jetpack Compose. It brings forecasts, weather alerts, air quality and pollen, marine data, maps and home-screen widgets into one app, with detailed charts you can explore by touch.

It is based on the design and requirements of the desktop app [Perun Weather](https://github.com/tothpsk/perun-weather).

**Current version:** 1.0.3 (see the [changelog](CHANGELOG.md)) | **Requires:** Android 15 (API 35) or newer | **Languages:** Slovak, British English

## Features

### Weather and charts

- Screens: Now, Forecast, Long-range forecast, Air quality and pollen, Marine, Sun and Moon, Maps and Alerts.
- Forecast charts with a touch cursor that shows the values at the chosen time, including the alerts active at that moment.
- Swipe between saved locations on Now and between parameters on Forecast and Air quality. Horizontal graph panning is not affected.
- Several saved locations, each with its own weather model and alert source.

### Alerts

Four independent sources that you can combine freely. Each is shown separately under its own heading, without merging duplicates:

- Local alerts computed in the app from the forecast.
- MeteoAlarm for Europe.
- LibreWXR for the world.
- FOSS Public Alert Server for the world (meteorological alerts only).

### Notifications

- Current conditions and a status-bar value.
- Weather alerts with a minimum severity, for the active location, all locations or a chosen set.
- Custom rules for a metric and threshold, applicable to one or several locations.
- Daily and tomorrow summaries.

### Widgets

Compact, current conditions and meteogram, each with its own settings. The meteogram shows daily highs and lows and marks the peaks of wind, gusts, precipitation and probability.

### Everything else

- Background refresh with a shared response cache, battery and network rules.
- Configuration backup and restore.
- Language setting in the app, or follow the system and per-app language of Android.

## Install

1. Download `Perun-<version>.apk` from the [Releases](../../releases) page. The release also contains a `.sha256` file for checking the download.
2. Open the APK on the phone and allow installation from that source when Android asks.
3. To update, install the newer APK over the existing app. Your locations and settings are kept, as long as the new APK is signed with the same key.

Builds from before 1.0.2 are debug builds signed with a different key. Uninstall one of those first, and note down your locations or use the backup in Settings.

## Data sources

- [Open-Meteo](https://open-meteo.com/) (CC BY 4.0) for current weather, forecast, air quality and pollen (CAMS/Copernicus).
- LibreWXR for precipitation nowcast and alerts.
- MeteoAlarm and the KDE FOSS Public Alert Server for alerts.

The full list with links is in the app under About, Data sources. Forecasts are model output and do not replace official warnings. The app sends the coordinates of your saved locations to these services to get the data.

## License

MIT, see [LICENSE](LICENSE).
