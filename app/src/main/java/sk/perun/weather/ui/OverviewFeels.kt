package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Feels-like bands in °C. The limits are a guide chosen for this app, not an official standard.
private const val FeelsScaleMin=-30.0
private const val FeelsScaleMax=45.0
private class FeelsBand(val from:Double,val to:Double,val sample:Double,val name:Int)
private val FeelsBands=listOf(
    FeelsBand(Double.NEGATIVE_INFINITY,-10.0,-15.0,R.string.feels_freezing),
    FeelsBand(-10.0,0.0,-5.0,R.string.feels_very_cold),
    FeelsBand(0.0,10.0,5.0,R.string.feels_cold),
    FeelsBand(10.0,18.0,14.0,R.string.feels_cool),
    FeelsBand(18.0,26.0,22.0,R.string.feels_pleasant),
    FeelsBand(26.0,32.0,29.0,R.string.feels_warm),
    FeelsBand(32.0,Double.POSITIVE_INFINITY,36.0,R.string.feels_hot),
)
private fun feelsBand(apparent:Double):FeelsBand? = if(apparent.isFinite()) FeelsBands.firstOrNull { apparent>=it.from && apparent<it.to } else null

/** Short text for the dot of the feels-like row, for example 0° · Cold. */
internal fun feelsSummary(apparent:Double,u:WeatherUnits):String {
    val band=feelsBand(apparent) ?: return uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61)
    return overviewNumber(u.temperature.fromCelsius(apparent),"°")+" · "+uiText(band.name)
}

/** Feels-like row on Now. A tap on the dot shows the value with its band, a tap on the value opens the forecast, a long press on either opens the help sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun FeelsRow(apparent:Double,temperature:Double,u:WeatherUnits,onOpen:()->Unit,onHelp:(HelpSpec)->Unit) {
    val help={ onHelp(HelpSpec(uiText(R.string.ui_pocitova_teplota_a08f2e01)) { FeelsHelp(apparent,temperature,u) }) }
    Row(verticalAlignment=Alignment.CenterVertically) {
        TileDot(tempDotColor(apparent),uiText(R.string.ui_pocitova_teplota_a08f2e01),false,feelsSummary(apparent,u),help)
        Text(uiText(R.string.ui_pocitovo_e11312e3)+overviewNumber(u.temperature.fromCelsius(apparent),"°"),Modifier.combinedClickable(onLongClick=help,onClick=onOpen),color=tempDotColor(apparent))
    }
}

/** Body of the feels-like help sheet on Now: explanation, the current value on a colour scale and the bands. */
@Composable internal fun FeelsHelp(apparent:Double,temperature:Double,u:WeatherUnits) {
    fun degrees(c:Double)=overviewNumber(u.temperature.fromCelsius(c),"°")
    HelpText(uiText(R.string.ui_pocitova_teplota_zohladnuje_vietor_vlhkost_a_slnecne_ziare_1f74486b))
    val current=feelsBand(apparent)
    if(current!=null) {
        Text(uiText(R.string.feels_now,degrees(apparent),uiText(current.name)),color=tempDotColor(apparent),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        if(temperature.isFinite()) HelpText(uiText(R.string.feels_air,degrees(temperature)))
        FeelsScale(apparent)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(degrees(FeelsScaleMin),color=ForecastMuted,fontSize=11.sp)
            Text(degrees(FeelsScaleMax),color=ForecastMuted,fontSize=11.sp)
        }
    }
    HelpText(uiText(R.string.feels_scale_note))
    FeelsBands.forEach { b ->
        val range=when {
            b.from==Double.NEGATIVE_INFINITY -> uiText(R.string.feels_range_below,degrees(b.to))
            b.to==Double.POSITIVE_INFINITY -> uiText(R.string.feels_range_above,degrees(b.from))
            else -> uiText(R.string.feels_range_between,degrees(b.from),degrees(b.to))
        }
        HelpRow(tempDotColor(b.sample),uiText(b.name),range)
    }
}

// Colour bar in the temperature colours of the app with a marker at the current feels-like value.
@Composable private fun FeelsScale(apparent:Double) {
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val barTop=12.dp.toPx()
        val barHeight=10.dp.toPx()
        val steps=30
        val colors=(0..steps).map { tempDotColor(FeelsScaleMin+(FeelsScaleMax-FeelsScaleMin)*it/steps) }
        drawRoundRect(brush=Brush.horizontalGradient(colors),topLeft=Offset(0f,barTop),size=Size(size.width,barHeight),cornerRadius=CornerRadius(barHeight/2f))
        val fraction=((apparent-FeelsScaleMin)/(FeelsScaleMax-FeelsScaleMin)).coerceIn(0.0,1.0).toFloat()
        val half=5.dp.toPx()
        val x=(fraction*size.width).coerceIn(half,(size.width-half).coerceAtLeast(half))
        val marker=Color(0xFFE8EAF0)
        val triangle=Path().apply { moveTo(x,barTop-1.dp.toPx());lineTo(x-half,0f);lineTo(x+half,0f);close() }
        drawPath(triangle,marker)
        drawLine(marker,Offset(x,barTop),Offset(x,barTop+barHeight),2.dp.toPx())
    }
}
