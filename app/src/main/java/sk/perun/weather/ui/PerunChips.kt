package sk.perun.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChipActive=Color(0xFF4FC3F7)
private val ChipIdle=Color(0xFF262B36)
private val ChipEdge=Color(0xFF4B586C)

/** Stand-alone rounded choice buttons: the active one is filled with the app accent, the rest stay dark. */
@OptIn(ExperimentalLayoutApi::class)
// [marked]: the chip of the Current location, with the location mark of the app after its label.
@Composable internal fun PerunChips(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,marked:Int=-1) {
    FlowRow(modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index,label -> PerunChip(label,index==selected,enabled,index==marked) { onSelect(index) } }
    }
}

@Composable internal fun PerunChip(label:String,active:Boolean,enabled:Boolean=true,mark:Boolean=false,onClick:()->Unit) {
    val shape=RoundedCornerShape(50)
    val filled=active && enabled
    Box(
        Modifier.heightIn(min=36.dp).clip(shape).background(if(filled) ChipActive else ChipIdle).border(1.dp,if(filled) ChipActive else ChipEdge,shape)
            .selectable(selected=active,enabled=enabled,role=Role.RadioButton,onClick=onClick).padding(horizontal=14.dp,vertical=8.dp),
        contentAlignment=Alignment.Center,
    ) {
        val color=if(!enabled) Color(0xFF9AA6B7) else if(filled) Color(0xFF10131A) else Color(0xFFBBC4D2)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(label,color=color,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false)
            if(mark) Icon(PerunIcons.Gps,null,Modifier.padding(start=5.dp).size(14.dp),tint=color)
        }
    }
}

/** Status line with a filled check circle when [ok] is true, otherwise an empty dark circle. */
@Composable internal fun PerunStatusRow(ok:Boolean,text:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if(ok) ChipActive else ChipIdle).border(1.dp,if(ok) ChipActive else ChipEdge,CircleShape),contentAlignment=Alignment.Center) {
            if(ok) Canvas(Modifier.size(12.dp)) {
                val w=size.width;val h=size.height
                drawLine(Color(0xFF10131A),Offset(w*0.1f,h*0.55f),Offset(w*0.4f,h*0.85f),strokeWidth=2.2f,cap=StrokeCap.Round)
                drawLine(Color(0xFF10131A),Offset(w*0.4f,h*0.85f),Offset(w*0.92f,h*0.2f),strokeWidth=2.2f,cap=StrokeCap.Round)
            }
        }
        Text(text,style=MaterialTheme.typography.bodySmall,color=if(ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
    }
}
