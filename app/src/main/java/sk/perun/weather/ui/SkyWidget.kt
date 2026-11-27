package sk.perun.weather.ui

import sk.perun.weather.R

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import sk.perun.weather.ui.theme.PerunTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

// The colours of the Sun and Moon screen.
private val SwSun=0xFFFFD166.toInt()
private val SwRise=0xFF66D17A.toInt()
private val SwSet=0xFFFF8F6B.toInt()
private val SwLit=0xFFE8EAF0.toInt()
private val SwShade=0xFF3A4352.toInt()

/** Sunrise, sunset and the length of the day; the same calculation as the Sun and Moon screen. */
private class SwSunDay(val rise:Instant?,val set:Instant?,val seconds:Long)
private fun swSunDay(date:LocalDate,zone:ZoneId,lat:Double,lon:Double):SwSunDay {
    val start=date.atStartOfDay(zone).toInstant();val end=date.plusDays(1).atStartOfDay(zone).toInstant()
    var previousTime=start;var previous=SunMoonMath.position(start,lat,lon).sun+0.833
    var anyUp=previous>=0;var anyDown=previous<0;var rise:Instant?=null;var set:Instant?=null
    var t=start.plusSeconds(300)
    while(t<=end) {
        val v=SunMoonMath.position(t,lat,lon).sun+0.833
        if(v>=0) anyUp=true else anyDown=true
        val step=t.epochSecond-previousTime.epochSecond
        if(previous<0 && v>=0 && rise==null) rise=previousTime.plusSeconds((-previous/(v-previous)*step).toLong())
        if(previous>=0 && v<0 && set==null) set=previousTime.plusSeconds((-previous/(v-previous)*step).toLong())
        previous=v;previousTime=t;t=t.plusSeconds(300)
    }
    val r=rise;val s=set
    val seconds=when { r!=null && s!=null && s>r -> s.epochSecond-r.epochSecond;!anyDown -> 86400L;!anyUp -> 0L;else -> -1L }
    return SwSunDay(r,s,seconds)
}
/** Time the Moon spends above the horizon within the local day, in seconds; the same calculation as the Sun and Moon screen. */
private fun swMoonUp(date:LocalDate,zone:ZoneId,lat:Double,lon:Double):Long {
    val start=date.atStartOfDay(zone).toInstant();val end=date.plusDays(1).atStartOfDay(zone).toInstant()
    var previousTime=start;var previous=SunMoonMath.position(start,lat,lon).moon;var total=0.0
    var t=start.plusSeconds(600)
    while(t<=end) {
        val v=SunMoonMath.position(t,lat,lon).moon;val step=(t.epochSecond-previousTime.epochSecond).toDouble()
        total+=when {
            previous>=0 && v>=0 -> step
            previous<0 && v<0 -> 0.0
            previous>=0 -> previous/(previous-v)*step
            else -> v/(v-previous)*step
        }
        previous=v;previousTime=t;t=t.plusSeconds(600)
    }
    return total.roundToLong()
}

/**
 * One value of the widget: its symbol, a short name, the value and the colour of the symbol.
 * Symbols, as on the Sun and Moon screen: 0 rise, 1 set, 2 solar noon, 3 day length, 4 rising trend, 5 falling trend, 6 Moon above the horizon; -1 none.
 */
private class SwFact(val icon:Int,val caption:String,val value:String,val color:Int)

internal class SkyWidgetConfig(val loc:String,val main:String,val bg:String,val text:String)

/** The Sun and Moon widget. It keeps its own settings and shows one body, "sun" or "moon", computed in the device; only the warnings of the place are downloaded. */
internal object SkyWidgets {
    private const val Prefs="perun_widget_sky"
    private const val Once="perun-widget-sky"
    private const val Periodic="perun-widget-sky-tick"
    private fun prefs(c:Context) = c.applicationContext.getSharedPreferences(Prefs,Context.MODE_PRIVATE)
    fun read(c:Context,id:Int):SkyWidgetConfig {
        val p=prefs(c)
        return SkyWidgetConfig(p.getString("loc_$id","active") ?: "active",if(p.getString("main_$id","sun")=="moon") "moon" else "sun",
            (p.getString("bg_$id","dark") ?: "dark").takeIf { it in PerunWidgets.Backgrounds } ?: "dark",if(p.getString("text_$id","light")=="dark") "dark" else "light")
    }
    fun write(c:Context,id:Int,w:SkyWidgetConfig) { prefs(c).edit().putString("loc_$id",w.loc).putString("main_$id",w.main).putString("bg_$id",w.bg).putString("text_$id",w.text).apply() }
    fun remove(c:Context,ids:IntArray) { val e=prefs(c).edit();ids.forEach { id -> listOf("loc_","main_","bg_","text_").forEach { e.remove(it+id) } };e.apply() }
    fun loc(c:Context,id:Int):String = read(c,id).loc
    fun ids(c:Context):IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,SkyWidget::class.java))
    fun refresh(c:Context) {
        if(ids(c).isEmpty()) return
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(Once,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<SkyWidgetWorker>().build())
    }
    // Every 30 minutes moves the Sun along its arc, keeps the next event current and checks the warnings of the place.
    fun schedule(c:Context) {
        WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(Periodic,ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<SkyWidgetWorker>(30,TimeUnit.MINUTES).build())
    }
    fun cancel(c:Context) { WorkManager.getInstance(c.applicationContext).cancelUniqueWork(Periodic) }
    /** Draws one widget; false when the place is missing. A tap opens the Sun and Moon screen. */
    fun render(c:Context,id:Int,all:List<Location>,active:Location?):Boolean {
        val cfg=read(c,id)
        val l=(if(cfg.loc=="active") null else all.firstOrNull { BackgroundRefresh.locationKey(it)==cfg.loc }) ?: active ?: return false
        val zone=runCatching { ZoneId.of(l.timezone) }.getOrElse { ZoneId.systemDefault() }
        val manager=AppWidgetManager.getInstance(c);val o=manager.getAppWidgetOptions(id)
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 160
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 160
        val cols=if(wDp<120) 1 else if(wDp<180) 2 else if(wDp<250) 3 else 4
        val rows=if(hDp<100) 1 else 2
        val dn=c.resources.displayMetrics.density
        // 10 dp at the sides as in the Meteogram widget; one cell of width keeps 6 dp. Above and below 10 dp at two rows and 6 dp at one row, which is too low for more.
        val padH=if(wDp<120) 6 else 10;val padV=if(hDp<100) 6 else 10
        val wPx=((wDp-2*padH)*dn).roundToInt().coerceIn(40,1400);val hPx=((hDp-2*padV)*dn).roundToInt().coerceIn(40,900)
        val v=RemoteViews(c.packageName,R.layout.wg_sky)
        // The warning of the next 24 hours, drawn behind the place where the widget shows it.
        val warning=widgetNextDay().let { (from,to)->widgetAlert(widgetPlaceAlerts(c,l),from,to) }
        val warningPicture=warning?.let { widgetAlertBitmap(it,(18*dn).roundToInt()) }
        v.setImageViewBitmap(R.id.wg_sky_img,skyWidgetBitmap(dn,l.name,zone,l.latitude,l.longitude,cfg.main=="moon",cfg.text=="dark",wPx,hPx,cols,rows,warningPicture,l.current))
        v.setContentDescription(R.id.wg_sky_img,l.name+" \u00B7 "+uiText(R.string.wg_name_sky)+(warning?.let { ", "+alertName(it) } ?: ""))
        v.setInt(R.id.wg_root,"setBackgroundResource",when(cfg.bg) { "glass"->R.drawable.wg_bg_glass;"light"->R.drawable.wg_bg_light;else->R.drawable.wg_bg })
        v.setViewPadding(R.id.wg_root,(padH*dn).roundToInt(),(padV*dn).roundToInt(),(padH*dn).roundToInt(),(padV*dn).roundToInt())
        val open=(c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent()).apply {
            setData(android.net.Uri.parse("perun://widget/"+id))
            putExtra("perun_route","more/sun-moon");putExtra("perun_location",BackgroundRefresh.locationKey(l))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        v.setOnClickPendingIntent(R.id.wg_root,PendingIntent.getActivity(c,10000+id,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id,v)
        return true
    }
}

/**
 * The whole widget as one picture, in the manner of the Air quality and pollen widget: a picture of the chosen body
 * (the day arc of the Sun, or the Moon in its phase) and its values, each with the symbol it has on the Sun and Moon screen.
 */
private fun skyWidgetBitmap(dn:Float,place:String,zone:ZoneId,lat:Double,lon:Double,moonMain:Boolean,darkText:Boolean,wPx:Int,hPx:Int,cols:Int,rows:Int,warning:Bitmap?=null,gps:Boolean=false):Bitmap {
    val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
    val w=wPx.toFloat();val h=hPx.toFloat()
    val fg=if(darkText) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
    val muted=if(darkText) 0xFF4B586C.toInt() else 0xFFBBC4D2.toInt()
    val track=(0x33 shl 24) or (fg and 0xFFFFFF)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val now=Instant.now();val today=now.atZone(zone).toLocalDate()
    val hm=DateTimeFormatter.ofPattern("HH:mm")
    fun clock(t:Instant?)=t?.atZone(zone)?.format(hm) ?: "\u2013"
    fun span(seconds:Long)=uiText(R.string.ui_value_h_value_min_49a3b581,seconds/3600,(seconds%3600)/60)
    val riseWord=uiText(R.string.wg_sky_rise);val setWord=uiText(R.string.wg_sky_set)

    // The values of the chosen body. The Sun: rise, solar noon, set and the change of the day length. The Moon: rise, set, time above the horizon and the trend.
    // pair holds the rise and the set for the sizes that show only these two.
    var sunRise:Instant?=null;var sunSet:Instant?=null
    var illum=0;var waxing=true;var dayLength=""
    val facts:List<SwFact>;val pair:List<SwFact>;val lead:SwFact;val headline:String;val rowHead:String;val rowSub:String;val headColor:Int;val nextIcon:Int;val nextText:String
    if(moonMain) {
        val events=SunMoonMath.moonEvents(today,zone,lat,lon)
        val point=SunMoonMath.position(now,lat,lon)
        waxing=SunMoonMath.position(now.plusSeconds(43200),lat,lon).illumination>=SunMoonMath.position(now.minusSeconds(43200),lat,lon).illumination
        illum=point.illumination.roundToInt().coerceIn(0,100)
        val lit="$illum %";val phase=SunMoonMath.phaseName(point.phase)
        facts=listOf(SwFact(0,riseWord,clock(events.first),SwRise),SwFact(1,setWord,clock(events.second),SwSet),
            SwFact(6,uiText(R.string.wg_sky_up),span(swMoonUp(today,zone,lat,lon)),muted),
            SwFact(if(waxing) 4 else 5,uiText(R.string.wg_sky_trend),if(waxing) uiText(R.string.ui_rastie_a5341d12) else uiText(R.string.ui_cuva_c55fd1d0),if(waxing) SwRise else SwSet))
        pair=facts.take(2)
        lead=SwFact(-1,uiText(R.string.wg_sky_lit),lit,muted)
        headline=phase+" \u2022 "+lit;rowHead=phase;rowSub=lit+" \u00B7 "+place;headColor=muted;nextIcon=-1;nextText=lit
    } else {
        val sun=swSunDay(today,zone,lat,lon);val before=swSunDay(today.minusDays(1),zone,lat,lon)
        val r=sun.rise;val s=sun.set
        sunRise=r;sunSet=s
        val next:Pair<Boolean,Instant?> = when {
            r!=null && now<r -> true to r
            s!=null && now<s -> false to s
            else -> true to swSunDay(today.plusDays(1),zone,lat,lon).rise
        }
        val noon=if(r!=null && s!=null && s>r) r.plusSeconds((s.epochSecond-r.epochSecond)/2) else null
        dayLength=if(sun.seconds>=0) span(sun.seconds) else "\u2013"
        // Change of the day length against yesterday, with the trend arrow of the Sun and Moon screen: up when the day grows, down when it shortens.
        val known=sun.seconds>=0 && before.seconds>=0
        val change=if(known) sun.seconds-before.seconds else 0L
        val changeText=if(!known) "\u2013" else (if(change>0) "+" else if(change<0) "\u2212" else "")+uiText(R.string.sky_min_s,abs(change)/60,abs(change)%60)
        facts=listOf(SwFact(0,riseWord,clock(r),SwRise),SwFact(2,uiText(R.string.wg_sky_noon),clock(noon),SwSun),SwFact(1,setWord,clock(s),SwSet),
            SwFact(if(change<0) 5 else 4,uiText(R.string.wg_sky_change),changeText,if(!known || change==0L) muted else if(change>0) SwRise else SwSet))
        pair=listOf(facts[0],facts[2])
        val nextUp=next.first
        nextIcon=if(nextUp) 0 else 1;nextText=clock(next.second);headColor=if(nextUp) SwRise else SwSet
        lead=SwFact(nextIcon,if(nextUp) riseWord else setWord,nextText,headColor)
        headline=lead.caption+" "+nextText;rowHead=headline;rowSub=place
    }

    fun font(size:Float,bold:Boolean) { p.style=Paint.Style.FILL;p.textSize=size*dn;p.typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    fun text(s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT) { font(size,bold);p.color=color;p.textAlign=align;cv.drawText(s,x,y,p) }
    fun width(s:String,size:Float,bold:Boolean=false):Float { font(size,bold);return p.measureText(s) }
    fun fit(s:String,size:Float,bold:Boolean,room:Float):String {
        font(size,bold)
        if(p.measureText(s)<=room) return s
        var t=s
        while(t.length>1 && p.measureText(t+"\u2026")>room) t=t.dropLast(1)
        return if(p.measureText(t+"\u2026")<=room) t+"\u2026" else ""
    }
    // The place with the warning of the next 24 hours behind it, as in the other widgets: the symbol a little taller than the letters,
    // 4 dp after the name and in the middle of its letters. Without a warning the text alone.
    fun placeText(s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean,room:Float,align:Paint.Align=Paint.Align.LEFT) {
        // The Current location has the location mark of the app right after the name, before the warning.
        val a=warning;val sz=(size+3)*dn;val gw=if(gps) size*dn+3*dn else 0f;val aw=(if(a!=null) sz+4*dn else 0f)+gw
        val shown=fit(s,size,bold,room-aw);val tw=width(shown,size,bold)
        val left=when(align) { Paint.Align.CENTER->x-(tw+aw)/2;Paint.Align.RIGHT->x-tw-aw;else->x }
        if(shown.isNotEmpty()) text(shown,left,y,size,color,bold)
        if(gps && shown.isNotEmpty()) drawGpsMark(cv,left+tw+3*dn,y-0.36f*size*dn,size*dn,color)
        if(a!=null) { val cy=y-0.36f*size*dn;val ax=(if(shown.isNotEmpty()) left+tw+4*dn else left)+gw;p.style=Paint.Style.FILL;p.color=0xFFFFFFFF.toInt();cv.drawBitmap(a,null,RectF(ax,cy-sz/2,ax+sz,cy+sz/2),p) }
    }
    fun stroke(color:Int,px:Float) { p.style=Paint.Style.STROKE;p.strokeWidth=px;p.strokeCap=Paint.Cap.ROUND;p.color=color }
    fun dot(x:Float,y:Float,color:Int,r:Float=3*dn) { p.style=Paint.Style.FILL;p.color=color;cv.drawCircle(x,y,r,p) }
    // A line through points given in the 24 x 24 grid of the symbols of the Sun and Moon screen.
    fun path(k:Float,ox:Float,oy:Float,vararg pts:Float) { for(i in 0 until pts.size/2-1) cv.drawLine(ox+pts[2*i]*k,oy+pts[2*i+1]*k,ox+pts[2*i+2]*k,oy+pts[2*i+3]*k,p) }
    // The symbols of the Sun and Moon screen in a 16 dp box whose middle is at cy: the rise and set arrows (12 x 15 dp, 2.2 dp line), solar noon,
    // the clock of the day length, the trend arrows and the Moon above the horizon.
    fun icon(kind:Int,x:Float,cy:Float,color:Int) {
        val s=16*dn;val k=s/24f;val oy=cy-s/2
        when(kind) {
            0,1 -> {
                val up=kind==0;val cx=x+s/2;val tip=if(up) cy-6.5f*dn else cy+6.5f*dn;val tail=if(up) cy+6.5f*dn else cy-6.5f*dn;val hd=3.5f*dn;val dir=if(up) 1f else -1f
                stroke(color,2.2f*dn);cv.drawLine(cx,tip,cx,tail,p);cv.drawLine(cx,tip,cx-hd,tip+dir*hd,p);cv.drawLine(cx,tip,cx+hd,tip+dir*hd,p)
            }
            2 -> { stroke(color,1.8f*k);path(k,x,oy,2.5f,19f,21.5f,19f);cv.drawArc(RectF(x+4*k,oy+11*k,x+20*k,oy+27*k),180f,180f,false,p);path(k,x,oy,12f,3.5f,12f,6f);dot(x+12*k,oy+11*k,color,3*k) }
            3 -> { stroke(color,1.8f*k);cv.drawCircle(x+12*k,oy+12*k,9*k,p);path(k,x,oy,12f,7f,12f,12f,15f,14f) }
            4 -> { stroke(color,1.8f*k);path(k,x,oy,3f,17f,9f,11f,13f,15f,21f,7f);path(k,x,oy,15f,7f,21f,7f,21f,13f) }
            5 -> { stroke(color,1.8f*k);path(k,x,oy,3f,7f,9f,13f,13f,9f,21f,17f);path(k,x,oy,15f,17f,21f,17f,21f,11f) }
            6 -> { stroke(color,1.8f*k);path(k,x,oy,2.5f,19f,21.5f,19f);cv.drawCircle(x+12*k,oy+10*k,4.5f*k,p) }
        }
        p.style=Paint.Style.FILL
    }
    // The Moon in its phase with a thin rim; the lit side follows the hemisphere, as on the Sun and Moon screen.
    fun moon(cx:Float,cy:Float,r:Float) {
        p.style=Paint.Style.FILL;p.color=SwShade;cv.drawCircle(cx,cy,r,p)
        val k=illum/100f;val right=waxing!=(lat<0)
        p.color=SwLit;cv.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),if(right) -90f else 90f,180f,true,p)
        val ex=r*abs(1-2*k);p.color=if(k<0.5f) SwShade else SwLit
        cv.drawOval(RectF(cx-ex,cy-r,cx+ex,cy+r),p)
        stroke(track,1*dn);cv.drawCircle(cx,cy,r,p);p.style=Paint.Style.FILL
    }
    // The day arc over the horizon, from the sunrise on the left to the sunset on the right. The part of the day that has passed
    // is in the colour of the Sun and the Sun stands at its end; at night the arc stays empty.
    fun arc(cx:Float,base:Float,full:Float) {
        // The picture is a square around the arc, 1/0.84 of its radius to each side. The arc is drawn a little smaller,
        // so that the glow of the Sun at the sunrise and the sunset, and the horizon, stay inside the square.
        val half=full/0.84f
        val r=min(full,half-1.9f*max(3.5f*dn,full*0.13f)).coerceAtLeast(1f);val side=min(5*dn,half-r)
        val box=RectF(cx-r,base-r,cx+r,base+r);val thick=max(2*dn,r*0.09f)
        stroke(track,thick);cv.drawArc(box,180f,180f,false,p)
        stroke(muted,1*dn);cv.drawLine(cx-r-side,base,cx+r+side,base,p)
        // The dots of the sunrise and the sunset first, so that the Sun is drawn over them.
        dot(cx-r,base,SwRise,2.5f*dn);dot(cx+r,base,SwSet,2.5f*dn)
        val r0=sunRise;val s0=sunSet
        if(r0!=null && s0!=null && s0>r0 && now>=r0 && now<=s0) {
            val f=((now.epochSecond-r0.epochSecond).toFloat()/(s0.epochSecond-r0.epochSecond)).coerceIn(0f,1f)
            stroke(SwSun,thick);cv.drawArc(box,180f,180f*f,false,p)
            val a=PI*(1-f);val sx=cx+r*cos(a).toFloat();val sy=base-r*sin(a).toFloat();val d=max(3.5f*dn,r*0.13f)
            dot(sx,sy,(0x55 shl 24) or (SwSun and 0xFFFFFF),d*1.9f);dot(sx,sy,SwSun,d)
        }
    }
    // The picture of the chosen body in a square of the given size around its centre; the length of the day can stand under the horizon of the Sun.
    fun picture(cx:Float,cy:Float,s:Float,withLength:Boolean=false) {
        if(moonMain) { moon(cx,cy,s*0.42f);return }
        val r=s*0.42f;val below=if(withLength) 16*dn else 0f
        val base=cy+(r-below)/2
        arc(cx,base,r)
        if(withLength) text(dayLength,cx,base+14*dn,11f,fg,false,Paint.Align.CENTER)
    }
    // A value: its symbol, the name above the value; cy is its middle. The value gets a little smaller before it is cut.
    fun column(x:Float,cy:Float,room:Float,f:SwFact) {
        val tx=if(f.icon>=0) x+20*dn else x
        if(f.icon>=0) icon(f.icon,x,cy,f.color)
        val tr=room-(tx-x)-4*dn
        text(fit(f.caption,9f,false,tr),tx,cy-3.5f*dn,9f,muted)
        var vs=12f
        while(vs>10f && width(f.value,vs,true)>tr) vs-=0.5f
        text(fit(f.value,vs,true,tr),tx,cy+10.5f*dn,vs,fg,true)
    }
    // The width a value takes: its symbol and the wider of its name and its value.
    fun columnWidth(f:SwFact):Float = (if(f.icon>=0) 20*dn else 0f)+max(width(f.caption,9f),width(f.value,12f,true))
    // Values in one row: the first starts at x0, the last ends at the right edge and the gaps between them are equal.
    // When they do not fit, the room is shared evenly.
    fun spread(x0:Float,cy:Float,items:List<SwFact>) {
        val widths=items.map { columnWidth(it) }
        val gap=if(items.size>1) (w-x0-widths.sum())/(items.size-1) else 0f
        if(items.size<2 || gap<6*dn) { val cw=(w-x0)/items.size;items.forEachIndexed { k,f -> column(x0+k*cw,cy,cw,f) };return }
        var x=x0
        items.forEachIndexed { k,f -> column(x,cy,widths[k]+6*dn,f);x+=widths[k]+gap }
    }

    if(cols==1) {
        // One cell: the picture, and below it the next sunrise or sunset, or the lit part of the Moon.
        picture(w/2,(h-18*dn)/2,min(w,h-18*dn))
        if(nextIcon<0) text(nextText,w/2,h-3*dn,12f,fg,true,Paint.Align.CENTER)
        else { val x=(w-width(nextText,12f,true)-18*dn)/2;icon(nextIcon,x,h-8*dn,headColor);text(nextText,x+18*dn,h-3*dn,12f,fg,true) }
    } else if(rows==1) {
        val s=min(h,w*0.4f)
        picture(s/2,h/2,s)
        val x0=s+8*dn
        if(cols==2) column(x0,h/2,w-x0,lead)
        else if(cols==3) spread(x0,h/2,pair)
        else if(!moonMain) {
            // The Sun at four cells: rise, solar noon and set. Five cells add the change of the day length, which gets a wider column.
            val total=w-x0
            spread(x0,h/2,if(total/4>=62*dn) facts else facts.take(3))
        } else {
            // The Moon at four cells and more: the phase with the lit part and the place below it, then the rise and the set;
            // when the room left for the phase stays at least 56 dp, also the trend. The phase and the place shorten first.
            val values=listOf(facts[0],facts[1],facts[3]);val gap=8*dn;val room=w-x0
            fun need(n:Int):Float = values.take(n).sumOf { columnWidth(it).toDouble() }.toFloat()+gap*n
            val n=if(room-need(3)>=56*dn) 3 else 2
            val block=min(130*dn,room-need(n)).coerceAtLeast(36*dn)
            text(fit(rowHead,13f,true,block),x0,h/2-2*dn,13f,fg,true)
            placeText(rowSub,x0,h/2+12*dn,10f,muted,false,block)
            spread(x0+block+gap,h/2,values.take(n))
        }
    } else if(cols==2) {
        // Two by two: the place on top, the picture in the middle, rise on the left and set on the right below.
        placeText(place,w/2,11*dn,11f,muted,false,w,Paint.Align.CENTER)
        val top=16*dn;val bottom=h-22*dn
        picture(w/2,(top+bottom)/2,min(w*0.9f,bottom-top))
        icon(0,0f,h-8*dn,SwRise);text(pair[0].value,18*dn,h-3*dn,12f,fg)
        val sw=width(pair[1].value,12f)
        icon(1,w-sw-18*dn,h-8*dn,SwSet);text(pair[1].value,w,h-3*dn,12f,fg,false,Paint.Align.RIGHT)
    } else {
        val top=22*dn;val area=h-top
        if(moonMain) {
            // The Moon in two rows, three cells and more: the place on top and the phase on the right, from four cells with its lit part;
            // the picture on the left, the values on the right.
            val head=fit(if(cols==3) rowHead else headline,12f,false,w*0.58f)
            text(head,w,13*dn,12f,fg,false,Paint.Align.RIGHT)
            if(head.isNotEmpty()) dot(w-width(head,12f)-7*dn,9*dn,headColor)
            placeText(place,0f,13*dn,13f,fg,true,w-width(head,12f)-20*dn)
            if(cols==3) {
                val s=min(area,w*0.48f);picture(s/2,top+area/2,s)
                val x1=s+10*dn;val step=area/3
                facts.take(3).forEachIndexed { k,f -> column(x1,top+step*(k+0.5f),w-x1,f) }
            } else {
                val s=min(area*0.85f,w*0.3f);picture(s/2,top+area/2,s)
                val x1=s+12*dn;val cw=(w-x1)/2
                // The right column ends at the right edge.
                val x2=max(x1+cw,w-max(columnWidth(facts[1]),columnWidth(facts[3])))
                // The left column stays close to the right one, as at four cells; the spare room is between the picture and the values.
                val xa=max(x1,x2-max(columnWidth(facts[0]),columnWidth(facts[2]))-14*dn)
                facts.forEachIndexed { k,f -> column(if(k%2==0) xa else x2,top+area*(if(k<2) 0.27f else 0.73f),if(k%2==0) x2-xa else w-x2+6*dn,f) }
            }
        } else {
            // The Sun in two rows, three cells and more: the place on top and the change of the day length with its trend arrow on the right.
            val trend=facts[3]
            val tw=width(trend.value,12f)
            icon(trend.icon,w-tw-20*dn,8.5f*dn,trend.color)
            text(trend.value,w,13*dn,12f,if(trend.color==muted) fg else trend.color,false,Paint.Align.RIGHT)
            placeText(place,0f,13*dn,13f,fg,true,w-tw-30*dn)
            if(cols>=4) {
                // Four cells and more: a larger day arc; to its right the rise above the solar noon and the set above the length of the day.
                // The arc is only half as tall as it is wide, so its square may be wider than the room below the header.
                val s=min(area*1.1f,w*0.4f);picture(s/2,top+area/2,s)
                val x1=s+12*dn;val cw=(w-x1)/2
                val grid=listOf(facts[0],facts[1],facts[2],SwFact(3,uiText(R.string.ui_dlzka_dna_bc509f34),dayLength,muted))
                // The right column ends at the right edge.
                val x2=max(x1+cw,w-max(columnWidth(grid[2]),columnWidth(grid[3])))
                // The left column stays close to the right one, as at four cells; the spare room is between the picture and the values.
                val xa=max(x1,x2-max(columnWidth(grid[0]),columnWidth(grid[1]))-14*dn)
                grid.forEachIndexed { k,f -> column(if(k<2) xa else x2,top+area*(if(k%2==0) 0.27f else 0.73f),if(k<2) x2-xa else w-x2+6*dn,f) }
            } else {
                // Three cells: the length of the day under the horizon of the arc; rise, solar noon and set one above another.
                val s=min(area,w*0.48f);picture(s/2,top+area/2,s,true)
                val x1=s+10*dn;val step=area/3
                facts.take(3).forEachIndexed { k,f -> column(x1,top+step*(k+0.5f),w-x1,f) }
            }
        }
    }
    return bmp
}

class SkyWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { SkyWidgets.refresh(context);SkyWidgets.schedule(context) }
    // A new size changes what the widget shows.
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { SkyWidgets.refresh(context) }
    override fun onDeleted(context:Context,ids:IntArray) { SkyWidgets.remove(context,ids) }
    override fun onDisabled(context:Context) { SkyWidgets.cancel(context) }
}

internal class SkyWidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext
        // A widget refresh moves the Current location first: precise after the refresh button, otherwise power-saving.
        CurrentLocation.update(c,precise=false,fromBackground=true)
        val store=LocationStore(c);val all=store.load()
        val active=all.getOrNull(store.loadSelected()) ?: all.firstOrNull()
        for(id in SkyWidgets.ids(c)) try { SkyWidgets.render(c,id,all,active) } catch(e:CancellationException) { throw e } catch(e:Exception) { }
        return Result.success()
    }
}

class SkyWidgetConfigActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID) { finish();return }
        val locations=LocationStore(this).load()
        val initial=SkyWidgets.read(this,id)
        val keys=listOf("active")+pickerLocations(locations).map { BackgroundRefresh.locationKey(it) }
        val names=listOf(uiText(R.string.wg_loc_active))+pickerLocations(locations).map { pickerName(it) }
        setContent {
            PerunTheme {
                Surface(Modifier.fillMaxSize(),color=Color(0xFF151A22),contentColor=Color(0xFFE8EAF0)) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(uiText(R.string.wg_config_title),style=MaterialTheme.typography.titleLarge,color=Color(0xFFE8EAF0))
                        SkyWidgetForm(locations,initial) { next ->
                            SkyWidgets.write(this@SkyWidgetConfigActivity,id,next)
                            SkyWidgets.refresh(this@SkyWidgetConfigActivity)
                            SkyWidgets.schedule(this@SkyWidgetConfigActivity)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
                            finish()
                        }
                    }
                }
            }
        }
    }
}

/** The settings of the Sun and Moon widget; shared by the screen that opens from the home screen and by the Settings of the app. */
@Composable internal fun SkyWidgetForm(locations:List<Location>,initial:SkyWidgetConfig,onSave:(SkyWidgetConfig)->Unit) {
    val keys=listOf("active")+pickerLocations(locations).map { BackgroundRefresh.locationKey(it) }
    val names=listOf(uiText(R.string.wg_loc_active))+pickerLocations(locations).map { pickerName(it) }
    var loc by remember { mutableStateOf(initial.loc) }
    var main by remember { mutableStateOf(initial.main) }
    var bg by remember { mutableStateOf(initial.bg) }
    var text by remember { mutableStateOf(initial.text) }
    val muted=Color(0xFFBBC4D2)
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(uiText(R.string.wg_location),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(names,keys.indexOf(loc).coerceAtLeast(0),{ loc=keys[it] })
        HorizontalDivider(color=Color(0x804B586C))
        Text(uiText(R.string.wg_sky_main),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(listOf(uiText(R.string.ui_slnko_d4e0d075),uiText(R.string.ui_mesiac_3ca578fb)),if(main=="moon") 1 else 0,{ main=if(it==1) "moon" else "sun" })
        HorizontalDivider(color=Color(0x804B586C))
        Text(uiText(R.string.wg_background),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(listOf(uiText(R.string.wg_bg_dark),uiText(R.string.wg_bg_glass),uiText(R.string.wg_bg_light)),PerunWidgets.Backgrounds.indexOf(bg).coerceAtLeast(0),{ bg=PerunWidgets.Backgrounds[it] })
        HorizontalDivider(color=Color(0x804B586C))
        Text(uiText(R.string.wg_text),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(listOf(uiText(R.string.wg_text_light),uiText(R.string.wg_text_dark)),if(text=="dark") 1 else 0,{ text=if(it==1) "dark" else "light" })
        Button(onClick={ onSave(SkyWidgetConfig(loc,main,bg,text)) },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_save)) }
    }
}
