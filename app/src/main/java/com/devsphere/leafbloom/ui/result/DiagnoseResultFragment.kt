package com.devsphere.leafbloom.ui.result

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.DiseaseCareInfo
import com.devsphere.leafbloom.data.model.DiseaseInfo
import com.devsphere.leafbloom.databinding.FragmentDiagnoseResultBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.animateGauge
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.entrancePop
import com.devsphere.leafbloom.ui.motion.entranceScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.primeScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeScalePop
import com.devsphere.leafbloom.ui.motion.pulseOnce
import com.devsphere.leafbloom.ui.motion.setGaugeInstant
import com.devsphere.leafbloom.ui.motion.snapVisible
import com.devsphere.leafbloom.util.ResultExporter
import com.devsphere.leafbloom.util.SnackbarUtils
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class DiagnoseResultFragment : BaseFragment() {

    private var _binding: FragmentDiagnoseResultBinding? = null
    private val binding get() = _binding!!

    private var pendingSaveAfterPermission = false
    private var hasPlayedEntrance: Boolean = false

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingSaveAfterPermission) {
            performSavePng()
        } else if (!granted) {
            SnackbarUtils.showSnackbar(
                requireView(),
                getString(R.string.storage_permission_required),
                Snackbar.LENGTH_LONG,
                SnackbarUtils.Type.ERROR
            )
        }
        pendingSaveAfterPermission = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnoseResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        applySystemBarInsets(binding.root)
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        binding.actionRescan.setOnClickListener {
            findNavController().navigate(R.id.action_diagnoseResult_to_scanner)
        }
        binding.actionExport.setOnClickListener { exportPdf() }
        binding.actionSave.setOnClickListener { savePng() }

        // Tactile press feedback on all CTAs.
        listOf(
            binding.btnBack,
            binding.actionRescan,
            binding.actionExport,
            binding.actionSave,
            binding.fabChat,
        ).forEach { it.bounceOnPress() }

        // Chat FAB
        binding.fabChat.setOnClickListener {
            val predictedName = arguments?.getString("predicted_class_name", "Unknown") ?: "Unknown"
            val scoreEarly = arguments?.getFloat("score_early_blight", 0f) ?: 0f
            val scoreHealthy = arguments?.getFloat("score_healthy", 0f) ?: 0f
            val scoreLate = arguments?.getFloat("score_late_blight", 0f) ?: 0f
            val scoreSeptoria = arguments?.getFloat("score_septoria", 0f) ?: 0f

            val diseaseInfo = DiseaseInfo.get(predictedName)
            val careInfo = DiseaseCareInfo.get(predictedName)

            val systemPrompt = buildDiseaseSystemPrompt(
                predictedName, scoreEarly, scoreHealthy, scoreLate, scoreSeptoria,
                getString(diseaseInfo.scientificNameRes),
                getString(diseaseInfo.tip1TitleRes), getString(diseaseInfo.tip1DescRes),
                getString(diseaseInfo.tip2TitleRes), getString(diseaseInfo.tip2DescRes),
                careInfo, getString(careInfo.overviewRes),
                getString(careInfo.treatmentRes), getString(careInfo.preventionRes)
            )

            findNavController().navigate(
                R.id.action_diagnoseResult_to_chat,
                bundleOf("system_prompt" to systemPrompt, "context_title" to predictedName)
            )
        }

        // Retrieve data
        val args = arguments
        val scoreEarly = args?.getFloat("score_early_blight", 0f) ?: 0f
        val scoreHealthy = args?.getFloat("score_healthy", 0f) ?: 0f
        val scoreLate = args?.getFloat("score_late_blight", 0f) ?: 0f
        val scoreSeptoria = args?.getFloat("score_septoria", 0f) ?: 0f
        val predictedName = args?.getString("predicted_class_name", "Unknown") ?: "Unknown"

        val diseaseInfo = DiseaseInfo.get(predictedName)
        binding.tvTip1Title.text = getString(diseaseInfo.tip1TitleRes)
        binding.tvTip1Desc.text = getString(diseaseInfo.tip1DescRes)
        binding.tvTip2Title.text = getString(diseaseInfo.tip2TitleRes)
        binding.tvTip2Desc.text = getString(diseaseInfo.tip2DescRes)

        highlightWinner(scoreEarly, binding.tvEarlyBlightPercent)
        highlightWinner(scoreHealthy, binding.tvHealthyPercent)
        highlightWinner(scoreLate, binding.tvLateBlightPercent)
        highlightWinner(scoreSeptoria, binding.tvSeptoriaPercent)

        playEntrance(scoreEarly, scoreHealthy, scoreLate, scoreSeptoria)
    }

    private fun playEntrance(
        scoreEarly: Float,
        scoreHealthy: Float,
        scoreLate: Float,
        scoreSeptoria: Float,
    ) {
        // Back-nav return: snap to final state, skip the choreography + gauge sweep.
        if (hasPlayedEntrance || Motion.reduced(requireContext())) {
            setGaugeInstant(binding.progressEarlyBlight, binding.tvEarlyBlightPercent, scoreEarly)
            setGaugeInstant(binding.progressHealthy, binding.tvHealthyPercent, scoreHealthy)
            setGaugeInstant(binding.progressLateBlight, binding.tvLateBlightPercent, scoreLate)
            setGaugeInstant(binding.progressSeptoria, binding.tvSeptoriaPercent, scoreSeptoria)
            listOf(
                binding.btnBack, binding.tvTitle,
                binding.cardEarlyBlight, binding.cardHealthy,
                binding.cardLateBlight, binding.cardSeptoria,
                binding.tvTipsTitle, binding.cardTips,
                binding.actionBar, binding.fabChat,
            ).forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }

        // Prime initial state (offscreen offsets + faded).
        binding.btnBack.primeFadeUp()
        binding.tvTitle.primeFadeUp()
        binding.cardEarlyBlight.primeScaleFadeUp()
        binding.cardHealthy.primeScaleFadeUp()
        binding.cardLateBlight.primeScaleFadeUp()
        binding.cardSeptoria.primeScaleFadeUp()
        binding.tvTipsTitle.primeFadeUp()
        binding.cardTips.primeFadeUp(20f)
        binding.actionBar.primeFadeUp(40f)
        binding.fabChat.primeScalePop()

        // Wait for the first real frame so the screen has actually settled
        // (avoids losing the start of the animation behind the nav transition jank).
        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()

            // Header
            binding.btnBack.entranceFadeUp(delay = 0L, duration = Motion.LONG_2)
            binding.tvTitle.entranceFadeUp(delay = 80L, duration = Motion.LONG_2)

            // Gauge cards — bigger gap between rows so the eye can follow.
            binding.cardEarlyBlight.entranceScaleFadeUp(delay = 160L, duration = 650L)
            binding.cardHealthy.entranceScaleFadeUp(delay = 280L, duration = 650L)
            binding.cardLateBlight.entranceScaleFadeUp(delay = 400L, duration = 650L)
            binding.cardSeptoria.entranceScaleFadeUp(delay = 520L, duration = 650L)

            // Sweep gauges & count percent — starts after the first card lands,
            // and runs slowly enough to actually watch the numbers climb.
            val gaugeStart = 500L
            val gaugeDuration = 1400L
            animateGauge(
                binding.progressEarlyBlight, binding.tvEarlyBlightPercent, scoreEarly,
                delay = gaugeStart, duration = gaugeDuration,
            )
            animateGauge(
                binding.progressHealthy, binding.tvHealthyPercent, scoreHealthy,
                delay = gaugeStart + 120L, duration = gaugeDuration,
            )
            animateGauge(
                binding.progressLateBlight, binding.tvLateBlightPercent, scoreLate,
                delay = gaugeStart + 240L, duration = gaugeDuration,
            )
            animateGauge(
                binding.progressSeptoria, binding.tvSeptoriaPercent, scoreSeptoria,
                delay = gaugeStart + 360L, duration = gaugeDuration,
            )

            // Tips (after all gauge cards have entered).
            binding.tvTipsTitle.entranceFadeUp(delay = 700L, duration = Motion.LONG_2)
            binding.cardTips.entranceFadeUp(delay = 820L, duration = Motion.LONG_2)

            // Bottom action bar slides up.
            binding.actionBar.entranceFadeUp(delay = 760L, duration = Motion.LONG_2)

            // FAB pops in with overshoot last so it feels like the final flourish.
            binding.fabChat.entrancePop(delay = 1000L, duration = Motion.LONG_2)

            // Pulse the winning card once the gauge fill completes (~gaugeStart + duration).
            pulseWinner(scoreEarly, scoreHealthy, scoreLate, scoreSeptoria, delay = 2100L)
            hasPlayedEntrance = true
        }
    }

    private fun pulseWinner(
        scoreEarly: Float,
        scoreHealthy: Float,
        scoreLate: Float,
        scoreSeptoria: Float,
        delay: Long,
    ) {
        val pairs = listOf(
            scoreEarly to binding.cardEarlyBlight,
            scoreHealthy to binding.cardHealthy,
            scoreLate to binding.cardLateBlight,
            scoreSeptoria to binding.cardSeptoria,
        )
        val winner = pairs.maxByOrNull { it.first } ?: return
        if (winner.first < 0.5f) return
        winner.second.pulseOnce(peakScale = 1.07f, duration = 900L, delay = delay)
    }


    private fun captureScreen(): Bitmap {
        // Must run on main thread — View.draw() is not thread-safe.
        val headerPadPx = (resources.getDimension(R.dimen.space_12) * 2).toInt()
        val headerHeightPx = binding.btnBack.height + headerPadPx
        return ResultExporter.captureResultScreen(
            context = requireContext(),
            contentView = binding.contentContainer,
            titleText = binding.tvTitle.text.toString(),
            titleTextSizePx = binding.tvTitle.textSize,
            titleTypeface = binding.tvTitle.typeface,
            titleTextColor = binding.tvTitle.currentTextColor,
            headerHeightPx = headerHeightPx
        )
    }

    private fun savePng() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingSaveAfterPermission = true
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        performSavePng()
    }

    private fun performSavePng() {
        val raw = captureScreen()
        val paddingPx = resources.getDimensionPixelSize(R.dimen.safe_margin_h)
        val bitmap = ResultExporter.wrapWithPadding(raw, paddingPx, android.graphics.Color.WHITE)
        raw.recycle()
        val ctx = requireContext().applicationContext
        val fileName = buildFileName("png")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ResultExporter.saveBitmapToGallery(ctx, bitmap, fileName)
                    .also { bitmap.recycle() }
            }
            if (_binding == null) return@launch
            result.onSuccess {
                SnackbarUtils.showSnackbar(
                    requireView(), getString(R.string.save_success),
                    Snackbar.LENGTH_SHORT, SnackbarUtils.Type.INFO
                )
            }.onFailure {
                SnackbarUtils.showSnackbar(
                    requireView(), getString(R.string.save_failed),
                    Snackbar.LENGTH_LONG, SnackbarUtils.Type.ERROR
                )
            }
        }
    }

    private fun exportPdf() {
        val bitmap = captureScreen()          // main thread — safe
        val ctx = requireContext().applicationContext
        val fileName = buildFileName("pdf")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ResultExporter.renderBitmapToPdf(ctx, bitmap, fileName)
                    .also { bitmap.recycle() }
            }
            if (_binding == null) return@launch
            result.onSuccess { file ->
                val intent = ResultExporter.buildShareIntent(
                    requireContext(), file, "application/pdf",
                    getString(R.string.share_pdf_subject),
                    getString(R.string.share_pdf_chooser)
                )
                startActivity(intent)
            }.onFailure {
                SnackbarUtils.showSnackbar(
                    requireView(), getString(R.string.export_failed),
                    Snackbar.LENGTH_LONG, SnackbarUtils.Type.ERROR
                )
            }
        }
    }

    private fun buildFileName(ext: String): String {
        val predicted = arguments?.getString("predicted_class_name", "leafbloom") ?: "leafbloom"
        val safe = predicted.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "leafbloom_${safe}_$ts.$ext"
    }

    private fun highlightWinner(score: Float, textView: android.widget.TextView?) {
        if (textView == null) return
        if (score >= 0.5f) {
            textView.setTypeface(null, android.graphics.Typeface.BOLD)
            textView.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_primary)
            )
        } else {
            textView.setTypeface(null, android.graphics.Typeface.NORMAL)
            textView.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            )
        }
    }

    private fun buildDiseaseSystemPrompt(
        predictedName: String,
        scoreEarly: Float, scoreHealthy: Float, scoreLate: Float, scoreSeptoria: Float,
        scientificName: String,
        tip1Title: String, tip1Desc: String,
        tip2Title: String, tip2Desc: String,
        careInfo: DiseaseCareInfo,
        overview: String, treatment: String, prevention: String
    ): String = """
You are LeafBloom AI, a friendly plant health assistant inside the LeafBloom app.
The user just scanned a tomato leaf and the AI model detected: "$predictedName" (scientific name: $scientificName).

Detection confidence scores:
- Early Blight: ${(scoreEarly * 100).roundToInt()}%
- Healthy: ${(scoreHealthy * 100).roundToInt()}%
- Late Blight: ${(scoreLate * 100).roundToInt()}%
- Septoria: ${(scoreSeptoria * 100).roundToInt()}%

Disease overview: $overview
Treatment advice: $treatment
Prevention tips: $prevention
Recommended care: Water=${careInfo.water}, Sunlight=${careInfo.sunlight}, Fertilizer=${careInfo.fertilizer}, Humidity=${careInfo.humidity}
Key tips: $tip1Title - $tip1Desc | $tip2Title - $tip2Desc

Guidelines:
- Answer in the SAME LANGUAGE the user writes in.
- Match response length to the question: keep casual chat and simple questions short (1-2 sentences), and only go longer when the user asks for details, steps, or a full explanation. Never pad replies.
- Focus on practical, actionable advice for home gardeners.
- If the plant is healthy, focus on maintenance tips.
- You may suggest organic or chemical treatments available in local markets.
- Do not diagnose new diseases from text descriptions alone — recommend another scan.
    """.trimIndent()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
