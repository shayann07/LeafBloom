package com.devsphere.leafbloom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.devsphere.leafbloom.databinding.FragmentHomeBinding
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.google.android.material.chip.Chip

class HomeFragment : BaseFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            // Initialize chips: save original text and set initial state
            chipGroup.children.forEach { view ->
                (view as? Chip)?.let { chip ->
                    chip.tag = chip.text // Save original text
                    chip.text = if (chip.isChecked) chip.tag.toString() else ""
                }
            }

            // Handle selection changes
            chipGroup.setOnCheckedStateChangeListener { group, _ ->
                TransitionManager.beginDelayedTransition(chipsScroll)
                group.children.forEach { view ->
                    (view as? Chip)?.let { chip ->
                        chip.text = if (chip.isChecked) chip.tag.toString() else ""
                    }
                }
            }

            // Initialize History RecyclerView
            val historyItems = listOf(
                HistoryItem("Rose", "Healthy", "25 November, 12:00 am", R.drawable.history_item),
                HistoryItem("Lily", "Healthy", "25 November, 12:00 am", R.drawable.history_item),
                HistoryItem("Apple", "Healthy", "25 November, 12:00 am", R.drawable.history_item)
            )
            val historyAdapter = HistoryAdapter(historyItems)
            rvHistory.layoutManager = LinearLayoutManager(requireContext())
            rvHistory.adapter = historyAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}