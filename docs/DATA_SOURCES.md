# Data sources and attribution

- Open-Meteo: forecast, air quality (CAMS/Copernicus), pollen, marine. CC BY 4.0.
- MeteoAlarm (EUMETNET): warnings from the Atom feed of each country at feeds.meteoalarm.org. A place gets the warnings of the areas it lies in; the position is checked against simplified area outlines bundled with the app (app/src/main/assets/meteoalarm, built by tools/meteoalarm_areas.py from the outlines of the meteoalarm Python package by Niklas Jordan, MIT; see the NOTICE.txt there). MeteoAlarm publishes its data under terms equivalent to CC BY 4.0 with additional requirements for redistribution. Whether bundling simplified outlines meets them has been asked of MeteoAlarm and is not answered yet.
- KDE FOSS Public Alert Server: CAP alerts, meteorological category only, matched to a place by their polygons.
- LibreWXR: precipitation nowcast, storm cells, lightning, map layers and alerts. Alerts come from the WMO alert hub and the NWS API; the text is in the first language of the issuing service.
- OpenStreetMap / Nominatim: place names and search. ODbL.
- WeatherAPI.com weather map tiles (no key): temperature, precipitation, pressure, wind; 3 days, hourly; time in UTC as yyyyMMddHH. Label as forecast, not radar.
- Xweather: radar, optional, user-supplied key (ID_SECRET).

Map layers must be transparent enough for the basemap; scale bar bottom-right; WeatherAPI legend hidden.

Model selection: per-location models (global + EU, plus CHMI ALADIN), seamless models, automatic fallback to Best match with a user-visible explanation. Shorter horizons leave gaps in charts, no invented values.
