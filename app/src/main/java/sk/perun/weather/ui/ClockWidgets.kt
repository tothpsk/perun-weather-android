package sk.perun.weather.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import sk.perun.weather.R
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ClockWidget:PerunWidgetProvider()
class ClockDaysWidget:PerunWidgetProvider()
class ClockHoursWidget:PerunWidgetProvider()

/** The alarm mark of the clock widgets follows the next alarm without downloading anything. */
class ClockAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action==AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) ClockRender.updateAlarm(context)
    }
}

/** The size of a widget with a clock, from its options. */
internal data class ClockSize(val wDp:Int,val hDp:Int) {
    val row:Boolean get()=hDp<100
    /** Cells of width, as in the Compact widget, with a fifth from about five cells (PerunWidgets.WideDp). */
    val cells:Int get()=when { wDp<180->2;wDp<250->3;wDp<PerunWidgets.WideDp->4;else->5 }
    /** Three rows of height or more: room for the bottom row of the 6 days and 6 hours with clock. */
    val tall:Boolean get()=hDp>=200
}

/**
 * Compact with clock, 6 days with clock and 6 hours with clock. The time and the date are system clocks (TextClock), so
 * they run without the app; the weather is drawn into pictures like the Compact widget, with the icons of the app.
 * A tap on the time opens the default clock app, on the date the default calendar, elsewhere Perun.
 */
internal object ClockRender {
    private val Hm:DateTimeFormatter=DateTimeFormatter.ofPattern("HH:mm")
    private val TimeIds=mapOf("light" to R.id.wg_time_light,"regular" to R.id.wg_time_regular,"bold" to R.id.wg_time_bold,"condensed" to R.id.wg_time_condensed)
    private val Kinds=listOf(ClockWidget::class.java,ClockDaysWidget::class.java,ClockHoursWidget::class.java)
    // The height of the time (TextClock without font padding) and of the date (with it) per sp.
    private const val TimeLine=1.17f
    private const val DateLine=1.33f

    fun size(o:Bundle):ClockSize = ClockSize(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 250,o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 110)

    private fun layoutOf(kind:Class<*>,s:ClockSize):Int = when {
        kind!=ClockWidget::class.java->R.layout.wg_clock_days
        s.row->R.layout.wg_clock_row
        s.cells<=2->R.layout.wg_clock_tall
        else->R.layout.wg_clock_box
    }

    private fun alarmSet(c:Context):Boolean = c.getSystemService(AlarmManager::class.java)?.nextAlarmClock!=null

    private fun datePattern(style:String,cells:Int):String {
        // Two cells in one row have room only for the short date.
        val skeleton=if(style=="short" || cells<=2) "EEEdM" else "EEEEdMMMM"
        return DateFormat.getBestDateTimePattern(appLocale(),skeleton)
    }
    private fun longDate(w:WidgetConfig,s:ClockSize):Boolean = w.date=="long" && s.cells>2

    private fun typeface(style:String):Typeface = when(style) {
        "regular"->Typeface.create("sans-serif",Typeface.NORMAL)
        "bold"->Typeface.create("sans-serif",Typeface.BOLD)
        "condensed"->Typeface.create("sans-serif-condensed",Typeface.NORMAL)
        else->Typeface.create("sans-serif-light",Typeface.NORMAL)
    }

    /** The clock: the size of the time in sp and the height of the whole block in dp. */
    private class ClockFit(val timeSp:Float,val dateSp:Float,val heightDp:Float)
    /** The largest time up to [spec] sp that leaves the date (and [below] dp) room in [roomDp]; never under [least] sp. */
    private fun fitClock(c:Context,w:WidgetConfig,spec:Float,dateSp:Float,roomDp:Float,below:Float=0f,least:Float=16f):ClockFit {
        val fs=c.resources.configuration.fontScale
        val dateH=if(w.date=="none") 0f else dateSp*fs*DateLine+1f
        val timeSp=((roomDp-dateH-below)/(TimeLine*fs)).coerceAtMost(spec).coerceAtLeast(least)
        return ClockFit(timeSp,dateSp,timeSp*fs*TimeLine+dateH+below)
    }

    /** The width of the clock block in dp: the wider of the time (with the alarm mark) and the date. */
    private fun clockWidthDp(c:Context,w:WidgetConfig,s:ClockSize,f:ClockFit,alarm:Boolean):Float {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        // Sizes in sp grow with the font size of the system.
        val scale=c.resources.configuration.fontScale
        p.typeface=typeface(w.clock);p.textSize=f.timeSp*scale
        val time=p.measureText("22:22")+(if(alarm) 16f else 0f)
        val date=if(w.date=="none") 0f else {
            p.typeface=Typeface.DEFAULT;p.textSize=f.dateSp*scale
            p.measureText(DateTimeFormatter.ofPattern(datePattern(w.date,s.cells),appLocale()).format(LocalDate.now()))
        }
        return max(time,date)
    }

    private fun clock(c:Context,v:RemoteViews,id:Int,w:WidgetConfig,s:ClockSize,kind:Class<*>,pal:Palette,f:ClockFit,alarm:Boolean) {
        TimeIds.forEach { (style,view) ->
            v.setViewVisibility(view,if(style==w.clock) View.VISIBLE else View.GONE)
            v.setTextViewTextSize(view,TypedValue.COMPLEX_UNIT_SP,f.timeSp)
            v.setTextColor(view,pal.fg)
        }
        v.setViewVisibility(R.id.wg_alarm,if(alarm) View.VISIBLE else View.GONE)
        v.setInt(R.id.wg_alarm,"setColorFilter",pal.fg)
        if(w.date=="none") v.setViewVisibility(R.id.wg_date,View.GONE) else {
            val pattern=datePattern(w.date,s.cells)
            v.setViewVisibility(R.id.wg_date,View.VISIBLE)
            v.setCharSequence(R.id.wg_date,"setFormat12Hour",pattern);v.setCharSequence(R.id.wg_date,"setFormat24Hour",pattern)
            v.setTextViewTextSize(R.id.wg_date,TypedValue.COMPLEX_UNIT_SP,f.dateSp)
            v.setTextColor(R.id.wg_date,pal.fg)
        }
        v.setViewVisibility(R.id.wg_extra,View.GONE)
        // 2 x 2 centres the date under the time.
        v.setInt(R.id.wg_clock_box,"setGravity",if(kind==ClockWidget::class.java && !s.row && s.cells<=2) android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.START)
        val clockApp=Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.wg_clock_tap,PendingIntent.getActivity(c,30000+id,clockApp,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val calendar=Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.wg_date,PendingIntent.getActivity(c,40000+id,calendar,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    fun render(c:Context,kind:Class<*>,id:Int,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,pal:Palette,o:Bundle,series:HourlySeries?):RemoteViews {
        val s=size(o);val dn=c.resources.displayMetrics.density
        val v=RemoteViews(c.packageName,layoutOf(kind,s))
        val alarm=alarmSet(c)
        val art=ClockArt(dn,pal,d,ex,u,l)
        val updated=uiText(R.string.wg_updated,d.time.format(Hm))
        val extra=w.extra.takeIf { it!="none" && it in Notifier.CurrentOptions }
        v.setInt(R.id.wg_refresh,"setColorFilter",pal.muted)
        if(kind==ClockWidget::class.java) {
            val a=widgetNextDay().let { (from,to)->widgetAlert(d.alerts,from,to) }
            when {
                s.row -> {
                    // The time shrinks until the date fits under it in the height of the row.
                    val rowH=s.hDp-8f-4f
                    val f=fitClock(c,w,when(s.cells) { 2->30f;3->36f;4->40f;else->44f },11f,rowH)
                    clock(c,v,id,w,s,kind,pal,f,alarm)
                    val refresh=s.cells>=5
                    v.setViewVisibility(R.id.wg_refresh,if(refresh) View.VISIBLE else View.GONE)
                    val weather=art.rowWeather(rowH,s.cells)
                    v.setImageViewBitmap(R.id.wg_weather,weather)
                    val clockW=clockWidthDp(c,w,s,f,alarm)
                    val room=s.wDp-20f-clockW-weather.width/dn-(if(refresh) 30f else 0f)-4*10f
                    val info=if(s.cells>=4 && room>=30f) art.rowInfo(room,rowH,extra,a) else null
                    if(info==null) v.setViewVisibility(R.id.wg_place,View.GONE) else { v.setViewVisibility(R.id.wg_place,View.VISIBLE);v.setImageViewBitmap(R.id.wg_place,info) }
                    // Without the long date the parts stand at even gaps, as in the Compact widget; with it the clock keeps the left edge
                    // and the place stands in the middle between the clock and the weather.
                    val even=!longDate(w,s)
                    v.setViewVisibility(R.id.wg_sp0,if(even) View.VISIBLE else View.GONE)
                    v.setViewVisibility(R.id.wg_sp1,View.VISIBLE)
                    v.setViewVisibility(R.id.wg_sp2,if(info!=null) View.VISIBLE else View.GONE)
                    v.setViewVisibility(R.id.wg_sp3,if(even) View.VISIBLE else View.GONE)
                }
                s.cells<=2 -> {
                    val f=fitClock(c,w,40f,12f,s.hDp-18f-20f-34f)
                    clock(c,v,id,w,s,kind,pal,f,alarm)
                    v.setImageViewBitmap(R.id.wg_weather,art.tall((s.wDp-16f).coerceAtLeast(40f),(s.hDp-18f-20f-f.heightDp-4f).coerceAtLeast(24f),extra))
                    v.setImageViewBitmap(R.id.wg_place,art.place((s.wDp-16f).coerceAtLeast(40f),20f,a,null,centre=true))
                }
                else -> {
                    val topH=(s.hDp-20f-26f).coerceAtLeast(30f)
                    // Three cells: the chosen value only without the date, under the clock.
                    val under=s.cells==3 && w.date=="none" && extra!=null
                    val f=fitClock(c,w,when(s.cells) { 3->36f;4->54f;else->60f },if(s.cells==3) 12f else 13f,topH,if(under) 22f else 0f)
                    clock(c,v,id,w,s,kind,pal,f,alarm)
                    if(under) { v.setViewVisibility(R.id.wg_extra,View.VISIBLE);v.setImageViewBitmap(R.id.wg_extra,art.extraOnly(extra!!,13f)) }
                    val room=(s.wDp-26f-clockWidthDp(c,w,s,f,alarm)-10f).coerceAtLeast(40f)
                    // The weather stands with its middle at the middle of the time.
                    val timeMid=f.timeSp*c.resources.configuration.fontScale*TimeLine/2
                    v.setImageViewBitmap(R.id.wg_weather,art.box(room,topH,s.cells,if(s.cells==3) null else extra,timeMid))
                    v.setImageViewBitmap(R.id.wg_place,art.place((s.wDp-26f-30f).coerceAtLeast(40f),26f,a,if(s.cells>=4) updated else null))
                }
            }
            v.setContentDescription(R.id.wg_weather,l.name+" "+WidgetRender.t(u,d.temperature)+(a?.let { ", "+alertName(it) } ?: ""))
        } else {
            val hours=kind==ClockHoursWidget::class.java
            val now=Instant.now()
            val a=if(hours) widgetAlert(d.alerts,now,now.plusSeconds(6*3600)) else widgetDays(now,d.zone,6).let { (from,to)->widgetAlert(d.alerts,from,to) }
            val bottom=if(s.tall) extra else null
            val avail=s.hDp-12f-22f-2f
            // The columns keep the heights of the 6 days widget; the clock takes what is left, the columns shrink only below the smallest clock.
            val rowsNatural=ClockArt.columnsHeight(bottom!=null)
            // The time is sized as with the long date, so that it, the place and the weather keep their size and place across the date styles;
            // without the date the columns get the freed room.
            val withDate=w.copy(date="long")
            val fs=c.resources.configuration.fontScale
            val sized=fitClock(c,withDate,if(s.cells>=5) 46f else 40f,12f,avail-rowsNatural,least=26f)
            val timeH=sized.timeSp*fs*TimeLine
            val f=ClockFit(sized.timeSp,sized.dateSp,if(w.date=="none") timeH else sized.heightDp)
            clock(c,v,id,w,s,kind,pal,f,alarm)
            val clockW=clockWidthDp(c,withDate,s,sized,alarm)
            v.setImageViewBitmap(R.id.wg_weather,art.head((s.wDp-22f-clockW-8f).coerceAtLeast(60f),timeH.coerceAtLeast(30f),a))
            // A little room is kept free, as the real heights of the texts may differ slightly from the estimate.
            val rowsH=(avail-max(f.heightDp,timeH.coerceAtLeast(30f))-4f).coerceAtLeast(30f)
            val cols=if(hours) art.hourColumns(series,bottom) else art.dayColumns(series,bottom)
            v.setImageViewBitmap(R.id.wg_rows,art.columns((s.wDp-28f).coerceAtLeast(120f),rowsH,cols,bottom))
            v.setTextViewText(R.id.wg_foot,updated);v.setTextColor(R.id.wg_foot,pal.muted)
            v.setViewVisibility(R.id.wg_refresh,View.VISIBLE)
            v.setContentDescription(R.id.wg_weather,l.name+" "+WidgetRender.t(u,d.temperature)+(a?.let { ", "+alertName(it) } ?: ""))
        }
        return v
    }

    /** Shows or hides the alarm mark of every clock widget; a partial update keeps the rest of the widget. */
    fun updateAlarm(c:Context) {
        val manager=AppWidgetManager.getInstance(c);val alarm=alarmSet(c)
        Kinds.forEach { kind -> PerunWidgets.ids(c,kind).forEach { id ->
            val v=RemoteViews(c.packageName,layoutOf(kind,size(manager.getAppWidgetOptions(id))))
            v.setViewVisibility(R.id.wg_alarm,if(alarm) View.VISIBLE else View.GONE)
            manager.partiallyUpdateAppWidget(id,v)
        } }
        // The width of the clock changes with the mark; the next drawing lays the weather out again.
        PerunWidgets.refresh(c)
    }
}

/** One column of the 6 days and 6 hours with clock: the label, the weather, the main and the second value, and the value of the bottom row. */
internal class ClockColumn(val label:String,val code:Int,val day:Boolean,val main:String,val second:String,val bottom:String)

/** A value of the bottom row from an hourly value or a day of them, in the units of the app; short, as the columns are narrow. */
internal fun clockBottomValue(key:String,x:Double,u:WeatherUnits):String {
    if(!x.isFinite()) return "-"
    fun n(v:Double,dec:Int)=weatherUnitNumber(v,dec)
    return when(key) {
        "wind","gust"->n(u.wind.fromKmh(x),0)+" "+u.wind.symbol
        "rain"->n(u.precipitation.fromMillimetres(x),1)+" "+u.precipitation.liquidSymbol
        "prob","humidity","cloud"->n(x,0)+" %"
        "uv"->n(x,1)
        "pressure"->n(u.pressure.fromHpa(x),0)
        "visibility"->n(x/1000.0,0)+" km"
        "dew","feels"->n(u.temperature.fromCelsius(x),0)+"\u00B0"
        else->n(x,0)
    }
}
/** The Open-Meteo key of a value of the bottom row. */
internal val ClockBottomKeys=mapOf("wind" to "wind_speed_10m","gust" to "wind_gusts_10m","rain" to "precipitation","prob" to "precipitation_probability","uv" to "uv_index",
    "humidity" to "relative_humidity_2m","pressure" to "pressure_msl","cloud" to "cloud_cover","visibility" to "visibility","dew" to "dew_point_2m","feels" to "apparent_temperature")
/** A day of a value: the sum of precipitation, the highest probability, UV, wind, gusts and apparent temperature, otherwise the mean. */
internal fun clockDayValue(key:String,values:List<Double>):Double {
    val f=values.filter { it.isFinite() }
    if(f.isEmpty()) return Double.NaN
    return when(key) { "rain"->f.sum();"prob","uv","wind","gust","feels"->f.max();"visibility"->f.min();else->f.average() }
}

/** The pictures of the clock widgets, in dp sizes, with the icons of the app (widgetSymbolBitmap, widgetParamIcon, drawGpsMark, widgetAlertBitmap). */
private class ClockArt(val dn:Float,val pal:Palette,val d:OverviewData,val ex:OverviewExtra?,val u:WeatherUnits,val l:Location) {
    companion object {
        // The column of the 6 days widget: the day (11 sp), the icon (26 dp and 2 dp above and below), the highest (13 sp bold) and the lowest (12 sp) temperature.
        const val Label=15f;const val Icon=26f;const val IconGap=2f;const val Main=17f;const val Second=16f;const val Bottom=16f
        fun columnsHeight(bottom:Boolean):Float = Label+Icon+2*IconGap+Main+Second+(if(bottom) Bottom else 0f)
    }
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val temp=WidgetRender.t(u,d.temperature)
    private fun canvas(wDp:Float,hDp:Float):Pair<Bitmap,Canvas> {
        val b=Bitmap.createBitmap((wDp*dn).roundToInt().coerceAtLeast(1),(hDp*dn).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        return b to Canvas(b)
    }
    private fun font(sp:Float,bold:Boolean) { p.textSize=sp*dn;p.typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    private fun width(s:String,sp:Float,bold:Boolean):Float { font(sp,bold);return p.measureText(s) }
    /** Text with its middle at [cy]. */
    private fun text(cv:Canvas,s:String,x:Float,cy:Float,sp:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT) {
        font(sp,bold);p.color=color;p.textAlign=align;cv.drawText(s,x,cy+sp*dn*0.36f,p);p.textAlign=Paint.Align.LEFT
    }
    private fun fit(s:String,sp:Float,bold:Boolean,room:Float):String {
        font(sp,bold)
        if(p.measureText(s)<=room) return s
        var t=s
        while(t.length>1 && p.measureText(t+"\u2026")>room) t=t.dropLast(1)
        return if(p.measureText(t+"\u2026")<=room) t+"\u2026" else ""
    }
    private fun pic(cv:Canvas,b:Bitmap,x:Float,y:Float,size:Float) { p.color=0xFFFFFFFF.toInt();cv.drawBitmap(b,null,RectF(x,y,x+size,y+size),p) }
    private fun weather(cv:Canvas,code:Int,day:Boolean,x:Float,y:Float,size:Float) = pic(cv,widgetSymbolBitmap(code,day,size.roundToInt().coerceAtLeast(1)),x,y,size)

    /** The chosen value: its icon (snow instead of rain while it snows, the pressure trend) and its value, with the wind arrow behind wind and gusts. */
    private fun extraWidth(key:String,sp:Float):Float = (sp+3f)*dn+width(WidgetRender.param(key,d,ex,u),sp,true)+(if(arrow(key)) (sp+2f)*dn else 0f)
    private fun arrow(key:String):Boolean = (key=="wind" || key=="gust") && d.direction.isFinite()
    private fun extra(cv:Canvas,key:String,right:Float,cy:Float,sp:Float) {
        val value=WidgetRender.param(key,d,ex,u)
        var x=right
        if(arrow(key)) { val a=sp*dn;pic(cv,widgetWindArrowBitmap(d.direction,pal.fg,(a).roundToInt().coerceAtLeast(1),sp),x-a,cy-a/2,a);x-=a+2f*dn }
        text(cv,value,x,cy,sp,pal.fg,true,Paint.Align.RIGHT)
        x-=width(value,sp,true)+3f*dn
        val icon=if(key=="rain" && WidgetRender.snowing(d)) "snow" else key
        pic(cv,widgetParamIcon(icon,pal.fg,(sp*dn).roundToInt().coerceAtLeast(1),ex?.pressureChange ?: Double.NaN,sp),x-sp*dn,cy-sp*dn/2,sp*dn)
    }
    /** The place in bold with the location mark of the Current location and the warning behind it. */
    private fun placeWidth(name:String,sp:Float,alert:UnifiedAlert?):Float =
        (if(name.isEmpty()) 0f else width(name,sp,true))+(if(l.current && name.isNotEmpty()) (sp+4f)*dn else 0f)+(if(alert!=null) (sp+9f)*dn else 0f)
    private fun place(cv:Canvas,name:String,x:Float,cy:Float,sp:Float,alert:UnifiedAlert?) {
        var at=x
        if(name.isNotEmpty()) { text(cv,name,at,cy,sp,pal.fg,true);at+=width(name,sp,true) }
        if(l.current && name.isNotEmpty()) { drawGpsMark(cv,at+4f*dn,cy,sp*dn,pal.fg);at+=(sp+4f)*dn }
        if(alert!=null) { val a=(sp+4f)*dn;pic(cv,widgetAlertBitmap(alert,a.roundToInt().coerceAtLeast(1)),at+5f*dn,cy-a/2,a) }
    }
    private fun fittedPlace(sp:Float,alert:UnifiedAlert?,room:Float):String = fit(l.name,sp,true,room-placeWidth("",sp,alert))

    /** One row, at its natural size: two cells the icon over the temperature, otherwise the icon beside it. */
    fun rowWeather(rowH:Float,cells:Int):Bitmap {
        if(cells<=2) {
            val ts=16f;val s=min(rowH-ts*1.25f,30f).coerceAtLeast(16f)
            val tw=width(temp,ts,true)/dn
            val (b,cv)=canvas(max(s,tw),s+ts*1.25f)
            weather(cv,d.code,d.isDayFlag,(max(s,tw)-s)/2*dn,0f,s*dn)
            text(cv,temp,max(s,tw)/2*dn,(s+ts*0.62f)*dn,ts,pal.fg,true,Paint.Align.CENTER)
            return b
        }
        val ts=if(cells>=4) 20f else 22f
        val s=min(rowH*0.8f,if(cells>=4) 30f else 34f)
        val tw=width(temp,ts,true)/dn
        val h=max(s,ts*1.25f)
        val (b,cv)=canvas(s+4f+tw,h)
        weather(cv,d.code,d.isDayFlag,0f,(h-s)/2*dn,s*dn)
        text(cv,temp,(s+4f)*dn,h/2*dn,ts,pal.fg,true)
        return b
    }

    /** One row, four and five cells: the place at the size of the Compact widget (15 sp), over the chosen value where it is set. */
    fun rowInfo(roomDp:Float,rowH:Float,extraKey:String?,alert:UnifiedAlert?):Bitmap? {
        val room=roomDp*dn
        if(extraKey!=null && extraWidth(extraKey,13f)<=room) {
            val sp=15f;val es=13f
            val name=fittedPlace(sp,alert,room)
            val w=max(placeWidth(name,sp,alert),extraWidth(extraKey,es))
            val h=min(rowH,44f)
            val (b,cv)=canvas(w/dn,h)
            place(cv,name,0f,h/2*dn-10f*dn,sp,alert)
            extra(cv,extraKey,extraWidth(extraKey,es),h/2*dn+10f*dn,es)
            return b
        }
        val sp=15f
        val name=fittedPlace(sp,alert,room)
        if(name.isEmpty() && alert==null) return null
        val w=placeWidth(name,sp,alert).coerceAtLeast(1f)
        val (b,cv)=canvas(w/dn,24f)
        place(cv,name,0f,12f*dn,sp,alert)
        return b
    }

    /** The chosen value alone, under the clock of Compact with clock 3 x 2 without the date. */
    fun extraOnly(key:String,sp:Float):Bitmap {
        val w=extraWidth(key,sp)
        val (b,cv)=canvas(w/dn,sp+5f)
        extra(cv,key,w,(sp+5f)/2*dn,sp)
        return b
    }

    /** 2 x 2: the icon and the temperature, centred, with the chosen value under them. */
    fun tall(wDp:Float,hDp:Float,extraKey:String?):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        val es=13f;val eh=if(extraKey!=null) (es+6f)*dn else 0f
        val s=min(h-eh,44f*dn).coerceAtLeast(16f*dn);val ts=(s/dn*0.5f).coerceIn(16f,24f)
        val tw=width(temp,ts,true);val x0=(w-s-6f*dn-tw)/2
        val top=(h-s-eh)/2
        weather(cv,d.code,d.isDayFlag,x0,top,s)
        text(cv,temp,x0+s+6f*dn,top+s/2,ts,pal.fg,true)
        if(extraKey!=null) { val ew=extraWidth(extraKey,es);extra(cv,extraKey,(w+ew)/2,top+s+eh/2+1f*dn,es) }
        return b
    }

    /**
     * Two rows, right of the clock, with the middle of the icon at the middle of the time ([timeMid]). Three cells: the icon beside the
     * temperature, smaller where the long date leaves less room. Four and five: the same, larger, with the chosen value and its name
     * under them, or the weather in words without it; the icon is smaller with the chosen value, so that both fit. The size does not
     * depend on the date.
     */
    fun box(roomDp:Float,topH:Float,cells:Int,extraKey:String?,timeMid:Float):Bitmap {
        if(cells==3) {
            var ts=24f;var s=min(36f,topH*0.6f)
            val k=min(1f,roomDp/(s+5f+width(temp,ts,true)/dn))
            ts*=k;s*=k
            val tw=width(temp,ts,true)/dn
            val ww=s+5f+tw;val cy=max(timeMid,max(s,ts*1.25f)/2)
            val h=cy+max(s,ts*1.25f)/2
            val (b,cv)=canvas(ww,h)
            text(cv,temp,ww*dn,cy*dn,ts,pal.fg,true,Paint.Align.RIGHT)
            weather(cv,d.code,d.isDayFlag,0f,(cy-s/2)*dn,s*dn)
            return b
        }
        val big=cells>=5
        val ts=if(big) 32f else 28f;val es=if(big) 16f else 14f
        val extraH=if(extraKey!=null) 10f+es+12f else 0f
        val condSp=12f;val condH=if(extraKey==null) condSp*1.3f+4f else 0f
        val s=if(extraKey!=null) min(if(big) 48f else 44f,topH-extraH-4f) else min(if(big) 56f else 50f,topH*0.85f-condH)
        val tw=width(temp,ts,true)/dn
        val cond=overviewCondition(d.code)
        val ew=if(extraKey!=null) max(extraWidth(extraKey,es),width(Notifier.paramLabel(extraKey),10f,false))/dn else 0f
        val cw=if(extraKey==null) width(cond,condSp,false)/dn else 0f
        val ww=min(maxOf(s+6f+tw,ew,cw),roomDp)
        // The icon centred on the time where the value under it still fits.
        val cyDp=timeMid.coerceIn(2f+s/2,max(2f+s/2,topH-extraH-condH-s/2))
        val h=cyDp+s/2+4f+extraH+condH
        val (b,cv)=canvas(ww,h);val w=ww*dn
        val cy=cyDp*dn
        text(cv,temp,w,cy,ts,pal.fg,true,Paint.Align.RIGHT)
        weather(cv,d.code,d.isDayFlag,w-(tw+6f+s)*dn,cy-s/2*dn,s*dn)
        val y=cy+(s/2+6f)*dn
        if(extraKey!=null) {
            text(cv,fit(Notifier.paramLabel(extraKey),10f,false,w),w,y+5f*dn,10f,pal.muted,false,Paint.Align.RIGHT)
            extra(cv,extraKey,w,y+10f*dn+es*dn*0.75f,es)
        } else text(cv,fit(cond,condSp,false,w),w,y+condSp*dn*0.55f,condSp,pal.muted,false,Paint.Align.RIGHT)
        return b
    }

    /** The second row: the place with the warning on the left, the time of the update on the right where it fits. */
    fun place(wDp:Float,hDp:Float,alert:UnifiedAlert?,updated:String?,centre:Boolean=false):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        val sp=if(centre) 13f else 14f
        val upW=if(updated!=null) width(updated,10f,false)+10f*dn else 0f
        val full=placeWidth(l.name,sp,alert)
        val showUpdated=updated!=null && full+upW<=w
        val name=fittedPlace(sp,alert,w-(if(showUpdated) upW else 0f))
        val x=if(centre) ((w-placeWidth(name,sp,alert))/2).coerceAtLeast(0f) else 0f
        place(cv,name,x,h/2,sp,alert)
        if(showUpdated) text(cv,updated!!,w,h/2,10f,pal.muted,false,Paint.Align.RIGHT)
        return b
    }

    /** 6 days and 6 hours: the place with the warning and the weather in words, the icon and the temperature, right of the clock, at the natural width. */
    fun head(roomDp:Float,hDp:Float,alert:UnifiedAlert?):Bitmap {
        val ts=if(roomDp>=150f) 26f else 22f
        val s=if(roomDp>=150f) 34f else 28f
        val tw=width(temp,ts,true)/dn
        val cond=overviewCondition(d.code)
        val placeW=placeWidth(l.name,14f,alert)/dn
        val oneLine=placeW+8f+s+6f+tw<=roomDp
        val textW=if(oneLine) max(placeW,width(cond,11f,false)/dn) else 0f
        val ww=if(oneLine) textW+8f+s+6f+tw else roomDp
        // The height follows the time only; the date does not move the picture.
        val hh=max(hDp,if(oneLine) max(s+4f,34f) else 20f+s+2f)
        val (b,cv)=canvas(ww,hh);val w=ww*dn;val h=hh*dn
        if(oneLine) {
            // One line: the place over the weather in words, the icon and the temperature.
            val top=ts*dn*0.6f+2f*dn
            text(cv,temp,w,top,ts,pal.fg,true,Paint.Align.RIGHT)
            weather(cv,d.code,d.isDayFlag,w-(tw+6f+s)*dn,top-s*dn/2,s*dn)
            val right=w-(tw+6f+s+8f)*dn
            place(cv,l.name,right-placeWidth(l.name,14f,alert),9f*dn,14f,alert)
            text(cv,cond,right,25f*dn,11f,pal.muted,false,Paint.Align.RIGHT)
        } else {
            // A narrow widget: the place on its own line, under it the weather in words, the icon and the temperature.
            val name=fittedPlace(14f,alert,w)
            place(cv,name,w-placeWidth(name,14f,alert),9f*dn,14f,alert)
            val cy=(20f*dn+s*dn/2).coerceAtMost(h-s*dn/2)
            text(cv,temp,w,cy,ts,pal.fg,true,Paint.Align.RIGHT)
            weather(cv,d.code,d.isDayFlag,w-(tw+6f+s)*dn,cy-s*dn/2,s*dn)
            val room=w-(tw+6f+s+8f)*dn
            if(room>30f*dn) text(cv,fit(cond,11f,false,room),room,cy,11f,pal.muted,false,Paint.Align.RIGHT)
        }
        return b
    }

    /** Six days: the day, the weather, the highest and the lowest temperature, as in the 6 days widget; the bottom row is a value of the day. */
    fun dayColumns(series:HourlySeries?,bottom:String?):List<ClockColumn> {
        series ?: return emptyList()
        val days=series.days()
        return days.mapIndexed { i,day ->
            val name=if(i==0) uiText(R.string.wg_today) else day.date.format(DateTimeFormatter.ofPattern("EEE",appLocale()))
            val value=bottom?.let { key ->
                val ix=series.times.indices.filter { java.time.Instant.ofEpochSecond(series.times[it]).atZone(series.zone).toLocalDate()==day.date }
                val raw=series.of(ClockBottomKeys.getValue(key))
                clockBottomValue(key,clockDayValue(key,ix.map { raw[it] }),u)
            } ?: ""
            ClockColumn(name,day.code,true,WidgetRender.t(u,day.max),WidgetRender.t(u,day.min),value)
        }
    }

    /** Six hours from the next one: the time, the weather, the temperature and the apparent temperature; the bottom row is a value of the hour. */
    fun hourColumns(series:HourlySeries?,bottom:String?):List<ClockColumn> {
        val now=Instant.now().epochSecond
        if(series!=null) {
            val start=series.times.indexOfFirst { it>now }
            if(start>=0) return (start until min(start+6,series.times.size)).map { i ->
                val time=Instant.ofEpochSecond(series.times[i]).atZone(series.zone)
                val code=series.of("weather_code")[i]
                ClockColumn(time.format(DateTimeFormatter.ofPattern("HH:mm")),if(code.isFinite()) code.toInt() else -1,series.of("is_day")[i]!=0.0,
                    WidgetRender.t(u,series.of("temperature_2m")[i]),WidgetRender.t(u,series.of("apparent_temperature")[i]),
                    bottom?.let { clockBottomValue(it,series.of(ClockBottomKeys.getValue(it))[i],u) } ?: "")
            }
        }
        return d.hours.take(6).map { h -> ClockColumn(h.time.format(DateTimeFormatter.ofPattern("HH:mm")),h.code,WidgetRender.dayAt(d,h.time),WidgetRender.t(u,h.temperature),"","") }
    }

    /**
     * Six columns with the heights of the 6 days widget. A lower picture shrinks them evenly instead of cutting them; a higher one
     * (without the date or the bottom row) widens the gaps between the rows a little. The bottom row is smaller and quieter than the
     * temperatures and has one icon of its value, on the left of the whole row; the columns then leave room for it.
     */
    fun columns(wDp:Float,hDp:Float,cols:List<ClockColumn>,bottomKey:String?):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        val bottom=bottomKey!=null
        val natural=columnsHeight(bottom)
        val k=min(1f,hDp/natural)
        val gaps=if(bottom) 4 else 3
        val gap=if(hDp>natural) min(6f,(hDp-natural)/(gaps+2)) else 0f
        val used=natural*k+gap*gaps
        val iconSize=12f*k*dn
        val gutter=if(bottom) iconSize+4f*dn else 0f
        val cw=(w-gutter)/6
        cols.take(6).forEachIndexed { i,col ->
            val cx=gutter+cw*i+cw/2
            var y=(h-used*dn)/2
            text(cv,fit(col.label,11f*k,false,cw),cx,y+Label*k*dn/2,11f*k,pal.muted,false,Paint.Align.CENTER);y+=(Label*k+gap)*dn
            y+=IconGap*k*dn;weather(cv,col.code,col.day,cx-Icon*k*dn/2,y,Icon*k*dn);y+=((Icon+IconGap)*k+gap)*dn
            text(cv,col.main,cx,y+Main*k*dn/2,13f*k,pal.fg,true,Paint.Align.CENTER);y+=(Main*k+gap)*dn
            if(col.second.isNotEmpty()) text(cv,col.second,cx,y+Second*k*dn/2,12f*k,pal.muted,false,Paint.Align.CENTER)
            y+=(Second*k+gap)*dn
            if(bottomKey!=null && col.bottom.isNotEmpty()) bottomValue(cv,col.bottom,cx,y+Bottom*k*dn/2,cw-2f*dn,10f*k)
            if(bottomKey!=null && i==0) pic(cv,widgetParamIcon(bottomKey,pal.muted,iconSize.roundToInt().coerceAtLeast(1),Double.NaN,12f*k),0f,y+Bottom*k*dn/2-iconSize/2,iconSize)
        }
        return b
    }

    /** A value of the bottom row, centred at [cx]; the text shrinks to the width of the column. */
    private fun bottomValue(cv:Canvas,value:String,cx:Float,cy:Float,room:Float,spec:Float) {
        var sp=spec
        while(sp>8f && width(value,sp,false)>room) sp-=0.5f
        text(cv,fit(value,sp,false,room),cx,cy,sp,pal.muted,false,Paint.Align.CENTER)
    }

}
