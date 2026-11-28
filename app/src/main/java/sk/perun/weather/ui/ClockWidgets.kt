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

    fun size(o:Bundle):ClockSize = ClockSize(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 250,o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 110)

    private fun layoutOf(kind:Class<*>,s:ClockSize):Int = when {
        kind!=ClockWidget::class.java->R.layout.wg_clock_days
        s.row->R.layout.wg_clock_row
        s.cells<=2->R.layout.wg_clock_tall
        else->R.layout.wg_clock_box
    }

    private fun alarmSet(c:Context):Boolean = c.getSystemService(AlarmManager::class.java)?.nextAlarmClock!=null

    /** The time and the date in sp for the size of the widget. */
    private fun clockSp(kind:Class<*>,s:ClockSize):Pair<Float,Float> = when {
        kind!=ClockWidget::class.java->(if(s.cells>=5) 46f else 40f).coerceAtMost(s.hDp*0.26f) to 12f
        s.row->(when(s.cells) { 2->30f;3->36f;4->40f;else->44f }).coerceAtMost(s.hDp*0.55f) to 11f
        s.cells<=2->40f.coerceAtMost(s.hDp*0.3f) to 12f
        else->(when(s.cells) { 3->44f;4->54f;else->60f }).coerceAtMost(s.hDp*0.36f) to 13f
    }

    private fun datePattern(style:String,cells:Int):String {
        // Two cells in one row have room only for the short date.
        val skeleton=if(style=="short" || cells<=2) "EEEdM" else "EEEEdMMMM"
        return DateFormat.getBestDateTimePattern(appLocale(),skeleton)
    }

    private fun typeface(style:String):Typeface = when(style) {
        "regular"->Typeface.create("sans-serif",Typeface.NORMAL)
        "bold"->Typeface.create("sans-serif",Typeface.BOLD)
        "condensed"->Typeface.create("sans-serif-condensed",Typeface.NORMAL)
        else->Typeface.create("sans-serif-light",Typeface.NORMAL)
    }

    /** The width of the clock block in dp: the wider of the time (with the alarm mark) and the date. */
    private fun clockWidthDp(c:Context,w:WidgetConfig,s:ClockSize,timeSp:Float,dateSp:Float,alarm:Boolean):Float {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        // Sizes in sp grow with the font size of the system.
        val scale=c.resources.configuration.fontScale
        p.typeface=typeface(w.clock);p.textSize=timeSp*scale
        val time=p.measureText("22:22")+(if(alarm) 16f else 0f)
        val date=if(w.date=="none") 0f else {
            p.typeface=Typeface.DEFAULT;p.textSize=dateSp*scale
            p.measureText(DateTimeFormatter.ofPattern(datePattern(w.date,s.cells),appLocale()).format(LocalDate.now()))
        }
        return max(time,date)
    }

    private fun clock(c:Context,v:RemoteViews,id:Int,w:WidgetConfig,s:ClockSize,kind:Class<*>,pal:Palette,timeSp:Float,dateSp:Float,alarm:Boolean) {
        TimeIds.forEach { (style,view) ->
            v.setViewVisibility(view,if(style==w.clock) View.VISIBLE else View.GONE)
            v.setTextViewTextSize(view,TypedValue.COMPLEX_UNIT_SP,timeSp)
            v.setTextColor(view,pal.fg)
        }
        v.setViewVisibility(R.id.wg_alarm,if(alarm) View.VISIBLE else View.GONE)
        v.setInt(R.id.wg_alarm,"setColorFilter",pal.fg)
        if(w.date=="none") v.setViewVisibility(R.id.wg_date,View.GONE) else {
            val pattern=datePattern(w.date,s.cells)
            v.setViewVisibility(R.id.wg_date,View.VISIBLE)
            v.setCharSequence(R.id.wg_date,"setFormat12Hour",pattern);v.setCharSequence(R.id.wg_date,"setFormat24Hour",pattern)
            v.setTextViewTextSize(R.id.wg_date,TypedValue.COMPLEX_UNIT_SP,dateSp)
            v.setTextColor(R.id.wg_date,pal.fg)
        }
        // 2 x 2 centres the date under the time.
        v.setInt(R.id.wg_clock_box,"setGravity",if(kind==ClockWidget::class.java && !s.row && s.cells<=2) android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.START)
        val clockApp=Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.wg_clock_tap,PendingIntent.getActivity(c,30000+id,clockApp,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val calendar=Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.wg_date,PendingIntent.getActivity(c,40000+id,calendar,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    fun render(c:Context,kind:Class<*>,id:Int,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,pal:Palette,o:Bundle,days:List<DaySummary>):RemoteViews {
        val s=size(o);val dn=c.resources.displayMetrics.density
        val v=RemoteViews(c.packageName,layoutOf(kind,s))
        val alarm=alarmSet(c)
        val (timeSp,dateSp)=clockSp(kind,s)
        clock(c,v,id,w,s,kind,pal,timeSp,dateSp,alarm)
        val clockW=clockWidthDp(c,w,s,timeSp,dateSp,alarm)
        val art=ClockArt(dn,pal,d,ex,u,l)
        val updated=uiText(R.string.wg_updated,d.time.format(Hm))
        v.setInt(R.id.wg_refresh,"setColorFilter",pal.muted)
        if(kind==ClockWidget::class.java) {
            val a=widgetNextDay().let { (from,to)->widgetAlert(d.alerts,from,to) }
            val extra=w.extra.takeIf { it!="none" && it in Notifier.CurrentOptions }
            when {
                s.row -> {
                    val refresh=s.cells>=5
                    v.setViewVisibility(R.id.wg_refresh,if(refresh) View.VISIBLE else View.GONE)
                    val wDp=s.wDp-22f-clockW-8f-(if(refresh) 30f else 0f)
                    v.setImageViewBitmap(R.id.wg_weather,art.row(wDp.coerceAtLeast(40f),(s.hDp-12f).coerceAtLeast(30f),s.cells,if(s.cells>=4) extra else null,a))
                }
                s.cells<=2 -> {
                    v.setImageViewBitmap(R.id.wg_weather,art.tall((s.wDp-16f).coerceAtLeast(40f),(s.hDp-20f-timeSp*1.2f-(if(w.date=="none") 0f else dateSp*1.5f)-20f).coerceAtLeast(24f)))
                    v.setImageViewBitmap(R.id.wg_place,art.place((s.wDp-16f).coerceAtLeast(40f),20f,a,null,centre=true))
                }
                else -> {
                    val topH=(s.hDp-20f-26f).coerceAtLeast(30f)
                    v.setImageViewBitmap(R.id.wg_weather,art.box((s.wDp-26f-clockW-8f).coerceAtLeast(40f),topH,s.cells,extra))
                    v.setImageViewBitmap(R.id.wg_place,art.place((s.wDp-26f-30f).coerceAtLeast(40f),26f,a,if(s.cells>=4) updated else null))
                }
            }
            v.setContentDescription(R.id.wg_weather,l.name+" "+WidgetRender.t(u,d.temperature)+(a?.let { ", "+alertName(it) } ?: ""))
        } else {
            val hours=kind==ClockHoursWidget::class.java
            val now=Instant.now()
            val a=if(hours) widgetAlert(d.alerts,now,now.plusSeconds(6*3600)) else widgetDays(now,d.zone,6).let { (from,to)->widgetAlert(d.alerts,from,to) }
            val headH=timeSp*1.15f+(if(w.date=="none") 0f else dateSp*1.6f)
            v.setImageViewBitmap(R.id.wg_weather,art.head((s.wDp-22f-clockW-8f).coerceAtLeast(60f),headH.coerceAtLeast(40f),a))
            val rowsH=(s.hDp-14f-headH-4f-24f).coerceAtLeast(40f)
            v.setImageViewBitmap(R.id.wg_rows,if(hours) art.hours((s.wDp-28f).coerceAtLeast(120f),rowsH) else art.days((s.wDp-28f).coerceAtLeast(120f),rowsH,days))
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

/** The pictures of the clock widgets, in dp sizes, with the icons of the app (widgetSymbolBitmap, widgetParamIcon, drawGpsMark, widgetAlertBitmap). */
private class ClockArt(val dn:Float,val pal:Palette,val d:OverviewData,val ex:OverviewExtra?,val u:WeatherUnits,val l:Location) {
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

    /** One row: two cells the icon over the temperature; three the icon beside it; four and five also the place with the warning over the chosen value. */
    fun row(wDp:Float,hDp:Float,cells:Int,extraKey:String?,alert:UnifiedAlert?):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        if(cells<=2) {
            val ts=(hDp*0.3f).coerceIn(13f,18f)
            val s=min(h-ts*dn*1.3f,w)
            weather(cv,d.code,d.isDayFlag,w-s-(width(temp,ts,true)-s).coerceAtLeast(0f)/2,0f,s)
            text(cv,temp,w-max(s,width(temp,ts,true))/2,h-ts*dn*0.6f,ts,pal.fg,true,Paint.Align.CENTER)
            return b
        }
        val ts=if(cells>=4) (hDp*0.38f).coerceIn(16f,20f) else (hDp*0.42f).coerceIn(16f,22f)
        val s=min(h*0.8f,(if(cells>=4) 30f else 34f)*dn)
        val tw=width(temp,ts,true)
        text(cv,temp,w,h/2,ts,pal.fg,true,Paint.Align.RIGHT)
        weather(cv,d.code,d.isDayFlag,w-tw-4f*dn-s,(h-s)/2,s)
        if(cells>=4) {
            val room=w-tw-4f*dn-s-16f*dn
            val sp=12f
            val name=fittedPlace(sp,alert,room)
            val hasExtra=extraKey!=null && extraWidth(extraKey,sp)<=room
            val colW=max(placeWidth(name,sp,alert),if(hasExtra) extraWidth(extraKey!!,sp) else 0f)
            val x0=(room-colW).coerceAtLeast(0f)
            if(hasExtra) {
                place(cv,name,x0,h/2-9f*dn,sp,alert)
                extra(cv,extraKey!!,x0+extraWidth(extraKey,sp),h/2+9f*dn,sp)
            } else place(cv,name,x0,h/2,sp,alert)
        }
        return b
    }

    /** 2 x 2: the icon and the temperature, centred. */
    fun tall(wDp:Float,hDp:Float):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        val s=min(h,44f*dn);val ts=(s/dn*0.5f).coerceIn(16f,24f)
        val tw=width(temp,ts,true);val x0=(w-s-6f*dn-tw)/2
        weather(cv,d.code,d.isDayFlag,x0,(h-s)/2,s)
        text(cv,temp,x0+s+6f*dn,h/2,ts,pal.fg,true)
        return b
    }

    /**
     * Two rows, right of the clock. Three cells: the icon, the temperature and the chosen value under each other. Four: the icon
     * beside the temperature with the chosen value under them. Five: the chosen value beside the temperature.
     */
    fun box(wDp:Float,hDp:Float,cells:Int,extraKey:String?):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        when(cells) {
            3 -> {
                val es=12f;val ts=22f
                val s=min(38f*dn,h-ts*dn-(if(extraKey!=null) es*dn+8f*dn else 0f)-4f*dn).coerceAtLeast(16f*dn)
                weather(cv,d.code,d.isDayFlag,w-s,0f,s)
                text(cv,temp,w,s+ts*dn*0.55f,ts,pal.fg,true,Paint.Align.RIGHT)
                if(extraKey!=null) extra(cv,extraKey,w,s+ts*dn*1.1f+es*dn*0.7f,es)
            }
            4 -> boxStacked(cv,w,h,extraKey)
            else -> {
                // The chosen value beside the temperature; without room for it the layout of four cells.
                val ts=30f;val es=16f
                val tw=width(temp,ts,true)
                val ew=if(extraKey!=null) max(extraWidth(extraKey,es),width(Notifier.paramLabel(extraKey),10f,false)) else 0f
                val right=if(extraKey!=null) w-ew-14f*dn else w
                val s=min(min(52f*dn,h*0.6f),right-tw-10f*dn)
                if(s<24f*dn) { boxStacked(cv,w,h,extraKey);return b }
                val cy=4f*dn+s/2
                if(extraKey!=null) {
                    text(cv,fit(Notifier.paramLabel(extraKey),10f,false,ew),w,cy-9f*dn,10f,pal.muted,false,Paint.Align.RIGHT)
                    extra(cv,extraKey,w,cy+8f*dn,es)
                }
                text(cv,temp,right,cy,ts,pal.fg,true,Paint.Align.RIGHT)
                weather(cv,d.code,d.isDayFlag,right-tw-10f*dn-s,cy-s/2,s)
            }
        }
        return b
    }
    /** The icon beside the temperature, the chosen value with its name under them. */
    private fun boxStacked(cv:Canvas,w:Float,h:Float,extraKey:String?) {
        val ts=28f;val es=14f
        val tw=width(temp,ts,true)
        val s=min(min(46f*dn,h*0.55f),(w-tw-6f*dn).coerceAtLeast(16f*dn))
        val cy=4f*dn+s/2
        text(cv,temp,w,cy,ts,pal.fg,true,Paint.Align.RIGHT)
        weather(cv,d.code,d.isDayFlag,w-tw-6f*dn-s,cy-s/2,s)
        if(extraKey!=null) {
            val y=cy+s/2+8f*dn
            text(cv,fit(Notifier.paramLabel(extraKey),10f,false,w),w,y+5f*dn,10f,pal.muted,false,Paint.Align.RIGHT)
            extra(cv,extraKey,w,y+10f*dn+es*dn*0.75f,es)
        }
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

    /** 6 days and 6 hours: the place with the warning and the weather in words, the icon and the temperature, right of the clock. */
    fun head(wDp:Float,hDp:Float,alert:UnifiedAlert?):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        val ts=if(wDp>=150f) 26f else 22f
        val s=min(if(wDp>=150f) 36f*dn else 30f*dn,h*0.6f)
        val tw=width(temp,ts,true)
        val room=w-tw-6f*dn-s-8f*dn
        if(placeWidth(l.name,14f,alert)<=room) {
            // One line: the place over the weather in words, the icon and the temperature.
            val top=ts*dn*0.6f+2f*dn
            text(cv,temp,w,top,ts,pal.fg,true,Paint.Align.RIGHT)
            weather(cv,d.code,d.isDayFlag,w-tw-6f*dn-s,top-s/2,s)
            place(cv,l.name,room-placeWidth(l.name,14f,alert),9f*dn,14f,alert)
            text(cv,fit(overviewCondition(d.code),11f,false,room),room,25f*dn,11f,pal.muted,false,Paint.Align.RIGHT)
        } else {
            // A narrow widget: the place on its own line, under it the weather in words, the icon and the temperature.
            val name=fittedPlace(14f,alert,w)
            place(cv,name,w-placeWidth(name,14f,alert),9f*dn,14f,alert)
            val cy=(20f*dn+s/2).coerceAtMost(h-s/2)
            text(cv,temp,w,cy,ts,pal.fg,true,Paint.Align.RIGHT)
            weather(cv,d.code,d.isDayFlag,w-tw-6f*dn-s,cy-s/2,s)
            if(room>30f*dn) text(cv,fit(overviewCondition(d.code),11f,false,room),room,cy,11f,pal.muted,false,Paint.Align.RIGHT)
        }
        return b
    }

    private fun columns(wDp:Float,hDp:Float,draw:(Canvas,Int,Float,Float,Float)->Unit):Bitmap {
        val (b,cv)=canvas(wDp,hDp);val w=wDp*dn;val h=hDp*dn
        val cw=w/6
        for(i in 0 until 6) draw(cv,i,cw*i+cw/2,h,cw)
        return b
    }

    /** Six days: the day, the icon, the highest and the lowest temperature, as in the 6 days widget. */
    fun days(wDp:Float,hDp:Float,days:List<DaySummary>):Bitmap = columns(wDp,hDp) { cv,i,cx,h,cw ->
        val day=days.getOrNull(i) ?: return@columns
        val s=min(22f*dn,h*0.3f)
        val total=11f*dn+3f*dn+s+3f*dn+13f*dn+2f*dn+11f*dn
        var y=((h-total)/2).coerceAtLeast(0f)
        val name=if(i==0) uiText(R.string.wg_today) else day.date.format(DateTimeFormatter.ofPattern("EEE",appLocale()))
        text(cv,fit(name,11f,false,cw),cx,y+5.5f*dn,11f,pal.muted,false,Paint.Align.CENTER);y+=14f*dn
        weather(cv,day.code,true,cx-s/2,y,s);y+=s+3f*dn
        text(cv,WidgetRender.t(u,day.max),cx,y+6.5f*dn,13f,pal.fg,true,Paint.Align.CENTER);y+=15f*dn
        text(cv,WidgetRender.t(u,day.min),cx,y+5.5f*dn,11f,pal.muted,false,Paint.Align.CENTER)
    }

    /** Six hours: the time, the icon and the temperature, as in the Next 6 hours widget. */
    fun hours(wDp:Float,hDp:Float):Bitmap = columns(wDp,hDp) { cv,i,cx,h,_ ->
        val hour=d.hours.getOrNull(i) ?: return@columns
        val s=min(22f*dn,h*0.34f)
        val total=11f*dn+3f*dn+s+3f*dn+13f*dn
        var y=((h-total)/2).coerceAtLeast(0f)
        text(cv,hour.time.format(DateTimeFormatter.ofPattern("HH:mm")),cx,y+5.5f*dn,11f,pal.muted,false,Paint.Align.CENTER);y+=14f*dn
        weather(cv,hour.code,WidgetRender.dayAt(d,hour.time),cx-s/2,y,s);y+=s+3f*dn
        text(cv,WidgetRender.t(u,hour.temperature),cx,y+6.5f*dn,13f,pal.fg,true,Paint.Align.CENTER)
    }
}
