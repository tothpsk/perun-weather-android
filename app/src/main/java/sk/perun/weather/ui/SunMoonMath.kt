package sk.perun.weather.ui

import sk.perun.weather.R

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

internal data class SkyPoint(val time:Instant,val sun:Double,val moon:Double,val illumination:Double,val phase:Int)
// Direct port of chart.html astronomical helpers. Low-precision geometric positions.
internal object SunMoonMath {
    private fun rad(v:Double)=v*PI/180.0
    private fun deg(v:Double)=v*180.0/PI
    private fun norm(v:Double)=((v%360.0)+360.0)%360.0
    private fun altitude(ra:Double,dec:Double,jd:Double,lat:Double,lon:Double):Double {
        val t=(jd-2451545.0)/36525.0
        val sidereal=norm(280.46061837+360.98564736629*(jd-2451545.0)+.000387933*t*t-t*t*t/38710000.0+lon)
        val h=norm(sidereal-ra);val hour=rad(if(h>180) h-360 else h)
        return deg(asin((sin(rad(lat))*sin(rad(dec))+cos(rad(lat))*cos(rad(dec))*cos(hour)).coerceIn(-1.0,1.0)))
    }
    fun position(time:Instant,lat:Double,lon:Double):SkyPoint {
        require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
        val jd=time.epochSecond/86400.0+2440587.5;val t=(jd-2451545.0)/36525.0;val eps=rad(23.439281)
        val mean=rad(norm(357.52911+35999.05029*t))
        val solarLon=rad(norm(280.46646+36000.76983*t+(1.914602-.004817*t)*sin(mean)+.019993*sin(2*mean)))
        val sunRa=norm(deg(atan2(cos(eps)*sin(solarLon),cos(solarLon))))
        val sunDec=deg(asin(sin(eps)*sin(solarLon)))
        val lp=norm(218.3164477+481267.88123421*t)
        val d=rad(norm(297.8501921+445267.1114034*t));val m=rad(norm(357.5291092+35999.0502909*t))
        val p=rad(norm(134.9633964+477198.8675055*t));val f=rad(norm(93.2720950+483202.0175233*t))
        val ml=lp+6.289*sin(p)-1.274*sin(2*d-p)+.658*sin(2*d)-.186*sin(m)-.059*sin(2*p-2*d)-.057*sin(p-2*d+m)+.053*sin(p+2*d)+.046*sin(2*d-m)+.041*sin(p-m)-.035*sin(d)-.031*sin(p+m)
        val mb=rad(5.128*sin(f)+.281*sin(p+f)-.278*sin(f-p)-.173*sin(2*d-f)+.055*sin(2*d+f-p)+.046*sin(2*d-f-p)+.033*sin(f+2*p))
        val lr=rad(norm(ml));val moonRa=norm(deg(atan2(sin(lr)*cos(eps)-tan(mb)*sin(eps),cos(lr))))
        val moonDec=deg(asin(sin(mb)*cos(eps)+cos(mb)*sin(eps)*sin(lr)))
        val elongation=norm(ml-norm(280.4665+36000.7698*t))
        return SkyPoint(time,altitude(sunRa,sunDec,jd,lat,lon),altitude(moonRa,moonDec,jd,lat,lon),(1-cos(rad(elongation)))/2*100,floor(elongation/45+.5).toInt()%8)
    }
    fun times(date:LocalDate,zone:ZoneId,days:Int,stepHours:Int):List<Instant> {
        require(days in 1..16 && stepHours in 1..6)
        val out=mutableListOf<Instant>();var t=date.atStartOfDay(zone).toInstant();val end=date.plusDays(days.toLong()).atStartOfDay(zone).toInstant()
        while(t<end) { out+=t;t=t.plusSeconds(stepHours*3600L) }
        return out
    }
    fun moonEvents(date:LocalDate,zone:ZoneId,lat:Double,lon:Double):Pair<Instant?,Instant?> {
        val start=date.atStartOfDay(zone).toInstant();val end=date.plusDays(1).atStartOfDay(zone).toInstant()
        var previous=position(start,lat,lon).moon;var t=start.plusSeconds(600);var rise:Instant?=null;var set:Instant?=null
        while(t<end) {
            val value=position(t,lat,lon).moon
            if(previous<0 && value>=0 && rise==null) rise=t
            if(previous>=0 && value<0 && set==null) set=t
            previous=value;t=t.plusSeconds(600)
        }
        return rise to set
    }
    fun phaseName(index:Int)=listOf(uiText(R.string.ui_nov_3e630d29),uiText(R.string.ui_dorastajuci_kosacik_8e90f090),uiText(R.string.ui_prva_stvrt_4ecd8721),uiText(R.string.ui_dorastajuci_mesiac_0d9af424),uiText(R.string.ui_spln_cc87a236),uiText(R.string.ui_cuvajuci_mesiac_4a6a004c),uiText(R.string.ui_posledna_stvrt_7a12e0f9),uiText(R.string.ui_cuvajuci_kosacik_03e67d5f))[index.mod(8)]
}
