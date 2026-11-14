# Overview acceptance criteria

Source of visual requirements: user-supplied perun-android-mockup-3.html. This document records requirements, not implemented features. Existing desktop behavior must be checked in desktop source before porting; do not redesign it from memory.

## Navigation

Keep native Kotlin / Compose shell and five bottom destinations. Use the mockup's outline home, chart, folded map, warning triangle and horizontal three-dot symbols. Use its menu and settings symbols in the top bar. Do not copy fictional warning/status dots as live status.

## Hero

Open layout, weather icon, large temperature, condition, apparent temperature and daily minimum/maximum. No narrative summary. Pull-to-refresh retained. Local times must refer to selected location.

## Default tiles in source order

1. Wind: speed, cardinal direction, gusts and compass together.
2. UV index: current value with category, plus today's maximum (not maximum presented as current).
3. Cloud cover: current total percent.
4. Precipitation: hourly amount and explicit interval (not daily total).
5. Sun today: local sunrise and sunset.
6. Air quality: European AQI, PM2.5 and PM10; white identity dot.
7. Warnings: full width, official source, severity and validity. The mockup warning is fictional; never ship it as real. No warning display without verified applicable warnings. A fetch failure must not mean 'no warnings'.

Optional tiles, initially off: precipitation probability for the same interval as precipitation; pressure; pollen. Preserve tile selection in settings when implemented.

## Five-day outlook

Compact selectable rows: day/date, weather icon, graphical range/track, values. Temperature mode shows min/max on a shared scale; precipitation mode shows daily amount and probability; wind mode shows speed and gusts. Real data only. Temperature scale must support negative values, unlike sample-only hard-coded mockup scaling. Zero precipitation must not have a misleading nonzero bar. Missing values remain missing. Day selection opens details; do not expose a working-chart button until destination works.

## Delivery and verification

Current icon commit changes shell icons only. Existing overview data and layout remain unfinished. Next: complete typed data adapters from verified desktop semantics, all default tiles, hero, and graphical five-day rows. Retain current data on refresh failure. Test error and missing-data states as well as successful responses. A successful CI build is not device verification.
