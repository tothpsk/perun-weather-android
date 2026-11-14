package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

internal enum class OutlookMode(val label:String,val dot:Color,val info:String) {
    Temp(uiText(R.string.ui_teplota_4b720579),Color(0xFFFF6B6B),uiText(R.string.ui_najvyssia_a_najnizsia_teplota_dna_jednotky_sa_riadia_nasta_944fd0b1)),
    Rain(uiText(R.string.ui_zrazky_66d17319),Color(0xFF4FC3F7),uiText(R.string.ui_denny_uhrn_dazda_a_prehanok_a_samostatne_vyska_snehu_jedno_567afe4f)),
    Wind(uiText(R.string.ui_vietor_8a3ebf26),Color(0xFF4CAF50),uiText(R.string.ui_najsilnejsi_vietor_a_narazy_dna_ruzica_ukazuje_kam_fuka_je_d6d05be8)),
    Cloud(uiText(R.string.ui_oblacnost_b5a6e70c),Color(0xFFAEB4C0),uiText(R.string.ui_priemerna_a_najvyssia_hodinova_oblacnost_dna_v_percentach_56da6f04)),
    Uv(uiText(R.string.ui_uv_027fcbbc),Color(0xFFFFD166),uiText(R.string.ui_najvyssia_hodinova_hodnota_uv_indexu_dna_farba_a_stupen_po_a10baa15)),
    Air(uiText(R.string.ui_kvalita_ovzdusia_08946dda),Color(0xFFE8EAF0),uiText(R.string.ui_najvyssi_europsky_aqi_dna_nie_americky_index_dbdc436d))
}
private fun outlookCloudName(v:Double)=when { !v.isFinite()->uiText(R.string.ui_udaj_nedostupny_8bbc9c94);v<12.5->uiText(R.string.ui_jasno_e78eee64);v<37.5->uiText(R.string.ui_prevazne_jasno_7331ba47);v<62.5->uiText(R.string.ui_polooblacno_83f2c354);v<87.5->uiText(R.string.ui_prevazne_oblacno_e5122573);else->uiText(R.string.ui_zamracene_829159bb) }
@Composable internal fun Outlook(w:OverviewData,mode:OutlookMode,onMode:(OutlookMode)->Unit,onInfo:(String,String)->Unit) {
    if(!rememberOverviewOutlook()) return
    val u=rememberForecastPreferences().units
    val temps=w.days.flatMap { listOf(it.tmin,it.tmax) }.filter { it.isFinite() }
    val gmin=temps.minOrNull() ?: 0.0;val span=max((temps.maxOrNull() ?: 1.0)-gmin,2.0)
    val maxGust=max(1.0,w.days.maxOfOrNull { d->listOf(d.gust,d.wind).firstOrNull { it.isFinite() && it>0 } ?: 0.0 } ?: 1.0)
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),colors=CardDefaults.outlinedCardColors(containerColor=Color(0xFF262B36),contentColor=Color(0xFFE8EAF0))) {
        Column(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                InfoDot(mode.dot,uiText(R.string.ui_vyhlad_na_5_dni_90f1b8c5)) { onInfo(mode.label,mode.info) }
                Text(uiText(R.string.ui_vyhlad_na_5_dni_90f1b8c5),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            }
            PerunSegments(OutlookMode.entries.map { it.label },mode.ordinal,{ onMode(OutlookMode.entries[it]) })
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) { w.days.forEachIndexed { i,d->DayCard(d,i,mode,gmin,span,maxGust,u) } }
        }
    }
}
@Composable private fun Bar(start:Double,width:Double,colors:List<Color>) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val radius=CornerRadius(size.height/2f);drawRoundRect(Color(0xFF414958),cornerRadius=radius)
        val left=start.coerceIn(0.0,1.0);val w=width.coerceIn(0.0,1.0-left)
        if(w>0 && colors.isNotEmpty()) {
            val x=(left*size.width).toFloat();val ww=(w*size.width).toFloat()
            val brush=if(colors.size==1) SolidColor(colors[0]) else Brush.horizontalGradient(colors,startX=x,endX=x+ww)
            drawRoundRect(brush,Offset(x,0f),Size(ww,size.height),radius)
        }
    }
}
@Composable private fun Value(v:Double,unit:String,color:Color=Color(0xFFE8EAF0),decimals:Int=0) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=color)) { append(weatherUnitNumber(v,decimals)) }
        if(unit.isNotEmpty()) withStyle(SpanStyle(fontSize=14.sp,color=ForecastMuted)) { append(" $unit") }
    })
}
@Composable private fun Muted(text:String,color:Color=ForecastMuted) { Text(text,fontSize=14.sp,color=color,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis) }
@Composable private fun DayCard(x:OutlookDay,index:Int,mode:OutlookMode,gmin:Double,span:Double,maxGust:Double,u:WeatherUnits) {
    val shape=RoundedCornerShape(14.dp)
    val locale=appLocale()
    val weekday=x.date.format(DateTimeFormatter.ofPattern("EEEE",locale)).replaceFirstChar { it.titlecase(locale) }
    Column(Modifier.width(126.dp).clip(shape).background(Color(0x7519212D)).border(1.dp,Color(0xFF4B586C),shape).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Column { Text(if(index==0) uiText(R.string.ui_dnes_ce1bed34) else weekday,fontSize=16.sp,fontWeight=FontWeight.Bold);Text(x.date.format(DateTimeFormatter.ofPattern(appPattern("d.M."),appLocale())),fontSize=14.sp,color=ForecastMuted) }
        if(mode==OutlookMode.Wind && x.wind.isFinite() && x.wind>0 && x.windDirection.isFinite()) WindCompass(x.windDirection,ForecastGreen,Modifier.size(54.dp)) else WeatherSymbol(x.code,54.dp)
        when(mode) {
            OutlookMode.Temp -> {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontSize=24.sp,fontWeight=FontWeight.SemiBold)) { append(overviewNumber(u.temperature.fromCelsius(x.tmax),"°")) }
                    withStyle(SpanStyle(fontSize=14.sp,color=ForecastMuted)) { append(" / "+overviewNumber(u.temperature.fromCelsius(x.tmin),"°")) }
                })
                if(x.tmin.isFinite() && x.tmax.isFinite()) Bar((x.tmin-gmin)/span,max(.08,(x.tmax-x.tmin)/span),listOf(tempDotColor(x.tmin),tempDotColor(x.tmax))) else Bar(0.0,0.0,emptyList())
                Muted(overviewCondition(x.code))
            }
            OutlookMode.Rain -> {
                val eq=x.liquid+x.snow;val has=eq>0;val type=precipitationType(x.rain,x.showers,x.snow)
                if(!has) Text(uiText(R.string.ui_ziadne_aa1757a8),fontSize=22.sp,fontWeight=FontWeight.SemiBold) else {
                    if(x.liquid>0) Value(u.precipitation.fromMillimetres(x.liquid),u.precipitation.liquidSymbol,RainBlue,if(u.precipitation==PrecipitationUnit.Imperial || x.liquid<.1) 2 else 1)
                    if(x.snow>0) Muted(overviewNumber(u.precipitation.fromSnowCentimetres(x.snow)," ${u.precipitation.snowSymbol}",if(u.precipitation==PrecipitationUnit.Imperial || x.snow<.1) 2 else 1),Color(0xFFE8EAF0))
                }
                Bar(0.0,if(has) max(.06,min(1.0,sqrt(eq/20.0))) else 0.0,listOf(type ?: RainBlue))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Umbrella(probabilityColor(x.probability),22.dp);Text(overviewNumber(x.probability," %"),fontSize=14.sp,fontWeight=FontWeight.Bold) }
            }
            OutlookMode.Wind -> {
                Value(u.wind.fromKmh(x.wind),u.wind.symbol,windAlert(x.wind) ?: Color(0xFFE8EAF0),if(u.wind==WindSpeedUnit.MetresPerSecond) 1 else 0)
                val top=listOf(x.gust,x.wind).firstOrNull { it.isFinite() && it>0 } ?: 0.0
                Bar(0.0,max(.06,min(1.0,top/maxGust)),listOf(windAlert(x.gust) ?: Color(0xFF4CAF50)))
                Text(uiText(R.string.ui_narazy_df599680)+overviewNumber(u.wind.fromKmh(x.gust)," ${u.wind.symbol}",if(u.wind==WindSpeedUnit.MetresPerSecond) 1 else 0),fontSize=13.sp,color=windAlert(x.gust) ?: ForecastMuted,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
            }
            OutlookMode.Cloud -> { Value(x.cloudMean,"%");Bar(0.0,if(x.cloudMean.isFinite()) max(.04,min(1.0,x.cloudMean/100.0)) else 0.0,listOf(Color(0xFF8294AD)));Muted(outlookCloudName(x.cloudMean)) }
            OutlookMode.Uv -> { val tint=uvColor(x.uv);Value(x.uv,"",tint);Bar(0.0,if(x.uv.isFinite()) max(.04,min(1.0,x.uv/11.0)) else 0.0,listOf(tint));Muted(uvLabel(x.uv),tint) }
            OutlookMode.Air -> { val band=aqiBand(x.aqi);val tint=band?.color ?: Color(0xFFE8EAF0);Value(x.aqi,"",tint);Bar(0.0,if(x.aqi.isFinite()) max(.04,min(1.0,x.aqi/100.0)) else 0.0,listOf(tint));Muted(band?.name ?: uiText(R.string.ui_udaj_nedostupny_8bbc9c94),band?.color ?: ForecastMuted) }
        }
    }
}
