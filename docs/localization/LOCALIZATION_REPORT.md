# Presun slovenských textov – odovzdanie zdrojov na kontrolu

## Stav

ZIP obsahuje celý upravený projekt. Patch obsahuje rovnaké zmeny proti nahranému archívu. Nie je to APK ani potvrdený úspešný Android build. Na GitHub nebolo nič zapísané.

Zdroj: perun-weather-android-main.zip.txt
SHA-256 vstupného archívu: b7b414e1fca010340fbd6b3c1c1123b5ca64808792a089e420e8329f0036104d
Aktuálna vzdialená vetva nebola overovaná ani prepisovaná. Patch sa nesmie slepo aplikovať na novší alebo inak zmenený projekt.

## Rozsah

- 938 pôvodných Kotlin string literálov nahradených odkazmi na resources v 35 Kotlin súboroch; 59 pôvodných výskytov obsahovalo interpoláciu hodnôt.
- Navyše presunutý slovenský názov automatického výberu ensemble v katalógu modelov.
- 661 nových string resources a 2 skupiny plurals s kategóriami one/few/other pre počty modelov a výstrah.
- 59 mapových prekladových kľúčov napojených na Android resources. Presunuté HTML/JavaScript texty ovládania, legendy, prístupnosti a hlásení.
- Šesť nových JVM testov. Pôvodné očakávania existujúcich testov sa nemenili; testovacie triedy dostali spoločnú inicializáciu resolvera čítajúceho skutočné XML resources.
- Pred pridaním tejto dokumentácie: 47 zmenených existujúcich súborov a 7 nových súborov.

## Technické riešenie a obmedzenia

AppStrings je procesový resolver resources. Inicializuje ho PerunApplication.attachBaseContext pred prezentačným kódom a umožňuje čítanie textov aj mimo Compose, napríklad v adaptéroch, enumoch a Canvas callbackoch. Resolver uchováva Resources, nie Activity. JVM testy používajú explicitne injektovaný resolver a XML z projektu, bez duplicitných prekladov a bez nových externých testovacích závislostí.

Ide o presun textov, nie dokončenú viacjazyčnú aplikáciu. Predvolené resources zostávajú slovenské; nepribudla angličtina ani prepínač jazyka. Niektoré existujúce enumy a kolekcie uchovávajú vyriešené labely počas života procesu. Pred spoľahlivým prepínaním jazyka za behu treba takéto cache nahradiť resource ID alebo ich obnovovať. Niektoré pôvodne skladané vety zostávajú skladané z resource fragmentov: pred prekladmi treba skontrolovať gramatiku/poradie a prípadne ich spojiť do celých formátovaných viet. Explicitné locale identifikátory a vzory dátumov zostali nezmenené.

API kľúče, perzistentné identifikátory, navigačné cesty, konverzie jednotiek a vyhľadávacie výrazy sa neprekladali. V OverviewTileKit zostali tieto vstupné klasifikačné korene: búrk, dážď, zrážk, poľadov, ľad, mráz, horúč. Nie sú to používateľské výstupné texty. V technickom katalógu zostali vlastné názvy Météo-France a ČHMÚ. Medzinárodné chemické/indexové značky a pevné anglické atribučné hlásenie sú označené translatable=false.

Lokálne mapové HTML sa načítava cez pôvodnú presnú URL a doplní resources. HTML hodnoty sa escapujú; JSON v scripte escapuje znak < a oddeľovače U+2028/U+2029. Nepribudli nové bridge metódy, sieťové oprávnenia ani povolené hosty.

## Vykonané kontroly

- Vstupný ZIP: CRC bez chyby.
- XML: načítateľné, všetkých 661 nových string hodnôt sa po escapovaní/spätnom dekódovaní zhoduje s pripravenými hodnotami.
- R.string a R.plurals odkazy: bez nenájdených produkčných odkazov.
- Formátovacie značky a plurals: statická kontrola prešla.
- Mapové placeholdery a slovník: statická kontrola 59 kľúčov prešla.
- Syntax mapového JavaScriptu: kontrola Node.js prešla.
- Zachovanie ostatného kódu: spätné nahradenie 938 migrácií a zohľadnenie úprav LocalIssuer/načítania mapy obnovilo pôvodný obsah všetkých 35 súborov.
- Guard proti diakritickým literálom v produkcii s explicitnými technickými výnimkami: prešiel. Toto nie je úplný jazykový audit ani dôkaz zachytenia všetkých ASCII textov.
- Patch: git apply --check, následné aplikovanie na pôvodný projekt a byte-for-byte porovnanie všetkých súborov s pripraveným projektom prešli pred odovzdaním.
- Výstupný ZIP: overenie CRC a byte-for-byte porovnanie každého archivovaného súboru prešli pred odovzdaním.

Výstup checkeru:

```text
PASS: map JavaScript syntax
PASS: 697 strings, 2 plural groups, 54 Kotlin files, 59 map entries
Scope: XML/formats, resource references, accented-text guard, map keys. Does not prove compilation or full linguistic correctness.
```

Doplnkový JavaScript smoke test s náhradným DOM:

```text
PASS: map boot, translated play label, missing-Leaflet error and retry label
```

Smoke test nenahrádza Android WebView integračné testy a neoveruje sieťové dáta. Prvý pokus testovacieho harnessu nemal náhradnú metódu document.addEventListener; opravený bol harness, nie produkčný JavaScript.

## Čo nie je overené

Java, Gradle ani Kotlin compiler v prostredí nie sú dostupné. assembleDebug, testDebugUnitTest a lintDebug preto neboli spustené. Šesť nových JVM testov nie je označených za úspešné. Kotlin kompilácia, Android resource compiler, skutočný WebView a vizuálna regresia zostávajú na overenie.

Existujúci GitHub workflow naďalej spúšťa build/test/lint; pred tento krok pribudol statický checker. Samotný workflow sa týmto odovzdaním nespustil.

## Použitie

Použi buď celý upravený projekt zo ZIP-u, alebo patch aplikovaný na zhodný pôvodný projekt; nie oboje za sebou. Pred aplikáciou na novšiu vetvu treba zosúladiť zmeny, aby sa neprepísal novší kód.

V koreňovom priečinku upraveného projektu:

```sh
python3 tools/check_localization.py
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Pre patch v koreňovom priečinku pôvodného projektu z nahraného archívu:

```sh
git apply --check perun-localization.patch
git apply perun-localization.patch
```

## Zmenené existujúce súbory

- .github/workflows/android.yml
- app/src/main/AndroidManifest.xml
- app/src/main/assets/map/librewxr_map.html
- app/src/main/java/sk/perun/weather/ui/AirPreferences.kt
- app/src/main/java/sk/perun/weather/ui/AirQualityData.kt
- app/src/main/java/sk/perun/weather/ui/AirQualityScreen.kt
- app/src/main/java/sk/perun/weather/ui/AlertIdentity.kt
- app/src/main/java/sk/perun/weather/ui/AlertPresentation.kt
- app/src/main/java/sk/perun/weather/ui/AlertSources.kt
- app/src/main/java/sk/perun/weather/ui/AlertsScreen.kt
- app/src/main/java/sk/perun/weather/ui/AttributionFooter.kt
- app/src/main/java/sk/perun/weather/ui/ForecastScreen.kt
- app/src/main/java/sk/perun/weather/ui/LocationModelAvailability.kt
- app/src/main/java/sk/perun/weather/ui/LocationModelCatalogue.kt
- app/src/main/java/sk/perun/weather/ui/LocationSourceEditor.kt
- app/src/main/java/sk/perun/weather/ui/LocationsSheet.kt
- app/src/main/java/sk/perun/weather/ui/LongCustomGraphs.kt
- app/src/main/java/sk/perun/weather/ui/LongEnsembleData.kt
- app/src/main/java/sk/perun/weather/ui/LongEnvelopePreferences.kt
- app/src/main/java/sk/perun/weather/ui/LongForecastScreen.kt
- app/src/main/java/sk/perun/weather/ui/MapsScreen.kt
- app/src/main/java/sk/perun/weather/ui/MarineData.kt
- app/src/main/java/sk/perun/weather/ui/MarineOverview.kt
- app/src/main/java/sk/perun/weather/ui/MarineScreen.kt
- app/src/main/java/sk/perun/weather/ui/MoreScreen.kt
- app/src/main/java/sk/perun/weather/ui/Overview.kt
- app/src/main/java/sk/perun/weather/ui/OverviewArt.kt
- app/src/main/java/sk/perun/weather/ui/OverviewData.kt
- app/src/main/java/sk/perun/weather/ui/OverviewExtra.kt
- app/src/main/java/sk/perun/weather/ui/OverviewHelp.kt
- app/src/main/java/sk/perun/weather/ui/OverviewNowcast.kt
- app/src/main/java/sk/perun/weather/ui/OverviewOutlook.kt
- app/src/main/java/sk/perun/weather/ui/OverviewTileKit.kt
- app/src/main/java/sk/perun/weather/ui/OverviewTilePreferences.kt
- app/src/main/java/sk/perun/weather/ui/OverviewV2.kt
- app/src/main/java/sk/perun/weather/ui/PerunApp.kt
- app/src/main/java/sk/perun/weather/ui/SunMoonMath.kt
- app/src/main/java/sk/perun/weather/ui/SunMoonScreen.kt
- app/src/main/java/sk/perun/weather/ui/WeatherUnits.kt
- app/src/test/java/sk/perun/weather/ui/AirQualityLevelTest.kt
- app/src/test/java/sk/perun/weather/ui/AlertPresentationTest.kt
- app/src/test/java/sk/perun/weather/ui/ForecastDayTest.kt
- app/src/test/java/sk/perun/weather/ui/LongForecastMathTest.kt
- app/src/test/java/sk/perun/weather/ui/MarineDataTest.kt
- app/src/test/java/sk/perun/weather/ui/OverviewTilePreferencesTest.kt
- app/src/test/java/sk/perun/weather/ui/SunMoonMathTest.kt
- app/src/test/java/sk/perun/weather/ui/WeatherUnitsTest.kt

## Nové implementačné/testovacie/nástrojové súbory

- app/src/main/java/sk/perun/weather/PerunApplication.kt
- app/src/main/java/sk/perun/weather/ui/AppStrings.kt
- app/src/main/java/sk/perun/weather/ui/MapStrings.kt
- app/src/main/res/values/ui_strings.xml
- app/src/test/java/sk/perun/weather/ui/AppStringsTest.kt
- app/src/test/java/sk/perun/weather/ui/ResourceStringsTestBase.kt
- tools/check_localization.py
