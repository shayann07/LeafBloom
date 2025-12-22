package com.devsphere.leafbloom.ui.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.devsphere.leafbloom.databinding.DialogPermissionRationaleBinding

class RationaleDialog(
    private val titleStr: String,
    private val descriptionStr: String,
    private val iconResId: Int,
    private val onPositive: () -> Unit,
    private val onNegative: () -> Unit
) : DialogFragment() {

    private var _binding: DialogPermissionRationaleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPermissionRationaleBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = titleStr
        binding.description.text = descriptionStr
        binding.icon.setImageResource(iconResId)

        binding.btnAllow.setOnClickListener {
            dismiss()
            onPositive()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
            onNegative()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "RationaleDialog"
    }
}
