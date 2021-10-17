package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CollectionItemDecoration : RecyclerView.ItemDecoration() {
    private val decorators = mutableListOf<RecyclerView.ItemDecoration>()

    fun clear() {
        decorators.clear()
    }

    fun addItemDecoration(decorator: RecyclerView.ItemDecoration): Int {
        val index = decorators.size
        decorators.add(decorator)
        return index
    }

    fun removeItemDecorationAt(index: Int) {
        if (index >= 0 && index < decorators.size) {
            decorators.removeAt(index)
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        decorators.forEach { decorator ->
            decorator.onDraw(c, parent, state)
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        decorators.forEach { decorator ->
            decorator.onDrawOver(c, parent, state)
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        decorators.forEach { decorator ->
            decorator.getItemOffsets(outRect, view, parent, state)
        }
        super.getItemOffsets(outRect, view, parent, state)
    }
}