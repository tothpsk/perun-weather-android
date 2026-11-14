package sk.perun.weather.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Horizontal swipe outside horizontally scrolling children: step is +1 for a swipe to the left (next) and -1 to the right (previous). */
internal fun Modifier.horizontalSwipe(key:Any?,onSwipe:(Int)->Unit):Modifier = pointerInput(key) {
    var total=0f
    detectHorizontalDragGestures(onDragStart={ total=0f },onDragCancel={ total=0f },onDragEnd={
        val limit=56.dp.toPx()
        if(total<=-limit) onSwipe(1) else if(total>=limit) onSwipe(-1)
        total=0f
    }) { change,amount -> total+=amount;change.consume() }
}
