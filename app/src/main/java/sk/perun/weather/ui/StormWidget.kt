package sk.perun.weather.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import sk.perun.weather.R
import sk.perun.weather.ui.theme.PerunTheme
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** [show]: "radar" (the circle of storm cells and lightning), "chart" (the bars of precipitation), "storm" (storm cells and lightning at all). */
internal class StormWidgetConfig(val loc:String,val show:Set<String>,val range:Int,val span:Int,val bg:String,val text:String)

/** The Precipitation and storms widget: the nowcast of the Now screen up to two hours, storm cells and lightning around the place. */
internal object StormWidgets {
    private const val Prefs="perun_widget_storm"
    private const val Once="perun-widget-storm"
    private const val Soon="perun-widget-storm-soon"
    private const val Periodic="perun-widget-storm-tick"
    const val ActionRefresh="sk.perun.weather.STORM_WIDGET_REFRESH"
    val Shows=listOf("radar","chart","storm")
    val Ranges=listOf(25,50,100)
    val Spans=listOf(60,120)
    private fun prefs(c:Context) = c.applicationContext.getSharedPreferences(Prefs,Context.MODE_PRIVATE)
    fun read(c:Context,id:Int):StormWidgetConfig {
        val p=prefs(c)
        val show=(p.getString("show_$id",null) ?: Shows.joinToString(",")).split(",").filter { it in Shows }.toSet()
        return StormWidgetConfig(p.getString("loc_$id","active") ?: "active",show,p.getInt("range_$id",50).takeIf { it in Ranges } ?: 50,p.getInt("span_$id",120).takeIf { it in Spans } ?: 120,
            (p.getString("bg_$id","dark") ?: "dark").takeIf { it in PerunWidgets.Backgrounds } ?: "dark",if(p.getString("text_$id","light")=="dark") "dark" else "light")
    }
    fun write(c:Context,id:Int,w:StormWidgetConfig) {
        prefs(c).edit().putString("loc_$id",w.loc).putString("show_$id",Shows.filter { it in w.show }.joinToString(",")).putInt("range_$id",w.range).putInt("span_$id",w.span)
            .putString("bg_$id",w.bg).putString("text_$id",w.text).apply()
    }
    fun remove(c:Context,ids:IntArray) { val e=prefs(c).edit();ids.forEach { id -> listOf("loc_","show_","range_","span_","bg_","text_").forEach { e.remove(it+id) } };e.apply() }
    fun loc(c:Context,id:Int):String = read(c,id).loc
    fun ids(c:Context):IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,StormWidget::class.java))
    private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun refresh(c:Context,force:Boolean=false) {
        if(ids(c).isEmpty()) return
        val request=OneTimeWorkRequestBuilder<StormWidgetWorker>().setInputData(workDataOf("force" to force)).setConstraints(online()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(Once,ExistingWorkPolicy.REPLACE,request)
    }
    /** Every 15 minutes, the shortest period of Android. */
    fun schedule(c:Context) {
        val request=PeriodicWorkRequestBuilder<StormWidgetWorker>(15,TimeUnit.MINUTES).setConstraints(online()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(Periodic,ExistingPeriodicWorkPolicy.KEEP,request)
    }
    /** While it rains or a storm is near, one more drawing 8 minutes later, between the periodic ones. */
    fun soon(c:Context) {
        val request=OneTimeWorkRequestBuilder<StormWidgetWorker>().setInitialDelay(8,TimeUnit.MINUTES).setConstraints(online()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(Soon,ExistingWorkPolicy.KEEP,request)
    }
    fun cancel(c:Context) { WorkManager.getInstance(c.applicationContext).cancelUniqueWork(Periodic);WorkManager.getInstance(c.applicationContext).cancelUniqueWork(Soon) }

    /** Loads and draws one widget; false when the place or the data is missing. Returns null on failure, otherwise whether the weather is lively. */
    suspend fun render(c:Context,id:Int,all:List<Location>,active:Location?):Boolean? {
        val cfg=read(c,id)
        val l=(if(cfg.loc=="active") null else all.firstOrNull { BackgroundRefresh.locationKey(it)==cfg.loc }) ?: active ?: return null
        val n=ncLoadLong(l,LocationSourceStore(c).read(l).nowcastSource)
        if(n.points.isEmpty() && n.storm.cells==null && n.storm.strikes==null) return null
        val manager=AppWidgetManager.getInstance(c);val o=manager.getAppWidgetOptions(id)
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 250
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 110
        val dn=c.resources.displayMetrics.density
        val warning=widgetNextDay().let { (from,to)->widgetAlert(widgetPlaceAlerts(c,l),from,to) }
        val pal=if(cfg.text=="dark") DarkText else LightText
        val shape=StormShape.of(wDp,hDp)
        val pad=if(shape.cols==1) 6 else 10
        val art=StormArt(dn,pal,l,n,cfg,warning,shape)
        val (bmp,refresh)=art.draw((wDp-2*pad).toFloat(),(hDp-2*pad).toFloat())
        val v=RemoteViews(c.packageName,R.layout.wg_storm)
        v.setImageViewBitmap(R.id.wg_storm_img,bmp)
        v.setContentDescription(R.id.wg_storm_img,l.name+" \u00B7 "+art.headline()+(warning?.let { ", "+alertName(it) } ?: ""))
        v.setInt(R.id.wg_root,"setBackgroundResource",widgetBackgroundRes(cfg.bg))
        v.setViewPadding(R.id.wg_root,(pad*dn).roundToInt(),(pad*dn).roundToInt(),(pad*dn).roundToInt(),(pad*dn).roundToInt())
        // The refresh button stands where the picture keeps its room: its top left corner in dp from the top left of the picture.
        if(refresh==null) v.setViewVisibility(R.id.wg_refresh,View.GONE) else {
            v.setViewVisibility(R.id.wg_refresh,View.VISIBLE)
            v.setViewLayoutMargin(R.id.wg_refresh,RemoteViews.MARGIN_LEFT,refresh.first,TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutMargin(R.id.wg_refresh,RemoteViews.MARGIN_TOP,refresh.second,TypedValue.COMPLEX_UNIT_DIP)
            v.setInt(R.id.wg_refresh,"setColorFilter",pal.muted)
            v.setOnClickPendingIntent(R.id.wg_refresh,PendingIntent.getBroadcast(c,20000+id,Intent(c,StormWidget::class.java).setAction(ActionRefresh),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        val open=(c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent()).apply {
            setData(android.net.Uri.parse("perun://widget/"+id))
            putExtra("perun_route","overview");putExtra("perun_location",BackgroundRefresh.locationKey(l))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        v.setOnClickPendingIntent(R.id.wg_root,PendingIntent.getActivity(c,10000+id,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id,v)
        return n.points.any { it.min<=60 && it.rate>=0.1 } || (n.storm.cells?.any { it.dist<=25 }==true) || (n.storm.strikes?.any { it.dist<=25 }==true)
    }
}

/** Cells of width (1 to 5) and rows (1 or 2) of a widget. */
internal data class StormShape(val cols:Int,val rows:Int) {
    companion object {
        fun of(wDp:Int,hDp:Int):StormShape = StormShape(when { wDp<120->1;wDp<180->2;wDp<250->3;wDp<PerunWidgets.WideDp->4;else->5 },if(hDp<100) 1 else 2)
    }
}

/** The colour of a rate of precipitation, as in the nowcast tile of the Now screen. */
internal fun stormRateColor(r:Double):Int = when { r>=7.6->0xFFEF5350.toInt();r>=2.5->0xFFFFCA28.toInt();r>=0.5->0xFF66BB6A.toInt();r>=0.1->0xFFA5D6A7.toInt();else->0 }

/** The whole widget as one picture, laid out by its shape; returns the picture and the place of the refresh button in dp, or null without it. */
private class StormArt(val dn:Float,val pal:Palette,val l:Location,val n:NcNow,val cfg:StormWidgetConfig,val warning:UnifiedAlert?,val shape:StormShape) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val StormText=0xFFFFB4A9.toInt()
    private val showStorm="storm" in cfg.show
    private val showRadar=showStorm && "radar" in cfg.show
    private val showChart="chart" in cfg.show
    private val points=n.points.filter { it.min<=cfg.span }
    private val cells=if(showStorm) n.storm.cells.orEmpty().filter { it.dist<=cfg.range } else emptyList()
    private val strikes=if(showStorm) n.storm.strikes.orEmpty().filter { it.dist<=cfg.range } else emptyList()
    private val main=cells.firstOrNull()
    private val nearestStrike=strikes.minByOrNull { it.dist }

    private fun font(sp:Float,bold:Boolean) { p.textSize=sp*dn;p.typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    private fun width(s:String,sp:Float,bold:Boolean):Float { font(sp,bold);return p.measureText(s) }
    private fun text(cv:Canvas,s:String,x:Float,cy:Float,sp:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT) {
        font(sp,bold);p.color=color;p.style=Paint.Style.FILL;p.textAlign=align;cv.drawText(s,x,cy+sp*dn*0.36f,p);p.textAlign=Paint.Align.LEFT
    }
    private fun fit(s:String,sp:Float,bold:Boolean,room:Float):String {
        font(sp,bold)
        if(p.measureText(s)<=room) return s
        var t=s
        while(t.length>1 && p.measureText(t+"\u2026")>room) t=t.dropLast(1)
        return if(p.measureText(t+"\u2026")<=room) t+"\u2026" else ""
    }
    private fun pic(cv:Canvas,b:Bitmap,x:Float,y:Float,size:Float) { p.color=0xFFFFFFFF.toInt();cv.drawBitmap(b,null,RectF(x,y,x+size,y+size),p) }

    // ---- Texts ----
    private val wetNow=points.firstOrNull()?.let { it.rate>=0.1 }==true
    private val firstWet=points.firstOrNull { it.rate>=0.1 }
    fun headline():String = when {
        wetNow->uiText(R.string.st_raining)
        firstWet!=null->uiText(R.string.st_rain_in,firstWet.min)
        else->uiText(if(cfg.span<=60) R.string.st_dry_1h else R.string.st_dry_2h)
    }
    /** The storm in words: where it is (with its motion on the widest widget) and when it comes, as two parts that may stand on two lines. */
    private fun stormParts(long:Boolean):Pair<String,String>? {
        if(!showStorm) return null
        if(main!=null) {
            val base=if(main.eta==0) uiText(R.string.st_storm_here) else uiText(R.string.st_storm_at,main.dist.roundToInt(),cardinal(main.bearing))
            val moving=if(long && main.speed.isFinite() && main.heading.isFinite() && main.speed>=3) uiText(R.string.st_storm_moving,cardinal(main.heading)) else ""
            val eta=if(main.eta!=null && main.eta>0) uiText(R.string.st_storm_eta,main.eta) else ""
            return base+moving to eta
        }
        if(nearestStrike!=null) return uiText(R.string.st_lightning_at,nearestStrike.dist.roundToInt(),cardinal(atan2(nearestStrike.dx,nearestStrike.dy)*180/PI)) to ""
        return null
    }
    private fun stormLine(long:Boolean):String? = stormParts(long)?.let { it.first+it.second }
    /** The storm on one line, or on two when it does not fit and [two] allows it; returns the number of lines. */
    private fun stormLines(cv:Canvas,x:Float,cy:Float,sp:Float,room:Float,long:Boolean,two:Boolean):Int {
        val (first,second)=stormParts(long) ?: return 0
        val whole=first+second
        if(!two || second.isEmpty() || width(whole,sp,false)+7f*sp*dn*0.11f<=room) { storm(cv,whole,x,cy,sp,room);return 1 }
        storm(cv,first,x,cy,sp,room)
        val arrives=main?.eta?.let { uiText(R.string.st_storm_arrives,it) } ?: second
        text(cv,fit(arrives,sp,false,room-6.5f*sp*dn*0.11f),x+6.5f*sp*dn*0.11f,cy+(sp+4f)*dn,sp,StormText)
        return 2
    }
    private fun calm():String = uiText(R.string.st_no_storms,cfg.range)

    // ---- Pieces ----
    /** The place in bold, the mark of the Current location and the warning. */
    private fun placeWidth(name:String,sp:Float):Float = (if(name.isEmpty()) 0f else width(name,sp,true))+(if(l.current && name.isNotEmpty()) (sp+3f)*dn else 0f)+(if(warning!=null) (sp+7f)*dn else 0f)
    private fun place(cv:Canvas,x:Float,cy:Float,sp:Float,room:Float,alignRight:Boolean=false) {
        val name=fit(l.name,sp,true,room-placeWidth("",sp))
        var at=if(alignRight) x-placeWidth(name,sp) else x
        if(name.isNotEmpty()) { text(cv,name,at,cy,sp,pal.fg,true);at+=width(name,sp,true) }
        if(l.current && name.isNotEmpty()) { drawGpsMark(cv,at+3f*dn,cy,sp*dn,pal.fg);at+=(sp+3f)*dn }
        if(warning!=null) { val a=(sp+3f)*dn;pic(cv,widgetAlertBitmap(warning,a.roundToInt().coerceAtLeast(1)),at+4f*dn,cy-a/2,a) }
    }
    /** A storm line in the colour of the storm tile, with the lightning bolt of the app before it. */
    private fun storm(cv:Canvas,s:String,x:Float,cy:Float,sp:Float,room:Float,color:Int=StormText) {
        val k=sp*dn*0.11f
        bolt(cv,x+2.6f*k,cy,k,color)
        text(cv,fit(s,sp,false,room-7f*k),x+6.5f*k,cy,sp,color)
    }
    private fun bolt(cv:Canvas,x:Float,y:Float,k:Float,color:Int) {
        val path=Path().apply { moveTo(x+.6f*k,y-4.5f*k);lineTo(x-2.6f*k,y+.8f*k);lineTo(x-.4f*k,y+.8f*k);lineTo(x-1.2f*k,y+4.5f*k);lineTo(x+2.6f*k,y-k);lineTo(x+.4f*k,y-k);close() }
        p.style=Paint.Style.FILL;p.color=color;cv.drawPath(path,p)
    }

    /** The radar circle: you in the middle, storm cells in the colours of the radar, the motion of the main cell, lightning of the last minutes, as on the Now screen. */
    private fun radar(cv:Canvas,cx:Float,cy:Float,size:Float) {
        val r=size/2-2f*dn
        p.style=Paint.Style.FILL;p.color=0x14FFFFFF;cv.drawCircle(cx,cy,r,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=1f*dn
        p.color=(0x66 shl 24) or (pal.muted and 0xFFFFFF);cv.drawCircle(cx,cy,r,p)
        p.pathEffect=DashPathEffect(floatArrayOf(2f*dn,3f*dn),0f);p.color=(0x44 shl 24) or (pal.muted and 0xFFFFFF);cv.drawCircle(cx,cy,r/2,p);p.pathEffect=null
        val range=cfg.range.toDouble()
        fun x(dx:Double)=cx+(dx/range*r).toFloat()
        fun y(dy:Double)=cy-(dy/range*r).toFloat()
        if(size>=70f*dn) text(cv,uiText(R.string.ui_s_8de0b3c4),cx,cy-r+6f*dn,8f,pal.muted,true,Paint.Align.CENTER)
        if(size>=110f*dn) {
            text(cv,(cfg.range/2).toString(),cx+r/2+2f*dn,cy-5f*dn,8f,pal.muted)
            text(cv,cfg.range.toString()+" km",cx+r-3f*dn,cy+8f*dn,8f,pal.muted,false,Paint.Align.RIGHT)
        }
        val k=(size/(120f*dn)).coerceIn(.55f,1.2f)
        cells.take(6).forEach { c ->
            p.style=Paint.Style.FILL
            p.color=if(c.dbz.isFinite()) (0xFF shl 24) or NcPalette[(c.dbz.roundToInt()-10).coerceIn(0,NcPalette.lastIndex)] else pal.muted
            cv.drawCircle(x(c.dx),y(c.dy),(if(c===main) 7f else 4.5f)*dn*k,p)
        }
        if(main!=null && main.speed.isFinite() && main.heading.isFinite() && main.speed>=3) {
            var ex=main.dx+main.speed*.5*sin(main.heading*PI/180);var ey=main.dy+main.speed*.5*cos(main.heading*PI/180)
            val len=hypot(ex,ey);if(len>range*1.02) { ex*=range*1.02/len;ey*=range*1.02/len }
            val ax=x(main.dx);val ay=y(main.dy);val bx=x(ex);val by=y(ey)
            p.style=Paint.Style.STROKE;p.strokeWidth=1.3f*dn;p.color=(0xCC shl 24) or (pal.fg and 0xFFFFFF)
            p.pathEffect=DashPathEffect(floatArrayOf(2f*dn,2f*dn),0f);cv.drawLine(ax,ay,bx,by,p);p.pathEffect=null
            val a=atan2((by-ay).toDouble(),(bx-ax).toDouble());val head=4.5f*dn*k
            for(side in listOf(-0.5,0.5)) cv.drawLine(bx,by,bx-(head*cos(a+side)).toFloat(),by-(head*sin(a+side)).toFloat(),p)
        }
        strikes.take(80).forEach { s -> bolt(cv,x(s.dx),y(s.dy),1.7f*dn*k,0xFFFFE14D.toInt()) }
        p.style=Paint.Style.FILL;p.color=0xFFFFFFFF.toInt();cv.drawCircle(cx,cy,4f*dn*k.coerceAtLeast(.8f),p)
        p.color=0xFFE53935.toInt();cv.drawCircle(cx,cy,2.7f*dn*k.coerceAtLeast(.8f),p)
    }

    /** The bars of precipitation every 10 minutes from now; the time axis under them when [axis]. */
    private fun bars(cv:Canvas,x0:Float,top:Float,w:Float,h:Float,axis:Boolean,span:Int=cfg.span) {
        val shown=points.filter { it.min<=span }
        if(shown.isEmpty()) return
        val axisH=if(axis) 12f*dn else 0f
        val bh=h-axisH
        val slots=span/10+1
        val gap=2f*dn;val bw=((w-gap*(slots-1))/slots).coerceAtLeast(1f)
        val top5=max(5.0,shown.maxOf { it.rate })
        shown.forEach { pt ->
            val i=(pt.min/10.0).roundToInt().coerceIn(0,slots-1)
            val x=x0+i*(bw+gap)
            val c=stormRateColor(pt.rate)
            p.style=Paint.Style.FILL
            if(c==0) { p.color=(0x22 shl 24) or (pal.fg and 0xFFFFFF);cv.drawRoundRect(x,top+bh-3f*dn,x+bw,top+bh,1f*dn,1f*dn,p) }
            else { p.color=c;val hh=(bh*min(pt.rate,top5)/top5).toFloat().coerceAtLeast(3f*dn);cv.drawRoundRect(x,top+bh-hh,x+bw,top+bh,1.5f*dn,1.5f*dn,p) }
        }
        if(axis) {
            val y=top+bh+7f*dn
            val marks=if(span<=60) listOf(0,30,60) else if(w<170f*dn) listOf(0,60,120) else listOf(0,30,60,90,120)
            marks.forEach { m ->
                val cx=x0+(m/10)*(bw+gap)+bw/2
                val label=if(m==0) uiText(R.string.st_now) else if(m==marks.last()) uiText(R.string.st_minutes,m) else m.toString()
                val align=when(m) { 0->Paint.Align.LEFT;marks.last()->Paint.Align.RIGHT;else->Paint.Align.CENTER }
                text(cv,label,if(m==0) x0 else if(m==marks.last()) x0+w else cx,y,9f,pal.muted,false,align)
            }
        }
    }

    /** 1 x 1: what matters most, in two lines under its icon. */
    private fun single(cv:Canvas,w:Float,h:Float) {
        val s=min(26f*dn,h*0.34f)
        val storming=showStorm && !wetNow && (firstWet==null || firstWet.min>30) && (main!=null || nearestStrike!=null)
        val (big,small)=when {
            storming->{
                val d=main?.dist ?: nearestStrike!!.dist
                val bearing=main?.bearing ?: (atan2(nearestStrike!!.dx,nearestStrike.dy)*180/PI)
                (d.roundToInt().toString()+" km") to uiText(if(main!=null) R.string.st_small_storm else R.string.st_small_lightning,cardinal(bearing))
            }
            wetNow->uiText(R.string.st_raining) to (weatherUnitNumber(points.first().rate,1)+" mm/h")
            firstWet!=null->uiText(R.string.st_minutes,firstWet.min) to uiText(R.string.st_until_rain)
            else->uiText(R.string.st_dry) to (cfg.span/60).toString()+" h"
        }
        val top=(h-s-17f*dn-12f*dn)/2
        if(storming) bolt(cv,w/2,top+s/2,s/9f,0xFFFFD54F.toInt())
        else pic(cv,widgetSymbolBitmap(if(wetNow || firstWet!=null) 61 else 2,true,s.roundToInt().coerceAtLeast(1)),(w-s)/2,top,s)
        var bs=14f
        while(bs>10f && width(big,bs,true)>w) bs-=0.5f
        text(cv,fit(big,bs,true,w),w/2,top+s+9f*dn,bs,pal.fg,true,Paint.Align.CENTER)
        text(cv,fit(small,9.5f,false,w),w/2,top+s+23f*dn,9.5f,pal.muted,false,Paint.Align.CENTER)
    }

    /** The headline in [room]: a size smaller first, then the short form of a dry headline, then shortened. */
    private fun headText(cv:Canvas,head:String,x:Float,cy:Float,sp:Float,room:Float) {
        var size=sp
        while(size>sp-1.5f && width(head,size,true)>room) size-=0.5f
        val shown=if(width(head,size,true)<=room) head else if(firstWet==null && !wetNow) uiText(R.string.st_dry) else head
        text(cv,fit(shown,size,true,room),x,cy,size,pal.fg,true)
    }

    fun draw(wDp:Float,hDp:Float):Pair<Bitmap,Pair<Float,Float>?> {
        val bmp=Bitmap.createBitmap((wDp*dn).roundToInt().coerceAtLeast(1),(hDp*dn).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
        val w=wDp*dn;val h=hDp*dn
        val rb=24f*dn // the room of the refresh button
        var refresh:Pair<Float,Float>?=null
        val head=headline()
        val line=stormLine(shape.cols>=5 && shape.rows==2)
        if(shape.cols==1) { single(cv,w,h);return bmp to null }
        if(shape.rows==1) when(shape.cols) {
            2 -> {
                var x=0f
                if(showRadar) { val s=min(h,58f*dn);radar(cv,s/2,h/2,s);x=s+7f*dn }
                val room=w-x
                headText(cv,head,x,h/2-14f*dn,12.5f,room)
                place(cv,x,h/2,10.5f,room)
                if(line!=null) storm(cv,line,x,h/2+14f*dn,10f,room)
            }
            3 -> {
                // The headline and the place on the first line, the bars under them with room below the place.
                refresh=(wDp-24f) to -2f
                val placeRoom=min(placeWidth(l.name,11f),w*0.48f-rb)
                headText(cv,head,0f,9f*dn,13f,w-rb-placeRoom-12f*dn)
                place(cv,w-rb-4f*dn,9f*dn,11f,placeRoom,alignRight=true)
                if(showChart) bars(cv,0f,24f*dn,w,h-24f*dn,true,min(cfg.span,60))
                else text(cv,fit(line ?: calm(),10.5f,false,w),0f,h-8f*dn,10.5f,if(line!=null) StormText else pal.muted)
            }
            4 -> {
                refresh=(wDp-24f) to -2f
                var x=0f
                if(showRadar) { val s=min(h,62f*dn);radar(cv,s/2,h/2,s);x=s+12f*dn }
                val room=w-x
                val placeRoom=min(placeWidth(l.name,11f),room*0.5f-rb)
                headText(cv,head,x,9f*dn,13f,room-rb-placeRoom-12f*dn)
                place(cv,w-rb-4f*dn,9f*dn,11f,placeRoom,alignRight=true)
                if(showChart) bars(cv,x,22f*dn,room,h-22f*dn-(if(line!=null) 16f*dn else 0f),false)
                if(line!=null) storm(cv,line,x,h-6f*dn,10.5f,room)
            }
            else -> {
                refresh=(wDp-24f) to -2f
                var x=0f
                if(showRadar) { val s=min(h,62f*dn);radar(cv,s/2,h/2,s);x=s+14f*dn }
                val colW=if(showChart) min(160f*dn,(w-x)*0.5f) else w-x-rb
                headText(cv,head,x,h/2-17f*dn,13f,colW)
                if(line!=null) storm(cv,line,x,h/2,10.5f,colW)
                place(cv,x,h/2+17f*dn,11f,colW)
                if(showChart) { val bx=x+colW+12f*dn;bars(cv,bx,rb,w-bx,h-rb,true) }
            }
        } else when(shape.cols) {
            2 -> {
                // The circle, the headline, the storm and the place with the refresh button at the bottom.
                refresh=(wDp-22f) to (hDp-22f)
                val placeY=h-10f*dn
                val textH=13f*dn+(if(line!=null) 14f*dn else 0f)
                if(showRadar) {
                    val s=min(w,placeY-10f*dn-textH-6f*dn)
                    radar(cv,w/2,s/2,s)
                    text(cv,fit(head,12.5f,true,w),w/2,s+10f*dn,12.5f,pal.fg,true,Paint.Align.CENTER)
                    if(line!=null) storm(cv,line,max(0f,(w-width(line,10f,false)-12f*dn)/2),s+24f*dn,10f,w)
                } else {
                    headText(cv,head,0f,12f*dn,13f,w)
                    if(line!=null) storm(cv,line,0f,30f*dn,10.5f,w)
                    if(showChart) bars(cv,0f,42f*dn,w,placeY-56f*dn,true,min(cfg.span,60))
                }
                place(cv,0f,placeY,11f,w-rb)
            }
            else -> {
                // The circle on the left; the headline, the place and the storm on the right, the storm on two lines where it does not fit;
                // the bars at the bottom of the column (3 x 2: under everything), lowered below the texts.
                val big=shape.cols>=4
                refresh=(wDp-24f) to -2f
                var x=0f
                val s=if(big) min(h,(if(shape.cols>=5) 132f else 128f)*dn) else min(h*0.56f,w*0.36f)
                if(showRadar) { radar(cv,s/2,s/2+(if(big) (h-s)/2 else 0f),s);x=s+(if(big) 16f else 12f)*dn }
                val room=w-x
                val hs=if(big) 15f else 13f
                headText(cv,head,x,10f*dn,hs,room-rb)
                place(cv,x,30f*dn,12f,room)
                val lines=if(showStorm) stormLines(cv,x,49f*dn,11f,room,shape.cols>=5,shape.cols<=4) else 0
                if(showStorm && lines==0) text(cv,fit(calm(),11f,false,room),x,49f*dn,11f,pal.muted)
                val textBottom=49f*dn+max(lines,1)*15f*dn
                if(showChart) {
                    if(big) { val top=textBottom+6f*dn;bars(cv,x,top,room,h-top,true) }
                    else { val top=max(s,textBottom)+8f*dn;bars(cv,0f,top,w,h-top,true,min(cfg.span,60)) }
                }
            }
        }
        return bmp to refresh
    }
}

class StormWidget:AppWidgetProvider() {
    override fun onReceive(context:Context,intent:Intent) { if(intent.action==StormWidgets.ActionRefresh) StormWidgets.refresh(context,true) else super.onReceive(context,intent) }
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { StormWidgets.refresh(context);StormWidgets.schedule(context) }
    // A new size changes what the widget shows.
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { StormWidgets.refresh(context) }
    override fun onDeleted(context:Context,ids:IntArray) { StormWidgets.remove(context,ids);WidgetBackup.release(context,ids) }
    override fun onDisabled(context:Context) { StormWidgets.cancel(context) }
}

internal class StormWidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext
        // A widget refresh moves the Current location first: precise after the refresh button, otherwise power-saving.
        CurrentLocation.update(c,precise=inputData.getBoolean("force",false),fromBackground=true)
        val store=LocationStore(c);val all=store.load()
        val active=all.getOrNull(store.loadSelected()) ?: all.firstOrNull()
        val force=inputData.getBoolean("force",false)
        var failed=false;var lively=false
        if(force) NetCache.forceNetwork=true
        try {
            for(id in StormWidgets.ids(c)) {
                val result=try { StormWidgets.render(c,id,all,active) } catch(e:CancellationException) { throw e } catch(e:Exception) { null }
                if(result==null) failed=true else if(result) lively=true
            }
        } finally { if(force) NetCache.forceNetwork=false }
        if(lively) StormWidgets.soon(c)
        return if(failed && runAttemptCount<2) Result.retry() else Result.success()
    }
}

class StormWidgetConfigActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID) { finish();return }
        // A widget placed after a restore takes the waiting settings of its kind from the backup.
        WidgetBackup.prefill(this,id,::backupReadValue)
        val locations=LocationStore(this).load()
        val initial=StormWidgets.read(this,id)
        setContent {
            PerunTheme {
                Surface(Modifier.fillMaxSize(),color=Color(0xFF151A22),contentColor=Color(0xFFE8EAF0)) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(uiText(R.string.wg_config_title),style=MaterialTheme.typography.titleLarge,color=Color(0xFFE8EAF0))
                        StormWidgetForm(locations,initial) { next ->
                            StormWidgets.write(this@StormWidgetConfigActivity,id,next)
                            StormWidgets.refresh(this@StormWidgetConfigActivity)
                            StormWidgets.schedule(this@StormWidgetConfigActivity)
                            WidgetBackup.commit(this@StormWidgetConfigActivity,id)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
                            finish()
                        }
                    }
                }
            }
        }
    }
}

/** The settings of the Precipitation and storms widget, in the style of the Compact widget; also used by the Settings of the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun StormWidgetForm(locations:List<Location>,initial:StormWidgetConfig,onSave:(StormWidgetConfig)->Unit) {
    val keys=listOf("active")+pickerLocations(locations).map { BackgroundRefresh.locationKey(it) }
    val names=listOf(uiText(R.string.wg_loc_active))+pickerLocations(locations).map { pickerName(it) }
    var loc by remember { mutableStateOf(initial.loc) }
    var show by remember { mutableStateOf(initial.show) }
    var range by remember { mutableStateOf(initial.range) }
    var span by remember { mutableStateOf(initial.span) }
    var bg by remember { mutableStateOf(initial.bg) }
    var text by remember { mutableStateOf(initial.text) }
    val muted=Color(0xFFBBC4D2)
    @Composable fun label(res:Int,first:Boolean=false) { if(!first) HorizontalDivider(color=Color(0x804B586C));Text(uiText(res),style=MaterialTheme.typography.labelLarge,color=muted) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        label(R.string.wg_location,true)
        PerunChips(names,keys.indexOf(loc).coerceAtLeast(0),{ loc=keys[it] },marked=keys.indexOf(CurrentLocation.Key))
        label(R.string.st_show)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("radar" to R.string.st_show_radar,"chart" to R.string.st_show_chart,"storm" to R.string.st_show_storm).forEach { (key,res) ->
                PerunChip(uiText(res),key in show) { show=if(key in show) show-key else show+key }
            }
        }
        label(R.string.st_range)
        PerunChips(StormWidgets.Ranges.map { "$it km" },StormWidgets.Ranges.indexOf(range).coerceAtLeast(0),{ range=StormWidgets.Ranges[it] })
        label(R.string.st_span)
        PerunChips(listOf(uiText(R.string.st_span_1h),uiText(R.string.st_span_2h)),StormWidgets.Spans.indexOf(span).coerceAtLeast(0),{ span=StormWidgets.Spans[it] })
        label(R.string.wg_background)
        PerunChips(widgetBackgroundLabels(),PerunWidgets.Backgrounds.indexOf(bg).coerceAtLeast(0),{ bg=PerunWidgets.Backgrounds[it] })
        label(R.string.wg_text)
        PerunChips(listOf(uiText(R.string.wg_text_light),uiText(R.string.wg_text_dark)),if(text=="dark") 1 else 0,{ text=if(it==1) "dark" else "light" })
        Button(onClick={ onSave(StormWidgetConfig(loc,show,range,span,bg,text)) },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_save)) }
    }
}
