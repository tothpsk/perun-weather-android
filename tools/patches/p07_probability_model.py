#!/usr/bin/env python3
"""Perun 1.2.1, patch 07. Ensemble: the precipitation probability is the forecast of the model again (filled in
from Best match when the selected model lacks it), without a band and without anything derived from the members.
The short tip and the help of a chart are shorter: they name what each quantity shows.

Every text must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

UI = "app/src/main/java/sk/perun/weather/ui/"
RES = "app/src/main/res/"
# (file, old, new, old is a regular expression)
EDITS = [
    (UI + "LongEnsembleData.kt",
     r'[ \t]*if\(key=="precipitation"\) \{.*?\n[ \t]*return result',
     "    // The probability of precipitation is not derived from the members; its line stays the forecast of the model.\n"
     "    return result", True),
    (UI + "LongEnsembleData.kt",
     r'[ \t]*if\("precipitation_probability"\+LongMedianSuffix in bands\) append\(" "\+uiText\(R\.string\.long_help_probability_ensemble\)\)\n',
     "", True),
    (UI + "LongForecastScreen.kt",
     "LongMetric.Prob->{HelpText(uiText(R.string.ui_prerusovana_ciara_ukazuje_pravdepodobnost_zrazok_v_danej_h_5ac15e46));HelpText(uiText(R.string.long_help_probability_ensemble))}",
     "LongMetric.Prob->HelpText(uiText(R.string.ui_prerusovana_ciara_ukazuje_pravdepodobnost_zrazok_v_danej_h_5ac15e46))", False),
    (UI + "LongForecastScreen.kt",
     r'// The line of the precipitation probability is a share of the members, not a median, so the tip names it separately\.\n[ \t]*val probShare=[^\n]*\n[ \t]*val tipNote=[^\n]*',
     "// With every quantity from the ensemble one sentence is enough; otherwise each quantity is named with what it shows.\n"
     '    val tipNote=if(!showBand) "" else if(modelOnly.isEmpty()) " "+uiText(R.string.long_tip_median) else metrics.joinToString("") {" "+it.label+": "+uiText(if(it in modelOnly) R.string.long_tip_model_short else R.string.long_tip_median_short)+"."}', True),
    (UI + "LongForecastScreen.kt",
     "the line of the probability of precipitation is the share of the members with precipitation.",
     "the probability of precipitation has no median and stays the forecast of the model.", False),
    (UI + "LongForecastScreen.kt",
     "HelpText(uiText(if(showBand) R.string.long_line_median else R.string.long_line_model))",
     "if(!showBand) HelpText(uiText(R.string.long_line_model)) else if(metrics.any {it!=LongMetric.Rain && it!=LongMetric.Uv && it !in modelOnly}) HelpText(uiText(R.string.long_line_median))", False),
    (UI + "LongForecastScreen.kt",
     'modelOnly.forEach {HelpText(it.label+": "+uiText(R.string.long_tip_model))}',
     'modelOnly.forEach {HelpText(it.label+": "+uiText(R.string.long_tip_model_short)+".")}', False),
]


def xml(median, model):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            '    <string name="long_tip_median_short" formatted="false">"' + median + '"</string>\n'
            '    <string name="long_tip_model_short" formatted="false">"' + model + '"</string>\n</resources>\n')


# These two files were created by the earlier patches of 1.2.1 and are rewritten as a whole.
REWRITE = {
    RES + "values/strings_long_probability.xml": xml("medián ensemble (P50)", "predpoveď modelu"),
    RES + "values-en/strings_long_probability.xml": xml("ensemble median (P50)", "forecast of the model"),
}

problems = []
texts = {}
crlf = {}
for name in REWRITE:
    if not Path(name).is_file() or "long_tip_probability" not in Path(name).read_text(encoding="utf-8"):
        problems.append(f"{name}: not in the expected state")
for name, old, new, regex in EDITS:
    if name not in texts:
        if not Path(name).is_file():
            problems.append(f"{name} not found")
            continue
        raw = Path(name).read_bytes().decode("utf-8")
        crlf[name] = "\r\n" in raw
        texts[name] = raw.replace("\r\n", "\n")
    pattern = old if regex else re.escape(old)
    n = len(re.findall(pattern, texts[name], flags=re.S))
    if n == 1:
        texts[name] = re.sub(pattern, lambda m: new, texts[name], flags=re.S)
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
for name, content in REWRITE.items():
    Path(name).write_bytes(content.encode("utf-8"))
    print(f"rewrote {name}")
