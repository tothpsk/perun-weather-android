package sk.perun.weather.ui

import sk.perun.weather.R

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews

/** The location mark after the place in the widget layouts with a text place: shown for the Current location, in the colour of the text. */
internal fun RemoteViews.gpsMark(l: Location, color: Int) {
    setViewVisibility(R.id.wg_gps, if (l.current) View.VISIBLE else View.GONE)
    if (l.current) setInt(R.id.wg_gps, "setColorFilter", color)
}

/** The location mark (PerunIcons.Gps) drawn in the widgets made of one picture, in a [size] box from [x] with its middle at [cy]. */
internal fun drawGpsMark(cv: Canvas, x: Float, cy: Float, size: Float, color: Int) {
    val k = size / 24f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * k; strokeCap = Paint.Cap.ROUND; this.color = color }
    val cx = x + 12 * k
    cv.drawCircle(cx, cy, 5.47f * k, p)
    cv.drawCircle(cx, cy, 1.37f * k, p)
    cv.drawLine(cx, cy - 9.12f * k, cx, cy - 6.38f * k, p)
    cv.drawLine(cx, cy + 6.39f * k, cx, cy + 9.13f * k, p)
    cv.drawLine(cx - 9.12f * k, cy, cx - 6.38f * k, cy, p)
    cv.drawLine(cx + 6.39f * k, cy, cx + 9.13f * k, cy, p)
}
