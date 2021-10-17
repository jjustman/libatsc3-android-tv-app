package com.nextgenbroadcast.mobile.tvandroid.servicesGuide

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.serviceGuide.SGProgram
import com.nextgenbroadcast.mobile.core.serviceGuide.SGProgramContent
import com.nextgenbroadcast.mobile.middleware.provider.esg.ESGContentProvider
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

typealias ServiceGuideObserver = (Map<AVService, List<SGProgram>>) -> Unit

class ServiceGuideManagerImpl(
    authority: String,
    private val contentResolver: ContentResolver
) : IServiceGuideManager, ContentObserver(Handler(Looper.getMainLooper())) {

    private val serviceContentUri = Uri.parse("content://$authority/services_data")
    private val programContentUri = Uri.parse("content://$authority/programs_data")

    private var observer: ServiceGuideObserver? = null

    override val timeStepMils = TimeUnit.MINUTES.toMillis(TIME_STEP_MINUTES.toLong())
    override var scheduleStartTime: Long = 0
    override var scheduleEndTime: Long = Long.MAX_VALUE
    override var columnCount: Int = 0

    private var scheduleUpdateJob: Job? = null

    private var activeServices: List<AVService> = emptyList()

    init {
        actualize()
    }

    override fun setActiveServices(services: List<AVService>) {
        if (services.isEmpty() && activeServices.isEmpty()) return

        activeServices = services.sortedWith(SERVICE_COMPARATOR)

        refreshSchedule()
    }

    override fun getDefaultService(): AVService? {
        return activeServices.firstOrNull { it.default } ?: activeServices.firstOrNull()
    }

    override fun startObserving(observer: ServiceGuideObserver) {
        if (this.observer == null)

        this.observer = observer
        contentResolver.registerContentObserver(serviceContentUri, true, this)

        refreshSchedule()
    }

    override fun stopObserving() {
        contentResolver.unregisterContentObserver(this)
        this.observer = null

        scheduleUpdateJob?.cancel()
        scheduleUpdateJob = null
    }

    override fun findServiceByGlobalId(globalId: String): AVService? {
        return activeServices.firstOrNull {
            it.globalId == globalId
        }
    }

    override fun isPeriodOutdated(): Boolean {
        return normalizedCurrentTimeMillis() >= scheduleStartTime + timeStepMils * 2 /* end of current period */
    }

    private fun actualize() {
        val currentTime = normalizedCurrentTimeMillis()

        scheduleStartTime = currentTime - timeStepMils
        scheduleEndTime = (currentTime
                + timeStepMils                                // current column
                + timeStepMils * (/*if (bigTime) 20 else*/ 4))    // two hours after
        columnCount = ((scheduleEndTime - scheduleStartTime) / timeStepMils).toInt()
    }

    private fun normalizedCurrentTimeMillis(): Long {
        return ((System.currentTimeMillis() / timeStepMils) * timeStepMils)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (scheduleUpdateJob?.isActive == true) {
            return
        }

        refreshSchedule()
    }

    override fun refreshSchedule() {
        observer?.let { observer ->
            val services = activeServices

            scheduleUpdateJob = CoroutineScope(Dispatchers.IO).launch {
                val schedule = queryServiceGuide(services)

                val sortedSchedule = schedule.toSortedMap(SERVICE_COMPARATOR)

                withContext(Dispatchers.Main) {
                    observer(sortedSchedule)
                }
            }
        }
    }

    private fun queryServiceGuide(activeServices: List<AVService>): Map<AVService, List<SGProgram>> {
        actualize()

        val schedule = mutableMapOf<AVService, List<SGProgram>>().apply {
            val activeServiceIDs = activeServices.map { it.id }

            contentResolver.query(serviceContentUri,
                null, null, null, null)?.use { serviceCursor ->

                val serviceColumnIndex = serviceCursor.getColumnIndex(ESGContentProvider.SERVICE_COLUMN_ID)
                if (serviceColumnIndex != -1) {
                    while (serviceCursor.moveToNext()) {
                        val serviceId = serviceCursor.getString(serviceColumnIndex)
                        if (activeServiceIDs.contains(serviceId.toIntOrNull())) {
                            //TODO: time should be sent as string representation
                            val selectArgs = arrayOf(serviceId, scheduleEndTime.toString(), scheduleStartTime.toString())
                            val sortOrder = "${ESGContentProvider.PROGRAM_COLUMN_START_TIME} ASC"
                            contentResolver.query(programContentUri,
                                null, SELECTION, selectArgs, sortOrder)?.use { programCursor ->

                                put(serviceCursor.toService(), programCursor.toProgramList())
                            }
                        }
                    }
                }
            }
        }

        val scheduleServiceIDs = schedule.mapKeys { it.key.id }
        activeServices.forEach { service ->
            if (!scheduleServiceIDs.containsKey(service.id)) {
                schedule[service] = emptyList()
            }
        }

        return schedule
    }

    private fun Cursor.toService(): AVService {
        return AVService(
            getInt(getColumnIndex(ESGContentProvider.SERVICE_COLUMN_BSID)),
            getInt(getColumnIndex(ESGContentProvider.SERVICE_COLUMN_ID)),
            getString(getColumnIndex(ESGContentProvider.SERVICE_COLUMN_SHORT_NAME)),
            getString(getColumnIndex(ESGContentProvider.SERVICE_COLUMN_GLOBAL_ID)),
            getInt(getColumnIndex(ESGContentProvider.SERVICE_COLUMN_MAJOR_CHANNEL_NO)),
            getInt(getColumnIndex(ESGContentProvider.SERVICE_COLUMN_MINOR_CHANNEL_NO)),
            getInt(getColumnIndex(ESGContentProvider.SERVICE_COLUMN_CATEGORY))
        )
    }

    private fun Cursor.toProgramList(): List<SGProgram> {
        return mutableListOf<SGProgram>().apply {
            while (moveToNext()) {
                add(
                    SGProgram(
                        getLong(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_START_TIME)),
                        getLong(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_END_TIME)),
                        getInt(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_DURATION)),
                        SGProgramContent(
                            getString(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_CONTENT_ID)),
                            getLong(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_CONTENT_VERSION)),
                            getString(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_CONTENT_ICON)),
                            getString(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_CONTENT_NAME)),
                            getString(getColumnIndex(ESGContentProvider.PROGRAM_COLUMN_CONTENT_DESCRIPTION))
                        )
                    )
                )
            }
        }
    }

    companion object {
        private const val TIME_STEP_MINUTES = 30

        const val SELECTION = "${ESGContentProvider.SERVICE_COLUMN_ID} = ? AND ${ESGContentProvider.PROGRAM_COLUMN_START_TIME} < ? AND ${ESGContentProvider.PROGRAM_COLUMN_END_TIME} > ?"

        private val SERVICE_COMPARATOR = compareBy<AVService>({ it.majorChannelNo }, { it.minorChannelNo }, { it.shortName }, { it.globalId })
    }
}