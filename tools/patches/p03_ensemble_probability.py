#!/usr/bin/env python3
"""Perun 1.2.1, patch 03. Ensemble: with the band on, the line of the precipitation probability is the share
of the ensemble members with at least 0.1 mm in the hour, and it has no band. The note about the quantities
filled in from Best match has one wording on Now and on Ensemble and names the precipitation probability in full.

Every text must be found exactly once. If anything differs, nothing is written."""
import re
import sys
from pathlib import Path

UI = "app/src/main/java/sk/perun/weather/ui/"
RES = "app/src/main/res/"
PROB = "if(it==LongMetric.Prob) uiText(R.string.ui_pravdepodobnost_zrazok_c9b0a84a) else it.label"
# (file, old, new, old is a regular expression)
EDITS = [
    (UI + "LongEnsembleData.kt",
     r"if\(finite\.size<2\) null else times\[i\] to EnsembleBand\(\s*100\.0\*finite\.count \{it>=1\.0\}/finite\.size,\s*100\.0\*finite\.count \{it>=0\.1\}/finite\.size,\s*0\.0,100\.0,finite\.size,\s*\)",
     "if(finite.size<2) null else {val share=100.0*finite.count {it>=0.1}/finite.size;times[i] to EnsembleBand(share,share,0.0,100.0,finite.size)}", True),
    (UI + "LongEnsembleData.kt",
     'if(probability.isNotEmpty()) result["precipitation_probability"]=probability',
     '// Kept as the line of the quantity: the share of the members with at least 0.1 mm in the hour. It has no band.\n'
     '        if(probability.isNotEmpty()) result["precipitation_probability"+LongMedianSuffix]=probability', False),
    (UI + "LongEnsembleData.kt",
     'if("precipitation_probability" in bands) append(uiText(R.string.ui_pasmo_pravdepodobnosti_zrazok_nie_je_percentilove_hranice_defaa900))',
     'if("precipitation_probability"+LongMedianSuffix in bands) append(" "+uiText(R.string.long_help_probability_ensemble))', False),
    (UI + "LongForecastScreen.kt",
     "LongMetric.Prob->HelpText(uiText(R.string.ui_prerusovana_ciara_ukazuje_pravdepodobnost_zrazok_v_danej_h_5ac15e46))",
     "LongMetric.Prob->{HelpText(uiText(R.string.ui_prerusovana_ciara_ukazuje_pravdepodobnost_zrazok_v_danej_h_5ac15e46));HelpText(uiText(R.string.long_help_probability_ensemble))}", False),
    (UI + "LongForecastScreen.kt",
     'uiText(R.string.long_fallback_note,LongMetric.entries.filter {it.key in filled}.joinToString(", ") {it.label})',
     'uiText(R.string.long_fallback_note,LongMetric.entries.filter {it.key in filled}.joinToString(", ") {' + PROB + '})', False),
    (UI + "LongForecastScreen.kt",
     "the probability of precipitation has no median.",
     "the line of the probability of precipitation is the share of the members with precipitation.", False),
    (UI + "Overview.kt",
     "else -> LongMetric.entries.firstOrNull { it.key==key }?.label",
     "else -> LongMetric.entries.firstOrNull { it.key==key }?.let { " + PROB + " }", False),
    (UI + "Overview.kt",
     'return if(labels.isEmpty()) null else "Best match: "+labels.joinToString(", ")',
     "// The same wording and order as in the footer of Ensemble.\n"
     "    val order=LongMetric.entries.map { " + PROB + " }\n"
     '    return if(labels.isEmpty()) null else uiText(R.string.long_fallback_note,labels.sortedBy { label->order.indexOf(label).let { i->if(i<0) order.size else i } }.joinToString(", "))', False),
    (RES + "values/ui_strings.xml",
     '<string name="long_fallback_note">"Doplnené z modelu Best match: %1$s."</string>',
     '<string name="long_fallback_note">"Doplnené z Best match: %1$s"</string>', False),
    (RES + "values-en/ui_strings.xml",
     '<string name="long_fallback_note">"Filled in from the Best match model: %1$s."</string>',
     '<string name="long_fallback_note">"Filled in from Best match: %1$s"</string>', False),
]
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="long_help_probability_ensemble" formatted="false">"'
NEW = {
    RES + "values/strings_long_probability.xml": HEAD + "Pri zapnutej obálke je čiara pravdepodobnosti zrážok podiel členov ensemble, ktoré v danej hodine dávajú aspoň 0,1 mm zrážok. Pásmo sa pri nej nekreslí." + '"</string>\n</resources>\n',
    RES + "values-en/strings_long_probability.xml": HEAD + "With the band on, the line of the precipitation probability is the share of the ensemble members that give at least 0.1 mm of precipitation in the hour. It has no band." + '"</string>\n</resources>\n',
}

problems = []
texts = {}
crlf = {}
for name in NEW:
    if Path(name).exists():
        problems.append(f"{name} already exists")
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
for name, content in NEW.items():
    Path(name).write_bytes(content.encode("utf-8"))
    print(f"created {name}")
