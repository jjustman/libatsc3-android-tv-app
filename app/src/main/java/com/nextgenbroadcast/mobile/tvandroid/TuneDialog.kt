package com.nextgenbroadcast.mobile.tvandroid

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.nextgenbroadcast.mobile.tvandroid.databinding.DialogTuneBinding

class TuneDialog(
    private val frequencyMHz: Int
) : DialogFragment() {

    private lateinit var binding: DialogTuneBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        binding = DialogTuneBinding.inflate(inflater)

        if (frequencyMHz > 0) {
            binding.tuneFrequencyInput.setText((frequencyMHz / 1000).toString())
        }

        binding.tuneActionBtn.setOnClickListener {
            val frequencyKHz = binding.tuneFrequencyInput.text?.toString()?.toIntOrNull() ?: 0

            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                action = MainActivity.ACTION_TUNE
            }.apply {
                putExtra(MainActivity.EXTRA_FREQUENCY, frequencyKHz)
            })

            dismiss()
        }

        binding.tuneScanBtn.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                action = MainActivity.ACTION_SCAN
            })

            dismiss()
        }

        return binding.root
    }

}