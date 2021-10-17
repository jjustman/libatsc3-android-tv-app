package com.nextgenbroadcast.mobile.tvandroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.nextgenbroadcast.mobile.tvandroid.databinding.DialogDeploymentInfoBinding

class DeploymentInfoDialog : DialogFragment() {

    private lateinit var binding: DialogDeploymentInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        binding = DialogDeploymentInfoBinding.inflate(inflater)

        val clickHereString = getString(R.string.dialog_atsc3_deployment_info_click_here)

        val infoText = SpannableString(
            getString(
                R.string.dialog_atsc3_deployment_info,
                clickHereString,
                clickHereString
            )
        )

        binding.deploymentInfoText.setOnClickListener {
            dialog?.dismiss()
        }

        val spanOne = object : ColoringClickableSpan(R.color.white, R.color.black) {
            override fun onClick(widget: View) {
                dialog?.dismiss()
                startActivity(Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.nextgen_deployments_url))
                ))
            }
        }

        val spanTwo = object : ColoringClickableSpan(R.color.white, R.color.black) {
            override fun onClick(widget: View) {
                dialog?.dismiss()
                startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_TUNE_DIALOG
                })
            }
        }

        val firstIndex = infoText.indexOf(clickHereString)
        val secondIndex = infoText.indexOf(clickHereString, firstIndex + 1)

        infoText.setSpan(
            spanOne,
            firstIndex,
            firstIndex + clickHereString.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        infoText.setSpan(
            spanTwo,
            secondIndex,
            secondIndex + clickHereString.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        with(binding.deploymentInfoText) {
            text = infoText
            movementMethod = LinkMovementMethod.getInstance()
        }

        return binding.root
    }

    abstract inner class ColoringClickableSpan(
        private val spanBackgroundColor: Int,
        private val spanTextColor: Int
    ) : ClickableSpan() {
        override fun updateDrawState(ds: TextPaint) {
            with(ds) {
                bgColor = requireContext().getColor(spanBackgroundColor)
                color = requireContext().getColor(spanTextColor)
                isUnderlineText = true
            }
        }
    }
}