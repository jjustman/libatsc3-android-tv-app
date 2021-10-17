package com.nextgenbroadcast.mobile.tvandroid.servicesGuide

import com.nextgenbroadcast.mobile.core.model.AVService

interface IServiceGuideManager {
    val timeStepMils: Long
    val scheduleStartTime: Long
    val scheduleEndTime: Long
    val columnCount: Int

    fun setActiveServices(services: List<AVService>)
    fun getDefaultService(): AVService?

    fun startObserving(observer: ServiceGuideObserver)
    fun stopObserving()
    fun refreshSchedule()

    fun findServiceByGlobalId(globalId: String): AVService?

    fun isPeriodOutdated(): Boolean
}