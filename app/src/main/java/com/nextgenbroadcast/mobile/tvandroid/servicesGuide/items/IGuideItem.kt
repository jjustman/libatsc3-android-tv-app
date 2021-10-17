package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items

interface IGuideItem {
    val size: Int
    val type: Int
    val text: String

    companion object {
        const val TYPE_TIME = 0
        const val TYPE_SERVICE = 1
        const val TYPE_DATA = 2
    }
}