#!/usr/bin/env python3
"""Perun 1.2.1, patch 06. Ensemble: the precipitation probability gets its band back. The line is the share of the
ensemble members with at least 0.1 mm in the hour, the band reaches down to the share with at least 1 mm.
The short tip and the help of the chart say so instead of naming the median.

Every text must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

UI = "app/src/main/java/sk/perun/weather/ui/"
RES = "app/src/main/res/"
HELP = r'<string name="long_help_probability_ensemble" formatted="false">"[^\n]*"</string>'


def strings(help_text, tip_text):
    return ('<string name="long_help_probability_ensemble" formatted="false">"' + help_text + '"</string>\n'
            '    <string name="long_tip_probability" formatted="false">"' + tip_text + '"</string>')


# (file, old, new, old is a regular expression)
EDITS = [
    (UI + "LongEnsembleData.kt",
     "times[i] to EnsembleBand(share,share,0.0,100.0,finite.size)}",
     "times[i] to EnsembleBand(100.0*finite.count {it>=1.0}/finite.size,share,0.0,100.0,finite.size)}", False),
    (UI + "LongEnsembleData.kt",
     r'// Kept as the line of the quantity: the share of the members with at least 0\.1 mm in the hour\. It has no band\.\n[ \t]*if\(probability\.isNotEmpty\(\)\) result\["precipitation_probability"\+LongMedianSuffix\]=probability',
     "// The band reaches from the share of the members with at least 1 mm in the hour up to the share with at least 0.1 mm.\n"
     "        // The line is the upper edge of the band; it is kept where the other quantities keep their median.\n"
     "        if(probability.isNotEmpty()) {\n"
     '            result["precipitation_probability"]=probability\n'
     '            result["precipitation_probability"+LongMedianSuffix]=probability.map {(t,b)->t to EnsembleBand(b.upper,b.upper,0.0,100.0,2)}\n'
     "        }", True),
    (UI + "LongForecastScreen.kt",
     'val tipNote=if(!showBand) "" else (if(modelOnly.size<metrics.size) " "+uiText(R.string.long_tip_median) else "")+modelOnly.joinToString("") {" "+it.label+": "+uiText(R.string.long_tip_model)}',
     "// The line of the precipitation probability is a share of the members, not a median, so the tip names it separately.\n"
     "    val probShare=showBand && LongMetric.Prob in metrics && medians.containsKey(LongMetric.Prob.key+LongMedianSuffix)\n"
     '    val tipNote=if(!showBand) "" else (if(metrics.any {it!=LongMetric.Prob && medians.containsKey(it.key+LongMedianSuffix)}) " "+uiText(R.string.long_tip_median) else "")+(if(probShare) " "+uiText(R.string.long_tip_probability) else "")+modelOnly.joinToString("") {" "+it.label+": "+uiText(R.string.long_tip_model)}', False),
    (RES + "values/strings_long_probability.xml", HELP,
     strings("Pri zapnutej obálke je čiara pravdepodobnosti zrážok podiel členov ensemble, ktoré v danej hodine dávajú aspoň 0,1 mm zrážok. Pásmo pod ňou siaha po podiel členov s aspoň 1 mm. Nie sú to percentily ani medián.",
             "Pravdepodobnosť: podiel členov ensemble s aspoň 0,1 mm za hodinu; pásmo siaha po podiel s aspoň 1 mm."), True),
    (RES + "values-en/strings_long_probability.xml", HELP,
     strings("With the band on, the line of the precipitation probability is the share of the ensemble members that give at least 0.1 mm of precipitation in the hour. The band below it reaches down to the share of the members with at least 1 mm. These are neither percentiles nor a median.",
             "Probability: the share of the ensemble members with at least 0.1 mm in the hour; the band reaches down to the share with at least 1 mm."), True),
]

problems = []
texts = {}
crlf = {}
for name, old, new, regex in EDITS:
    if name not in texts:
        if not Path(name).is_file():
            problems.append(f"{name} not found")
            continue
        raw = Path(name).read_bytes().decode("utf-8")
        crlf[name] = "\r\n" in raw
        texts[name] = raw.replace("\r\n", "\n")
    pattern = old if regex else re.escape(old)
    n = len(re.findall(pattern, texts[name]))
    if n == 1:
        texts[name] = re.sub(pattern, lambda m: new, texts[name])
    else:
        problems.append(f"{name}: expected 1 match, found {n}: {old[:90]}")
if problems:
    print("Nothing was changed:")
    for p in problems:
        print("  " + p)
    sys.exit(1)
for name, text in texts.items():
    Path(name).write_bytes((text.replace("\n", "\r\n") if crlf[name] else text).encode("utf-8"))
    print(f"updated {name}")
