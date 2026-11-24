package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun bftRange(i:Int):String {
    fun f(v:Double)=weatherUnitNumber(v,if(v%1.0==0.0) 0 else 1)
    return if(i==BeaufortLimits.lastIndex) uiText(R.string.ui_od_value_km_h_a1ba8bc3, f(BeaufortLimits[i])) else uiText(R.string.ui_value_value_km_h_47ef61f2, f(BeaufortLimits[i]), f(BeaufortLimits[i+1]))
}

/** The four main Moon phases drawn next to their names; used in the Moon help sheets instead of a text list. */
@Composable internal fun MoonPhaseHelp(southern:Boolean=false) {
    listOf(
        Triple(0,true,uiText(R.string.ui_nov_3e630d29)),
        Triple(50,true,uiText(R.string.ui_prva_stvrt_4ecd8721)),
        Triple(100,true,uiText(R.string.ui_spln_cc87a236)),
        Triple(50,false,uiText(R.string.ui_posledna_stvrt_7a12e0f9)),
    ).forEach { (illumination,waxing,name) ->
        Row(verticalAlignment=Alignment.CenterVertically) {
            MoonGraphic(illumination,waxing,southern,Modifier.size(26.dp))
            Text(name+" \u00B7 "+illumination+" %",Modifier.padding(start=10.dp),fontWeight=FontWeight.SemiBold,fontSize=15.sp)
        }
    }
}

@Composable internal fun TileHelp(id:OverviewTileId) {
    when(id) {
        OverviewTileId.Precipitation -> {
            HelpText(uiText(R.string.ui_uhrn_zrazok_za_posledny_interval_modelu_farba_bodky_a_hodn_2547a048))
            HelpRow(RainBlue,uiText(R.string.ui_dazd_8eda180e),uiText(R.string.ui_suvisle_zrazky_ae2adfaf))
            HelpRow(Color(0xFF2196C9),uiText(R.string.ui_prehanky_ef949e7b),uiText(R.string.ui_kratke_a_lokalne_zrazky_6fc5ecd7))
            HelpRow(Color(0xFFE8EAF0),uiText(R.string.ui_sneh_27400f08),uiText(R.string.ui_vyska_snehu_je_v_cm_alebo_palcoch_c81b99fc))
            HelpRow(Color(0xFFB388FF),uiText(R.string.ui_dazd_so_snehom_586507c0),uiText(R.string.ui_zmiesane_zrazky_0c420ad6))
            HelpText(uiText(R.string.ui_dazdnik_ukazuje_pravdepodobnost_zrazok_cim_je_modrejsi_tym_bbe5b634))
        }
        OverviewTileId.Wind -> {
            HelpText(uiText(R.string.ui_vietor_vo_vyske_10_m_ruzica_ukazuje_kam_vietor_fuka_nie_od_6acbaeff))
            for(i in BeaufortNames.indices) HelpRow(windBeaufortColor(BeaufortLimits[i]),uiText(R.string.ui_value_bft_value_87b5fd30, i, BeaufortNames[i]),bftRange(i))
        }
        OverviewTileId.Uv -> {
            HelpText(uiText(R.string.ui_aktualny_modelovy_uv_index_podla_stupnice_who_816c1c7f))
            HelpRow(uvColor(1.0),uiText(R.string.ui_nizka_02_93a5db81),uiText(R.string.ui_ochrana_zvycajne_nie_je_potrebna_87975225))
            HelpRow(uvColor(4.0),uiText(R.string.ui_mierna_35_62495930),uiText(R.string.ui_v_poludnie_hladaj_tien_pouzi_klobuk_a_krem_s_ochrannym_fak_963ad988))
            HelpRow(uvColor(6.0),uiText(R.string.ui_vysoka_67_61c69e71),uiText(R.string.ui_obmedz_pobyt_na_slnku_v_poludnajsich_hodinach_pouzi_spf_30_3591c93c))
            HelpRow(uvColor(8.0),uiText(R.string.ui_velmi_vysoka_810_4fcf7a45),uiText(R.string.ui_v_poludnie_sa_slnku_vyhybaj_pouzivaj_spf_50_oblecenie_a_ok_211537fe))
            HelpRow(uvColor(11.0),uiText(R.string.ui_extremna_11_a_viac_bed08146),uiText(R.string.ui_dlhy_pobyt_vonku_v_poludnie_sa_neodporuca_068ddfa4))
        }
        OverviewTileId.Cloud -> {
            HelpText(uiText(R.string.ui_celkova_modelova_oblacnost_v_percentach_oblak_sa_naplna_zd_83740492))
            HelpRow(Color(0xFFB8C4D8),uiText(R.string.ui_jasno_e78eee64),uiText(R.string.ui_menej_ako_12_5_8952dd78))
            HelpRow(Color(0xFFA0AEC4),uiText(R.string.ui_prevazne_jasno_7331ba47),"12,5 – 37,5 %")
            HelpRow(Color(0xFF8294AD),uiText(R.string.ui_polooblacno_83f2c354),"37,5 – 62,5 %")
            HelpRow(Color(0xFF66758C),uiText(R.string.ui_prevazne_oblacno_e5122573),"62,5 – 87,5 %")
            HelpRow(Color(0xFF4B586C),uiText(R.string.ui_zamracene_829159bb),uiText(R.string.ui_viac_ako_87_5_105ed338))
            HelpText(uiText(R.string.cloud_median_note))
        }
        OverviewTileId.Humidity -> {
            HelpText(uiText(R.string.ui_relativna_vlhkost_vzduchu_a_rosny_bod_rosny_bod_je_teplota_c1b0a236))
            HelpRow(Color(0xFFE6C27A),uiText(R.string.ui_suchy_vzduch_b65d36c7),uiText(R.string.ui_vlhkost_pod_30_moze_vysusat_pokozku_a_sliznice_cc5559eb))
            HelpRow(Color(0xFF66D17A),uiText(R.string.ui_prijemna_26c506d8),uiText(R.string.ui_rosny_bod_pod_16_0cb517ab))
            HelpRow(Color(0xFFFFB74D),uiText(R.string.ui_vlhka_8c3aea38),uiText(R.string.ui_rosny_bod_od_16_53b53fa7))
            HelpRow(Color(0xFFFF6B6B),uiText(R.string.ui_dusno_ab731e80),uiText(R.string.ui_rosny_bod_od_21_8f801694))
            HelpText(uiText(R.string.ui_pri_hodnoteni_sa_pouziva_povodny_rosny_bod_v_c_bez_ohladu_b00e4efb))
        }
        OverviewTileId.Pressure -> {
            HelpText(uiText(R.string.ui_tlak_vzduchu_prepocitany_na_hladinu_mora_a_jeho_zmena_za_p_fc20d5b7))
            HelpRow(PressureRiseFast,uiText(R.string.ui_rychlo_stupa_aea55e49),uiText(R.string.ui_zmena_3_hpa_a_viac_za_3_hodiny_9ab7197c))
            HelpRow(PressureRise,uiText(R.string.ui_stupa_c05574b4),uiText(R.string.ui_zmena_od_1_do_3_hpa_dfe2dd9f))
            HelpRow(Color(0xFF66D17A),uiText(R.string.ui_stabilny_17f01156),uiText(R.string.ui_zmena_do_1_hpa_2c7301b8))
            HelpRow(Color(0xFFFFB74D),uiText(R.string.ui_klesa_54707dc3),uiText(R.string.ui_zmena_od_1_do_3_hpa_ec84e4fe))
            HelpRow(Color(0xFFFF6B6B),uiText(R.string.ui_rychlo_klesa_1bac87f5),uiText(R.string.ui_zmena_3_hpa_a_viac_ca71accc))
            HelpText(uiText(R.string.ui_rychly_pokles_tlaku_casto_predchadza_zhorseniu_pocasia_nie_ace0ce61))
        }
        OverviewTileId.Visibility -> {
            HelpText(uiText(R.string.ui_modelova_horizontalna_viditelnost_v_km_d7a2ed32))
            HelpRow(Color(0xFF66D17A),uiText(R.string.ui_dobra_19daeeb7),uiText(R.string.ui_10_km_a_viac_265d0ceb))
            HelpRow(Color(0xFFFFD166),uiText(R.string.ui_znizena_7bb856e1),uiText(R.string.ui_4_10_km_916c65ea))
            HelpRow(Color(0xFFFB8C00),uiText(R.string.ui_zla_258323d1),uiText(R.string.ui_1_4_km_42694d48))
            HelpRow(Color(0xFFE53935),uiText(R.string.ui_hmla_386a47cf),uiText(R.string.ui_menej_ako_1_km_pri_soferovani_zniz_rychlost_4dedd316))
        }
        OverviewTileId.Sun -> {
            HelpText(uiText(R.string.ui_vychod_a_zapad_slnka_v_miestnom_case_lokality_grafika_ukaz_76ec5c97))
            HelpRow(Color(0xFFFFD166),uiText(R.string.ui_den_8cdbc624),uiText(R.string.ui_bodka_je_zlata_7b37dd0d))
            HelpRow(Color(0xFF2B3550),uiText(R.string.ui_noc_c592606f),uiText(R.string.ui_bodka_je_tmavomodra_6221eed2))
        }
        OverviewTileId.Moon -> {
            HelpText(uiText(R.string.ui_vychod_a_zapad_mesiaca_sa_pocitaju_v_zariadeni_ak_mesiac_n_47d5af50))
            MoonPhaseHelp()
        }
        OverviewTileId.Air -> {
            HelpText(uiText(R.string.ui_europsky_aqi_z_cams_copernicus_cez_open_meteo_nie_je_to_am_dba1f246))
            listOf(10.0 to "0–20",30.0 to "21–40",50.0 to "41–60",70.0 to "61–80",90.0 to "81–100",120.0 to uiText(R.string.ui_nad_100_c3e214fd)).forEach { (v,range) ->
                val b=aqiBand(v)
                if(b!=null) HelpRow(b.color,"${b.name} · $range")
            }
            HelpText(uiText(R.string.ui_pri_zlej_a_horsej_kvalite_ovzdusia_obmedz_namahavu_aktivit_fa3dc0e7))
        }
        OverviewTileId.Pollen -> {
            HelpText(uiText(R.string.ui_dva_druhy_s_najvyssou_zatazou_v_aktualnej_hodine_vedla_naz_4aa38ca5))
            HelpRow(PollenLevelColors[0],uiText(R.string.ui_nizka_992049fb),uiText(R.string.ui_pod_prahom_zvysenej_urovne_b3b23adf))
            HelpRow(PollenLevelColors[1],uiText(R.string.ui_zvysena_2944aa11),uiText(R.string.ui_od_prahu_zvysenej_urovne_e27eb726))
            HelpRow(PollenLevelColors[2],uiText(R.string.ui_vysoka_35517062),uiText(R.string.ui_od_prahu_vysokej_urovne_4bfe789b))
            HelpText(uiText(R.string.ui_prahy_zvysena_vysoka_jelsa_50_200_breza_50_400_trava_20_50_b9196393))
        }
        OverviewTileId.Alerts -> {
            HelpText(uiText(R.string.ui_oficialne_vystrahy_a_lokalne_odhady_z_predpovede_farba_zod_2d393e9d))
            HelpRow(Color(0xFFFDD835),uiText(R.string.ui_zlta_64273c3a),uiText(R.string.ui_bud_pozorny_fe1c266c))
            HelpRow(Color(0xFFFB8C00),uiText(R.string.ui_oranzova_ab35bb41),uiText(R.string.ui_nebezpecne_pocasie_626c5a11))
            HelpRow(Color(0xFFE53935),uiText(R.string.ui_cervena_f1d8f5c8),uiText(R.string.ui_velmi_nebezpecne_pocasie_f539aaf7))
        }
    }
}
