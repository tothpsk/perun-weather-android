# Changelog

All notable changes to Perun Weather for Android. The format follows [Keep a Changelog](https://keepachangelog.com/) and versions follow semantic versioning.

## [Unreleased]

## [1.4.1] - 2026-11-26

### Changed

- Notifications of warnings: the persistent alert notification shows one notification for each warning, as the cards of the Alerts screen, instead of one for each kind of hazard. Two warnings of snow with different levels or times are two notifications.
- Notifications of rules: a rule notification belongs to the group of its place, below the warnings of the place. Two or more notifications of a place are grouped under the header of the place; a single notification names its place in the line.
- Notifications of rules: a rule notification goes away when the rule is no longer fulfilled. Its end comes from the hourly forecast (weather, air quality and pollen) or, for weather events, from the weather codes; the expanded notification shows it.
- Notifications of rules: removing a rule or moving it to another place removes its notification; an edited rule is evaluated again and notifies when it is fulfilled.
- Alerts: a tap on the header of the details of a warning closes them, as the cross does.
- Forecast, sea and Sun and Moon charts: the days are labelled in one form that stays the same wherever the chart is scrolled: the short name of the day with the date when it fits a whole day, otherwise the date only.

### Fixed

- Notifications of rules from 1.4.0 that stayed outside the groups are removed at the first background refresh.

## [1.4.0] - 2026-11-26

### Added

- Alerts: a tap on a bar of the chart or on a warning in the overview opens its details in place: the short name of the hazard and the level, the validity with the time to its start or end, the name of the warning, its description, advice and area, the issuer with a link, the sources and the time of the update. A warning sent in more languages has a language switch; the language of the app comes first, then English.
- Alerts, chart: a short tap on the icons names the hazards; the (i) under the icons explains the chart.
- Notifications: a new option, Persistent alert notification. While a place has valid or coming warnings, a header with the name of the place stays in the shade, and under it one notification for each kind of warning. Only a new warning makes a sound, and the notifications go away when their warnings end.
- Notifications: a tap on a warning opens the Alerts screen with the details of that warning.

### Changed

- Alerts: a new installation turns on all alert sources: local estimates, MeteoAlarm, LibreWXR and the FOSS public alert server. An update keeps the sources chosen before.
- Alerts: MeteoAlarm, LibreWXR and the FOSS public alert server read every warning from its whole CAP message. The kind of the hazard comes from the codes of the message, so an icing warning gets the ice icon in every source, and a warning of two hazards (for example freezing rain and blowing snow) shows both. The text is in the language of the app when the message has it, otherwise in English.
- Alerts: the same warning from MeteoAlarm, LibreWXR and the FOSS public alert server is shown once, with all its sources. Warnings are joined by the identifier of their CAP message, not by their text. An updated or cancelled warning removes its older version. Official warnings are one group, local estimates another.
- Alerts, chart: one row for each kind of hazard; the warnings of a row follow each other, a higher level lies on top. Warnings valid now come first, then the higher levels. A warning of more hazards shows up to three icons side by side.
- Alerts: the list under the chart is the Alerts overview, with short cards. Empty texts such as "No further information available" are left out.
- Notifications of warnings: one line with the icon in the colour of the level, the short name and the level; the place is in the header, or in the line when the place has only one notification. Expanded: the validity and the description in the language of the app. The notifications of one place are grouped, sorted from the highest level, and go away when their warnings end.
- Notifications of warnings: the same warning from more sources is announced once. An update of a warning is announced again only when its level rises. Warnings already announced by 1.3 are not announced again.
- Now, the alert chip, the mark of a place in the list of places and the widgets use the same warnings as the Alerts screen, grouped by the kind of the hazard and the level.
- Alerts: whole CAP messages are kept on the phone until they expire, so a refresh downloads only new warnings. One refresh downloads at most 48 new messages for a place; the rest follow at the next refresh, and the list says it may be incomplete.

### Fixed

- Alerts: MeteoAlarm read the issuer of at most eight warnings of a place; now every warning has it.
- Alerts: warnings of the German weather service (DWD) without a MeteoAlarm level get their level from the DWD severity, which is one step lower than at other issuers.
- Alerts: green MeteoAlarm warnings are not shown.

## [1.3.1] - 2026-11-25

### Added

- Meteogram widget: the precipitation bars are coloured by the kind of precipitation: rain, showers, snow and rain with snow.
- Alerts, details of a MeteoAlarm warning: the issuer is shown. Its name is a link to the page of the warning when MeteoAlarm gives one, and plain text when it does not.
- Now, wave tile: the direction of the waves shows the degrees beside the point of the compass, for example NW · 322°.

### Changed

- Alerts, chart: the icon of the hazard stands above a bar instead of text.
- Meteogram widget: a tap opens Now for the place of the widget, not Forecast. The refresh control is separate.

### Fixed

- Alert icons: an alert of the shared snow and ice group is drawn by its own name. An icing warning no longer gets the snowflake because its general headline says "Snow-ice". A headline alone does not decide between snow and ice; it is used only when the name of the alert does not say.

## [1.3.0] - 2026-11-24

### Added

- Widgets: every widget that shows a place shows the strongest official warning of the period it covers, and at an equal level the one that starts first: the next 24 hours in Compact, Sun and Moon, and Air and pollen, six hours in Next 6 hours, the hours of the chart in Meteogram, six days in 6 days and the shown day in Day by hours.
- Sun and Moon, and Air and pollen widgets: the warning stands behind the name of the place. These widgets download the warnings at most once in 25 minutes.
- Compact, Next 6 hours and 6 days widgets: the pressure icon shows the trend of the pressure as on Now; without a known trend it shows a gauge.
- Now, storms tile: the help explains the dots of the storm cells on the radar and their colours by radar reflectivity.
- Air quality and pollen, expanded day: a swipe over the header of the chart opens the next or the previous day.
- Sun and Moon: a tap on the change of the day length, the time the Moon is above the horizon or the phase explains the value in words. A day as long as the day before has its own wording.
- Forecast, cloud cover: an (i) button beside the heading explains the daily value.

### Changed

- Icons: one set of outline icons for the app, the widgets and the notifications. The line is 1.75 of the 24 grid and at least 1.0 dp, and the drawings are fitted to their key shapes, so that icons of one size look equally large. Warnings have their own icons by the type of the hazard, taken from the code of the warning where the issuer sends it.
- Now: the tile graphics are 40 dp drawings with the line of the icons; the day arc and the UV and air quality scales are 64 x 40 dp. The glow behind the weather symbol is left out.
- Now: the wind and wave tiles show an arrow instead of a compass, in the colour of the Beaufort scale or of the wave height. The sea temperature tile shows a thermometer on a wave, the sea level tile a wave with the arrow of the trend.
- Now and Forecast: the daily cloud cover is the median of the hours between sunrise and sunset instead of the mean of the whole day.
- Now, precipitation tile: rain and snow stand on one line, separated by a comma, and the line shrinks only when it does not fit.
- Forecast: the compass is replaced by the arrow of the wind direction, in the list of days and in the expanded day. The pressure row and the pressure mode have a gauge icon.
- Forecast, expanded day, and Air quality and pollen, expanded day: the curves start just above the bottom of their row, as in the Day by hours widget.
- Forecast: the footer names what was filled in from Best match and what from GFS. Now and Ensemble use the same wording, "Filled in from Best match: ...".
- Ensemble: the precipitation probability is the forecast of the model, without a band; the tip and the help of the chart are shorter.
- Air quality and pollen: the description of a kind of pollen leaves out the word "Pollen".
- Settings, units: one card with a divider instead of two cards.
- Meteogram widget at two rows: the icons of the axes are 12 dp.
- Sun and Moon widget, the Moon in one row: rise and set at four cells, and the trend as well when there is room.
- Sun and Moon screen: the Moon of the phase row is drawn as an icon, with the line of the other icons.

### Fixed

- 6 days and Day by hours widgets: the warning followed different rules from the other widgets and could be missing while Next 6 hours showed it.
- Sun and Moon widget, the Moon at four cells in one row: only the rise was shown.

## [1.2.0] - 2026-11-23

### Added

- Settings: the Day by hours, Air and pollen, and Sun and Moon widgets can be set in the app, with the same form as on the screen that opens from the home screen.
- Meteogram widget: the wind direction as a row of arrows above the plot, in the colour of the wind line; it is chosen in the widget settings.
- Compact, Next 6 hours and 6 days widgets: an arrow behind the wind speed and the gusts points to where the wind blows to.
- Ensemble: with the uncertainty band on, the lines and bars show the ensemble median (P50). The tip and the help of a chart say so and name the quantities that the ensemble does not have and that are taken from the model.
- Forecast, expanded day: a swipe to the left or right over the name of the day opens the next or the previous day.

### Changed

- Now: the Sun tile draws the day arc of the Sun and Moon widget. In both, the Sun is drawn over the sunrise and sunset dots, and in the widget it stays inside the picture.
- Now: the alerts in the header are always shown; they are no longer an item in the list of tiles.
- Settings, tiles of Now: the tiles are listed in two columns and are dragged across and down; the help text is shorter.
- Settings: the notes of background refresh, charts and forecast, notifications and the widget settings are behind (i) buttons, and the buttons are pills across the width.
- Widget settings: dividers between the sections; the note about refreshing is left out.
- Forecast, expanded day, and the Day by hours widget: precipitation is drawn as bars. On a day with snow, marks under the bars show the kind of precipitation: a snowflake, a drop, or a drop with a snowflake for rain with snow.
- Forecast, expanded day, and the Day by hours widget: the compass icon of the wind direction row is white.
- Ensemble: the quantities filled in from Best match are named in the footer of the screen instead of the header of every chart.

## [1.1.1] - 2026-11-21

### Changed

- UV icon: an outlined sun with the letters UV below it, in the app and in the widgets.
- Now: the day summary shows its icons and values in white.
- Now: the wave tile has a shorter line for the direction of the waves.
- Forecast: the humidity drop in the list of days carries the percent sign, as on the humidity tile on Now.
- Forecast, expanded day, and the Day by hours widget: the row icons are white; the compass keeps its green arrow.
- Moon help sheets: the lit share of the disc in percent beside the four main phases.
- Meteogram widget: larger icons above the value axes.
- Compact, Sun and Moon, and Air and pollen widgets: 10 dp at the sides, as in the Meteogram widget, and the values reach the right edge instead of leaving the spare room there.
- Compact and Sun and Moon widgets at two rows: the size of the picture follows the height, so five cells show it as large as four. The two columns of values of the Sun and Moon widget stay close together.

### Fixed

- Day by hours widget: a tall widget drew its chart cut short and stretched. The chart keeps its proportions now.

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
