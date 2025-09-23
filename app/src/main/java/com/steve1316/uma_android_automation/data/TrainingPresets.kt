package com.steve1316.uma_android_automation.data

/**
 * Data class for training stat targets
 */
data class StatTargets(
    val speed: Int,
    val stamina: Int,
    val power: Int,
    val guts: Int,
    val wit: Int
)

/**
 * Presets for different distance and running style combinations
 */
object TrainingPresets {
    
    // Distance-based presets (Running style agnostic)
    private val SPRINT_BASE = StatTargets(
        speed = 1000,
        stamina = 300,
        power = 700,
        guts = 400,
        wit = 600
    )
    
    private val MILE_BASE = StatTargets(
        speed = 900,
        stamina = 600,
        power = 700,
        guts = 400,
        wit = 400
    )
    
    private val MEDIUM_BASE = StatTargets(
        speed = 800,
        stamina = 800,
        power = 600,
        guts = 400,
        wit = 400
    )
    
    private val LONG_BASE = StatTargets(
        speed = 600,
        stamina = 1000,
        power = 500,
        guts = 500,
        wit = 400
    )
    
    // Balanced preset for all distances
    private val BALANCED = StatTargets(
        speed = 600,
        stamina = 600,
        power = 600,
        guts = 300,
        wit = 600
    )
    
    /**
     * Get preset based on distance and running style
     * @param distance: Sprint, Mile, Medium, Long
     * @param runningStyle: Nige, Senkou, Sashi, Oikomi
     */
    fun getPreset(distance: String, runningStyle: String): StatTargets {
        val basePreset = when (distance) {
            "Sprint" -> SPRINT_BASE
            "Mile" -> MILE_BASE
            "Medium" -> MEDIUM_BASE
            "Long" -> LONG_BASE
            else -> BALANCED
        }
        
        // Apply running style modifiers
        return when (runningStyle) {
            "Nige" -> {
                // Escape runners need more speed and stamina
                basePreset.copy(
                    speed = (basePreset.speed * 1.1).toInt().coerceAtMost(1200),
                    stamina = (basePreset.stamina * 1.05).toInt().coerceAtMost(1200)
                )
            }
            "Senkou" -> {
                // Leaders need balanced speed and power
                basePreset.copy(
                    speed = (basePreset.speed * 1.05).toInt().coerceAtMost(1200),
                    power = (basePreset.power * 1.05).toInt().coerceAtMost(1200)
                )
            }
            "Sashi" -> {
                // Betweeners need balanced stats with wit focus
                basePreset.copy(
                    wit = (basePreset.wit * 1.15).toInt().coerceAtMost(1200),
                    power = (basePreset.power * 1.05).toInt().coerceAtMost(1200)
                )
            }
            "Oikomi" -> {
                // Chasers need more stamina and power
                basePreset.copy(
                    stamina = (basePreset.stamina * 1.1).toInt().coerceAtMost(1200),
                    power = (basePreset.power * 1.1).toInt().coerceAtMost(1200),
                    speed = (basePreset.speed * 0.95).toInt()
                )
            }
            else -> basePreset
        }
    }
    
    /**
     * Get balanced preset regardless of distance/style
     */
    fun getBalancedPreset(): StatTargets = BALANCED
    
    /**
     * Get default preset for initial setup
     */
    fun getDefaultPreset(): StatTargets = MEDIUM_BASE
}