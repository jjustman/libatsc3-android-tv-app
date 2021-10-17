package com.nextgenbroadcast.mobile.tvandroid.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class WebImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    fun setUrl(url: String?) {
        if (url.isNullOrBlank()) {
            clear()
        } else {
            load(url)
        }
    }

    private fun clear() {
        setImageBitmap(null)
    }

    private fun load(url: String) {
        Glide.with(this)
            .load(url)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(this)
    }

}