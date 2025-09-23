package com.steve1316.uma_android_automation.ui.monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.steve1316.uma_android_automation.R
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class DecisionLogAdapter(
    private var decisions: List<TrainingDecision>
) : RecyclerView.Adapter<DecisionLogAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.decision_card)
        val timestampText: TextView = view.findViewById(R.id.timestamp_text)
        val actionText: TextView = view.findViewById(R.id.action_text)
        val reasonText: TextView = view.findViewById(R.id.reason_text)
        val gainsText: TextView = view.findViewById(R.id.gains_text)
        val scoreText: TextView = view.findViewById(R.id.score_text)
        val typeIndicator: View = view.findViewById(R.id.type_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_decision_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val decision = decisions[position]

        holder.timestampText.text = dateFormat.format(Date(decision.timestamp))
        holder.actionText.text = decision.action
        holder.reasonText.text = decision.reason
        holder.gainsText.text = decision.statGains

        // Show score only for training decisions
        if (decision.score > 0) {
            holder.scoreText.visibility = View.VISIBLE
            holder.scoreText.text = "Score: ${String.format("%.1f", decision.score)}"
        } else {
            holder.scoreText.visibility = View.GONE
        }

        // Color code the type indicator
        val indicatorColor = when (decision.type) {
            "Training" -> when {
                decision.action.contains("Speed") -> R.color.uma_speed
                decision.action.contains("Stamina") -> R.color.uma_stamina
                decision.action.contains("Power") -> R.color.uma_power
                decision.action.contains("Guts") -> R.color.uma_guts
                decision.action.contains("Wit") -> R.color.uma_wit
                else -> R.color.md_grey_500
            }
            "Rest" -> R.color.md_blue_500
            "Event" -> R.color.md_purple_500
            "Race" -> R.color.md_orange_500
            "Skill" -> R.color.md_green_500
            else -> R.color.md_grey_500
        }

        holder.typeIndicator.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, indicatorColor)
        )

        // Highlight important decisions
        if (decision.reason.contains("critical", ignoreCase = true) ||
            decision.reason.contains("important", ignoreCase = true)) {
            holder.card.strokeColor = ContextCompat.getColor(
                holder.itemView.context,
                R.color.md_red_500
            )
            holder.card.strokeWidth = 2
        } else {
            holder.card.strokeWidth = 0
        }
    }

    override fun getItemCount() = decisions.size

    fun updateData(newDecisions: List<TrainingDecision>) {
        decisions = newDecisions
        notifyDataSetChanged()
    }
}