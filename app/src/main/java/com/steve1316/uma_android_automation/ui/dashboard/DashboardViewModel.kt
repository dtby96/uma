package com.steve1316.uma_android_automation.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DashboardViewModel : ViewModel() {

    private val _currentStats = MutableLiveData<TrainingStats>()
    val currentStats: LiveData<TrainingStats> = _currentStats

    private val _trainingProgress = MutableLiveData<List<TrainingProgress>>()
    val trainingProgress: LiveData<List<TrainingProgress>> = _trainingProgress

    private val _botStatus = MutableLiveData<BotStatus>()
    val botStatus: LiveData<BotStatus> = _botStatus

    private val _energyLevel = MutableLiveData<Int>()
    val energyLevel: LiveData<Int> = _energyLevel

    init {
        // Initialize with default values
        _botStatus.value = BotStatus(BotState.STOPPED, "Bot is not running", "Idle")
        _energyLevel.value = 100
        _trainingProgress.value = emptyList()
    }

    fun updateCurrentStats(stats: TrainingStats) {
        _currentStats.value = stats
    }

    fun updateBotStatus(status: BotStatus) {
        _botStatus.value = status
    }

    fun updateEnergyLevel(energy: Int) {
        _energyLevel.value = energy.coerceIn(0, 100)
    }

    fun addTrainingProgress(progress: TrainingProgress) {
        val currentList = _trainingProgress.value?.toMutableList() ?: mutableListOf()
        currentList.add(progress)
        _trainingProgress.value = currentList
    }

    fun clearTrainingProgress() {
        _trainingProgress.value = emptyList()
    }
}