package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items

import com.nextgenbroadcast.mobile.core.model.AVService

data class GuideServiceItem (
    val service: AVService,
) : IGuideItem {
    override val type: Int = IGuideItem.TYPE_SERVICE
    override val text: String = service.shortName ?: ""
    override val size: Int = 1
}