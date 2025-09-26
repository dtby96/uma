package com.steve1316.uma_android_automation.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.steve1316.uma_android_automation.R
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter


class DashboardFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var rootView: View

    // UI Components
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var currentPhaseText: TextView
    private lateinit var energyIndicator: CircularProgressIndicator
    private lateinit var energyText: TextView

    // Stat visualization
    private lateinit var statRadarChart: RadarChart
    private lateinit var progressLineChart: LineChart

    // Progress cards
    private lateinit var speedProgressCard: MaterialCardView
    private lateinit var speedProgress: CircularProgressIndicator
    private lateinit var speedValueText: TextView
    private lateinit var staminaProgressCard: MaterialCardView
    private lateinit var staminaProgress: CircularProgressIndicator
    private lateinit var staminaValueText: TextView
    private lateinit var powerProgressCard: MaterialCardView
    private lateinit var powerProgress: CircularProgressIndicator
    private lateinit var powerValueText: TextView
    private lateinit var gutsProgressCard: MaterialCardView
    private lateinit var gutsProgress: CircularProgressIndicator
    private lateinit var gutsValueText: TextView
    private lateinit var witProgressCard: MaterialCardView
    private lateinit var witProgress: CircularProgressIndicator
    private lateinit var witValueText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_dashboard, container, false)

        viewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)

        initializeViews()
        setupCharts()
        observeViewModel()
        loadCurrentStats()

        return rootView
    }

    private fun initializeViews() {
        // Status card
        statusCard = rootView.findViewById(R.id.status_card)
        statusText = rootView.findViewById(R.id.status_text)
        currentPhaseText = rootView.findViewById(R.id.current_phase_text)
        energyIndicator = rootView.findViewById(R.id.energy_indicator)
        energyText = rootView.findViewById(R.id.energy_text)

        // Charts
        statRadarChart = rootView.findViewById(R.id.stat_radar_chart)
        progressLineChart = rootView.findViewById(R.id.progress_line_chart)

        // Progress cards for each stat
        speedProgressCard = rootView.findViewById(R.id.speed_progress_card)
        speedProgress = rootView.findViewById(R.id.speed_progress)
        speedValueText = rootView.findViewById(R.id.speed_value_text)

        staminaProgressCard = rootView.findViewById(R.id.stamina_progress_card)
        staminaProgress = rootView.findViewById(R.id.stamina_progress)
        staminaValueText = rootView.findViewById(R.id.stamina_value_text)

        powerProgressCard = rootView.findViewById(R.id.power_progress_card)
        powerProgress = rootView.findViewById(R.id.power_progress)
        powerValueText = rootView.findViewById(R.id.power_value_text)

        gutsProgressCard = rootView.findViewById(R.id.guts_progress_card)
        gutsProgress = rootView.findViewById(R.id.guts_progress)
        gutsValueText = rootView.findViewById(R.id.guts_value_text)

        witProgressCard = rootView.findViewById(R.id.wit_progress_card)
        witProgress = rootView.findViewById(R.id.wit_progress)
        witValueText = rootView.findViewById(R.id.wit_value_text)
    }

    private fun setupCharts() {
        setupRadarChart()
        setupLineChart()
    }

    private fun setupRadarChart() {
        statRadarChart.apply {
            description.isEnabled = false
            webLineWidth = 1f
            webColor = ContextCompat.getColor(requireContext(), R.color.md_grey_400)
            webLineWidthInner = 1f
            webColorInner = ContextCompat.getColor(requireContext(), R.color.md_grey_300)
            webAlpha = 100
            setTouchEnabled(true)

            // Configure X axis (stat labels)
            xAxis.apply {
                textSize = 12f
                yOffset = 0f
                xOffset = 0f
                valueFormatter = IndexAxisValueFormatter(arrayOf("Speed", "Stamina", "Power", "Guts", "Wit"))
                textColor = ContextCompat.getColor(requireContext(), R.color.md_grey_700)
            }

            // Configure Y axis (stat values)
            yAxis.apply {
                setLabelCount(5, false)
                textSize = 9f
                setStartAtZero(true)
                axisMinimum = 0f
                axisMaximum = 1200f
                setDrawLabels(false)
            }

            legend.isEnabled = true
            animateXY(1000, 1000)
        }
    }

    private fun setupLineChart() {
        progressLineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // Configure X axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textSize = 10f
                setDrawGridLines(false)
                setDrawAxisLine(true)
            }

            // Configure Y axis
            axisLeft.apply {
                textSize = 10f
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 1200f
            }

            axisRight.isEnabled = false
            legend.isEnabled = true
            animateX(1000)
        }
    }

    private fun observeViewModel() {
        // Observe current stats
        viewModel.currentStats.observe(viewLifecycleOwner) { stats ->
            updateStatProgress(stats)
            updateRadarChart(stats)
        }

        // Observe training progress
        viewModel.trainingProgress.observe(viewLifecycleOwner) { progress ->
            updateLineChart(progress)
        }

        // Observe bot status
        viewModel.botStatus.observe(viewLifecycleOwner) { status ->
            updateStatusCard(status)
        }

        // Observe energy level
        viewModel.energyLevel.observe(viewLifecycleOwner) { energy ->
            updateEnergyIndicator(energy)
        }
    }

    private fun loadCurrentStats() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val stats = TrainingStats(
            speed = prefs.getInt("stat_speed", 0),
            stamina = prefs.getInt("stat_stamina", 0),
            power = prefs.getInt("stat_power", 0),
            guts = prefs.getInt("stat_guts", 0),
            wit = prefs.getInt("stat_wit", 0),
            speedTarget = prefs.getInt("speed_target", 600),
            staminaTarget = prefs.getInt("stamina_target", 600),
            powerTarget = prefs.getInt("power_target", 600),
            gutsTarget = prefs.getInt("guts_target", 300),
            witTarget = prefs.getInt("wit_target", 600)
        )
        viewModel.updateCurrentStats(stats)
    }

    private fun updateStatProgress(stats: TrainingStats) {
        // Update Speed
        speedProgress.progress = ((stats.speed.toFloat() / stats.speedTarget) * 100).toInt()
        speedValueText.text = "${stats.speed}/${stats.speedTarget}"
        speedProgressCard.setCardBackgroundColor(
            if (stats.speed >= stats.speedTarget)
                ContextCompat.getColor(requireContext(), R.color.md_green_100)
            else
                ContextCompat.getColor(requireContext(), R.color.white)
        )

        // Update Stamina
        staminaProgress.progress = ((stats.stamina.toFloat() / stats.staminaTarget) * 100).toInt()
        staminaValueText.text = "${stats.stamina}/${stats.staminaTarget}"
        staminaProgressCard.setCardBackgroundColor(
            if (stats.stamina >= stats.staminaTarget)
                ContextCompat.getColor(requireContext(), R.color.md_green_100)
            else
                ContextCompat.getColor(requireContext(), R.color.white)
        )

        // Update Power
        powerProgress.progress = ((stats.power.toFloat() / stats.powerTarget) * 100).toInt()
        powerValueText.text = "${stats.power}/${stats.powerTarget}"
        powerProgressCard.setCardBackgroundColor(
            if (stats.power >= stats.powerTarget)
                ContextCompat.getColor(requireContext(), R.color.md_green_100)
            else
                ContextCompat.getColor(requireContext(), R.color.white)
        )

        // Update Guts
        gutsProgress.progress = ((stats.guts.toFloat() / stats.gutsTarget) * 100).toInt()
        gutsValueText.text = "${stats.guts}/${stats.gutsTarget}"
        gutsProgressCard.setCardBackgroundColor(
            if (stats.guts >= stats.gutsTarget)
                ContextCompat.getColor(requireContext(), R.color.md_green_100)
            else
                ContextCompat.getColor(requireContext(), R.color.white)
        )

        // Update Wit
        witProgress.progress = ((stats.wit.toFloat() / stats.witTarget) * 100).toInt()
        witValueText.text = "${stats.wit}/${stats.witTarget}"
        witProgressCard.setCardBackgroundColor(
            if (stats.wit >= stats.witTarget)
                ContextCompat.getColor(requireContext(), R.color.md_green_100)
            else
                ContextCompat.getColor(requireContext(), R.color.white)
        )
    }

    private fun updateRadarChart(stats: TrainingStats) {
        val entries = ArrayList<RadarEntry>()
        entries.add(RadarEntry(stats.speed.toFloat()))
        entries.add(RadarEntry(stats.stamina.toFloat()))
        entries.add(RadarEntry(stats.power.toFloat()))
        entries.add(RadarEntry(stats.guts.toFloat()))
        entries.add(RadarEntry(stats.wit.toFloat()))

        val currentDataSet = RadarDataSet(entries, "Current Stats")
        currentDataSet.color = ContextCompat.getColor(requireContext(), R.color.uma_primary)
        currentDataSet.fillColor = ContextCompat.getColor(requireContext(), R.color.uma_primary_light)
        currentDataSet.setDrawFilled(true)
        currentDataSet.fillAlpha = 80
        currentDataSet.lineWidth = 2f
        currentDataSet.isDrawHighlightCircleEnabled = true
        currentDataSet.setDrawHighlightIndicators(false)

        // Add target stats
        val targetEntries = ArrayList<RadarEntry>()
        targetEntries.add(RadarEntry(stats.speedTarget.toFloat()))
        targetEntries.add(RadarEntry(stats.staminaTarget.toFloat()))
        targetEntries.add(RadarEntry(stats.powerTarget.toFloat()))
        targetEntries.add(RadarEntry(stats.gutsTarget.toFloat()))
        targetEntries.add(RadarEntry(stats.witTarget.toFloat()))

        val targetDataSet = RadarDataSet(targetEntries, "Target Stats")
        targetDataSet.color = ContextCompat.getColor(requireContext(), R.color.md_grey_400)
        targetDataSet.fillColor = ContextCompat.getColor(requireContext(), R.color.md_grey_200)
        targetDataSet.setDrawFilled(false)
        targetDataSet.lineWidth = 1f
        targetDataSet.enableDashedHighlightLine(10f, 5f, 0f)

        val dataSets = ArrayList<IRadarDataSet>()
        dataSets.add(currentDataSet)
        dataSets.add(targetDataSet)

        val data = RadarData(dataSets)
        data.setValueTextSize(8f)
        data.setDrawValues(true)

        statRadarChart.data = data
        statRadarChart.invalidate()
    }

    private fun updateLineChart(progress: List<TrainingProgress>) {
        // Implementation for progress over time chart
        // This would show how stats have progressed during the training session
    }

    private fun updateStatusCard(status: BotStatus) {
        statusText.text = status.message
        currentPhaseText.text = "Phase: ${status.currentPhase}"

        statusCard.setCardBackgroundColor(
            when (status.state) {
                BotState.RUNNING -> ContextCompat.getColor(requireContext(), R.color.md_green_100)
                BotState.PAUSED -> ContextCompat.getColor(requireContext(), R.color.md_yellow_100)
                BotState.STOPPED -> ContextCompat.getColor(requireContext(), R.color.md_grey_100)
                BotState.ERROR -> ContextCompat.getColor(requireContext(), R.color.md_red_100)
            }
        )
    }

    private fun updateEnergyIndicator(energy: Int) {
        energyIndicator.progress = energy
        energyText.text = "$energy%"

        energyIndicator.setIndicatorColor(
            when {
                energy >= 70 -> ContextCompat.getColor(requireContext(), R.color.md_green_500)
                energy >= 40 -> ContextCompat.getColor(requireContext(), R.color.md_yellow_700)
                else -> ContextCompat.getColor(requireContext(), R.color.md_red_500)
            }
        )
    }
}


// Data classes
data class TrainingStats(
    val speed: Int,
    val stamina: Int,
    val power: Int,
    val guts: Int,
    val wit: Int,
    val speedTarget: Int,
    val staminaTarget: Int,
    val powerTarget: Int,
    val gutsTarget: Int,
    val witTarget: Int
)

data class TrainingProgress(
    val turn: Int,
    val speed: Int,
    val stamina: Int,
    val power: Int,
    val guts: Int,
    val wit: Int
)

data class BotStatus(
    val state: BotState,
    val message: String,
    val currentPhase: String
)

enum class BotState {
    RUNNING,
    PAUSED,
    STOPPED,
    ERROR
}