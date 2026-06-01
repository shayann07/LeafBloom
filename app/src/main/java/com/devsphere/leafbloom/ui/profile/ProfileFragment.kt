package com.devsphere.leafbloom.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentProfileBinding
import com.devsphere.leafbloom.prefs.UserProfile
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.entrancePop
import com.devsphere.leafbloom.ui.motion.entranceScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.primeScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeScalePop
import com.devsphere.leafbloom.ui.motion.snapVisible
import com.devsphere.leafbloom.util.SnackbarUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val vm: ProfileViewModel by viewModels {
        ProfileViewModel.Factory(requireActivity().application, this)
    }

    private var hasPlayedEntrance: Boolean = false

    private val pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.handlePickedImage(uri, vm.pendingPickTarget.value)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)

        view.post {
            if (!isAdded) return@post
            setupVersion()
            setupInlineEdits()
            setupTapOutsideToCommit()
            setupClickListeners()
        }

        playEntrance()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.profile.collect(::renderProfile) }
                launch {
                    vm.imageSaveEvents.collect { event ->
                        when (event) {
                            is ImageSaveResult.Failed -> SnackbarUtils.showSnackbar(
                                binding.root,
                                getString(R.string.image_load_failed),
                                Snackbar.LENGTH_SHORT,
                                SnackbarUtils.Type.ERROR
                            )
                            is ImageSaveResult.Saved -> applySavedImage(event)
                        }
                    }
                }
                launch {
                    vm.historyClearedEvents.collect {
                        SnackbarUtils.showSnackbar(
                            binding.root,
                            getString(R.string.history_cleared),
                            Snackbar.LENGTH_SHORT,
                            SnackbarUtils.Type.SUCCESS
                        )
                    }
                }
            }
        }
    }

    private fun playEntrance() {
        val headerIcons = listOf(binding.btnHeaderEdit, binding.btnHeaderCamera)
        val identity = listOf(binding.nameContainer, binding.emailContainer)
        // contentContainer children: index 0 = name, 1 = email — skip them; the rest are
        // alternating section title + card, ending with tvVersion.
        val contentRows = (2 until binding.contentContainer.childCount)
            .map { binding.contentContainer.getChildAt(it) }

        if (hasPlayedEntrance || Motion.reduced(requireContext())) {
            (headerIcons + identity + contentRows + binding.profilePictureContainer)
                .forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }

        headerIcons.forEach { it.primeFadeUp(12f) }
        binding.profilePictureContainer.primeScalePop(startScale = 0.6f)
        identity.forEach { it.primeFadeUp(16f) }
        contentRows.forEach { it.primeScaleFadeUp(translationDp = 20f) }

        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()

            headerIcons.forEachIndexed { i, v ->
                v.entranceFadeUp(delay = i * 60L, duration = Motion.LONG_2)
            }
            binding.profilePictureContainer.entrancePop(delay = 200L, duration = Motion.LONG_2)
            identity.forEachIndexed { i, v ->
                v.entranceFadeUp(delay = 360L + i * 80L, duration = Motion.LONG_2)
            }
            contentRows.forEachIndexed { i, v ->
                v.entranceScaleFadeUp(delay = 540L + i * Motion.STAGGER_GAP, duration = 600L)
            }
            hasPlayedEntrance = true
        }
    }

    private fun applySavedImage(event: ImageSaveResult.Saved) {
        val target = when (event.target) {
            PickTarget.AVATAR -> binding.ivProfilePicture
            PickTarget.HEADER -> binding.ivHeader
        }
        val fallback = when (event.target) {
            PickTarget.AVATAR -> R.drawable.farmer_image
            PickTarget.HEADER -> R.drawable.profile_header
        }
        loadImage(event.absolutePath, target, fallback)
    }

    private fun renderProfile(profile: UserProfile) {
        binding.tvUserName.text = if (profile.userName.isNotBlank()) profile.userName
            else getString(R.string.profile_placeholder_name)
        binding.tvUserEmail.text = if (profile.userEmail.isNotBlank()) profile.userEmail
            else getString(R.string.profile_placeholder_email)

        loadImage(profile.avatarPath, binding.ivProfilePicture, R.drawable.farmer_image)
        loadImage(profile.headerPath, binding.ivHeader, R.drawable.profile_header)
    }

    private fun loadImage(path: String?, target: android.widget.ImageView, fallbackRes: Int) {
        val file = path?.let(::File)?.takeIf(File::exists)
        val key = file?.let { "${it.absolutePath}|${it.lastModified()}" } ?: "fallback:$fallbackRes"

        // Dedupe — repeat emissions of the same profile state must not trigger a reload,
        // otherwise the placeholder flashes between every emission.
        if (target.getTag(R.id.tag_profile_image_key) == key) return
        target.setTag(R.id.tag_profile_image_key, key)

        if (file == null) {
            Glide.with(this).clear(target)
            target.setImageResource(fallbackRes)
            return
        }

        // No .placeholder() — keep the existing bitmap visible while the new one decodes,
        // instead of resetting to the fallback drawable. Signature busts the cache when
        // the underlying file changes. SIZE_ORIGINAL prevents Glide from sampling down to
        // the view's pre-layout size (the header is resized larger by setupAdaptiveHeader
        // *after* this load may have started, which would otherwise leave a blurry bitmap
        // cached under the file signature).
        Glide.with(this)
            .load(file)
            .signature(ObjectKey(file.lastModified()))
            .override(Target.SIZE_ORIGINAL)
            .error(fallbackRes)
            .dontAnimate()
            .into(target)
    }

    private fun setupVersion() {
        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        binding.tvVersion.text = getString(R.string.version, versionName)

        binding.tvVersion.setOnLongClickListener {
            vm.enableDevModeAndResetOnboarding()
            SnackbarUtils.showSnackbar(
                binding.root,
                "\uD83D\uDEE0\uFE0F Dev Mode ON — Onboarding reset. Restart the app to replay.",
                Snackbar.LENGTH_LONG,
                SnackbarUtils.Type.WARNING
            )
            true
        }
    }

    private fun setupInlineEdits() {
        binding.apply {
            wireInlineEdit(
                textView = tvUserName,
                editText = etUserName,
                placeholder = getString(R.string.profile_placeholder_name),
                getCurrent = { vm.profile.value.userName },
                commit = { vm.setName(it) }
            )
            wireInlineEdit(
                textView = tvUserEmail,
                editText = etUserEmail,
                placeholder = getString(R.string.profile_placeholder_email),
                getCurrent = { vm.profile.value.userEmail },
                commit = { vm.setEmail(it) }
            )
        }
    }

    private fun wireInlineEdit(
        textView: TextView,
        editText: EditText,
        placeholder: String,
        getCurrent: () -> String,
        commit: (String) -> Unit
    ) {
        textView.setOnClickListener { morphToEdit(textView, editText, getCurrent()) }

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitInlineEdit(textView, editText, placeholder, commit)
        }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                editText.clearFocus()
                true
            } else false
        }
    }

    private fun morphToEdit(textView: TextView, editText: EditText, currentValue: String) {
        editText.setText(currentValue)
        editText.setSelection(editText.text?.length ?: 0)

        editText.alpha = 0f
        editText.visibility = View.VISIBLE
        editText.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        textView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        editText.animate()
            .alpha(1f)
            .setDuration(150)
            .withEndAction { editText.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()

        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.visibility = View.INVISIBLE
                textView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            .start()

        editText.requestFocus()
        editText.post { showKeyboard(editText) }
    }

    private fun commitInlineEdit(
        textView: TextView,
        editText: EditText,
        placeholder: String,
        commit: (String) -> Unit
    ) {
        val value = editText.text?.toString()?.trim().orEmpty()
        commit(value)
        textView.text = if (value.isNotBlank()) value else placeholder

        hideKeyboard(editText)

        textView.alpha = 0f
        textView.visibility = View.VISIBLE
        textView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        editText.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        textView.animate()
            .alpha(1f)
            .setDuration(150)
            .withEndAction { textView.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()

        editText.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                editText.visibility = View.INVISIBLE
                editText.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            .start()
    }

    private fun setupTapOutsideToCommit() {
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val focused = listOf(binding.etUserName, binding.etUserEmail)
                    .firstOrNull { it.hasFocus() }
                if (focused != null) {
                    val rect = android.graphics.Rect()
                    focused.getGlobalVisibleRect(rect)
                    if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        focused.clearFocus()
                    }
                }
            }
            false
        }
    }

    private fun showKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun launchPicker(target: PickTarget) {
        vm.launchPicker(target)
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun setupClickListeners() {
        binding.apply {
            btnHeaderEdit.setOnClickListener { launchPicker(PickTarget.HEADER) }
            btnHeaderCamera.setOnClickListener { launchPicker(PickTarget.AVATAR) }
            btnProfileCamera.setOnClickListener { launchPicker(PickTarget.AVATAR) }

            rowClearHistory.setOnClickListener { confirmClearHistory() }
            rowTerms.setOnClickListener { openUrl(URL_TERMS) }
            rowPrivacy.setOnClickListener { openUrl(URL_PRIVACY) }
            rowHelp.setOnClickListener { openHelpMail() }

            listOf(
                btnHeaderEdit, btnHeaderCamera, btnProfileCamera,
                rowClearHistory, rowTerms, rowPrivacy, rowHelp,
            ).forEach { it.bounceOnPress() }
        }
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_LeafBloom_Dialog)
            .setTitle(R.string.clear_history_dialog_title)
            .setMessage(R.string.clear_history_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_history_dialog_confirm) { _, _ -> vm.clearHistory() }
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { /* no-op */ }
    }

    private fun openHelpMail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "LeafBloom – Help")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) { /* no mail app */ }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val URL_TERMS = "https://gitpulse.shayxo.dev/terms"
        private const val URL_PRIVACY = "https://gitpulse.shayxo.dev/privacy"
        private const val SUPPORT_EMAIL = "hello@shayxo.dev"
    }
}
