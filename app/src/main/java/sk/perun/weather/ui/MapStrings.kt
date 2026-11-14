package sk.perun.weather.ui

import android.content.Context
import android.text.TextUtils
import org.json.JSONObject
import sk.perun.weather.R

private val MapStringIds = mapOf(
    "map_alert_count" to R.string.map_alert_count,
    "map_alert_count_label" to R.string.map_alert_count_label,
    "map_alerts_load_error" to R.string.map_alerts_load_error,
    "map_forecast_time" to R.string.map_forecast_time,
    "map_frames_load_error" to R.string.map_frames_load_error,
    "map_history_time" to R.string.map_history_time,
    "map_now_time" to R.string.map_now_time,
    "ui_blesky_bcc2d8d0" to R.string.ui_blesky_bcc2d8d0,
    "ui_burkove_bunky_02324dfe" to R.string.ui_burkove_bunky_02324dfe,
    "ui_cas_snimky_87293daa" to R.string.ui_cas_snimky_87293daa,
    "ui_casovy_interval_historia_e76ab9b1" to R.string.ui_casovy_interval_historia_e76ab9b1,
    "ui_casovy_limit_2687ec9f" to R.string.ui_casovy_limit_2687ec9f,
    "ui_cela_dostupna_historia_24c2ee99" to R.string.ui_cela_dostupna_historia_24c2ee99,
    "ui_chyba_b9f17311" to R.string.ui_chyba_b9f17311,
    "ui_infracerveny_satelit_noaa_gmgsi_stupnica_pre_satelit_zatia_a998f52f" to R.string.ui_infracerveny_satelit_noaa_gmgsi_stupnica_pre_satelit_zatia_a998f52f,
    "ui_interval_a_krok_vyberaju_len_existujuce_snimky_nic_sa_nein_872feabc" to R.string.ui_interval_a_krok_vyberaju_len_existujuce_snimky_nic_sa_nein_872feabc,
    "ui_kazdych_20_minut_8c036649" to R.string.ui_kazdych_20_minut_8c036649,
    "ui_kazdych_30_minut_c0d1867e" to R.string.ui_kazdych_30_minut_c0d1867e,
    "ui_kniznicu_mapy_leaflet_sa_nepodarilo_nacitat_skontroluj_pri_a323dd2e" to R.string.ui_kniznicu_mapy_leaflet_sa_nepodarilo_nacitat_skontroluj_pri_a323dd2e,
    "ui_krok_medzi_snimkami_e093aa89" to R.string.ui_krok_medzi_snimkami_e093aa89,
    "ui_legenda_61154f2a" to R.string.ui_legenda_61154f2a,
    "ui_librewxr_nevratil_ziadne_snimky_pre_tuto_vrstvu_89c7d6cc" to R.string.ui_librewxr_nevratil_ziadne_snimky_pre_tuto_vrstvu_89c7d6cc,
    "ui_mapa_s_radarom_e59e75af" to R.string.ui_mapa_s_radarom_e59e75af,
    "ui_mapy_c9e02d67" to R.string.ui_mapy_c9e02d67,
    "ui_moznosti_0fec1c16" to R.string.ui_moznosti_0fec1c16,
    "ui_nacitavam_snimky_librewxr_265b09ca" to R.string.ui_nacitavam_snimky_librewxr_265b09ca,
    "ui_nacitavam_vystrahy_5e685bde" to R.string.ui_nacitavam_vystrahy_5e685bde,
    "ui_neocakavany_format_8c464e2c" to R.string.ui_neocakavany_format_8c464e2c,
    "ui_niektore_dlazdice_sa_nenacitali_mimo_pokrytia_alebo_chyba_61fdbc23" to R.string.ui_niektore_dlazdice_sa_nenacitali_mimo_pokrytia_alebo_chyba_61fdbc23,
    "ui_normalna_b50a82f9" to R.string.ui_normalna_b50a82f9,
    "ui_nowcast_nie_je_na_serveri_dostupny_zobrazena_je_len_histor_76b4d741" to R.string.ui_nowcast_nie_je_na_serveri_dostupny_zobrazena_je_len_histor_76b4d741,
    "ui_obnovit_snimky_55c17851" to R.string.ui_obnovit_snimky_55c17851,
    "ui_odrazivost_radaru_od_10_do_80_dbz_7d801db2" to R.string.ui_odrazivost_radaru_od_10_do_80_dbz_7d801db2,
    "ui_odrazivost_radaru_v_dbz_10_az_80_je_to_orientacna_velicina_9c205545" to R.string.ui_odrazivost_radaru_v_dbz_10_az_80_je_to_orientacna_velicina_9c205545,
    "ui_ovladanie_mapy_45588feb" to R.string.ui_ovladanie_mapy_45588feb,
    "ui_podkladove_dlazdice_openstreetmap_sa_nenacitali_77cb8583" to R.string.ui_podkladove_dlazdice_openstreetmap_sa_nenacitali_77cb8583,
    "ui_podrobnosti_4a928310" to R.string.ui_podrobnosti_4a928310,
    "ui_pomala_7b30c785" to R.string.ui_pomala_7b30c785,
    "ui_poslednych_120_minut_7a0b6f9e" to R.string.ui_poslednych_120_minut_7a0b6f9e,
    "ui_poslednych_60_minut_e647f479" to R.string.ui_poslednych_60_minut_e647f479,
    "ui_pozastavit_f7ac504e" to R.string.ui_pozastavit_f7ac504e,
    "ui_prehrat_9b880ad3" to R.string.ui_prehrat_9b880ad3,
    "ui_prekrytia_8b855b5d" to R.string.ui_prekrytia_8b855b5d,
    "ui_priehladnost_vrstvy_7515278a" to R.string.ui_priehladnost_vrstvy_7515278a,
    "ui_radar_524f8327" to R.string.ui_radar_524f8327,
    "ui_radar_a_satelit_dea02d0a" to R.string.ui_radar_a_satelit_dea02d0a,
    "ui_rychla_b8d46a57" to R.string.ui_rychla_b8d46a57,
    "ui_rychlost_animacie_fc172f06" to R.string.ui_rychlost_animacie_fc172f06,
    "ui_satelit_4a02d9f5" to R.string.ui_satelit_4a02d9f5,
    "ui_sipky_pohybu_10b141be" to R.string.ui_sipky_pohybu_10b141be,
    "ui_skusit_znova_69d3c884" to R.string.ui_skusit_znova_69d3c884,
    "ui_spat_na_lokalitu_f2fe9805" to R.string.ui_spat_na_lokalitu_f2fe9805,
    "ui_typ_vrstvy_55f95de0" to R.string.ui_typ_vrstvy_55f95de0,
    "ui_v_zobrazenej_oblasti_librewxr_nevratil_ziadne_vystrahy_pok_025fb013" to R.string.ui_v_zobrazenej_oblasti_librewxr_nevratil_ziadne_vystrahy_pok_025fb013,
    "ui_vrstvy_5ad3c03c" to R.string.ui_vrstvy_5ad3c03c,
    "ui_vsetky_dostupne_snimky_9f6a695d" to R.string.ui_vsetky_dostupne_snimky_9f6a695d,
    "ui_vybrana_lokalita_c1fe9cc3" to R.string.ui_vybrana_lokalita_c1fe9cc3,
    "ui_vystraha_a0ff184b" to R.string.ui_vystraha_a0ff184b,
    "ui_vystrahy_d88f15ec" to R.string.ui_vystrahy_d88f15ec,
    "ui_zavaznost_69005d4e" to R.string.ui_zavaznost_69005d4e,
)

internal fun localizedMapPage(context: Context): ByteArray {
    val strings = MapStringIds.mapValues { (_, id) -> context.getString(id) }
    val template = context.assets.open("map/librewxr_map.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
    val html = Regex("""\{\{pw:([a-z0-9_]+)\}\}""").replace(template) { match ->
        TextUtils.htmlEncode(strings.getValue(match.groupValues[1]))
    }
    val json = JSONObject(strings).toString()
        .replace("<", "\\u003c")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
    return html.replace("__PERUN_I18N_JSON__", json).toByteArray(Charsets.UTF_8)
}
