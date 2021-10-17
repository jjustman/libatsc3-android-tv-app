package com.nextgenbroadcast.mobile.tvandroid.servicesGuide

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ScheduleLayoutManager
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.serviceGuide.SGProgram
import com.nextgenbroadcast.mobile.core.serviceGuide.SGScheduleMap
import com.nextgenbroadcast.mobile.tvandroid.R
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideAdapter.Companion.IGNORE_POSITION
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideAdapter.Companion.NO_POSITION
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator.CollectionItemDecoration
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator.CurrentTimeIndicator
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator.HackServiceHeaderLayoutter
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.decorator.HackTimeHeaderLayoutter
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideDataItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideServiceItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideTimeItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.IGuideItem
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.view.ServiceGuideServiceItemView
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.view.ServiceGuideTimeItemView
import com.nextgenbroadcast.mobile.tvandroid.utils.ViewUtils.findChildViewUnder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val GUIDE_UI_HACK = true

class ServiceGuideView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    interface OnServiceListener {
        fun onChannelSelect(service: AVService)
        fun onContentSelected(name: String, description: String, info: String, previewUrl: String?)
    }

    private lateinit var serviceFilterView: ViewGroup
    private lateinit var scheduleGridView: RecyclerView
    private lateinit var guideLine: View
    private lateinit var filterContainer: ViewGroup
    private lateinit var placeholder: ViewGroup
    private lateinit var timeContainer: ViewGroup
    private lateinit var channelsContainer: ViewGroup
    private lateinit var serviceGuideAdapter: ServiceGuideAdapter
    private lateinit var layoutManager: LockableStaggeredGridLayoutManager
    private lateinit var hackDecorator: CollectionItemDecoration

    private val layoutInflater = LayoutInflater.from(context)
    private val noInfoStr = resources.getString(R.string.service_guide_no_information)

    private val metrics = context.resources.displayMetrics
    private var columnWidth = (metrics.widthPixels * 0.2).toInt()
    private val headerWidth = (columnWidth * 1.5).toInt()
    private val textPlaceHolderHeight = (metrics.heightPixels / metrics.density / 2).toInt()

    private var dataManager: IServiceGuideManager? = null

    private var serviceListener: OnServiceListener? = null
    private var lastScheduleUpdate: Long = 0
    private var afterScrollSelection: Runnable? = null

    private val serviceHeaders = mutableListOf<View>()

    private var scheduleData: SGScheduleMap = emptyMap()
    private var allServices: List<AVService> = emptyList()
    private var selectedServiceGlobalId: String? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (isInEditMode) return

        serviceFilterView = findViewById(R.id.service_filter_view)
        serviceFilterView.layoutParams.width = headerWidth

        guideLine = findViewById(R.id.guide_line)
        (guideLine.layoutParams as LayoutParams).guideBegin = textPlaceHolderHeight

        placeholder = findViewById(R.id.esg_placeholder)
        timeContainer = findViewById(R.id.time_container)
        channelsContainer = findViewById(R.id.channels_container)
        filterContainer = findViewById(R.id.service_filter_container)

        scheduleGridView = findViewById(R.id.service_guide_grid)
        if (GUIDE_UI_HACK) {
            // fixes snap
            columnWidth = (context.resources.displayMetrics.widthPixels - headerWidth) / 3
            val rowHeight = resources.getDimensionPixelSize(R.dimen.services_guide_cell_height)
            scheduleGridView.setPaddingRelative(headerWidth, rowHeight, 0, 0)
            channelsContainer.layoutParams.width = headerWidth
        } else {
            filterContainer.visibility = GONE
        }

        serviceGuideAdapter = ServiceGuideAdapter(layoutInflater, columnWidth)

        layoutManager = LockableStaggeredGridLayoutManager(1, resources.getDimensionPixelSize(R.dimen.services_guide_cell_height))

        val divider = ContextCompat.getDrawable(context, R.drawable.divider)

        with(scheduleGridView) {
            layoutManager = this@ServiceGuideView.layoutManager
            adapter = serviceGuideAdapter

            //TODO: works incorrect with be-directional scroll
            //PagerSnapHelper().attachToRecyclerView(this)

            if (GUIDE_UI_HACK) {
                hackDecorator = CollectionItemDecoration().also {
                    addItemDecoration(it)
                }
            }

            if (divider != null) {
                addItemDecoration(
                    DividerItemDecoration(context, DividerItemDecoration.HORIZONTAL).apply {
                        setDrawable(divider)
                    })
                addItemDecoration(
                    DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                        setDrawable(divider)
                    }
                )
            }

            addOnItemTouchListener(ScheduleOnClickListener(context))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        dataManager?.startObserving { schedule ->
            setSchedule(schedule)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        dataManager?.stopObserving()
    }

    fun setDataManger(manager: IServiceGuideManager) {
        if (dataManager != null) throw IllegalStateException("Service Schedule Manager can't be reassigned")

        dataManager = manager

        scheduleGridView.addItemDecoration(
            CurrentTimeIndicator(
                ContextCompat.getColor(context, R.color.gridTimeIndicatorColor),
                resources.getDimensionPixelSize(R.dimen.services_guide_indicator_width).toFloat(),
                manager.timeStepMils.toInt()
            )
        )

        manager.startObserving { schedule ->
            setSchedule(schedule)
        }
    }

    fun setServiceListener(listener: OnServiceListener) {
        serviceListener = listener
    }

    fun setSelectedService(serviceGlobalId: String?) {
        selectedServiceGlobalId = serviceGlobalId
        val selectedRow = serviceGlobalId?.let { findServiceRow(serviceGlobalId) } ?: 0
        if (selectedRow >= 0 && selectedRow != serviceGuideAdapter.selectedSpanIndex) {
            selectRowAndActualProgram(selectedRow)
        }
    }

    fun getSelectedServiceGlobalId(): String? {
        return selectedServiceGlobalId
    }

    private fun setSchedule(scheduleMap: SGScheduleMap) {
        if (scheduleMap.isEmpty() && scheduleData.isEmpty()) return

        val wasCleared = scheduleMap.isEmpty() && scheduleData.isNotEmpty() || scheduleMap.isNotEmpty() && scheduleData.isEmpty()
        val servicesChanged = scheduleMap.size != scheduleData.size || !scheduleMap.keys.containsAll(scheduleData.keys)

        scheduleData = scheduleMap

        startScheduleUpdate(scheduleMap, wasCleared || servicesChanged)
    }

    private fun rebuildSchedule() {
        startScheduleUpdate(scheduleData, true)
    }

    private fun startScheduleUpdate(scheduleMap: SGScheduleMap, forced: Boolean) {
        removeCallbacks(updateSchedule)

        val milsFromLastUpdate = System.currentTimeMillis() - lastScheduleUpdate
        if (forced || milsFromLastUpdate > SCHEDULE_UPDATE_THRESHOLD_MILS) {
            updateSchedule(scheduleMap)
        } else {
            postDelayed(updateSchedule, SCHEDULE_UPDATE_THRESHOLD_MILS - milsFromLastUpdate)
        }
    }

    private fun updateSchedule(scheduleMap: SGScheduleMap) {
        val services = scheduleMap.keys
            .distinctBy { it.globalId }
            .toMutableList()
        val schedule = scheduleMap
            .filter { services.contains(it.key) }
            .mapKeys { it.key.globalId }
            .toMutableMap()

        allServices = services

        val (servicesSchedule, timeline) = scheduleToGuideItems(schedule, services)

        scheduleGridView.stopScroll()
        layoutManager.lockVerticalScrolling = true
        removeCallbacks(selectFirstIfNoSelection)
        removeCallbacks(afterScrollSelection)
        removeCallbacks(unlockScrolling)
        removeCallbacks(scrollToActualPositionRunnable)
        removeCallbacks(refreshScheduleRunnable)

        if (schedule.isEmpty()) {
            serviceGuideAdapter.reset()

            fireOnContentSelected(NO_POSITION)
            placeholder.animate().setDuration(ANIMATION_DURATION).alpha(1f)
        } else {
            layoutManager.spanCount = schedule.size

            if (GUIDE_UI_HACK) {
                hackDecorator.clear()

                hackRecreateServiceHeaders(services)

                val dividerSize = resources.getDimensionPixelSize(R.dimen.services_guide_divider_size)

                hackDecorator.addItemDecoration(
                    HackServiceHeaderLayoutter(dividerSize, serviceHeaders)
                )
                hackDecorator.addItemDecoration(
                    HackTimeHeaderLayoutter(timeline, columnWidth, dividerSize) {
                        layoutInflater.inflate(R.layout.service_guide_time_header, this@ServiceGuideView.timeContainer, false).also {
                            it.layoutParams.width = columnWidth
                            this@ServiceGuideView.timeContainer.addView(it)
                        } as ServiceGuideTimeItemView
                    }
                )
            }

            val oldPosition = serviceGuideAdapter.selectedPosition
            val oldSpanPosition = serviceGuideAdapter.selectedSpanIndex
            val oldItem = if (oldPosition >= 0) (serviceGuideAdapter.getItem(oldPosition) as? GuideDataItem) else null
            serviceGuideAdapter.setItems(servicesSchedule)

            // restore program selection
            oldItem?.let {
                val oldServiceGlobalId = oldItem.service.globalId
                var newPosition = serviceGuideAdapter.findPosition { item ->
                    item.type == IGuideItem.TYPE_DATA
                            && (item as GuideDataItem).service.globalId == oldServiceGlobalId
                            && item.startTime < oldItem.endTime
                            && item.endTime > oldItem.startTime
                }
                // search for suitable program
                if (newPosition >= 0) {
                    val firstProgram = serviceGuideAdapter.getItem(newPosition) as GuideDataItem
                    val nextProgram = serviceGuideAdapter.getItem(newPosition + 1) as? GuideDataItem
                    if (nextProgram != null && nextProgram.startTime < oldItem.endTime && nextProgram.endTime > oldItem.startTime) {
                        val firstDiff = min(firstProgram.endTime, oldItem.endTime) - oldItem.startTime
                        val nextDiff = min(nextProgram.endTime, oldItem.endTime) - oldItem.startTime
                        if (nextDiff > firstDiff) {
                            newPosition++
                        }
                    }
                }
                val newSpanPosition = services.indexOfFirst { it.globalId == oldServiceGlobalId }

                if ((newPosition >= 0 && newPosition != oldPosition) || newSpanPosition != oldSpanPosition) {
                    serviceGuideAdapter.setSelected(
                        if (newPosition < 0) NO_POSITION else newPosition,
                        if (newSpanPosition < 0) NO_POSITION else newSpanPosition
                    )
                }
            }

            fireOnContentSelected(serviceGuideAdapter.selectedPosition)
            placeholder.animate().setDuration(ANIMATION_DURATION).alpha(0f)

            //default selection
            postDelayed(selectFirstIfNoSelection, 200)
        }

        postDelayed(unlockScrolling, 300)
        lastScheduleUpdate = System.currentTimeMillis()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun hackRecreateServiceHeaders(services: List<AVService>) {
        serviceHeaders.clear()
        with(channelsContainer) {
            for (i in childCount - 1 downTo 0) {
                val view = getChildAt(i)
                if (view is ServiceGuideServiceItemView) {
                    removeView(view)
                }
            }

            services.forEach { service ->
                val rowIndex = serviceHeaders.size
                addView(
                    layoutInflater.inflate(R.layout.service_guide_service_item, this, false).also { view ->
                        view.layoutParams.width = headerWidth
                        (view as ServiceGuideServiceItemView).bind(GuideServiceItem(service))

                        view.setOnClickListener {
                            selectService(rowIndex)
                            serviceListener?.onChannelSelect(service)
                        }
                        //click emitted by RecyclerView on click listener
                        view.isClickable = false

                        serviceHeaders.add(view)
                    }
                )
            }
        }
    }

    private fun selectService(rowIndex: Int) {
        val itemPosition = findActualPositionInRow(rowIndex)

        if ((itemPosition != serviceGuideAdapter.selectedPosition
                        || rowIndex != serviceGuideAdapter.selectedSpanIndex)) {
            setSelection(-1, rowIndex)
            scheduleGridView.smoothScrollToPosition(itemPosition)
            setSelectionAfterScroll(itemPosition, rowIndex)
        } else {
            scheduleGridView.smoothScrollToPosition(itemPosition)
        }
    }

    private fun findActualPositionInRow(rowIndex: Int): Int {
        val timeEdge = System.currentTimeMillis()
        return serviceGuideAdapter.findPosition { item ->
            item.type == IGuideItem.TYPE_DATA
                    && (item as GuideDataItem).rowIndex == rowIndex
                    && item.startTime <= timeEdge
                    && item.endTime >= timeEdge
        }
    }

    private fun setSelectionAfterScroll(itemPosition: Int, rowIndex: Int) {
        if (afterScrollSelection != null) {
            removeCallbacks(afterScrollSelection)
        }

        afterScrollSelection = object : Runnable {
            override fun run() {
                if (scheduleGridView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                    postDelayed(this, AFTER_SCROLL_SELECTION_DELAY_MILS)
                } else {
                    afterScrollSelection = null
                    layoutManager.scrollToPositionWithOffset(itemPosition, 0)
                    setSelection(itemPosition, rowIndex)
                }
            }
        }.also {
            postDelayed(it, AFTER_SCROLL_SELECTION_DELAY_MILS)
        }
    }

    private fun selectServiceGuideHeader(rowIndex: Int) {
        if (rowIndex == IGNORE_POSITION) return

        serviceHeaders.forEachIndexed { index, v ->
            v.isSelected = (index == rowIndex)
        }
    }

    private fun scheduleToGuideItems(data: Map<String?, List<SGProgram>>, services: List<AVService>): Pair<List<GuideDataItem>, List<GuideTimeItem>> {
        val manager = dataManager
        if (manager == null || data.size != services.size) {
            return Pair(emptyList(), emptyList())
        }

        val dateFormat = SimpleDateFormat(TIME_PATTERN, Locale.US)
        val times = mutableListOf<GuideTimeItem>().apply {
            var time = manager.scheduleStartTime
            for (i in 0 until manager.columnCount) {
                add(GuideTimeItem(time, dateFormat.format(time)))
                time += manager.timeStepMils
            }
        }

        val dataIndexes = IntArray(data.size).apply {
            fill(0)
        }

        val columnIndexes = IntArray(data.size).apply {
            fill(0)
        }

        val servicesSchedule = mutableListOf<GuideDataItem>().apply {
            times.forEachIndexed { clmIndex, time ->
                val rangeStart = time.timestamp
                val rangeEnd = rangeStart + manager.timeStepMils
                services.forEachIndexed { srvIndex, service ->
                    if (columnIndexes[srvIndex] <= clmIndex) {
                        var index = dataIndexes[srvIndex]++

                        // search for must suitable program
                        val programs = data[service.globalId]
                        val program = programs?.getOrNull(index)?.let { firstProgram ->
                            var result = firstProgram
                            if (firstProgram.startTime < rangeEnd && firstProgram.endTime > rangeStart) {
                                programs.getOrNull(index + 1)?.let { nextProgram ->
                                    if (nextProgram.startTime < rangeEnd && nextProgram.endTime > rangeStart) {
                                        val firstDiff = min(firstProgram.endTime, rangeEnd) - rangeStart
                                        val nextDiff = min(nextProgram.endTime, rangeEnd) - rangeStart
                                        if (nextDiff > firstDiff) {
                                            index = dataIndexes[srvIndex]++
                                            result = nextProgram
                                        }
                                    }
                                }
                            }
                            result
                        }

                        // create grid item
                        program?.let {
                            if (program.startTime < rangeEnd && program.endTime > rangeStart) {
                                val size = max(1, ((min(program.endTime, manager.scheduleEndTime) - rangeStart) / manager.timeStepMils).toInt())
                                val content = program.content
                                add(
                                    GuideDataItem(
                                        service,
                                        program.startTime,
                                        program.endTime,
                                        content?.name ?: noInfoStr,
                                        content?.description,
                                        content?.icon,
                                        size
                                    ).apply {
                                        rowIndex = srvIndex
                                        columnIndex = clmIndex
                                        rangeStartTime = rangeStart
                                    }
                                )

                                columnIndexes[srvIndex] += size
                            } else {
                                // skip program if out of range
                                dataIndexes[srvIndex]--
                                null
                            }
                        } ?: let {
                            add(
                                GuideDataItem(service, rangeStart, rangeEnd, noInfoStr).apply {
                                    rowIndex = srvIndex
                                    columnIndex = clmIndex
                                    rangeStartTime = rangeStart
                                }
                            )

                            columnIndexes[srvIndex]++
                        }
                    }
                }
            }
        }

        return Pair(servicesSchedule, times)
    }

    private fun calcVisibilityPercent(program: SGProgram, scheduleStartTime: Long, step: Long) =
        (min(program.endTime, scheduleStartTime + step) - max(program.startTime, scheduleStartTime)) / step.toFloat()

    private fun setSelection(position: Int, selectedRow: Int) {
        val selectedPosition = if (position == NO_POSITION) {
            findActualAdapterPositionInRow(selectedRow)
        } else {
            position
        }

        selectServiceGuideHeader(selectedRow)

        serviceGuideAdapter.setSelected(selectedPosition, selectedRow)

        // set focused item equal to selection
        scheduleGridView.findViewHolderForAdapterPosition(selectedPosition)?.itemView?.requestFocus()

        fireOnContentSelected(selectedPosition)
    }

    private fun fireOnContentSelected(adapterPosition: Int) {
        var serviceName = ""
        var serviceDescription = ""
        var serviceInfo = ""
        var programPreviewUrl: String? = null

        serviceGuideAdapter.getItem(adapterPosition)?.let { data ->
            serviceName = data.text

            if (data is GuideDataItem) {
                with(data.service) {
                    serviceDescription =
                        resources.getString(R.string.service_guide_program_info_format,
                            majorChannelNo, minorChannelNo, shortName, data.startTime, data.endTime)
                    serviceInfo = data.description ?: noInfoStr
                    programPreviewUrl = data.previewUrl
                }
            }
        }

        serviceListener?.onContentSelected(serviceName, serviceDescription, serviceInfo, programPreviewUrl)
    }

    private fun findRowPositions(selectedRow: Int): List<Int> {
        val recyclerView = scheduleGridView
        return mutableListOf<Int>().apply {
            for (i in 0 until recyclerView.childCount) {
                val view = recyclerView.getChildAt(i)
                val lp = view.layoutParams as ScheduleLayoutManager.LayoutParams
                if (lp.spanIndex == selectedRow) {
                    add(lp.viewAdapterPosition)
                }
            }

            sort()
        }
    }

    @Suppress("SameParameterValue")
    private fun findAdapterPositionInRow(selectedRow: Int, rowPosition: Int): Int {
        return findRowPositions(selectedRow).getOrNull(rowPosition) ?: NO_POSITION
    }

    private fun findServiceRow(serviceId:String) = allServices.indexOfFirst {
        it.globalId == serviceId
    }

    private fun findActualAdapterPositionInRow(selectedRow: Int): Int {
        val timeEdge = System.currentTimeMillis()
        findRowPositions(selectedRow).forEach { index ->
            val item = serviceGuideAdapter.getItem(index)
            if (item is GuideDataItem) {
                if (item.startTime <= timeEdge && item.endTime >= timeEdge) {
                    return index
                }
            }
        }

        return NO_POSITION
    }

    private fun selectRowAndActualProgram(selectedRow: Int) {
        setSelection(NO_POSITION, selectedRow)

        val selectedPosition = serviceGuideAdapter.selectedPosition
        if (selectedPosition != NO_POSITION) {
            layoutManager.scrollToPositionWithOffset(selectedPosition, 0)
            // scroll vertically to selected position
            layoutManager.findViewByPosition(selectedPosition)?.let { view ->
                val viewBottom = view.bottom
                val gridBottom = scheduleGridView.bottom - scheduleGridView.paddingBottom
                if (viewBottom > gridBottom) {
                    scheduleGridView.scrollBy(0, viewBottom - gridBottom)
                } else {
                    val viewTop = view.top
                    val gridTop = scheduleGridView.top + scheduleGridView.paddingTop
                    if (viewTop < gridTop) {
                        scheduleGridView.scrollBy(0, viewTop - gridTop)
                    }
                }
            }
        }
    }

    private fun onCellClick(position: Int) {
        val item = serviceGuideAdapter.getItem(position) as? GuideDataItem
        setSelection(position, item?.rowIndex ?: IGNORE_POSITION)
        layoutManager.scrollToPositionWithOffset(position, 0)
        if (item != null && item.service.globalId != selectedServiceGlobalId) {
            serviceListener?.onChannelSelect(item.service)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    scheduleGridView.focusedChild?.let { v ->
                        val newSelection = scheduleGridView.getChildAdapterPosition(v)
                        if (newSelection >= 0) {
                            onCellClick(newSelection)
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when(e.action) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_DOWN, // because ACTION_UP could be intercepted by motion layout
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(scrollToActualPositionRunnable)
                postDelayed(scrollToActualPositionRunnable, DELAY_BEFORE_SCROLL_TO_ACTUAL_TIME)
            }
        }

        return super.onInterceptTouchEvent(e)
    }

    private val scrollToActualPositionRunnable = Runnable {
        if (dataManager?.isPeriodOutdated() == true) {
            removeCallbacks(refreshScheduleRunnable)
            postDelayed(refreshScheduleRunnable, 1000)
        }

        val serviceGlobalId = selectedServiceGlobalId ?: return@Runnable
        val serviceRow = findServiceRow(serviceGlobalId)
        if (serviceRow >= 0) {
            val position = findActualPositionInRow(serviceRow)
            if (position >= 0) {
                setSelectionAfterScroll(position, serviceRow)
            }
        }
    }

    private val refreshScheduleRunnable = Runnable {
        dataManager?.refreshSchedule()
    }

    private val updateSchedule = Runnable {
        updateSchedule(scheduleData)
    }

    private val selectFirstIfNoSelection = Runnable {
        if (serviceGuideAdapter.selectedPosition == NO_POSITION) {
            var rowToSelect = 0
            selectedServiceGlobalId?.let {
                val rowIndex = findServiceRow(it)
                if (rowIndex >= 0) {
                    rowToSelect = rowIndex
                }
            }

            selectRowAndActualProgram(rowToSelect)
        } else {
            selectServiceGuideHeader(max(serviceGuideAdapter.selectedSpanIndex, 0))
        }
    }

    private val unlockScrolling = Runnable {
        layoutManager.lockVerticalScrolling = false
    }

    private inner class ScheduleOnClickListener(context: Context) : RecyclerView.SimpleOnItemTouchListener() {
        private val gestureDetector: GestureDetector = GestureDetector(context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!onInterceptTapEvent(e)) {
                        findView(e) { _, position ->
                            onCellClick(position)
                            true
                        }
                    }

                    return super.onSingleTapConfirmed(e)
                }

                override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    // Prevent horizontal scrolling if it started over a channel header
                    return e1.x < headerWidth && (abs(e1.x - e2.x) > abs(e1.y - e2.y))
                }
            }
        )

        private fun onInterceptTapEvent(e: MotionEvent): Boolean {
            // Are we over a channel header?
            channelsContainer.findChildViewUnder(e.x, e.y)?.let { view ->
                view.performClick()
                return true
            }

            return false
        }

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            return gestureDetector.onTouchEvent(e)
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            super.onRequestDisallowInterceptTouchEvent(disallowIntercept)
            if (disallowIntercept) {
                gestureDetector.onTouchEvent(
                    MotionEvent.obtain(0,
                        0,
                        MotionEvent.ACTION_CANCEL,
                        0f,
                        0f,
                        0)
                )
            }
        }

        private fun findView(e: MotionEvent, action: (View, Int) -> Boolean): Boolean {
            scheduleGridView.findChildViewUnder(e.x, e.y)?.let { childView ->
                val position = scheduleGridView.getChildAdapterPosition(childView)
                return action.invoke(childView, position)
            }

            return false
        }
    }

    private class LockableStaggeredGridLayoutManager(spanCount: Int, spanSize: Int) :
        ScheduleLayoutManager(spanCount, spanSize) {

        var lockVerticalScrolling = false
        var lockHorizontalScrolling = false

        override fun canScrollVertically(): Boolean {
            return !lockVerticalScrolling && super.canScrollVertically()
        }

        override fun canScrollHorizontally(): Boolean {
            return !lockHorizontalScrolling && super.canScrollHorizontally()
        }

        override fun onRequestChildFocus(parent: RecyclerView, state: RecyclerView.State, child: View, focused: View?): Boolean {
            // This disables the default scroll behavior for focus movement.
            return true
        }
    }

    companion object {
        private const val TIME_PATTERN = "h:mm a"

        private val SCHEDULE_UPDATE_THRESHOLD_MILS = TimeUnit.SECONDS.toMillis(15)
        private val DELAY_BEFORE_SCROLL_TO_ACTUAL_TIME = TimeUnit.MINUTES.toMillis(5)

        private const val  AFTER_SCROLL_SELECTION_DELAY_MILS = 50L
        private const val ANIMATION_DURATION = 300L
    }
}