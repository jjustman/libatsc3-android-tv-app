package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nextgenbroadcast.mobile.tvandroid.R
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideTimeItem

class ServiceGuideTimeItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    fun bind(time: GuideTimeItem) {
        val periodTimeView: TextView = findViewById(R.id.period_time_view)
        val periodDescriptionView: TextView = findViewById(R.id.period_description_view)

        periodTimeView.text = time.text
        time.desc?.let { description ->
            periodDescriptionView.text = description
            periodDescriptionView.visibility = VISIBLE
        } ?: let {
            periodDescriptionView.visibility = GONE
        }
    }
}