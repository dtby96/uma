package com.steve1316.uma_android_automation.ui.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.steve1316.uma_android_automation.R
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class TrainingMonitorFragment : Fragment() {

    private lateinit var rootView: View
    private lateinit var decisionLogRecycler: RecyclerView
    private lateinit var decisionLogAdapter: DecisionLogAdapter
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var currentTrainingCard: MaterialCardView
    private lateinit var currentTrainingText: TextView
    private lateinit var failureRateText: TextView
    private lateinit var energyStatusText: TextView
    private lateinit var moodStatusText: TextView
    private lateinit var phaseText: TextView

    private val decisionLogs = mutableListOf<TrainingDecision>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_training_monitor, container, false)

        initializeViews()
        setupRecyclerView()
        setupFilters()
        loadMockData() // Replace with real data later

        return rootView
    }

    private fun initializeViews() {
        decisionLogRecycler = rootView.findViewById(R.id.decision_log_recycler)
        filterChipGroup = rootView.findViewById(R.id.filter_chip_group)
        currentTrainingCard = rootView.findViewById(R.id.current_training_card)
        currentTrainingText = rootView.findViewById(R.id.current_training_text)
        failureRateText = rootView.findViewById(R.id.failure_rate_text)
        energyStatusText = rootView.findViewById(R.id.energy_status_text)
        moodStatusText = rootView.findViewById(R.id.mood_status_text)
        phaseText = rootView.findViewById(R.id.phase_text)
    }

    private fun setupRecyclerView() {
        decisionLogAdapter = DecisionLogAdapter(decisionLogs)
        decisionLogRecycler.apply {
            layoutManager = LinearLayoutManager(context).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            adapter = decisionLogAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupFilters() {
        val filterOptions = listOf("All", "Training", "Rest", "Event", "Race", "Skill")

        filterOptions.forEach { filter ->
            val chip = Chip(context).apply {
                text = filter
                isCheckable = true
                isChecked = filter == "All"
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        filterDecisions(filter)
                    }
                }
            }
            filterChipGroup.addView(chip)
        }
    }

    private fun filterDecisions(filter: String) {
        val filtered = when (filter) {
            "All" -> decisionLogs
            else -> decisionLogs.filter { it.type.equals(filter, ignoreCase = true) }
        }
        decisionLogAdapter.updateData(filtered)
    }

    fun addDecision(decision: TrainingDecision) {
        decisionLogs.add(decision)
        decisionLogAdapter.notifyItemInserted(decisionLogs.size - 1)
        decisionLogRecycler.smoothScrollToPosition(decisionLogs.size - 1)
    }

    fun updateCurrentStatus(status: TrainingStatus) {
        currentTrainingText.text = "Current: ${status.currentAction}"
        failureRateText.text = "Failure: ${status.failureRate}%"
        energyStatusText.text = "Energy: ${status.energy}%"
        moodStatusText.text = "Mood: ${status.mood}"
        phaseText.text = "Phase: ${status.phase}"

        // Update card color based on action
        val cardColor = when (status.currentAction) {
            "Speed Training" -> ContextCompat.getColor(requireContext(), R.color.uma_speed_light)
            "Stamina Training" -> ContextCompat.getColor(requireContext(), R.color.uma_stamina_light)
            "Power Training" -> ContextCompat.getColor(requireContext(), R.color.uma_power_light)
            "Guts Training" -> ContextCompat.getColor(requireContext(), R.color.uma_guts_light)
            "Wit Training" -> ContextCompat.getColor(requireContext(), R.color.uma_wit_light)
            "Rest" -> ContextCompat.getColor(requireContext(), R.color.md_blue_100)
            else -> ContextCompat.getColor(requireContext(), R.color.white)
        }
        currentTrainingCard.setCardBackgroundColor(cardColor)
    }

    private fun loadMockData() {
        // Add some mock data for demonstration
        val mockDecisions = listOf(
            TrainingDecision(
                timestamp = System.currentTimeMillis(),
                type = "Training",
                action = "Speed Training",
                reason = "Highest value: 45 stats, 2 friendships, 15% failure",
                statGains = "+25 Speed, +10 Power",
                score = 85.5
            ),
            TrainingDecision(
                timestamp = System.currentTimeMillis() - 60000,
                type = "Rest",
                action = "Rest",
                reason = "Energy below 30%, high failure rates",
                statGains = "Energy recovery",
                score = 0.0
            ),
            TrainingDecision(
                timestamp = System.currentTimeMillis() - 120000,
                type = "Event",
                action = "Event Choice",
                reason = "Selected option 2: +20 Speed",
                statGains = "+20 Speed",
                score = 0.0
            )
        )

        mockDecisions.forEach { addDecision(it) }

        // Mock current status
        updateCurrentStatus(
            TrainingStatus(
                currentAction = "Speed Training",
                failureRate = 15,
                energy = 65,
                mood = "Good",
                phase = "Year 2, Month 6"
            )
        )
    }
}

// Data classes
data class TrainingDecision(
    val timestamp: Long,
    val type: String,
    val action: String,
    val reason: String,
    val statGains: String,
    val score: Double
)

data class TrainingStatus(
    val currentAction: String,
    val failureRate: Int,
    val energy: Int,
    val mood: String,
    val phase: String
)