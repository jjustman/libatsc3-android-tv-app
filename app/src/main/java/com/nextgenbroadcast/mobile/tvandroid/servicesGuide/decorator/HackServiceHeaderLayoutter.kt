package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.RecyclerView.State
import androidx.recyclerview.widget.ScheduleLayoutManager
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideAdapter

class HackServiceHeaderLayoutter(
    private val dividerSize: Int,
    private val headers: List<View>
) : ItemDecoration() {

    private var into: IntArray? = null

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {
        val lm = parent.layoutManager as? ScheduleLayoutManager ?: return

        var i = 0
        lm.findFirstVisibleItemPositions(getInfoArray(lm.spanCount)).forEach { position ->
            parent.findViewHolderForAdapterPosition(position)?.itemView?.let { child ->
                with(headers[i++]) {
                    x = parent.left.toFloat()
                    y = child.y - dividerSize
                    alpha = 1f
                }
            }
        }

        for (index in i until headers.size) {
            headers[index].alpha = 0f
        }
    }

    //TODO: could we find better place for selection fix?
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
        super.getItemOffsets(outRect, view, parent, state)

        val adapter = parent.adapter
        if (adapter is ServiceGuideAdapter) {
            val lp = view.layoutParams as ScheduleLayoutManager.LayoutParams
            val isSelected = adapter.selectedSpanIndex == lp.spanIndex
            if (view.isSelected != isSelected) {
                view.isSelected = isSelected
            }
        }
    }

    private fun getInfoArray(size: Int): IntArray {
        return into?.let { array ->
            if (array.size <= size) array else null
        } ?: IntArray(size).also { array ->
            into = array
        }
    }
}