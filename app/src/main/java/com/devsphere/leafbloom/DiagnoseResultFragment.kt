package com.devsphere.leafbloom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.databinding.FragmentDiagnoseResultBinding
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.Locale
import kotlin.math.roundToInt

class DiagnoseResultFragment : BaseFragment() {

    private var _binding: FragmentDiagnoseResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnoseResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // Bottom actions (wire later)
        binding.actionAddNote.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.add_note), Toast.LENGTH_SHORT).show()
        }
        binding.actionExport.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.export), Toast.LENGTH_SHORT).show()
        }
        binding.actionSave.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.save), Toast.LENGTH_SHORT).show()
        }

        // Demo placeholders (0.00%)
        setGauge(binding.progressEarlyBlight, binding.tvEarlyBlightPercent, 0f)
        setGauge(binding.progressHealthy, binding.tvHealthyPercent, 0f)
        setGauge(binding.progressLateBlight, binding.tvLateBlightPercent, 0f)
        setGauge(binding.progressSeptoria, binding.tvSeptoriaPercent, 0f)
    }

    private fun setGauge(indicator: CircularProgressIndicator, percentView: View, fraction: Float) {
        val safe = fraction.coerceIn(0f, 1f)
        val progress = (safe * 100f).roundToInt()
        indicator.setProgressCompat(progress, true)

        (percentView as? android.widget.TextView)?.text =
            String.format(Locale.US, "%.2f%%", safe * 100f)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
