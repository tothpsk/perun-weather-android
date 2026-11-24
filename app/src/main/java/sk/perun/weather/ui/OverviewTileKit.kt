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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class HelpSpec(val title:String,val body:@Composable ColumnScope.()->Unit)

internal enum class SummaryIcon { Temp, Rain, Snow, Wind, Uv }

@Composable internal fun SummaryGlyph(icon:SummaryIcon,color:Color,dimension:Dp=18.dp) {
    GlyphIcon(when(icon) { SummaryIcon.Temp->Glyph.Temp;SummaryIcon.Rain->Glyph.Rain;SummaryIcon.Snow->Glyph.Snow;SummaryIcon.Wind->Glyph.Wind;SummaryIcon.Uv->Glyph.Uv },color,dimension)
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


@Composable internal fun TkFlakeGraphic(strength:Double,color:Color,long:Dp=TileLong) {
    TkGlyph(Glyph.Snow,color.copy(alpha=(.35+.65*strength.coerceIn(0.0,1.0)).toFloat()),long)
}

@Composable internal fun TkEyeGraphic(color:Color) { TkGlyph(Glyph.EyeTile,color,muted=Color(0xFFBBC4D2)) }

@Composable internal fun TkTrendGraphic(change:Double,color:Color) {
    if(!change.isFinite()) { TkGlyph(Glyph.Pressure,color,muted=Color(0xFF8C9BB2));return }
    val angle=if(change.isFinite()) (-change/3.0*60.0).coerceIn(-60.0,60.0).toFloat() else 0f
    TkGlyph(Glyph.Trend,color,muted=Color(0xFF8C9BB2),turn=angle)
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

// The room beside the two rows of text of a tile. height: from the top of the digits of the value to the baseline of
// the second row. descent: from that baseline to the bottom of the text.
internal class TileTextBox(val height:Dp,val descent:Dp)

@Composable internal fun rememberTileTextBox():TileTextBox {
    val measurer=rememberTextMeasurer()
    val density=androidx.compose.ui.platform.LocalDensity.current
    val base=LocalTextStyle.current
    return remember(base,density) {
        val value=measurer.measure("0",base.merge(TextStyle(fontSize=24.sp,fontWeight=FontWeight.SemiBold)))
        val sub=measurer.measure("0",base.merge(TextStyle(fontSize=12.sp)))
        // The digits are about .71 of the font size tall; 2 dp is the gap between the two rows.
        with(density) { TileTextBox((value.size.height-value.firstBaseline+24.sp.toPx()*.71f+2.dp.toPx()+sub.firstBaseline).toDp(),(sub.size.height-sub.firstBaseline).toDp()) }
    }
}

// Arrow of a direction with the shape of the arrow in the hourly chart of an expanded day. direction is where the wind
// or the waves come from; the arrow points where they go, north is up.
@Composable internal fun TkDirectionArrow(direction:Double,color:Color,length:Dp,description:String) {
    Canvas(Modifier.size(length).semantics { contentDescription=description }) {
        val angle=(direction+180)*PI/180
        val v=Offset(sin(angle).toFloat(),-cos(angle).toFloat())
        val stroke=maxOf(size.width*GlyphLine/24f,GlyphMinDp.dp.toPx())
        val half=(size.width-stroke)/2
        val tip=center+v*half
        val base=tip-v*(half*4f/7f)
        val side=Offset(-v.y,v.x)*(half*3f/7f)
        drawLine(color,center-v*half,tip,stroke,StrokeCap.Round)
        drawLine(color,tip,base+side,stroke,StrokeCap.Round)
        drawLine(color,tip,base-side,stroke,StrokeCap.Round)
    }
}
