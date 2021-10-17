package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator

import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideAdapter
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideDataItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.IGuideItem

class CurrentTimeIndicator(
    indicatorColor: Int,
    indicatorWidth: Float,
    private val timelineStep: Int
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        color = indicatorColor
        strokeWidth = indicatorWidth
    }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? ServiceGuideAdapter ?: return

        val currentTime = System.currentTimeMillis()
        val intervalPosition = adapter.findPosition { item ->
            item.type == IGuideItem.TYPE_DATA && with(item as GuideDataItem) { currentTime in startTime..endTime }
        }

        if (intervalPosition >= 0) {
            (adapter.getItem(intervalPosition) as? GuideDataItem)?.let {
                val percent = (currentTime - it.rangeStartTime) / (timelineStep.toFloat() * it.size)

                parent.findViewHolderForAdapterPosition(intervalPosition)?.itemView?.let { timeView ->
                    canvas.save()

                    val offsetX = timeView.x + timeView.width * percent
                    val startY = parent.top.toFloat()
                    val stopY = parent.bottom.toFloat()
                    canvas.drawLine(
                        offsetX, startY,
                        offsetX, stopY,
                        paint)

                    canvas.restore()
                }
            }
        }

        parent.postInvalidateDelayed(1000)
    }
}