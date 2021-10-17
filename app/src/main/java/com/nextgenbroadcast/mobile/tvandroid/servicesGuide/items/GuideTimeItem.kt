package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items

data class GuideTimeItem(
    val timestamp: Long,
    override val text: String,
    override val size: Int = 1
) : IGuideItem {
    override val type: Int = IGuideItem.TYPE_TIME

    var desc: String? = null //"On Now"
}