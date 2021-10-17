package com.nextgenbroadcast.mobile.tvandroid.servicesGuide.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nextgenbroadcast.mobile.tvandroid.R
import com.nextgenbroadcast.mobile.tvandroid.servicesGuide.items.GuideServiceItem

class ServiceGuideServiceItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    fun bind(service: GuideServiceItem) {
        val serviceTitle: TextView = findViewById(R.id.service_title)
        val serviceMajorNumView: TextView = findViewById(R.id.service_major_num_view)
        val serviceFullNumView: TextView = findViewById(R.id.service_full_num_view)

        with(service.service) {
            serviceMajorNumView.text = majorChannelNo.toString()
            val s = "$majorChannelNo - $minorChannelNo"
            serviceFullNumView.text = s
            serviceTitle.text = shortName
        }
    }
}