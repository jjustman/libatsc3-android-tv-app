package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.view

import android.content.Context
import android.util.AttributeSet
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideDataItem

class ServiceGuideDataItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {

    fun bind(data: GuideDataItem) {
        text = data.text
    }
}