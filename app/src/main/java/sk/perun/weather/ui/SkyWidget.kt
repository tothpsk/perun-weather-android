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

/** One value of the widget: a short name, the value and the colour of its bar. */
private class SwFact(val caption:String,val value:String,val color:Int)

internal class SkyWidgetConfig(val loc:String,val main:String,val bg:String,val text:String)

/** The Sun and Moon widget. It keeps its own settings and shows one body, "sun" or "moon". Everything is computed in the device. */
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
    // No network is needed; every 30 minutes moves the Sun along its arc and keeps the next event current.
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
        val wPx=((wDp-12)*dn).roundToInt().coerceIn(40,1400);val hPx=((hDp-12)*dn).roundToInt().coerceIn(40,900)
        val v=RemoteViews(c.packageName,R.layout.wg_sky)
        v.setImageViewBitmap(R.id.wg_sky_img,skyWidgetBitmap(dn,l.name,zone,l.latitude,l.longitude,cfg.main=="moon",cfg.text=="dark",wPx,hPx,cols,rows))
        v.setContentDescription(R.id.wg_sky_img,l.name+" \u00B7 "+uiText(R.string.wg_name_sky))
        v.setInt(R.id.wg_root,"setBackgroundResource",when(cfg.bg) { "glass"->R.drawable.wg_bg_glass;"light"->R.drawable.wg_bg_light;else->R.drawable.wg_bg })
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
 * (the day arc of the Sun, or the Moon in its phase) and its values as columns with a coloured bar, as many as the size allows.
 */
private fun skyWidgetBitmap(dn:Float,place:String,zone:ZoneId,lat:Double,lon:Double,moonMain:Boolean,darkText:Boolean,wPx:Int,hPx:Int,cols:Int,rows:Int):Bitmap {
    val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
    val w=wPx.toFloat();val h=hPx.toFloat()
    val fg=if(darkText) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
    val muted=if(darkText) 0xFF4B586C.toInt() else 0xFFBBC4D2.toInt()
    val track=(0x33 shl 24) or (fg and 0xFFFFFF)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val now=Instant.now();val today=now.atZone(zone).toLocalDate()
    val hm=DateTimeFormatter.ofPattern("HH:mm")
    fun clock(t:Instant?)=t?.atZone(zone)?.format(hm) ?: "\u2013"
    val riseWord=uiText(R.string.wg_sky_rise);val setWord=uiText(R.string.wg_sky_set)

    // The values of the chosen body, the most important first; the widget shows as many as fit.
    var sunRise:Instant?=null;var sunSet:Instant?=null
    var illum=0;var waxing=true
    val facts:List<SwFact>;val lead:SwFact;val headline:String;val headColor:Int;val nextUp:Boolean;val nextText:String
    if(moonMain) {
        val events=SunMoonMath.moonEvents(today,zone,lat,lon)
        val point=SunMoonMath.position(now,lat,lon)
        waxing=SunMoonMath.position(now.plusSeconds(43200),lat,lon).illumination>=SunMoonMath.position(now.minusSeconds(43200),lat,lon).illumination
        illum=point.illumination.roundToInt().coerceIn(0,100)
        val lit=SwFact(uiText(R.string.wg_sky_lit),"$illum %",muted)
        facts=listOf(SwFact(riseWord,clock(events.first),SwRise),SwFact(setWord,clock(events.second),SwSet),lit,
            SwFact(uiText(R.string.wg_sky_trend),if(waxing) uiText(R.string.ui_rastie_a5341d12) else uiText(R.string.ui_cuva_c55fd1d0),if(waxing) SwRise else SwSet))
        lead=lit;headline=SunMoonMath.phaseName(point.phase);headColor=muted;nextUp=true;nextText=lit.value
    } else {
        val sun=swSunDay(today,zone,lat,lon);val before=swSunDay(today.minusDays(1),zone,lat,lon)
        val r=sun.rise;val s=sun.set
        sunRise=r;sunSet=s
        val next:Pair<Boolean,Instant?> = when {
            r!=null && now<r -> true to r
            s!=null && now<s -> false to s
            else -> true to swSunDay(today.plusDays(1),zone,lat,lon).rise
        }
        val dayLength=if(sun.seconds>=0) uiText(R.string.ui_value_h_value_min_49a3b581,sun.seconds/3600,(sun.seconds%3600)/60) else "\u2013"
        // Change of the day length against yesterday, as on the Sun and Moon screen.
        val known=sun.seconds>=0 && before.seconds>=0
        val change=if(known) sun.seconds-before.seconds else 0L
        val changeText=if(!known) "\u2013" else (if(change>0) "+" else if(change<0) "\u2212" else "")+uiText(R.string.sky_min_s,abs(change)/60,abs(change)%60)
        facts=listOf(SwFact(riseWord,clock(r),SwRise),SwFact(setWord,clock(s),SwSet),SwFact(uiText(R.string.wg_sky_day),dayLength,SwSun),
            SwFact(uiText(R.string.wg_sky_change),changeText,if(!known || change==0L) muted else if(change>0) SwRise else SwSet))
        nextUp=next.first;nextText=clock(next.second);headColor=if(nextUp) SwRise else SwSet
        lead=SwFact(if(nextUp) riseWord else setWord,nextText,headColor)
        headline=lead.caption+" "+nextText
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
    fun stroke(color:Int,px:Float) { p.style=Paint.Style.STROKE;p.strokeWidth=px;p.strokeCap=Paint.Cap.ROUND;p.color=color }
    fun dot(x:Float,y:Float,color:Int,r:Float=3*dn) { p.style=Paint.Style.FILL;p.color=color;cv.drawCircle(x,y,r,p) }
    fun arrow(x:Float,cy:Float,s:Float,up:Boolean,color:Int) {
        stroke(color,1.8f*dn);val cx=x+s/2;val t=cy-s*0.4f;val b=cy+s*0.4f
        cv.drawLine(cx,t,cx,b,p)
        val tip=if(up) t else b;val back=if(up) t+s*0.32f else b-s*0.32f
        cv.drawLine(cx,tip,cx-s*0.3f,back,p);cv.drawLine(cx,tip,cx+s*0.3f,back,p)
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
    fun arc(cx:Float,base:Float,r:Float) {
        val box=RectF(cx-r,base-r,cx+r,base+r);val thick=max(2*dn,r*0.09f)
        stroke(track,thick);cv.drawArc(box,180f,180f,false,p)
        stroke(muted,1*dn);cv.drawLine(cx-r-5*dn,base,cx+r+5*dn,base,p)
        val r0=sunRise;val s0=sunSet
        if(r0!=null && s0!=null && s0>r0 && now>=r0 && now<=s0) {
            val f=((now.epochSecond-r0.epochSecond).toFloat()/(s0.epochSecond-r0.epochSecond)).coerceIn(0f,1f)
            stroke(SwSun,thick);cv.drawArc(box,180f,180f*f,false,p)
            val a=PI*(1-f);val sx=cx+r*cos(a).toFloat();val sy=base-r*sin(a).toFloat();val d=max(3.5f*dn,r*0.13f)
            dot(sx,sy,(0x55 shl 24) or (SwSun and 0xFFFFFF),d*1.9f);dot(sx,sy,SwSun,d)
        }
        dot(cx-r,base,SwRise,2.5f*dn);dot(cx+r,base,SwSet,2.5f*dn)
    }
    // The picture of the chosen body in a square of the given size around its centre.
    fun graphic(cx:Float,cy:Float,s:Float) { if(moonMain) moon(cx,cy,s*0.42f) else arc(cx,cy+s*0.2f,s*0.42f) }
    // A column as in the Air quality widget: a coloured bar, the name above the value; cy is its middle.
    fun column(x:Float,cy:Float,room:Float,f:SwFact) {
        p.style=Paint.Style.FILL;p.color=f.color;cv.drawRect(x,cy-11.5f*dn,x+2.5f*dn,cy+11.5f*dn,p)
        text(fit(f.caption,9f,false,room-8*dn),x+6*dn,cy-3.5f*dn,9f,muted)
        text(fit(f.value,12f,true,room-8*dn),x+6*dn,cy+10.5f*dn,12f,fg,true)
    }

    if(cols==1) {
        // One cell: the picture, and below it the next sunrise or sunset, or the lit part of the Moon.
        graphic(w/2,(h-16*dn)/2,min(w,h-16*dn))
        if(moonMain) text(nextText,w/2,h-3*dn,12f,fg,true,Paint.Align.CENTER)
        else { val x=(w-width(nextText,12f,true)-13*dn)/2;arrow(x,h-7*dn,10*dn,nextUp,headColor);text(nextText,x+13*dn,h-3*dn,12f,fg,true) }
    } else if(rows==1) {
        val s=min(h,w*0.4f)
        graphic(s/2,h/2,s)
        val x0=s+8*dn
        if(cols==2) column(x0,h/2,w-x0,lead)
        else if(cols==3) { val cw=(w-x0)/2;facts.take(2).forEachIndexed { k,f -> column(x0+k*cw,h/2,cw,f) } }
        else {
            // Four cells and more: what comes next (or the phase) with the place below it, then the columns.
            val block=min(110*dn,(w-x0)*0.4f)
            text(fit(headline,13f,true,block),x0,h/2-2*dn,13f,fg,true)
            text(fit(place,10f,false,block),x0,h/2+12*dn,10f,muted)
            val start=x0+block+6*dn
            val shown=facts.take(((w-start)/(62*dn)).toInt().coerceIn(0,facts.size))
            if(shown.isNotEmpty()) { val cw=(w-start)/shown.size;shown.forEachIndexed { k,f -> column(start+k*cw,h/2,cw,f) } }
        }
    } else if(cols==2) {
        // Two by two: the place on top, the picture in the middle, rise on the left and set on the right below.
        text(fit(place,11f,false,w),w/2,11*dn,11f,muted,false,Paint.Align.CENTER)
        val top=16*dn;val bottom=h-20*dn
        graphic(w/2,(top+bottom)/2,min(w*0.9f,bottom-top))
        arrow(0f,h-7*dn,10*dn,true,SwRise);text(facts[0].value,13*dn,h-3*dn,12f,fg)
        val sw=width(facts[1].value,12f)
        arrow(w-sw-13*dn,h-7*dn,10*dn,false,SwSet);text(facts[1].value,w,h-3*dn,12f,fg,false,Paint.Align.RIGHT)
    } else {
        // Two rows, three cells and more: the place and what comes next (or the phase) on top, the picture on the left, the columns on the right.
        text(fit(place,13f,true,w*0.5f),0f,13*dn,13f,fg,true)
        val head=fit(headline,12f,false,w*0.45f)
        text(head,w,13*dn,12f,fg,false,Paint.Align.RIGHT)
        if(head.isNotEmpty()) dot(w-width(head,12f)-7*dn,9*dn,headColor)
        val top=22*dn;val area=h-top
        if(cols==3) {
            val s=min(area,w*0.48f);graphic(s/2,top+area/2,s)
            val x1=s+10*dn;val step=area/3
            facts.take(3).forEachIndexed { k,f -> column(x1,top+step*(k+0.5f),w-x1,f) }
        } else {
            val s=min(area,w*0.34f);graphic(s/2,top+area/2,s)
            val x1=s+14*dn;val cw=(w-x1)/2
            facts.forEachIndexed { k,f -> column(x1+(k%2)*cw,top+area*(if(k<2) 0.27f else 0.73f),cw,f) }
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
        val keys=listOf("active")+locations.map { BackgroundRefresh.locationKey(it) }
        val names=listOf(uiText(R.string.wg_loc_active))+locations.map { it.name }
        setContent {
            PerunTheme {
                Surface(Modifier.fillMaxSize(),color=Color(0xFF151A22),contentColor=Color(0xFFE8EAF0)) {
                    var loc by remember { mutableStateOf(initial.loc) }
                    var main by remember { mutableStateOf(initial.main) }
                    var bg by remember { mutableStateOf(initial.bg) }
                    var text by remember { mutableStateOf(initial.text) }
                    val muted=Color(0xFFBBC4D2)
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(uiText(R.string.wg_config_title),style=MaterialTheme.typography.titleLarge,color=Color(0xFFE8EAF0))
                        Text(uiText(R.string.wg_location),style=MaterialTheme.typography.labelLarge,color=muted)
                        PerunChips(names,keys.indexOf(loc).coerceAtLeast(0),{ loc=keys[it] })
                        Text(uiText(R.string.wg_sky_main),style=MaterialTheme.typography.labelLarge,color=muted)
                        PerunChips(listOf(uiText(R.string.ui_slnko_d4e0d075),uiText(R.string.ui_mesiac_3ca578fb)),if(main=="moon") 1 else 0,{ main=if(it==1) "moon" else "sun" })
                        Text(uiText(R.string.wg_background),style=MaterialTheme.typography.labelLarge,color=muted)
                        PerunChips(listOf(uiText(R.string.wg_bg_dark),uiText(R.string.wg_bg_glass),uiText(R.string.wg_bg_light)),PerunWidgets.Backgrounds.indexOf(bg).coerceAtLeast(0),{ bg=PerunWidgets.Backgrounds[it] })
                        Text(uiText(R.string.wg_text),style=MaterialTheme.typography.labelLarge,color=muted)
                        PerunChips(listOf(uiText(R.string.wg_text_light),uiText(R.string.wg_text_dark)),if(text=="dark") 1 else 0,{ text=if(it==1) "dark" else "light" })
                        Button(onClick={
                            SkyWidgets.write(this@SkyWidgetConfigActivity,id,SkyWidgetConfig(loc,main,bg,text))
                            SkyWidgets.refresh(this@SkyWidgetConfigActivity)
                            SkyWidgets.schedule(this@SkyWidgetConfigActivity)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
                            finish()
                        },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_save)) }
                    }
                }
            }
        }
    }
}
