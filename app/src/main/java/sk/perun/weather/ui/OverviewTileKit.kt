package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class HelpSpec(val title:String,val body:@Composable ColumnScope.()->Unit)

private fun tkPath(d:String):Path=PathParser().parsePathString(d).toNodes().toPath()
private val TkArrow=tkPath("M50 28L38 80L50 68L62 80Z")
private val TkArrowRight=tkPath("M5 12h14M14 7l5 5-5 5")
private val TkSun=tkPath("M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2")
private val TkDrop=tkPath("M12 2C12 2 5 10 5 15a7 7 0 0 0 14 0C19 10 12 2 12 2Z")
private val TkEye=tkPath("M2 12C5 6 9 4 12 4s7 2 10 8c-3 6-7 8-10 8s-7-2-10-8Z")
private val TkWind=tkPath("M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3M3 12h7")
private val TkStorm=tkPath("M13 2L5 14h6l-1 8l8-12h-6Z")
private val TkRain=tkPath("M7 6l-2 6m7-6l-2 6m7-6l-2 6M7 14l-2 6m7-6l-2 6m7-6l-2 6")
private val TkSnow=tkPath("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9")
private val TkHeat=tkPath("M10 4a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0Z")
private val TkFog=tkPath("M4 8h16M2 12h16M6 16h14")
private val TkOther=tkPath("M12 3L2 21h20ZM12 10v5M12 18v.5")

@Composable internal fun AlertGlyph(kind:AlertKind,color:Color,dimension:Dp=18.dp) {
    val path=when(kind) {
        AlertKind.Wind -> TkWind
        AlertKind.Storm -> TkStorm
        AlertKind.Rain, AlertKind.Flood -> TkRain
        AlertKind.Snow, AlertKind.Ice, AlertKind.Cold -> TkSnow
        AlertKind.Heat -> TkHeat
        AlertKind.Fog -> TkFog
        AlertKind.Other -> TkOther
    }
    Canvas(Modifier.size(dimension)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) { drawPath(path,color,style=Stroke(2f,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
    }
}

internal enum class SummaryIcon { Temp, Rain, Snow, Wind, Uv }

@Composable internal fun SummaryGlyph(icon:SummaryIcon,color:Color,dimension:Dp=18.dp) {
    Canvas(Modifier.size(dimension)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            val s=Stroke(2f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            when(icon) {
                SummaryIcon.Temp -> drawPath(TkHeat,color,style=s)
                SummaryIcon.Rain -> drawPath(TkDrop,color,style=s)
                SummaryIcon.Snow -> drawPath(TkSnow,color,style=s)
                SummaryIcon.Wind -> drawPath(TkWind,color,style=s)
                SummaryIcon.Uv -> { drawPath(TkSun,color,style=s);drawCircle(color,4.5f,Offset(12f,12f),style=s) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun TileDot(color:Color,label:String,ring:Boolean,tooltip:String,onHelp:()->Unit) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(36.dp).combinedClickable(role=Role.Button,onClickLabel=uiText(R.string.ui_strucny_popis_e8d7e5f7),onLongClickLabel=uiText(R.string.ui_pomocnik_c68b7b84),onLongClick=onHelp,onClick={ open=true }),
        contentAlignment=Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp).semantics { contentDescription=uiText(R.string.ui_informacie_value_f076eb88, label) }) {
            if(ring) drawCircle(color,style=Stroke(1.6.dp.toPx())) else drawCircle(color)
        }
        if(open) {
            LaunchedEffect(tooltip) { delay(2600); open=false }
            Popup(alignment=Alignment.BottomStart,onDismissRequest={ open=false },properties=PopupProperties(focusable=true)) {
                Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF10131A),contentColor=Color(0xFFE8EAF0),border=BorderStroke(1.dp,color),shadowElevation=4.dp) {
                    Text(tooltip,Modifier.widthIn(max=150.dp).padding(horizontal=10.dp,vertical=6.dp),fontSize=13.sp)
                }
            }
        }
    }
}

@Composable internal fun TileTitle(title:String) {
    Text(title,fontSize=15.sp,fontWeight=FontWeight.Normal,color=ForecastMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
}

@Composable internal fun TkTile(title:String,dot:Color,ring:Boolean,tooltip:String,modifier:Modifier,onHelp:()->Unit,onOpen:(()->Unit)?,content:@Composable ColumnScope.()->Unit) {
    val click=if(onOpen!=null) Modifier.clickable { onOpen() } else Modifier
    OutlinedCard(
        modifier.height(112.dp).then(click),
        shape=RoundedCornerShape(15.dp),
        border=BorderStroke(1.dp,Color(0xFF4B586C)),
        colors=CardDefaults.outlinedCardColors(containerColor=Color(0xFF202833),contentColor=Color(0xFFE8EAF0)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start=2.dp),verticalAlignment=Alignment.CenterVertically) {
                TileDot(dot,title,ring,tooltip,onHelp)
                TileTitle(title)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(start=12.dp,end=10.dp,bottom=6.dp),
                verticalArrangement=Arrangement.spacedBy(2.dp,Alignment.CenterVertically),
                horizontalAlignment=Alignment.Start,
                content=content,
            )
        }
    }
}

private fun DrawScope.tkText(measurer:TextMeasurer,text:String,cx:Float,cy:Float,fontSize:TextUnit,color:Color,weight:FontWeight=FontWeight.Normal) {
    val layout=measurer.measure(text,TextStyle(color=color,fontSize=fontSize,fontWeight=weight))
    drawText(layout,topLeft=Offset(cx-layout.size.width/2f,cy-layout.size.height/2f))
}

@Composable internal fun TkCompass(direction:Double,color:Color,modifier:Modifier=Modifier.size(62.dp)) {
    val measurer=rememberTextMeasurer()
    val description=uiText(R.string.ui_vietor_fuka_zo_smeru_value_sipka_ukazuje_kam_fuka_sever_je_983405a4, cardinal(direction))
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription=description }) {
        val k=size.width/100f
        scale(k,k,Offset.Zero) {
            drawCircle(Color(0xFF8C9BB2),36f,Offset(50f,58f),style=Stroke(2f))
            if(direction.isFinite()) rotate(((direction+180)%360).toFloat(),Offset(50f,58f)) { drawPath(TkArrow,color) }
            drawCircle(Color(0xFFE8EAF0),4f,Offset(50f,58f))
        }
        tkText(measurer,uiText(R.string.ui_s_8de0b3c4),50f*k,8f*k,(16f*k).toSp(),Color(0xFFE8EAF0),FontWeight.Bold)
    }
}

@Composable internal fun TkDropGraphic(fraction:Double,color:Color,modifier:Modifier) {
    HumidityGraphic(fraction,color,modifier)
}

@Composable internal fun TkFlakeGraphic(strength:Double,color:Color,modifier:Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            drawPath(TkSnow,color.copy(alpha=(.35+.65*strength.coerceIn(0.0,1.0)).toFloat()),style=Stroke(2f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}

@Composable internal fun TkEyeGraphic(color:Color,modifier:Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            drawPath(TkEye,Color(0xFFBBC4D2),style=Stroke(1.6f,join=StrokeJoin.Round))
            drawCircle(color,4f,Offset(12f,12f))
        }
    }
}

@Composable internal fun TkTrendGraphic(change:Double,color:Color,modifier:Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            drawCircle(Color(0xFF8C9BB2),10.5f,Offset(12f,12f),style=Stroke(1.4f))
            val angle=if(change.isFinite()) (-change/3.0*60.0).coerceIn(-60.0,60.0).toFloat() else 0f
            rotate(angle,Offset(12f,12f)) { drawPath(TkArrowRight,color,style=Stroke(2f,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
        }
    }
}

@Composable internal fun TkPollenGraphic(color:Color,modifier:Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            drawCircle(color,3f,Offset(12f,12f))
            for(i in 0 until 6) {
                val a=i*PI/3
                drawCircle(color.copy(alpha=.75f),2.4f,Offset((12+7.5*cos(a)).toFloat(),(12+7.5*sin(a)).toFloat()))
            }
        }
    }
}

@Composable internal fun HelpText(text:String) { Text(text,color=ForecastMuted,fontSize=14.sp) }

@Composable internal fun HelpRow(color:Color,title:String,text:String="") {
    Row(verticalAlignment=Alignment.Top) {
        Canvas(Modifier.padding(top=5.dp).size(12.dp)) { drawCircle(color) }
        Column(Modifier.padding(start=10.dp)) {
            Text(title,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
            if(text.isNotEmpty()) Text(text,color=ForecastMuted,fontSize=13.sp)
        }
    }
}

@Composable internal fun ChartNowButton(enabled:Boolean,onClick:()->Unit) {
    val shape=RoundedCornerShape(50)
    // Same size as the "Teraz" pill on Sun & Moon.
    Box(Modifier.height(48.dp).clip(shape).background(Color(0xFF202833)).border(1.dp,Color(0xFF4B586C),shape).clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=18.dp),contentAlignment=Alignment.Center) {
        Text(uiText(R.string.ui_teraz_6172392c),color=if(enabled) Color(0xFF4FC3F7) else Color(0xFF9AA6B7),fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
    }
}
