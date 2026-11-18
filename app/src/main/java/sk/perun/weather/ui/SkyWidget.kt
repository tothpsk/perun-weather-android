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

internal class SkyWidgetConfig(val loc:String,val main:String,val bg:String,val text:String)

/** The Sun and Moon widget. It keeps its own settings; the main value is "sun" or "moon". Everything is computed in the device. */
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

/** The whole widget as one picture, laid out by its size in cells: one to four and more of width, one or two of height. */
private fun skyWidgetBitmap(dn:Float,place:String,zone:ZoneId,lat:Double,lon:Double,moonMain:Boolean,darkText:Boolean,wPx:Int,hPx:Int,cols:Int,rows:Int):Bitmap {
    val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
    val w=wPx.toFloat();val h=hPx.toFloat()
    val fg=if(darkText) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
    val muted=if(darkText) 0xFF4B586C.toInt() else 0xFFBBC4D2.toInt()
    val track=(0x44 shl 24) or (fg and 0xFFFFFF)
    val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val now=Instant.now();val today=now.atZone(zone).toLocalDate()
    val sun=swSunDay(today,zone,lat,lon);val before=swSunDay(today.minusDays(1),zone,lat,lon)
    val hm=DateTimeFormatter.ofPattern("HH:mm")
    fun clock(t:Instant?)=t?.atZone(zone)?.format(hm) ?: "\u2013"
    val sunRise=sun.rise;val sunSet=sun.set
    val sunNext:Pair<Boolean,Instant?> = when {
        sunRise!=null && now<sunRise -> true to sunRise
        sunSet!=null && now<sunSet -> false to sunSet
        else -> true to swSunDay(today.plusDays(1),zone,lat,lon).rise
    }
    val moonToday=SunMoonMath.moonEvents(today,zone,lat,lon)
    val moonTomorrow=SunMoonMath.moonEvents(today.plusDays(1),zone,lat,lon)
    val moonNext=listOfNotNull(moonToday.first?.let { true to it },moonToday.second?.let { false to it },moonTomorrow.first?.let { true to it },moonTomorrow.second?.let { false to it })
        .filter { it.second>now }.minByOrNull { it.second }
    val point=SunMoonMath.position(now,lat,lon)
    val waxing=SunMoonMath.position(now.plusSeconds(43200),lat,lon).illumination>=SunMoonMath.position(now.minusSeconds(43200),lat,lon).illumination
    val illum=point.illumination.roundToInt().coerceIn(0,100)
    val lit="$illum %"
    val phase=SunMoonMath.phaseName(point.phase)
    val dayLength=if(sun.seconds>=0) uiText(R.string.ui_value_h_value_min_49a3b581,sun.seconds/3600,(sun.seconds%3600)/60) else "\u2013"
    // Change of the day length against yesterday, as on the Sun and Moon screen.
    val known=sun.seconds>=0 && before.seconds>=0
    val change=if(known) sun.seconds-before.seconds else 0L
    val changeText=if(!known) "\u2013" else (if(change>0) "+" else if(change<0) "\u2212" else "")+uiText(R.string.sky_min_s,abs(change)/60,abs(change)%60)
    val changeColor=if(!known || change==0L) fg else if(change>0) SwRise else SwSet

    fun font(size:Float,bold:Boolean) { p.style=Paint.Style.FILL;p.textSize=size*dn;p.typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    fun width(s:String,size:Float,bold:Boolean=false):Float { font(size,bold);return p.measureText(s) }
    fun line(s:String,x:Float,cy:Float,size:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT) { font(size,bold);p.color=color;p.textAlign=align;cv.drawText(s,x,cy+size*dn*0.36f,p) }
    fun fit(s:String,size:Float,bold:Boolean,room:Float):String {
        font(size,bold)
        if(p.measureText(s)<=room) return s
        var t=s
        while(t.length>1 && p.measureText(t+"\u2026")>room) t=t.dropLast(1)
        return if(p.measureText(t+"\u2026")<=room) t+"\u2026" else ""
    }
    fun stroke(color:Int,wd:Float=1.8f) { p.style=Paint.Style.STROKE;p.strokeWidth=wd*dn;p.strokeCap=Paint.Cap.ROUND;p.color=color }
    fun arrow(x:Float,cy:Float,s:Float,up:Boolean,color:Int) {
        stroke(color);val cx=x+s/2;val t=cy-s*0.4f;val b=cy+s*0.4f
        cv.drawLine(cx,t,cx,b,p)
        val tip=if(up) t else b;val back=if(up) t+s*0.32f else b-s*0.32f
        cv.drawLine(cx,tip,cx-s*0.3f,back,p);cv.drawLine(cx,tip,cx+s*0.3f,back,p)
        p.style=Paint.Style.FILL
    }
    fun clockGlyph(x:Float,cy:Float,s:Float) {
        stroke(muted,1.5f);val cx=x+s/2;val r=s*0.42f
        cv.drawCircle(cx,cy,r,p);cv.drawLine(cx,cy,cx,cy-r*0.6f,p);cv.drawLine(cx,cy,cx+r*0.45f,cy+r*0.3f,p)
        p.style=Paint.Style.FILL
    }
    // The Moon in its phase; the lit side follows the hemisphere, as on the Sun and Moon screen.
    fun moon(cx:Float,cy:Float,r:Float) {
        p.style=Paint.Style.FILL;p.color=SwShade;cv.drawCircle(cx,cy,r,p)
        val k=illum/100f;val right=waxing!=(lat<0)
        p.color=SwLit;cv.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),if(right) -90f else 90f,180f,true,p)
        val ex=r*abs(1-2*k);p.color=if(k<0.5f) SwShade else SwLit
        cv.drawOval(RectF(cx-ex,cy-r,cx+ex,cy+r),p)
    }
    fun sunIcon(x:Float,y:Float,s:Float) { p.color=0xFFFFFFFF.toInt();cv.drawBitmap(widgetWeatherBitmap(0,true,s.roundToInt().coerceAtLeast(1)),null,RectF(x,y,x+s,y+s),p) }
    // The day arc: the Sun moves from the sunrise on the left to the sunset on the right; at night the arc stays empty.
    fun arc(cx:Float,base:Float,r:Float) {
        val box=RectF(cx-r,base-r,cx+r,base+r)
        stroke(track,2.5f);cv.drawArc(box,180f,180f,false,p)
        stroke(muted,1.2f);cv.drawLine(cx-r-4*dn,base,cx+r+4*dn,base,p)
        if(sunRise!=null && sunSet!=null && sunSet>sunRise && now>=sunRise && now<=sunSet) {
            val f=((now.epochSecond-sunRise.epochSecond).toFloat()/(sunSet.epochSecond-sunRise.epochSecond)).coerceIn(0f,1f)
            stroke(SwSun,2.5f);cv.drawArc(box,180f,180f*f,false,p)
            val a=PI*(1-f)
            p.style=Paint.Style.FILL;p.color=SwSun
            cv.drawCircle(cx+r*cos(a).toFloat(),base-r*sin(a).toFloat(),max(3.5f*dn,r*0.14f),p)
        }
        p.style=Paint.Style.FILL
    }
    // The main picture in a square of the given size around its centre.
    fun graphic(cx:Float,cy:Float,s:Float) { if(moonMain) moon(cx,cy,s*0.42f) else arc(cx,cy+s*0.22f,s*0.44f) }
    fun icon(x:Float,cy:Float,s:Float) { if(moonMain) moon(x+s/2,cy,s*0.42f) else sunIcon(x,cy-s/2,s) }
    // A value with its symbol: 0 rise, 1 set, 2 day length, 3 the lit part of the Moon, 4 change of the day length.
    fun itemWidth(value:String,ts:Float,bold:Boolean=false)=ts*dn+4*dn+width(value,ts,bold)
    fun item(kind:Int,value:String,x:Float,cy:Float,ts:Float,color:Int=fg,bold:Boolean=false) {
        val s=ts*dn
        when(kind) {
            0->arrow(x,cy,s,true,SwRise)
            1->arrow(x,cy,s,false,SwSet)
            2->clockGlyph(x,cy,s)
            3->moon(x+s/2,cy,s*0.42f)
            else->arrow(x,cy,s,change>=0,if(changeColor==fg) muted else changeColor)
        }
        line(value,x+s+4*dn,cy,ts,color,bold)
    }
    val riseText=clock(if(moonMain) moonToday.first else sunRise)
    val setText=clock(if(moonMain) moonToday.second else sunSet)
    val next:Pair<Boolean,Instant?> = if(moonMain) moonNext ?: (true to null) else sunNext
    val nextText=clock(next.second)
    val third=if(moonMain) lit else dayLength
    val thirdKind=if(moonMain) 3 else 2

    if(cols==1) {
        // One cell: the picture, and below it the next sunrise or sunset, or the lit part of the Moon.
        graphic(w/2,(h-16*dn)/2,min(w,h-16*dn))
        if(moonMain) line(lit,w/2,h-7*dn,12f,fg,true,Paint.Align.CENTER)
        else { val iw=itemWidth(nextText,12f,true);item(if(next.first) 0 else 1,nextText,(w-iw)/2,h-7*dn,12f,fg,true) }
    } else if(rows==1) {
        val cy=h/2;val s=min(h,36*dn)
        if(cols==2) {
            // Two cells: the symbol on the left, the next event (or the lit part of the Moon) on the right.
            icon(0f,cy,s)
            if(moonMain) line(lit,w,cy,16f,fg,true,Paint.Align.RIGHT)
            else { val iw=itemWidth(nextText,16f,true);item(if(next.first) 0 else 1,nextText,w-iw,cy,16f,fg,true) }
        } else {
            // Three cells: symbol, rise and set from edge to edge. Four and more: the place and the third value as well.
            val parts=ArrayList<Pair<Float,(Float)->Unit>>()
            parts.add(s to { x:Float -> icon(x,cy,s) })
            parts.add(itemWidth(riseText,15f) to { x:Float -> item(0,riseText,x,cy,15f) })
            parts.add(itemWidth(setText,15f) to { x:Float -> item(1,setText,x,cy,15f) })
            if(cols>=4) {
                if(moonMain) parts.add(width(lit,15f) to { x:Float -> line(lit,x,cy,15f,fg) })
                else parts.add(itemWidth(dayLength,15f) to { x:Float -> item(2,dayLength,x,cy,15f) })
                val used=parts.sumOf { it.first.toDouble() }.toFloat()
                val shown=fit(place,15f,false,w-used-12*dn*parts.size)
                if(shown.isNotEmpty()) parts.add(1,width(shown,15f) to { x:Float -> line(shown,x,cy,15f,fg) })
            }
            val gap=(w-parts.sumOf { it.first.toDouble() }.toFloat())/(parts.size-1)
            var x=0f
            parts.forEach { (pw,draw) -> draw(x);x+=pw+gap }
        }
    } else if(cols==2) {
        // Two by two: the place on top, the picture in the middle, rise on the left and set on the right below.
        line(fit(place,15f,false,w),0f,9*dn,15f,fg)
        val top=20*dn;val bottom=h-20*dn
        graphic(w/2,(top+bottom)/2,min(w*0.8f,bottom-top))
        item(0,riseText,0f,h-8*dn,13f)
        item(1,setText,w-itemWidth(setText,13f),h-8*dn,13f)
    } else if(cols==3) {
        // Three by two: the place on top, a large picture on the left, rise, set and the third value on the right.
        line(fit(place,15f,false,w),0f,9*dn,15f,fg)
        val top=22*dn;val s=min(h-top,w*0.45f)
        graphic(s/2,top+(h-top)/2,s)
        val step=(h-top)/3
        listOf(0 to riseText,1 to setText,thirdKind to third).forEachIndexed { i,(k,v) -> item(k,v,w-itemWidth(v,15f),top+step*(i+0.5f),15f) }
    } else {
        // Four and five by two: the place and the phase of the Moon on top, the main picture on the left, then a column for the Sun and one for the Moon.
        line(fit(place,15f,false,w*0.6f),0f,9*dn,15f,fg)
        line(fit(phase,12f,false,w*0.38f),w,9*dn,12f,muted,false,Paint.Align.RIGHT)
        val top=22*dn;val s=min(h-top,w*0.26f)
        graphic(s/2,top+(h-top)/2,s)
        val x1=s+14*dn;val colW=(w-x1)/2
        val sunRows=listOf(0 to clock(sunRise),1 to clock(sunSet),2 to dayLength,4 to changeText)
        val moonRows=listOf(0 to clock(moonToday.first),1 to clock(moonToday.second),3 to lit)
        val columns=if(moonMain) listOf(uiText(R.string.ui_mesiac_3ca578fb) to moonRows,uiText(R.string.ui_slnko_d4e0d075) to sunRows)
            else listOf(uiText(R.string.ui_slnko_d4e0d075) to sunRows,uiText(R.string.ui_mesiac_3ca578fb) to moonRows)
        val step=(h-top)/5
        val ts=(step/dn*0.62f).coerceIn(10f,14f)
        columns.forEachIndexed { c,(title,list) ->
            val x=x1+c*colW
            line(fit(title,10f,false,colW),x,top+step*0.5f,10f,muted)
            list.forEachIndexed { i,(k,v) -> item(k,fit(v,ts,false,colW-ts*dn-8*dn),x,top+step*(i+1.5f),ts,if(k==4) changeColor else fg) }
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
