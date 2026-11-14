package sk.perun.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun PerunSegments(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,compact:Boolean=false) {
    val shape=RoundedCornerShape(50)
    Box((if(compact) modifier else modifier.fillMaxWidth()).clip(shape).background(Color(0xFF262B36)).border(1.dp,Color(0xFF4B586C),shape).padding(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(if(compact) 2.dp else 4.dp)) {
            labels.forEachIndexed { index,label ->
                val active=index==selected
                // Compact segments are 48 dp tall overall (40 + 2×4 padding), matching the pills on Sun & Moon.
                Box(Modifier.heightIn(min=if(compact) 40.dp else 44.dp).clip(shape).background(if(active && enabled) Color(0xFF4FC3F7) else Color.Transparent)
                    .selectable(selected=active,enabled=enabled,role=Role.RadioButton,onClick={onSelect(index)}).padding(horizontal=if(compact) 14.dp else 16.dp,vertical=if(compact) 6.dp else 8.dp),contentAlignment=Alignment.Center) {
                    Text(label,color=if(!enabled) Color(0xFF9AA6B7) else if(active) Color(0xFF10131A) else Color(0xFFBBC4D2),fontSize=if(compact) 14.sp else 13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false)
                }
            }
        }
    }
}
