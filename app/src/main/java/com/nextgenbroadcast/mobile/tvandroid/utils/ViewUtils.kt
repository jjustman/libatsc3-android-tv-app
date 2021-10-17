package com.nextgenbroadcast.mobile.tvandroid.utils

import android.view.View
import android.view.ViewGroup

object ViewUtils {
    fun ViewGroup.findChildViewUnder(x: Float, y: Float): View? {
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            val translationX = child.translationX
            val translationY = child.translationY
            if (x >= child.left + translationX && x <= child.right + translationX && y >= child.top + translationY && y <= child.bottom + translationY) {
                return child
            }
        }
        return null
    }
}