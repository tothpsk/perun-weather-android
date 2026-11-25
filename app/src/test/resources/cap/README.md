# CAP test messages

Real CAP messages collected on 2026-11-25 for the unit tests of the alert model. They are used only as test input.

- `meteoalarm_*.xml`: [MeteoAlarm](https://meteoalarm.org) (`feeds.meteoalarm.org/api/v1/warnings/...`). The warnings are issued by the national weather services named in each message. MeteoAlarm content is licensed under terms equivalent to CC BY 4.0 with additional redistribution requirements; see the MeteoAlarm terms.
- `kde_*.xml`: [FOSS Public Alert Server](https://alerts.kde.org) (`alerts.kde.org/alert/<uuid>`).
- `dwd_*.xml`: Deutscher Wetterdienst CAP feed, reached through the `uri` of a LibreWXR alert. Source: Deutscher Wetterdienst.

Shortened for size: in `dwd_*.xml`, `kde_dwd_mul.xml` and `meteoalarm_de_*.xml` only three areas are kept per info block and the polygons are removed. Nothing else is changed.
