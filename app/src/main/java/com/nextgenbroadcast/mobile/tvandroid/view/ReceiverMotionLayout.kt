package com.nextgenbroadcast.mobile.tvandroid.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import com.nextgenbroadcast.mobile.tvandroid.R
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.ServiceGuideView
import com.nextgenbroadcast.mobile.view.UserAgentView
import kotlin.math.abs

class ReceiverMotionLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MotionLayout(context, attrs, defStyleAttr), MotionLayout.TransitionListener {

    private lateinit var baView: UserAgentView
    private lateinit var serviceGuideView: ServiceGuideView
    private lateinit var rmpPlayerContainer: ConstraintLayout
    private lateinit var sideMenuIndicator: View

    private var touchEdgeSize: Int = 0
    private var midPositionScaleFactor: Float = 0f
    private var endPositionScaleFactor: Float = 0f

    private var flingLock = false
    private var allowDrag = true

    var lockBottomView = false

    val state: Int
        get() = when(currentState) {
            R.id.start -> CLOSED
            R.id.middle -> IN_MIDDLE
            R.id.end -> IN_END
            R.id.top -> IN_TOP
                else -> NEUTRAL
        }

    fun closeAllMenu() {
        transitionToState(R.id.start)
        rmpContainerScale(0f, 1f)
    }

    fun gotoState(newState: Int) {
        when (newState) {
            CLOSED -> transitionToState(R.id.start)
            IN_MIDDLE -> transitionToState(R.id.middle)
            IN_END -> transitionToState(R.id.end)
            IN_TOP -> transitionToState(R.id.top)
            else -> {
            }
        }
    }

    fun disableMenu(value: Boolean) {
        closeAllMenu()

        getTransition(R.id.open_right_side_menu).setEnable(!value)
        getTransition(R.id.open_bottom_menu_from_start).setEnable(!value)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        touchEdgeSize = resources.getDimensionPixelSize(R.dimen.services_guide_touch_edge)

        sideMenuIndicator = findViewById(R.id.side_menu_indicator)
        baView = findViewById(R.id.ba_view)
        serviceGuideView = findViewById(R.id.service_guide_view)
        rmpPlayerContainer = findViewById(R.id.rmp_player_container)

        val typedValue = TypedValue()
        resources.getValue(R.dimen.scale_factor_for_end_position_of_esg, typedValue, true)
        endPositionScaleFactor = typedValue.float
        resources.getValue(R.dimen.scale_factor_for_mid_position_of_esg, typedValue, true)
        midPositionScaleFactor = typedValue.float

        setTransitionListener(this)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        updateDragState(event)

        if (!allowDrag) return false

        val consumed = super.onInterceptTouchEvent(event)
        onTouchEvent(event)
        return state == CLOSED && isOverTouchEdge(event) || consumed
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        updateDragState(event)

        if (!allowDrag) return false

        return super.onTouchEvent(event)
    }

    private fun updateDragState(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                allowDrag = state != CLOSED || isOverTouchEdge(event)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> allowDrag = true
        }
    }

    private fun isOverTouchEdge(event: MotionEvent) =
        !(event.x < right - touchEdgeSize && (lockBottomView || event.y < bottom - touchEdgeSize))

    override fun onStopNestedScroll(target: View, type: Int) {
        // fix accidental scrolling by key up action
        if (currentState == -1) {
            super.onStopNestedScroll(target, type)
        }
        // unlock prescrolling
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            flingLock = false
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        //!target.canScrollVertically(-1) brakes parent scroll
        if (!flingLock && (dx >= 0 || !target.canScrollHorizontally(-1))) {
            val view = (target.getTag(R.id.receiver_motion_layout_view_wrapper) as? View) ?: let {
                ViewWrapper(target).also { view ->
                    target.setTag(R.id.receiver_motion_layout_view_wrapper, view)
                }
            }

            super.onNestedPreScroll(view, dx, 0, consumed, type)
        }
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        flingLock = true
        return super.onNestedPreFling(target, velocityX, velocityY)
    }

    fun animateIndicator() {
        if (state != CLOSED) return

        sideMenuIndicator.animate()
            .alpha(1f)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator { input ->
                // 0..1..0
                1 - abs(input - 0.5f) * 2
            }
            .start()
    }

    override fun onTransitionStarted(motionLayout: MotionLayout, startId: Int, endId: Int) {
        // ignore
    }

    override fun onTransitionChange(motionLayout: MotionLayout, startId: Int, endId: Int, progress: Float) {
        if (startId == R.id.start && endId == R.id.middle) {
            val diff = 1 - midPositionScaleFactor
            val scaleFactor = 1 - (diff * progress)
            val pivotY = bottom.toFloat() / 2
            rmpContainerScale(pivotY, scaleFactor)
        }

        if (startId == R.id.middle && endId == R.id.end) {
            val diff = midPositionScaleFactor - endPositionScaleFactor
            val scaleFactor = midPositionScaleFactor - (diff * progress)
            val height = bottom.toFloat() / 2
            val pivotY = height - (height * progress)
            rmpContainerScale(pivotY, scaleFactor)
        }
    }

    override fun onTransitionCompleted(motionLayout: MotionLayout, currentId: Int) {
        when(currentId) {
            R.id.top -> baView.actionExit()
            R.id.start -> rmpContainerScale(0f, 1f)
            R.id.middle -> {
                val pivotY = height.toFloat() / 2
                rmpContainerScale(pivotY, midPositionScaleFactor)
            }
            R.id.end -> rmpContainerScale(0f, endPositionScaleFactor)
        }
    }

    override fun onTransitionTrigger(motionLayout: MotionLayout, triggerId: Int, positive: Boolean, progress: Float) {
        // ignore
    }

    private fun rmpContainerScale(y: Float, scaleFactor: Float) {
        with(rmpPlayerContainer) {
            pivotX = 0f
            pivotY = y
            scaleX = scaleFactor
            scaleY = scaleFactor
        }
    }

    private class ViewWrapper(
        private var view: View
    ): View(view.context) {
        override fun getId(): Int {
            return view.id
        }

        override fun setNestedScrollingEnabled(enabled: Boolean) {
            view.isNestedScrollingEnabled = enabled
        }

        override fun canScrollVertically(direction: Int): Boolean {
            return false
        }
    }

    companion object {
        private const val ANIMATION_DURATION = 600L * 2

        const val CLOSED = 0
        const val IN_MIDDLE = 1
        const val IN_END = 2
        const val IN_TOP = 3
        const val NEUTRAL = -1
    }
}