package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items

import androidx.recyclerview.widget.ScheduleLayoutManager
import com.nextgenbroadcast.mobile.core.model.AVService

data class GuideDataItem (
    val service: AVService,
    val startTime: Long,
    val endTime: Long,
    override val text: String,
    val description: String? = null,
    val previewUrl: String? = null,
    override val size: Int = 1
) : IGuideItem {
    override val type: Int = IGuideItem.TYPE_DATA

    var rowIndex: Int = ScheduleLayoutManager.LayoutParams.INVALID_SPAN_ID
    var columnIndex: Int = -1
    var rangeStartTime: Long = 0
}