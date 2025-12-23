package com.devsphere.leafbloom.ui.walkthrough

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.devsphere.leafbloom.databinding.FragmentWalkthroughBinding

class WalkthroughFragment : BaseFragment() {

    private var _binding: FragmentWalkthroughBinding? = null
    private val binding get() = _binding!!

    private val layouts = listOf(
        R.layout.layout_walk_1, R.layout.layout_walk_2, R.layout.layout_walk_3
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalkthroughBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(binding.root)
        setupViewPager()

        // Skip → same destination as final "Continue"
        binding.btnSkip.setOnClickListener {
            findNavController().navigate(R.id.modelDownloadFragment)
        }
    }

    private fun setupViewPager() {
        binding.viewPagerWalk.adapter = WalkthroughAdapter(layouts)
        binding.dotsIndicator.attachTo(binding.viewPagerWalk)

        binding.viewPagerWalk.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                binding.btnNext.text = if (position == layouts.size - 1) {
                    getString(R.string.continue_text)
                } else {
                    getString(R.string.next)
                }
            }
        })

        binding.btnNext.setOnClickListener {
            val index = binding.viewPagerWalk.currentItem
            if (index < layouts.size - 1) {
                binding.viewPagerWalk.currentItem = index + 1
            } else {
                findNavController().navigate(R.id.modelDownloadFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
