package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator

import android.graphics.Canvas
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ScheduleLayoutManager
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideAdapter
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideDataItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideTimeItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.view.ServiceGuideTimeItemView

class HackTimeHeaderLayoutter(
    private val timeline: List<GuideTimeItem>,
    private val columnWidth: Int,
    private val dividerSize: Int,
    private val inflateView: () -> ServiceGuideTimeItemView
) : RecyclerView.ItemDecoration() {

    private var into: IntArray? = null
    private val headers = mutableListOf<ServiceGuideTimeItemView>()

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val lm = parent.layoutManager as? ScheduleLayoutManager ?: return
        val adapter = parent.adapter as? ServiceGuideAdapter ?: return

        val firstViewIndex = lm.findFirstVisibleItemPositions(getInfoArray(lm.spanCount)).apply {
            sort()
        }.firstOrNull() ?: -1
        var startX = 0f
        var startY = 0f
        lm.findViewByPosition(firstViewIndex)?.let { view ->
            startX = view.x
            startY = (-dividerSize).toFloat()
        }

        var index = 0
        adapter.getItem(firstViewIndex)?.let { item ->
            if (item is GuideDataItem) {
                for (i in item.columnIndex until timeline.size) {
                    with(getView(index++)) {
                        x = startX
                        y = startY
                        alpha = 1f

                        bind(timeline[i])
                    }

                    startX += columnWidth

                    if (startX > lm.width) {
                        break
                    }
                }
            }
        }

        for (i in index until headers.size) {
            headers[i].alpha = 0f
        }
    }

    private fun getInfoArray(size: Int): IntArray {
        return into?.let { array ->
            if (array.size <= size) array else null
        } ?: IntArray(size).also { array ->
            into = array
        }
    }

    private fun getView(index: Int): ServiceGuideTimeItemView {
        return headers.getOrNull(index) ?: inflateView.invoke().also {
            headers.add(it)
        }
    }
}