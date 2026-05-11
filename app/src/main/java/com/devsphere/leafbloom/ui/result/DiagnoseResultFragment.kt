package com.devsphere.leafbloom.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.devsphere.leafbloom.R
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.databinding.FragmentDiagnoseResultBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
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
        applySystemBarInsets(binding.root)
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        // Bottom actions (wire later)
        binding.actionAddNote.setOnClickListener {
            com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(requireView(), getString(R.string.add_note), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT, com.devsphere.leafbloom.util.SnackbarUtils.Type.INFO)
        }
        binding.actionExport.setOnClickListener {
            com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(requireView(), getString(R.string.export), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT, com.devsphere.leafbloom.util.SnackbarUtils.Type.INFO)
        }
        binding.actionSave.setOnClickListener {
            com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(requireView(), getString(R.string.save), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT, com.devsphere.leafbloom.util.SnackbarUtils.Type.INFO)
        }

        // Retrieve data
        val args = arguments
        if (args != null) {
            val scoreEarly = args.getFloat("score_early_blight", 0f)
            val scoreHealthy = args.getFloat("score_healthy", 0f)
            val scoreLate = args.getFloat("score_late_blight", 0f)
            val scoreSeptoria = args.getFloat("score_septoria", 0f)
            val predictedName = args.getString("predicted_class_name", "Unknown")

            // Update Header Title (if there's a title TextView)
            // binding.tvResultTitle.text = predictedName // Use if a title view exists
            
             // Populate Gauges
            setGauge(binding.progressEarlyBlight, binding.tvEarlyBlightPercent, scoreEarly)
            setGauge(binding.progressHealthy, binding.tvHealthyPercent, scoreHealthy)
            setGauge(binding.progressLateBlight, binding.tvLateBlightPercent, scoreLate)
            setGauge(binding.progressSeptoria, binding.tvSeptoriaPercent, scoreSeptoria)
            
            // Highlight the winner? 
            // The prompt says "Highlight the one with the max score."
            // The gauges are CircularProgressIndicators. We can maybe change color or stroke width?
            // For now, setting the progress is the main requirement. "Set the progress of each gauge... Highlight the one with the max score."
            // I'll add simple logic for highlighting (e.g. bold text or different color if possible, but standard material progress doesn't change color easily dynamically without custom logic, 
            // but I can bold the text).
            
            highlightWinner(scoreEarly, binding.tvEarlyBlightPercent)
            highlightWinner(scoreHealthy, binding.tvHealthyPercent)
            highlightWinner(scoreLate, binding.tvLateBlightPercent)
            highlightWinner(scoreSeptoria, binding.tvSeptoriaPercent)

            // Dynamic tips based on predicted disease
            val diseaseInfo = com.devsphere.leafbloom.data.model.DiseaseInfo.get(predictedName)
            binding.tvTip1Title.text = getString(diseaseInfo.tip1TitleRes)
            binding.tvTip1Desc.text = getString(diseaseInfo.tip1DescRes)
            binding.tvTip2Title.text = getString(diseaseInfo.tip2TitleRes)
            binding.tvTip2Desc.text = getString(diseaseInfo.tip2DescRes)

        } else {
            // Fallback for preview
            setGauge(binding.progressEarlyBlight, binding.tvEarlyBlightPercent, 0f)
            setGauge(binding.progressHealthy, binding.tvHealthyPercent, 0f)
            setGauge(binding.progressLateBlight, binding.tvLateBlightPercent, 0f)
            setGauge(binding.progressSeptoria, binding.tvSeptoriaPercent, 0f)
        }
    }

    private fun highlightWinner(score: Float, textView: android.widget.TextView?) {
       if (textView == null) return
       if (score >= 0.5f) { // Simple threshold for winner visual
           textView.setTypeface(null, android.graphics.Typeface.BOLD)
           textView.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_primary)) 
       } else {
           textView.setTypeface(null, android.graphics.Typeface.NORMAL)
           textView.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
       }
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
