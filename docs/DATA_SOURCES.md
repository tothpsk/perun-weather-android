# Data sources and attribution

- Open-Meteo: forecast, air quality (CAMS/Copernicus), pollen, marine. CC BY 4.0.
- MeteoAlarm warnings via KDE/FOSS Warn. Shown only when an alert exists for the selected location.
- OpenStreetMap / Nominatim: place names and search. ODbL.

Map layers must be transparent enough for the basemap; scale bar bottom-right; WeatherAPI legend hidden.

Model selection: per-location models (global + EU, plus CHMI ALADIN), seamless models, automatic fallback to Best match with a user-visible explanation. Shorter horizons leave gaps in charts, no invented values.
