package com.steve1316.uma_android_automation.bot

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.campaigns.AoHaru
import com.steve1316.uma_android_automation.utils.BotService
import com.steve1316.uma_android_automation.utils.ImageUtils
import com.steve1316.uma_android_automation.utils.MediaProjectionService
import com.steve1316.uma_android_automation.utils.MessageLog
import com.steve1316.uma_android_automation.utils.MyAccessibilityService
import com.steve1316.uma_android_automation.utils.SettingsPrinter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.opencv.core.Point
import java.text.DecimalFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.intArrayOf

/**
 * Main driver for bot activity and navigation.
 */
class Game(val myContext: Context) {
	private val tag: String = "[${MainActivity.loggerTag}]Game"
	var notificationMessage: String = ""
	private val decimalFormat = DecimalFormat("#.##")
	val imageUtils: ImageUtils = ImageUtils(myContext, this)
	val gestureUtils: MyAccessibilityService = MyAccessibilityService.getInstance()
	private val textDetection: TextDetection = TextDetection(this, imageUtils)

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// SharedPreferences
	private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(myContext)
	private val campaign: String = sharedPreferences.getString("campaign", "")!!
	private val debugMode: Boolean = sharedPreferences.getBoolean("debugMode", false)

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Training
	private val trainings: List<String> = listOf("Speed", "Stamina", "Power", "Guts", "Wit")
	private val trainingMap: MutableMap<String, Training> = mutableMapOf()
	private var currentStatsMap: MutableMap<String, Int> = mutableMapOf(
		"Speed" to 0,
		"Stamina" to 0,
		"Power" to 0,
		"Guts" to 0,
		"Wit" to 0
	)

	// Actual energy costs for Global server URA Finale scenario
	private val trainingEnergyCosts = mapOf(
		"Speed" to 21,    // -21 energy
		"Stamina" to 19,  // -19 energy (lowest cost)
		"Power" to 20,    // -20 energy
		"Guts" to 22,     // -22 energy (highest cost)
		"Wit" to -5       // +5 energy (recovers energy)
	)

	// Base training stats at Level 1 for Global server
	data class BaseTrainingStats(
		val speed: Int = 0,
		val stamina: Int = 0,
		val power: Int = 0,
		val guts: Int = 0,
		val wit: Int = 0,
		val sp: Int = 0
	)

	private val baseTrainingStats = mapOf(
		"Speed" to BaseTrainingStats(speed=10, power=5, sp=2),
		"Stamina" to BaseTrainingStats(stamina=9, guts=4, sp=2),
		"Power" to BaseTrainingStats(stamina=5, power=8, sp=2),
		"Guts" to BaseTrainingStats(speed=4, power=4, guts=8, sp=2),
		"Wit" to BaseTrainingStats(speed=2, wit=9, sp=4)
	)
	
	// Track current conditions (good and bad)
	private var currentConditions: MutableList<String> = mutableListOf()
	private var currentFans: Int = 0
	private var currentDistance: String = "Medium" // Default to Medium distance
	private val blacklist: List<String> = sharedPreferences.getStringSet("trainingBlacklist", setOf())!!.toList()
	// Auto-calculated stat prioritization based on target values
	private var statPrioritization: List<String> = sharedPreferences.getString("statPrioritization", "Speed|Stamina|Power|Guts|Wit")!!.split("|")
	private val enablePrioritizeEnergyOptions: Boolean = sharedPreferences.getBoolean("enablePrioritizeEnergyOptions", false)
	private val maximumFailureChance: Int = sharedPreferences.getInt("maximumFailureChance", 15)
	private val disableTrainingOnMaxedStat: Boolean = sharedPreferences.getBoolean("disableTrainingOnMaxedStat", true)
	private val focusOnSparkStatTarget: Boolean = sharedPreferences.getBoolean("focusOnSparkStatTarget", false)
	private var statTargets: IntArray = intArrayOf(0, 0, 0, 0, 0)
	private var runningStyle: String = ""
	private var firstTrainingCheck = true
	// Dynamic stat cap based on character and supports (can be adjusted)
	private val baseStatCap = 1200
	private val currentStatCap = baseStatCap  // Could be modified based on supports/character
	private val historicalTrainingCounts: MutableMap<String, Int> = mutableMapOf()

	// Loop prevention and training history
	private val recentTrainings = mutableListOf<String>()  // Track last 5 trainings
	private val maxRecentHistory = 5
	private var consecutiveSameTraining = 0
	private var lastTrainingName = ""
	private val maxConsecutiveSame = 3  // Prevent doing same training more than 3 times in a row

	// Training level tracking (each stat starts at Lv1, needs 4 trainings to level up, max Lv5)
	private val trainingLevels: MutableMap<String, Int> = mutableMapOf(
		"Speed" to 1, "Stamina" to 1, "Power" to 1, "Guts" to 1, "Wit" to 1
	)
	private val trainingCountForLevel: MutableMap<String, Int> = mutableMapOf(
		"Speed" to 0, "Stamina" to 0, "Power" to 0, "Guts" to 0, "Wit" to 0
	)
	private val trainingsPerLevel = 4  // Need 4 trainings to level up
	private val maxTrainingLevel = 5   // Maximum level is 5
	private val absoluteStatCap = 1200 // Stats beyond 1200 give 0 gains

	// Level multipliers - URA Finale training levels increase every 4 uses
	// These are approximate multipliers based on community testing
	private val levelMultipliers = mapOf(
		1 to 1.0,   // Base value
		2 to 1.15,  // +15% stat gains
		3 to 1.35,  // +35% stat gains
		4 to 1.55,  // +55% stat gains
		5 to 1.75   // +75% stat gains (Lv5 is equivalent to summer camp)
	)

	// Thompson Sampling tracking
	private val trainingSuccessHistory: MutableMap<String, Pair<Int, Int>> = mutableMapOf() // (successes, total attempts)
	private val trainingValueHistory: MutableMap<String, Double> = mutableMapOf() // Average value per training
	private var totalTrainingsDone = 0

	// Support Card Friendship Tracking (0-100% for each card)
	private val supportFriendships: MutableMap<String, Int> = mutableMapOf()
	private val friendshipGainPerTraining = 4 // Average gain per training together
	private val maxFriendship = 100

	// Skill Point Tracking and Warnings
	private var currentSkillPoints = 0
	private var lastSkillPointCheck = 0 // Turn number of last check
	private val skillPointWarningThresholds = mapOf(
		"Year 2 Mid" to 200,  // Should have 200+ by mid Year 2
		"Year 3 Start" to 400, // Should have 400+ by Year 3
		"Pre-URA" to 600      // Should have 600+ before URA Finals
	)

	// Bad Condition Management (Disabled - needs image assets)
	private val badConditions = mutableSetOf<String>()
	private val knownBadConditions = listOf(
		"practice_poor",   // 練習下手 - Training failure rate +2%
		"night_owl",       // 夜ふかし - Random -10 energy
		"slow_metabolism", // Speed stat cannot increase
		"slacker",         // なまけ癖 - May skip training
		"migraine",        // 頭痛 - Mood cannot increase
		"dry_skin"         // Random mood decrease
	)

	// Algorithm parameters
	private val discountFactor = 0.9  // Future value discount

	// Character race calendar data structure
	data class CharacterRace(
		val name: String,
		val turn: Int,         // Turn number (1-72)
		val month: Int,        // Month (1-12)
		val phase: String,     // "Early" or "Late"
		val grade: String,     // "Debut", "G3", "G2", "G1"
		val required: Boolean, // Is this a mandatory goal race?
		val placement: Int     // Required placement (1=1st, 3=3rd or better, 5=5th or better)
	)

	// Example: Grass Wonder's goal races
	// This should eventually be loaded from characters.json or scraped from GameTora
	private val grassWonderRaces = listOf(
		CharacterRace("Make Debut", 12, 6, "Late", "Debut", true, 99),
		CharacterRace("Asahi Hai Futurity", 23, 12, "Early", "G1", true, 5),
		CharacterRace("Tokyo Yushun", 34, 5, "Late", "G1", true, 5),
		CharacterRace("Japan Cup", 46, 11, "Late", "G1", true, 5),
		CharacterRace("Arima Kinen", 48, 12, "Late", "G1", true, 3),
		CharacterRace("Takarazuka Kinen", 60, 6, "Late", "G1", true, 3),
		CharacterRace("Mainichi Okan", 67, 10, "Early", "G2", true, 1),
		CharacterRace("Arima Kinen", 72, 12, "Late", "G1", true, 1)
	)

	// URA Finals milestones
	private val uraFinalesMilestones = mapOf(
		"valentine" to Pair(60000, 38),    // Early Feb Year 3
		"april" to Pair(70000, 40),         // Early April Year 3
		"christmas" to Pair(120000, 72)     // Late Dec Year 3
	)
	private val ucbExplorationParam = 1.4  // UCB1 exploration parameter
	private val thompsonExplorationWeight = 0.3  // Weight for exploration vs exploitation

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Racing
	private val enableFarmingFans = sharedPreferences.getBoolean("enableFarmingFans", false)
	private val daysToRunExtraRaces: Int = sharedPreferences.getInt("daysToRunExtraRaces", 4)
	private val disableRaceRetries: Boolean = sharedPreferences.getBoolean("disableRaceRetries", false)
	val enableForceRacing = sharedPreferences.getBoolean("enableForceRacing", false)
	private var raceRetries = 3
	private var raceRepeatWarningCheck = false
	var encounteredRacingPopup = false
	var skipRacing = false

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Stops
	val enableSkillPointCheck: Boolean = sharedPreferences.getBoolean("enableSkillPointCheck", false)
	val skillPointsRequired: Int = sharedPreferences.getInt("skillPointCheck", 750)
	private val enablePopupCheck: Boolean = sharedPreferences.getBoolean("enablePopupCheck", false)
	private val enableStopOnMandatoryRace: Boolean = sharedPreferences.getBoolean("enableStopOnMandatoryRace", false)
	var detectedMandatoryRaceCheck = false

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Misc
	internal var currentDate: Date = Date(1, "Early", 1, 1)
	private var inheritancesDone = 0
	private val startTime: Long = System.currentTimeMillis()

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	data class Training(
		val name: String,
		val statGains: IntArray,
		val failureChance: Int,
		val relationshipBars: ArrayList<ImageUtils.BarFillResult>
	) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Training

            if (failureChance != other.failureChance) return false
            if (name != other.name) return false
            if (!statGains.contentEquals(other.statGains)) return false
            if (relationshipBars != other.relationshipBars) return false

            return true
        }

        override fun hashCode(): Int {
            var result = failureChance
            result = 31 * result + name.hashCode()
            result = 31 * result + statGains.contentHashCode()
            result = 31 * result + relationshipBars.hashCode()
            return result
        }
    }

	data class Date(
		val year: Int,
		val phase: String,
		val month: Int,
		val turnNumber: Int
	)

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	/**
	 * Loads stat targets from SharedPreferences using the new simplified system.
	 * Also reloads the auto-calculated stat prioritization.
	 */
	private fun loadStatTargets() {
		// Reload the auto-calculated priority in case it changed
		statPrioritization = sharedPreferences.getString("statPrioritization", "Speed|Stamina|Power|Guts|Wit")!!.split("|")
		
		val speedTarget = sharedPreferences.getInt("current_speed_target", 800)
		val staminaTarget = sharedPreferences.getInt("current_stamina_target", 600)
		val powerTarget = sharedPreferences.getInt("current_power_target", 600)
		val gutsTarget = sharedPreferences.getInt("current_guts_target", 300)
		val witTarget = sharedPreferences.getInt("current_wit_target", 300)

		// Load distance and running style (though distance is no longer used for targeting)
		val selectedDistance = sharedPreferences.getString("selected_distance", "Medium") ?: "Medium"
		runningStyle = sharedPreferences.getString("selected_running_style", "Nige") ?: "Nige"

		// Set the stat targets array
		// Order: Speed, Stamina, Power, Guts, Wit
		statTargets = intArrayOf(speedTarget, staminaTarget, powerTarget, gutsTarget, witTarget)
	}

	/**
	 * Returns a formatted string of the elapsed time since the bot started as HH:MM:SS format.
	 *
	 * Source is from https://stackoverflow.com/questions/9027317/how-to-convert-milliseconds-to-hhmmss-format/9027379
	 *
	 * @return String of HH:MM:SS format of the elapsed time.
	 */
	@SuppressLint("DefaultLocale")
	private fun printTime(): String {
		val elapsedMillis: Long = System.currentTimeMillis() - startTime

		return String.format(
			"%02d:%02d:%02d",
			TimeUnit.MILLISECONDS.toHours(elapsedMillis),
			TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(elapsedMillis)),
			TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsedMillis))
		)
	}

	/**
	 * Print the specified message to debug console and then saves the message to the log.
	 *
	 * @param message Message to be saved.
	 * @param tag Distinguishes between messages for where they came from. Defaults to Game's TAG.
	 * @param isError Flag to determine whether to display log message in console as debug or error.
	 * @param isOption Flag to determine whether to append a newline right after the time in the string.
	 */
	fun printToLog(message: String, tag: String = this.tag, isError: Boolean = false, isOption: Boolean = false) {
		if (!isError) {
			Log.d(tag, message)
		} else {
			Log.e(tag, message)
		}

		// Remove the newline prefix if needed and place it where it should be.
		if (message.startsWith("\n")) {
			val newMessage = message.removePrefix("\n")
			if (isOption) {
				MessageLog.addMessage("\n" + printTime() + "\n" + newMessage)
			} else {
				MessageLog.addMessage("\n" + printTime() + " " + newMessage)
			}
		} else {
			if (isOption) {
				MessageLog.addMessage(printTime() + "\n" + message)
			} else {
				MessageLog.addMessage(printTime() + " " + message)
			}
		}
	}

	/**
	 * Wait the specified seconds to account for ping or loading.
	 * It also checks for interruption every 100ms to allow faster interruption and checks if the game is still in the middle of loading.
	 *
	 * @param seconds Number of seconds to pause execution.
	 * @param skipWaitingForLoading If true, then it will skip the loading check. Defaults to false.
	 */
	fun wait(seconds: Double, skipWaitingForLoading: Boolean = false) {
		val totalMillis = (seconds * 1000).toLong()
		// Check for interruption every 100ms.
		val checkInterval = 100L

		var remainingMillis = totalMillis
		while (remainingMillis > 0) {
			if (!BotService.isRunning) {
				throw InterruptedException()
			}

			val sleepTime = minOf(checkInterval, remainingMillis)
			runBlocking {
				delay(sleepTime)
			}
			remainingMillis -= sleepTime
		}

		if (!skipWaitingForLoading) {
			// Check if the game is still loading as well.
			waitForLoading()
		}
	}

	/**
	 * Wait for the game to finish loading.
	 */
	fun waitForLoading() {
		while (checkLoading()) {
			// Avoid an infinite loop by setting the flag to true.
			wait(0.5, skipWaitingForLoading = true)
		}
	}

	/**
	 * Find and tap the specified image.
	 *
	 * @param imageName Name of the button image file in the /assets/images/ folder.
	 * @param tries Number of tries to find the specified button. Defaults to 3.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param taps Specify the number of taps on the specified image. Defaults to 1.
	 * @param suppressError Whether or not to suppress saving error messages to the log in failing to find the button. Defaults to false.
	 * @return True if the button was found and clicked. False otherwise.
	 */
	fun findAndTapImage(imageName: String, tries: Int = 3, region: IntArray = intArrayOf(0, 0, 0, 0), taps: Int = 1, suppressError: Boolean = false): Boolean {
		if (debugMode) {
			printToLog("[DEBUG] Now attempting to find and click the \"$imageName\" button.")
		}

		val tempLocation: Point? = imageUtils.findImage(imageName, tries = tries, region = region, suppressError = suppressError).first

		return if (tempLocation != null) {
			Log.d(tag, "Found and going to tap: $imageName")
			tap(tempLocation.x, tempLocation.y, imageName, taps = taps)
			true
		} else {
			false
		}
	}

	/**
	 * Performs a tap on the screen at the coordinates and then will wait until the game processes the server request and gets a response back.
	 *
	 * @param x The x-coordinate.
	 * @param y The y-coordinate.
	 * @param imageName The template image name to use for tap location randomization.
	 * @param taps The number of taps.
	 * @param ignoreWaiting Flag to ignore checking if the game is busy loading.
	 */
	fun tap(x: Double, y: Double, imageName: String, taps: Int = 1, ignoreWaiting: Boolean = false) {
		// Perform the tap.
		gestureUtils.tap(x, y, imageName, taps = taps)

		if (!ignoreWaiting) {
			// Now check if the game is waiting for a server response from the tap and wait if necessary.
			wait(0.20)
			waitForLoading()
		}
	}

	/**
	 * Handles the test to perform template matching to determine what the best scale will be for the device.
	 */
	fun startTemplateMatchingTest() {
		printToLog("\n[TEST] Now beginning basic template match test on the Home screen.")
		printToLog("[TEST] Template match confidence setting will be overridden for the test.\n")
		val results = imageUtils.startTemplateMatchingTest()
		printToLog("\n[TEST] Basic template match test complete.")

		// Print all scale/confidence combinations that worked for each template.
		for ((templateName, scaleConfidenceResults) in results) {
			if (scaleConfidenceResults.isNotEmpty()) {
				printToLog("[TEST] All working scale/confidence combinations for $templateName:")
				for (result in scaleConfidenceResults) {
					printToLog("[TEST]	Scale: ${result.scale}, Confidence: ${result.confidence}")
				}
			} else {
				printToLog("[WARNING] No working scale/confidence combinations found for $templateName")
			}
		}

		// Then print the median scales and confidences.
		val medianScales = mutableListOf<Double>()
		val medianConfidences = mutableListOf<Double>()
		for ((templateName, scaleConfidenceResults) in results) {
			if (scaleConfidenceResults.isNotEmpty()) {
				val sortedScales = scaleConfidenceResults.map { it.scale }.sorted()
				val sortedConfidences = scaleConfidenceResults.map { it.confidence }.sorted()
				val medianScale = sortedScales[sortedScales.size / 2]
				val medianConfidence = sortedConfidences[sortedConfidences.size / 2]
				medianScales.add(medianScale)
				medianConfidences.add(medianConfidence)
				printToLog("[TEST] Median scale for $templateName: $medianScale")
				printToLog("[TEST] Median confidence for $templateName: $medianConfidence")
			}
		}

		if (medianScales.isNotEmpty()) {
			printToLog("\n[TEST] The following are the recommended scales to set (pick one as a whole number value): $medianScales.")
			printToLog("[TEST] The following are the recommended confidences to set (pick one as a whole number value): $medianConfidences.")
		} else {
			printToLog("\n[ERROR] No median scale/confidence can be found.", isError = true)
		}
	}

	/**
	 * Handles the test to perform OCR on the training failure chance for the current training on display.
	 */
	fun startSingleTrainingFailureOCRTest() {
		printToLog("\n[TEST] Now beginning Single Training Failure OCR test on the Training screen for the current training on display.")
		printToLog("[TEST] Note that this test is dependent on having the correct scale.")
		val failureChance: Int = imageUtils.findTrainingFailureChance()
		if (failureChance == -1) {
			printToLog("[ERROR] Training Failure Chance detection failed.", isError = true)
		} else {
			printToLog("[TEST] Training Failure Chance: $failureChance")
		}
	}

	/**
	 * Handles the test to perform OCR on training failure chances for all 5 of the trainings on display.
	 */
	fun startComprehensiveTrainingFailureOCRTest() {
		printToLog("\n[TEST] Now beginning Comprehensive Training Failure OCR test on the Training screen for all 5 trainings on display.")
		printToLog("[TEST] Note that this test is dependent on having the correct scale.")
		analyzeTrainings(test = true)
		printTrainingMap()
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Helper functions to be shared amongst the various Campaigns.
	
	/**
	 * Calculate turns until the next training camp (summer or winter).
	 * Returns -1 if no camp is approaching within 2 turns.
	 * We only prepare in the 2 turns immediately before camp starts.
	 */
	private fun getTurnsUntilTrainingCamp(): Int {
		return when {
			// Summer camp starts at Late June
			currentDate.month == 6 && currentDate.phase == "Early" -> 1  // Next turn is camp
			currentDate.month == 5 && currentDate.phase == "Late" -> 2   // 2 turns until camp
			
			// Winter camp starts at Late December
			currentDate.month == 12 && currentDate.phase == "Early" -> 1  // Next turn is camp
			currentDate.month == 11 && currentDate.phase == "Late" -> 2   // 2 turns until camp
			
			else -> -1  // No camp approaching or too far away
		}
	}
	
	/**
	 * Calculate turns until URA Finals.
	 * Returns -1 if not in Year 3 or too far from finals.
	 */
	private fun getTurnsUntilURAFinals(): Int {
		if (currentDate.year != 3) return -1

		return when {
			// URA Finals happens AFTER Late December Year 3
			// Late December is the last training turn before URA Finals
			currentDate.month == 12 && currentDate.phase == "Early" -> 1  // Next is Late Dec (last training)
			currentDate.month == 11 && currentDate.phase == "Late" -> 2
			currentDate.month == 11 && currentDate.phase == "Early" -> 3
			currentDate.month == 10 && currentDate.phase == "Late" -> 4
			currentDate.month == 10 && currentDate.phase == "Early" -> 5
			else -> -1
		}
	}

	/**
	 * Check if currently in summer training camp.
	 */
	private fun isInSummerTraining(): Boolean {
		return (currentDate.month == 6 && currentDate.phase == "Late") || currentDate.month == 7
	}

	/**
	 * Check if currently in winter training camp.
	 */
	private fun isInWinterTraining(): Boolean {
		return (currentDate.month == 12 && currentDate.phase == "Late") || currentDate.month == 1
	}

	/**
	 * Get energy estimate from failure rates using the game formula.
	 * Failure% = max(0, (50 - Energy) * 0.5)
	 * So: Energy = 50 - (Failure% * 2)
	 */
	private fun estimateCurrentEnergy(): Double {
		// If we have recent training data, use average failure rate to estimate
		if (trainingMap.isNotEmpty()) {
			val avgFailure = trainingMap.values.map { it.failureChance }.average()
			// Using correct game formula: Energy = 50 - (Failure% * 2)
			return (50 - avgFailure * 2).coerceIn(0.0, 100.0)
		}
		// Default to moderate energy if no data
		return 50.0
	}

	/**
	 * Updates training level tracking for a stat.
	 * Each stat levels up after 4 trainings (max level 5).
	 */
	private fun updateTrainingLevel(statName: String) {
		val currentLevel = trainingLevels.getOrDefault(statName, 1)
		val currentCount = trainingCountForLevel.getOrDefault(statName, 0) + 1

		if (currentCount >= trainingsPerLevel && currentLevel < maxTrainingLevel) {
			// Level up!
			trainingLevels[statName] = currentLevel + 1
			trainingCountForLevel[statName] = 0
			printToLog("[TRAINING-LEVEL] $statName leveled up to Lv${currentLevel + 1}!")
		} else {
			trainingCountForLevel[statName] = currentCount
		}
	}

	/**
	 * Apply training level adjustments - REWARD leveling instead of penalizing.
	 * Higher levels provide better stat gains, making leveling strategic.
	 */
	private fun applyTrainingLevelAdjustments(training: Training, baseScore: Double): Double {
		var score = baseScore
		val currentLevel = trainingLevels.getOrDefault(training.name, 1)
		val levelProgress = trainingCountForLevel.getOrDefault(training.name, 0)
		val currentStat = currentStatsMap.getOrDefault(training.name, 0)

		// Apply level multiplier bonus - higher levels are BETTER
		val levelBonus = levelMultipliers[currentLevel] ?: 1.0
		score *= levelBonus
		if (currentLevel > 1) {
			printToLog("[TRAINING-LEVEL] ${training.name} Lv$currentLevel provides ${((levelBonus - 1) * 100).toInt()}% stat bonus")
		}

		// Check if approaching absolute stat cap
		if (currentStat >= absoluteStatCap - 100) {
			score *= 0.1  // Massive penalty near cap
			printToLog("[TRAINING-LEVEL] ${training.name} near absolute cap ($currentStat/$absoluteStatCap) - 90% penalty")
			return score
		}

		// Check if this training would push us over the cap
		val estimatedGain = training.statGains[trainings.indexOf(training.name)]
		if (currentStat + estimatedGain > absoluteStatCap) {
			score *= 0.2  // Heavy penalty for wasted gains
			printToLog("[TRAINING-LEVEL] ${training.name} would exceed cap - 80% penalty")
			return score
		}

		// Calculate how many times this stat has been trained recently
		val recentCount = recentTrainings.count { it == training.name }
		val totalCount = historicalTrainingCounts.getOrDefault(training.name, 0)

		// Strategic level-up bonus - leveling up priority stats is GOOD
		if (levelProgress == trainingsPerLevel - 1 && currentLevel < maxTrainingLevel) {
			// About to level up - significant bonus for important stats
			val priorityIndex = statPrioritization.indexOf(training.name)
			val levelUpBonus = when (priorityIndex) {
				0 -> 1.5  // 50% bonus for top priority
				1 -> 1.3  // 30% bonus for second priority
				2 -> 1.15 // 15% bonus for third
				else -> 1.05 // Small bonus for others
			}
			score *= levelUpBonus
			printToLog("[TRAINING-LEVEL] ${training.name} about to level up (Lv$currentLevel->Lv${currentLevel+1}) - ${((levelUpBonus - 1) * 100).toInt()}% bonus")
		}

		// Check if we should strategically spam this training
		val shouldSpam = shouldStrategicallySpam(training)
		if (shouldSpam) {
			score *= 1.5  // 50% bonus for strategic spamming
			printToLog("[TRAINING-LEVEL] Strategic spam bonus for ${training.name} - critical for build")
		} else {
			// Apply soft balance constraints only if NOT strategically spamming
			val avgOtherStats = currentStatsMap.filterKeys { it != training.name }.values.average()
			when {
				// Only penalize if EXTREMELY imbalanced and not priority
				currentStat > avgOtherStats * 2.0 && statPrioritization.indexOf(training.name) > 2 -> {
					score *= 0.6
					printToLog("[TRAINING-LEVEL] ${training.name} ($currentStat) extremely high vs others (${avgOtherStats.toInt()}) - soft balance penalty")
				}
			}
		}

		// Bonus for balanced leveling
		val minLevel = trainingLevels.values.minOrNull() ?: 1
		val maxLevel = trainingLevels.values.maxOrNull() ?: 1
		if (maxLevel - minLevel > 2 && currentLevel == minLevel) {
			score *= 1.2
			printToLog("[TRAINING-LEVEL] ${training.name} Lv$currentLevel is behind (max: Lv$maxLevel) - 20% catch-up bonus")
		}

		return score
	}

	/**
	 * Check if we should strategically spam a training.
	 * Some builds REQUIRE spamming (e.g., 1200 Speed for Sprint).
	 */
	private fun shouldStrategicallySpam(training: Training): Boolean {
		val statIndex = trainings.indexOf(training.name)
		val currentStat = currentStatsMap.getOrDefault(training.name, 0)
		val target = statTargets.getOrElse(statIndex) { 600 }
		val completion = if (target > 0) currentStat.toDouble() / target else 1.0
		val priorityIndex = statPrioritization.indexOf(training.name)

		// Strategic spam conditions
		return when {
			// Sprint builds MUST have 1200 Speed
			currentDistance == "Sprint" && training.name == "Speed" && currentStat < 1150 -> true

			// Long distance MUST have high Stamina
			currentDistance == "Long" && training.name == "Stamina" && currentStat < 900 -> true

			// About to level up a high-priority stat
			trainingCountForLevel[training.name] == 3 && priorityIndex <= 1 -> true

			// Lv5 training camp with high-value opportunity (3+ friends or rainbow)
			(isInSummerTraining() || isInWinterTraining()) &&
			(training.relationshipBars.count { it.dominantColor == "blue" } >= 3 ||
			 training.statGains.sum() > 80) -> true

			// Critical deficit in primary stat (less than 40% complete)
			completion < 0.4 && priorityIndex == 0 -> true

			// Year 3 final push for primary stats
			currentDate.year == 3 && priorityIndex <= 1 && completion < 0.85 -> true

			else -> false
		}
	}

	/**
	 * Apply loop prevention with intelligence - allow strategic spamming.
	 */
	private fun applyLoopPreventionPenalties(training: Training, baseScore: Double): Double {
		var score = baseScore

		// If strategic spamming is needed, don't apply penalties
		if (shouldStrategicallySpam(training)) {
			return score
		}

		// Otherwise apply soft penalties for repetition
		if (training.name == lastTrainingName && consecutiveSameTraining >= 3) {
			score *= 0.7  // Soft penalty after 3 consecutive
			printToLog("[LOOP-PREVENTION] ${training.name} done $consecutiveSameTraining times - soft 30% penalty")
		}

		// Only penalize if appears too frequently AND isn't priority
		val recentFrequency = recentTrainings.count { it == training.name }
		if (recentFrequency >= 4 && statPrioritization.indexOf(training.name) > 1) {
			score *= 0.6
			printToLog("[LOOP-PREVENTION] ${training.name} appears $recentFrequency times in last 5 (non-priority) - 40% penalty")
		}

		return score
	}

	/**
	 * Thompson Sampling for exploration-exploitation balance.
	 * Samples from Beta distribution based on success history.
	 */
	private fun calculateThompsonScore(training: Training): Double {
		val history = trainingSuccessHistory.getOrDefault(training.name, Pair(0, 0))
		val successes = history.first
		val attempts = history.second

		// Beta distribution parameters
		val alpha = successes + 1.0
		val beta = (attempts - successes) + 1.0

		// Sample from Beta distribution (simplified using mean + exploration)
		val mean = alpha / (alpha + beta)
		val variance = (alpha * beta) / ((alpha + beta) * (alpha + beta) * (alpha + beta + 1))
		val exploration = kotlin.math.sqrt(variance) * thompsonExplorationWeight

		// Add random exploration factor
		val randomFactor = (kotlin.random.Random.nextDouble() - 0.5) * exploration
		val thompsonSample = (mean + randomFactor).coerceIn(0.0, 1.0)

		// Combine with expected value
		val expectedValue = calculateImmediateValue(training)
		val score = thompsonSample * expectedValue

		if (attempts < 3) {
			printToLog("[THOMPSON] ${training.name}: Exploration mode (${attempts} attempts) - score: ${score.toInt()}")
		}

		return score
	}

	/**
	 * UCB1 algorithm for exploration bonus.
	 * Balances exploitation with exploration through upper confidence bound.
	 */
	private fun calculateUCBBonus(training: Training): Double {
		val trainingCount = historicalTrainingCounts.getOrDefault(training.name, 0)

		// Unexplored training gets maximum bonus
		if (trainingCount == 0) {
			return 1000.0
		}

		// UCB1 formula: exploitation + exploration
		val avgValue = trainingValueHistory.getOrDefault(training.name, 100.0)
		val explorationBonus = kotlin.math.sqrt(
			2 * kotlin.math.ln(totalTrainingsDone.toDouble().coerceAtLeast(1.0)) / trainingCount
		)

		val ucbScore = avgValue + ucbExplorationParam * explorationBonus * 100

		if (explorationBonus > 0.5) {
			printToLog("[UCB1] ${training.name}: Exploration bonus ${(explorationBonus * 100).toInt()} - total: ${ucbScore.toInt()}")
		}

		return ucbScore
	}

	/**
	 * Dynamic Programming value function.
	 * Considers both immediate and future value of training decisions.
	 */
	private fun calculateDynamicValue(training: Training): Double {
		val turnsRemaining = estimateTurnsRemaining()
		val immediateValue = calculateImmediateValue(training)
		val futureValue = calculateFutureValue(training, turnsRemaining)

		val totalValue = immediateValue + discountFactor * futureValue

		if (futureValue > 50) {
			printToLog("[DP] ${training.name}: Immediate: ${immediateValue.toInt()}, Future: ${futureValue.toInt()}, Total: ${totalValue.toInt()}")
		}

		return totalValue
	}

	/**
	 * Calculate immediate value of a training.
	 */
	private fun calculateImmediateValue(training: Training): Double {
		var value = 0.0

		// Base stat value with level multiplier
		val currentLevel = trainingLevels.getOrDefault(training.name, 1)
		val levelBonus = levelMultipliers[currentLevel] ?: 1.0
		val statValue = training.statGains.sum() * levelBonus

		// Deficit-based priority
		val statIndex = trainings.indexOf(training.name)
		val currentStat = currentStatsMap.getOrDefault(training.name, 0)
		val target = statTargets.getOrElse(statIndex) { 600 }
		val completion = if (target > 0) currentStat.toDouble() / target else 1.0

		val deficitMultiplier = when {
			completion < 0.3 -> 4.0
			completion < 0.5 -> 3.0
			completion < 0.7 -> 2.0
			completion < 0.85 -> 1.5
			completion < 1.0 -> 1.2
			else -> 0.8
		}

		value += statValue * deficitMultiplier

		// Relationship value
		val blueBars = training.relationshipBars.count { it.dominantColor == "blue" }
		val totalBars = training.relationshipBars.size
		value += blueBars * 50 + totalBars * 20

		// Success rate adjustment
		val successRate = (100 - training.failureChance) / 100.0
		value *= successRate

		return value
	}

	/**
	 * Calculate future value of training (for Dynamic Programming).
	 */
	private fun calculateFutureValue(training: Training, turnsRemaining: Int): Double {
		if (turnsRemaining <= 0) return 0.0

		var futureValue = 0.0
		val levelProgress = trainingCountForLevel.getOrDefault(training.name, 0)
		val currentLevel = trainingLevels.getOrDefault(training.name, 1)

		// Value of leveling up
		if (levelProgress + 1 >= trainingsPerLevel && currentLevel < maxTrainingLevel) {
			val nextLevelBonus = levelMultipliers[currentLevel + 1] ?: 1.0
			val currentLevelBonus = levelMultipliers[currentLevel] ?: 1.0
			val bonusIncrease = nextLevelBonus - currentLevelBonus

			// Estimate future trainings of this type
			val expectedFutureUses = kotlin.math.min(4.0, turnsRemaining / 8.0)
			val baseStatGain = training.statGains[trainings.indexOf(training.name)]

			futureValue += expectedFutureUses * baseStatGain * bonusIncrease * 100
			printToLog("[DP-FUTURE] ${training.name} will level to ${currentLevel + 1}, future bonus: ${futureValue.toInt()}")
		}

		// Value of maintaining balance for URA finals
		if (turnsRemaining < 10 && statPrioritization.indexOf(training.name) <= 2) {
			futureValue += 50  // Bonus for priority stats near end
		}

		return futureValue
	}

	/**
	 * Estimate remaining turns in the campaign.
	 */
	private fun estimateTurnsRemaining(): Int {
		val totalTurns = 72  // 3 years * 24 turns/year
		val currentTurn = currentDate.turnNumber
		return (totalTurns - currentTurn).coerceAtLeast(0)
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Functions to check what screen the bot is at.

	/**
	 * Checks if the bot is at the Main screen or the screen with available options to undertake.
	 * This will also make sure that the Main screen does not contain the option to select a race.
	 *
	 * @return True if the bot is at the Main screen. Otherwise false.
	 */
	fun checkMainScreen(): Boolean {
		printToLog("[INFO] Checking if the bot is sitting at the Main screen.")
		return if (imageUtils.findImage("tazuna", tries = 1, region = imageUtils.regionTopHalf).first != null &&
			imageUtils.findImage("race_select_mandatory", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true).first == null) {
			printToLog("\n[INFO] Current bot location is at Main screen.")

			// Perform updates here if necessary.
			updateDate()
			true
		} else if (!enablePopupCheck && imageUtils.findImage("cancel", tries = 1, region = imageUtils.regionBottomHalf).first != null &&
			imageUtils.findImage("race_confirm", tries = 1, region = imageUtils.regionBottomHalf).first != null) {
			// This popup is most likely the insufficient fans popup. Force an extra race to catch up on the required fans.
			printToLog("[INFO] There is a possible insufficient fans or maiden race popup.")
			encounteredRacingPopup = true
			skipRacing = false
			true
		} else {
			false
		}
	}

	/**
	 * Checks if the bot is at the Training Event screen with an active event with options to select on screen.
	 *
	 * @return True if the bot is at the Training Event screen. Otherwise false.
	 */
	fun checkTrainingEventScreen(): Boolean {
		printToLog("[INFO] Checking if the bot is sitting on the Training Event screen.")
		return if (imageUtils.findImage("training_event_active", tries = 1, region = imageUtils.regionMiddle).first != null) {
			printToLog("\n[INFO] Current bot location is at Training Event screen.")
			true
		} else {
			false
		}
	}

	/**
	 * Checks if the bot is at the preparation screen with a mandatory race needing to be completed.
	 *
	 * @return True if the bot is at the Main screen with a mandatory race. Otherwise false.
	 */
	fun checkMandatoryRacePrepScreen(): Boolean {
		printToLog("[INFO] Checking if the bot is sitting on the Race Preparation screen.")
		return if (imageUtils.findImage("race_select_mandatory", tries = 1, region = imageUtils.regionBottomHalf).first != null) {
			printToLog("\n[INFO] Current bot location is at the preparation screen with a mandatory race ready to be completed.")
			true
		} else if (imageUtils.findImage("race_select_mandatory_goal", tries = 1, region = imageUtils.regionMiddle).first != null) {
			// Most likely the user started the bot here so a delay will need to be placed to allow the start banner of the Service to disappear.
			wait(2.0)
			printToLog("\n[INFO] Current bot location is at the Race Selection screen with a mandatory race needing to be selected.")
			// Walk back to the preparation screen.
			findAndTapImage("back", tries = 1, region = imageUtils.regionBottomHalf)
			wait(1.0)
			true
		} else {
			false
		}
	}

	/**
	 * Checks if the bot is at the Racing screen waiting to be skipped or done manually.
	 *
	 * @return True if the bot is at the Racing screen. Otherwise, false.
	 */
	fun checkRacingScreen(): Boolean {
		printToLog("[INFO] Checking if the bot is sitting on the Racing screen.")
		return if (imageUtils.findImage("race_change_strategy", tries = 1, region = imageUtils.regionBottomHalf).first != null) {
			printToLog("\n[INFO] Current bot location is at the Racing screen waiting to be skipped or done manually.")
			true
		} else {
			false
		}
	}

	/**
	 * Checks if the day number is odd to be eligible to run an extra race, excluding Summer where extra racing is not allowed.
	 * Now with smarter decision making based on energy, mood, and game phase.
	 *
	 * @return True if the day number is odd. Otherwise false.
	 */
	fun checkExtraRaceAvailability(): Boolean {
		val dayNumber = imageUtils.determineDayForExtraRace()
		printToLog("\n[INFO] Current remaining number of days before the next mandatory race: $dayNumber.")

		// If the setting to force racing extra races is enabled, always return true.
		if (enableForceRacing) return true
		
		// Check if we're near important events
		val turnsUntilCamp = getTurnsUntilTrainingCamp()
		val turnsUntilURA = getTurnsUntilURAFinals()
		
		// Don't run extra races if important events are approaching
		if (turnsUntilCamp in 1..2) {
			printToLog("[RACE] Training camp in $turnsUntilCamp turns - skipping extra race to save energy")
			return false
		}
		
		if (turnsUntilURA in 1..3) {
			printToLog("[RACE] URA Finals in $turnsUntilURA turns - focusing on training instead of extra races")
			return false
		}
		
		// Estimate current energy from training failure rates
		val avgFailureRate = trainingMap.values
			.filter { it.failureChance >= 0 }
			.map { it.failureChance }
			.average()
		
		val estimatedEnergy = if (!avgFailureRate.isNaN()) {
			(50 - avgFailureRate * 2).coerceIn(0.0, 100.0)
		} else 60.0
		
		// Skip extra race if energy is too low
		if (estimatedEnergy < 40) {
			printToLog("[RACE] Energy too low (~${estimatedEnergy.toInt()}%) - skipping extra race")
			return false
		}

		return enableFarmingFans && dayNumber % daysToRunExtraRaces == 0 && !raceRepeatWarningCheck &&
				imageUtils.findImage("race_select_extra_locked_uma_finals", tries = 1, region = imageUtils.regionBottomHalf).first == null &&
				imageUtils.findImage("race_select_extra_locked", tries = 1, region = imageUtils.regionBottomHalf).first == null &&
				imageUtils.findImage("recover_energy_summer", tries = 1, region = imageUtils.regionBottomHalf).first == null
	}

	/**
	 * Checks if the bot is at the Ending screen detailing the overall results of the run.
	 *
	 * @return True if the bot is at the Ending screen. Otherwise false.
	 */
	fun checkEndScreen(): Boolean {
		return if (imageUtils.findImage("complete_career", tries = 1, region = imageUtils.regionBottomHalf).first != null) {
			printToLog("\n[END] Bot has reached the End screen.")
			true
		} else {
			false
		}
	}

	/**
	 * Checks if the bot has an injury or bad condition.
	 * Uses infirmary whenever it's available (indicates a bad condition is present).
	 *
	 * @return True if infirmary was used. Otherwise false.
	 */
	fun checkInjury(): Boolean {
		// First check if infirmary button is available and clickable
		val recoverInjuryLocation = imageUtils.findImage("recover_injury", tries = 1, region = imageUtils.regionBottomHalf).first

		if (recoverInjuryLocation != null) {
			// Check if the infirmary button is actually clickable (not grayed out)
			val isClickable = imageUtils.checkColorAtCoordinates(
				recoverInjuryLocation.x.toInt(),
				recoverInjuryLocation.y.toInt() + 15,
				intArrayOf(151, 105, 243),
				10
			)

			if (isClickable) {
				// Infirmary is available - use it regardless of what condition we have
				printToLog("\n[INFIRMARY] Infirmary available - indicates bad condition present")

				// Track what turn/year we're using infirmary for analysis
				if (currentDate.year == 3) {
					printToLog("[INFIRMARY] Year 3 - Using infirmary immediately for any bad condition")
				} else if (currentDate.year == 2 && currentDate.month >= 6) {
					printToLog("[INFIRMARY] Mid Year 2+ - Using infirmary to maintain performance")
				}

				if (findAndTapImage("recover_injury", tries = 1, region = imageUtils.regionBottomHalf)) {
					wait(0.3)
					if (imageUtils.confirmLocation("recover_injury", tries = 1, region = imageUtils.regionMiddle)) {
						printToLog("[INFIRMARY] Successfully used infirmary to cure bad condition")

						// Note: Bad conditions include:
						// - Practice Poor (+2% failure rate)
						// - Night Owl (random -10 energy)
						// - Migraine (mood cannot increase)
						// - Slacker (may skip training)
						// - Slow Metabolism (speed cannot increase)
						// - Dry Skin (random mood decrease)

						return true
					} else {
						return false
					}
				} else {
					printToLog("[WARNING] Infirmary available but tap failed")
					return false
				}
			} else {
				// Infirmary button exists but is grayed out (no conditions to cure)
				printToLog("\n[INFO] No injuries or bad conditions detected (infirmary grayed out)")
				return false
			}
		} else {
			// No infirmary button found at all
			printToLog("\n[INFO] No injury/infirmary option found")
			return false
		}
	}

	/**
	 * Simple check if we should prioritize using infirmary based on game phase.
	 * In late game, we always use infirmary when available.
	 * In early game, we might skip it to save turns.
	 */
	private fun shouldPrioritizeInfirmary(): Boolean {
		return when {
			// Always use in Year 3 - every bad condition hurts our final push
			currentDate.year == 3 -> true

			// Use in late Year 2 - preparing for Year 3
			currentDate.year == 2 && currentDate.month >= 9 -> true

			// During training camps - maximize the Lv5 training value
			isInSummerTraining() || isInWinterTraining() -> true

			// Before important races/URA qualifiers
			currentDate.month == 12 || currentDate.month == 6 -> true

			// Otherwise only if we have low energy (bad conditions drain resources)
			getEstimatedEnergy() < 50 -> true

			else -> false
		}
	}

	/**
	 * Gets estimated current energy based on average failure rates.
	 * Used to decide if we should prioritize infirmary use.
	 */
	private fun getEstimatedEnergy(): Double {
		if (trainingMap.isEmpty()) return 50.0 // Default assumption

		val avgFailure = trainingMap.values.map { it.failureChance }.average()
		// Reverse engineer energy from failure rate
		// Failure% = max(0, (50 - Energy) * 0.5)
		// So Energy = 50 - (Failure% * 2)
		return maxOf(0.0, 50.0 - (avgFailure * 2))
	}

	/**
	 * Checks if the bot is at a "Now Loading..." screen or if the game is awaiting for a server response. This may cause significant delays in normal bot processes.
	 *
	 * @return True if the game is still loading or is awaiting for a server response. Otherwise, false.
	 */
	fun checkLoading(): Boolean {
		printToLog("[INFO] Now checking if the game is still loading...")
		return if (imageUtils.findImage("connecting", tries = 1, region = imageUtils.regionTopHalf, suppressError = true).first != null) {
			printToLog("[INFO] Detected that the game is awaiting a response from the server from the \"Connecting\" text at the top of the screen. Waiting...")
			true
		} else if (imageUtils.findImage("now_loading", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true).first != null) {
			printToLog("[INFO] Detected that the game is still loading from the \"Now Loading\" text at the bottom of the screen. Waiting...")
			true
		} else {
			false
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Functions to execute Training by determining failure percentages, overall stat gains and stat weights.

	/**
	 * The entry point for handling Training.
	 */
	fun handleTraining() {
		printToLog("\n[TRAINING] Starting Training process...")

		// Track skill points periodically
		trackSkillPoints()

		// Enter the Training screen.
		if (findAndTapImage("training_option", region = imageUtils.regionBottomHalf)) {
			// Acquire the percentages and stat gains for each training.
			wait(0.5)
			analyzeTrainings()

			if (trainingMap.isEmpty()) {
				printToLog("[TRAINING] Backing out of Training and returning on the Main screen.")
				findAndTapImage("back", region = imageUtils.regionBottomHalf)
				wait(1.0)

				if (checkMainScreen()) {
					printToLog("[TRAINING] Will recover energy due to either failure chance was high enough to do so or no failure chances were detected via OCR.")
					// Force rest when trainingMap is empty - this means we explicitly need to rest
					recoverEnergy(forceRest = true)
				} else {
					printToLog("[ERROR] Could not head back to the Main screen in order to recover energy.")
				}
			} else {
				// Now select the training option with the highest weight.
				executeTraining()

				firstTrainingCheck = false
			}

			raceRepeatWarningCheck = false
			printToLog("\n[TRAINING] Training process completed.")
		} else {
			printToLog("[ERROR] Cannot start the Training process. Moving on...", isError = true)
		}
	}
	
	/**
	 * Calculates the deficit multiplier based on how far a stat is from its target.
	 * Based on the comprehensive guide:
	 * - Deficit > 300: 3.0x (Critical priority)
	 * - Deficit 200-300: 2.5x (High priority)
	 * - Deficit 100-200: 2.0x (Moderate priority)
	 * - Deficit 50-100: 1.5x (Low priority)
	 * - Deficit < 50: 1.1x (Maintenance)
	 * - Surplus: 0.5x (Diminishing returns)
	 */
	private fun getDeficitMultiplier(deficit: Int): Double {
		return when {
			deficit > 300 -> 3.0
			deficit in 200..300 -> 2.5
			deficit in 100..199 -> 2.0
			deficit in 50..99 -> 1.5
			deficit in 1..49 -> 1.1
			deficit <= 0 -> 0.5  // Surplus - diminishing returns
			else -> 1.0
		}
	}
	
	/**
	 * Determines if we should prioritize recovery based on failure rates.
	 * Returns true if recovery (rest or Wit training) should be prioritized.
	 */
	private fun shouldPrioritizeRecovery(avgFailureRate: Double, estimatedEnergy: Double): Boolean {
		// More nuanced recovery decision based on multiple factors
		
		// If we have max energy (0% failures), don't prioritize recovery
		// This prevents resting at 100% energy with 0% failure rates
		if (avgFailureRate <= 5 && estimatedEnergy >= 85) {
			printToLog("[RECOVERY] High energy detected (~${estimatedEnergy.toInt()}%), no recovery needed")
			return false
		}
		
		// URA Finals happens AFTER Late December Year 3
		// Late December Year 3 is the last training opportunity before URA Finals
		// Don't force rest if we have decent energy on this crucial last turn
		if (currentDate.year == 3 && currentDate.month == 12 && currentDate.phase == "Late" && estimatedEnergy >= 40) {
			printToLog("[RECOVERY] Last turn before URA Finals! Energy ~${estimatedEnergy.toInt()}%, allowing training")
			return false
		}
		
		// Count trainings at different risk levels
		val trainingsUnder10 = trainingMap.values.count { it.failureChance in 0..10 }
		val trainingsUnder20 = trainingMap.values.count { it.failureChance in 0..20 }
		val trainingsUnder30 = trainingMap.values.count { it.failureChance in 0..30 }
		
		// Evaluate high-value trainings that might be worth the risk
		val exceptionalTrainings = trainingMap.values.filter { training ->
			val friendCount = training.relationshipBars.count { it.fillPercent >= 80 }
			val statValue = training.statGains.sum()
			val riskRewardRatio = calculateRiskRewardRatio(training)
			
			// Worth taking risk if:
			// - Multiple friends OR high stats
			// - Good risk-reward ratio
			training.failureChance in 20..35 && 
			(friendCount >= 2 || statValue > 70 || riskRewardRatio > 50)
		}
		
		// Dynamic recovery threshold based on game phase
		val recoveryThreshold = when {
			// Early game: more tolerant of risk for relationships
			currentDate.year == 1 -> 35
			// Mid game: balanced approach
			currentDate.year == 2 -> 30
			// Late game: conservative for stat gains
			currentDate.year == 3 -> 25
			else -> 30
		}
		
		// Check camp timing
		val turnsUntilCamp = getTurnsUntilTrainingCamp()
		val preCampRecovery = turnsUntilCamp in 1..2 && estimatedEnergy < 60
		
		val needsRecovery = when {
			// Pre-camp energy management
			preCampRecovery -> {
				printToLog("[RECOVERY] Training camp in $turnsUntilCamp turns. Building energy (currently ~${estimatedEnergy.toInt()}%)")
				true
			}
			// No viable options at all (but only if we don't have high energy already)
			trainingsUnder30 == 0 && exceptionalTrainings.isEmpty() && estimatedEnergy < 80 -> {
				printToLog("[RECOVERY] No trainings under 30% and no exceptional options")
				true
			}
			// Very limited safe options (but only if energy isn't already high)
			trainingsUnder20 == 0 && exceptionalTrainings.size < 2 && estimatedEnergy < 70 -> {
				printToLog("[RECOVERY] No safe options and limited exceptional trainings")
				true
			}
			// Average failure too high with no mitigation
			avgFailureRate > recoveryThreshold && trainingsUnder10 == 0 -> {
				printToLog("[RECOVERY] High avg failure (${avgFailureRate.toInt()}%) with no low-risk options")
				true
			}
			else -> false
		}
		
		if (!needsRecovery) {
			val viableOptions = trainingsUnder20 + exceptionalTrainings.size
			printToLog("[RECOVERY] $viableOptions viable training options available - continuing")
		}
		
		return needsRecovery
	}
	
	/**
	 * Calculates the risk-reward ratio for a training option.
	 * Higher values mean better risk-reward ratio.
	 */
	private fun calculateRiskRewardRatio(training: Training): Double {
		// Enhanced stat value calculation with percentage-based completion awareness
		val statIndex = trainings.indexOf(training.name)
		val currentStat = currentStatsMap.getOrDefault(training.name, 0)
		val targetStat = statTargets.getOrElse(statIndex) { 600 }
		val completionPercent = if (targetStat > 0) (currentStat.toDouble() / targetStat * 100) else 100.0

		// Percentage-based deficit multiplier for smoother progression
		val deficitMultiplier = when {
			completionPercent < 30 -> 4.5   // Less than 30% complete - extreme priority
			completionPercent < 50 -> 3.5   // Less than 50% complete - critical
			completionPercent < 70 -> 2.5   // Less than 70% complete - high
			completionPercent < 85 -> 1.8   // Less than 85% complete - moderate
			completionPercent < 100 -> 1.3  // Almost complete - low priority
			completionPercent >= 100 -> {
				// Even if complete, consider if training provides exceptional value
				val totalStatGain = training.statGains.sum()
				if (totalStatGain > 80) 1.0      // Very high stat gain - still worth considering
				else if (totalStatGain > 60) 0.7 // High stat gain - reduced value
				else 0.4  // Normal gain - significantly reduced
			}
			else -> 0.5  // Significantly over target
		}
		val adjustedStatValue = training.statGains.sum() * deficitMultiplier

		// Enhanced relationship value with blue bar priority
		val blueBars = training.relationshipBars.count { it.dominantColor == "blue" }
		val highFillBars = training.relationshipBars.count { it.fillPercent >= 80 }
		val relationshipValue = blueBars * 60 + highFillBars * 40 + training.relationshipBars.size * 20

		// Rainbow training detection and bonus
		val rainbowBonus = when {
			highFillBars >= 3 -> 150.0  // Exceptional rainbow training
			blueBars >= 3 -> 120.0      // Multiple blue bars
			highFillBars >= 2 -> 60.0   // Good multi-friend training
			else -> 0.0
		}

		val totalValue = adjustedStatValue + relationshipValue + rainbowBonus

		// Refined risk factor with smoother progression
		val riskFactor = when {
			training.failureChance <= 5 -> 1.0   // Minimal risk
			training.failureChance <= 10 -> 1.1  // Low risk
			training.failureChance <= 15 -> 1.3  // Moderate risk
			training.failureChance <= 20 -> 1.6  // High risk
			training.failureChance <= 25 -> 2.2  // Very high risk
			training.failureChance <= 30 -> 3.0  // Extreme risk
			else -> 5.0  // Unacceptable risk
		}

		// Risk-reward ratio with value divided by risk
		val baseRatio = totalValue / riskFactor

		// Enhanced phase-based adjustments
		val phaseMultiplier = when {
			// Year 1: Prioritize relationships even with moderate risk
			currentDate.year == 1 && blueBars > 0 -> 1.4
			currentDate.year == 1 && training.relationshipBars.isNotEmpty() -> 1.2
			// Year 2: Balanced approach
			currentDate.year == 2 && highFillBars >= 2 -> 1.3
			// Year 3: Prioritize safe high-stat gains
			currentDate.year == 3 && training.statGains.sum() > 50 && training.failureChance <= 15 -> 1.3
			currentDate.year == 3 && training.failureChance <= 10 -> 1.2
			// Training camp bonus
			isInSummerTraining() || isInWinterTraining() -> 1.25
			else -> 1.0
		}

		return baseRatio * phaseMultiplier
	}
	
	/**
	 * Evaluates if Wit training is worth doing over resting.
	 * Enhanced with better compound value calculation and context awareness.
	 */
	private fun evaluateWitVsRest(witTraining: Training?, estimatedEnergy: Double): Boolean {
		if (witTraining == null) return false

		// Check if we're currently IN a training camp (last turn of camp)
		val isLastDayOfSummerCamp = currentDate.month == 7 && currentDate.phase == "Late"
		val isLastDayOfWinterCamp = currentDate.month == 1 && currentDate.phase == "Late"
		val isLastDayOfTrainingCamp = isLastDayOfSummerCamp || isLastDayOfWinterCamp

		// Special handling for LAST DAY of training camp
		if (isLastDayOfTrainingCamp) {
			// Check current mood (would need to be detected earlier in the turn)
			// For now, we'll assume mood is good if energy is high
			val likelyHasGoodMood = estimatedEnergy >= 60

			if (likelyHasGoodMood && witTraining.failureChance <= 30) {
				printToLog("[TRAINING CAMP] LAST DAY of camp! Mood likely Great, prioritizing Lv5 Wit training")
				printToLog("[TRAINING CAMP] Wit has ${witTraining.failureChance}% failure - acceptable for Lv5 benefits")
				printToLog("[TRAINING CAMP] Stats: ${witTraining.statGains.sum()}, Friends: ${witTraining.relationshipBars.size}")
				return true  // Do Wit training to maximize Lv5 benefits
			} else if (witTraining.failureChance > 30) {
				printToLog("[TRAINING CAMP] Last day but Wit failure too high (${witTraining.failureChance}%) - will rest")
				return false
			}
		}

		// Check if training camp is approaching (summer or winter) - more aggressive preparation
		val turnsUntilCamp = getTurnsUntilTrainingCamp()
		val isTrainingCampApproaching = turnsUntilCamp in 1..3  // Extended preparation window

		if (isTrainingCampApproaching) {
			// More aggressive energy preparation for camps
			val targetEnergy = when (turnsUntilCamp) {
				1 -> 80  // Next turn is camp - need very high energy
				2 -> 65  // 2 turns away - build energy aggressively
				3 -> 55  // 3 turns away - start preparation
				else -> 50
			}

			if (estimatedEnergy < targetEnergy) {
				printToLog("[WIT VS REST] Training camp in $turnsUntilCamp turns. Energy (~${estimatedEnergy.toInt()}%) below target ($targetEnergy%), prioritizing Rest")
				return false
			}
		}
		
		// Check if we've already reached Wit stat target
		val currentWit = currentStatsMap.getOrDefault("Wit", 0)
		val witTarget = statTargets.getOrNull(4) ?: 600
		val witDeficit = witTarget - currentWit
		
		// Get the actual Wit stat gain from the training
		val witStatGain = witTraining.statGains.getOrNull(4) ?: 0
		
		// If we're at or above target, only do Wit if it provides exceptional value
		if (witDeficit <= 0) {
			// We've exceeded the target
			val totalFriendships = witTraining.relationshipBars.count { it.fillPercent >= 80 }
			val hasLowFailure = witTraining.failureChance <= 10
			val hasExceptionalValue = witTraining.statGains.sum() >= 60 || totalFriendships >= 3
			
			if (!hasExceptionalValue && totalFriendships < 2) {
				printToLog("[WIT VS REST] Wit stat already at target ($currentWit/$witTarget). Rest is better unless exceptional value.")
				return false
			}
			printToLog("[WIT VS REST] Wit at target but training has exceptional value - considering it")
		} else if (witDeficit <= 50) {
			// We're very close to target
			if (witStatGain >= witDeficit * 2) {
				// This would overshoot significantly
				printToLog("[WIT VS REST] Wit training would overshoot target significantly ($currentWit + $witStatGain vs $witTarget)")
				// Only do it if it has other great benefits (friendships or low failure)
				val totalFriendships = witTraining.relationshipBars.count { it.fillPercent >= 80 }
				if (totalFriendships < 2 && witTraining.failureChance > 15) {
					return false
				}
			}
		}
		
		// Enhanced compound value calculation for Wit training
		val witStatValue = witTraining.statGains.sum()
		val witRelationshipValue = witTraining.relationshipBars.size * 25  // Increased base value
		val witNonMaxedFriendships = witTraining.relationshipBars.count { it.fillPercent >= 80 && it.fillPercent < 100 }
		val witMaxedFriendships = witTraining.relationshipBars.count { it.fillPercent >= 100 }
		val witBlueBars = witTraining.relationshipBars.count { it.dominantColor == "blue" }

		// Enhanced year-based friendship valuation with blue bar consideration
		val witFriendshipValue = when (currentDate.year) {
			1 -> {
				// Year 1: Blue bars extremely valuable for relationship building
				witBlueBars * 40 + witNonMaxedFriendships * 30 + witMaxedFriendships * 20
			}
			2 -> {
				// Year 2: Balanced value with emphasis on finishing relationships
				witBlueBars * 30 + witNonMaxedFriendships * 25 + witMaxedFriendships * 25
			}
			3 -> {
				// Year 3: Maxed relationships provide stat bonuses
				witBlueBars * 20 + witNonMaxedFriendships * 20 + witMaxedFriendships * 35
			}
			else -> witBlueBars * 25 + witNonMaxedFriendships * 25 + witMaxedFriendships * 25
		}
		// Don't count Wit's energy recovery in our calculations - user requested this
		// Wit recovers ~5 energy but we'll evaluate it purely on training value
		val witEnergyRecovery = 0  // Ignoring energy recovery per user request
		
		// Reduce value if we're already at/near Wit target
		val witTargetMultiplier = when {
			witDeficit <= 0 -> 0.1  // Already exceeded target - drastically reduce priority
			witDeficit <= 50 -> 0.25  // Very close to target - significantly reduce
			witDeficit <= 100 -> 0.5  // Close to target - moderately reduce
			else -> 1.0  // Still need Wit
		}
		
		// Total value per Wit training (adjusted for target proximity)
		val witValuePerTurn = (witStatValue * witTargetMultiplier + witRelationshipValue + witFriendshipValue).toInt()
		
		// Compare Wit training value vs Rest value without considering energy recovery
		// User requested not to count Wit's energy recovery in calculations
		// We'll evaluate purely based on stat and friendship gains
		val witSuccessRate = (100 - witTraining.failureChance) / 100.0
		
		// Expected value over multiple turns (considering failure chance)
		val expectedWitValue = witValuePerTurn * witSuccessRate
		
		// Rest value: Full energy recovery allows for better trainings later
		// Dynamic rest value based on context
		val restValue = when {
			turnsUntilCamp == 1 && estimatedEnergy < 75 -> 400  // Must rest before camp
			turnsUntilCamp == 2 && estimatedEnergy < 60 -> 300  // Pre-camp preparation critical
			estimatedEnergy < 20 -> 250  // Critical energy
			estimatedEnergy < 30 -> 180  // Low energy
			estimatedEnergy < 40 -> 120  // Moderate energy
			estimatedEnergy < 50 -> 80   // Decent energy
			else -> 40  // Good energy
		}
		
		// Enhanced decision factors with better context awareness
		val shouldDoWit = when {
			// Hard cap at 22% failure rate (with very rare exceptions)
			witTraining.failureChance > 22 -> {
				// Only allow higher failure in extreme circumstances
				val hasExceptionalValue = witBlueBars >= 4 ||
					(witNonMaxedFriendships + witMaxedFriendships) >= 5 ||
					witTraining.statGains.sum() > 100
				hasExceptionalValue && witTraining.failureChance <= 25
			}

			// Critical energy management
			estimatedEnergy < 20 -> {
				// Only consider Wit in extreme circumstances
				val isLastTurnBeforeURA = currentDate.year == 3 && currentDate.month == 12 && currentDate.phase == "Late"
				val hasExceptionalValue = witBlueBars >= 3 || (witNonMaxedFriendships + witMaxedFriendships) >= 4
				!isLastTurnBeforeURA && hasExceptionalValue && witTraining.failureChance <= 15
			}

			// Rainbow training detection - high value multi-friendship training
			witBlueBars >= 3 && witTraining.failureChance <= 25 -> {
				printToLog("[WIT VS REST] Rainbow training detected with $witBlueBars blue bars!")
				true
			}

			// Always rest if Wit target is exceeded significantly and energy is moderate
			witDeficit <= -100 && estimatedEnergy < 60 -> false

			// High-value blue bar training in Year 1
			currentDate.year == 1 && witBlueBars >= 2 && witTraining.failureChance <= 20 -> true

			// Multiple friendships with good stats
			(witNonMaxedFriendships + witMaxedFriendships) >= 3 && witStatValue >= 40 && witTraining.failureChance <= 20 -> true

			// Excellent stats with low risk
			witStatValue >= 60 && witTraining.failureChance <= 10 -> true

			// Good compound value
			expectedWitValue > 80 && witTraining.failureChance <= 15 -> true

			// Minimal value threshold
			witValuePerTurn < 40 -> false

			// Enhanced comparison with rest value
			else -> {
				// Consider energy state and game phase
				val multiplier = when {
					currentDate.year == 1 && witBlueBars > 0 -> 2.5  // Favor Wit with blue bars in Year 1
					estimatedEnergy < 40 -> 1.5  // Favor rest when energy is low
					else -> 2.0  // Standard comparison
				}
				expectedWitValue * multiplier > restValue
			}
		}
		
		if (shouldDoWit) {
			printToLog("[WIT VS REST] Wit training is worth doing: ${witValuePerTurn} value/turn, ${witTraining.failureChance}% failure")
			printToLog("[WIT VS REST] Current Wit: $currentWit/$witTarget (deficit: $witDeficit)")
			val friendshipNote = when (currentDate.year) {
				3 -> "Year 3 - maxed friendships provide max stat bonus"
				1 -> "Year 1 - non-maxed friendships are priority for progress"
				else -> "Balanced value from both"
			}
			printToLog("[WIT VS REST] Friendships: $witNonMaxedFriendships non-maxed, $witMaxedFriendships maxed ($friendshipNote)")
		} else {
			printToLog("[WIT VS REST] Rest is better: Wit only provides ${witValuePerTurn} value at ${witTraining.failureChance}% failure")
			if (isTrainingCampApproaching && estimatedEnergy < 70) {
				printToLog("[WIT VS REST] Pre-camp preparation: Resting to maximize Lv5 training benefits")
			} else if (witDeficit <= 0) {
				printToLog("[WIT VS REST] Wit target already reached: $currentWit/$witTarget")
			}
		}
		
		return shouldDoWit
	}

	/**
	 * Analyze all 5 Trainings for their details including stat gains, relationship bars, etc.
	 * Now checks ALL trainings first to make better decisions, and considers Wit training for energy recovery.
	 *
	 * @param test Flag that forces the failure chance through even if it is not in the acceptable range for testing purposes.
	 */
	private fun analyzeTrainings(test: Boolean = false) {
		printToLog("\n[TRAINING] Now starting process to analyze all 5 Trainings.")

		// Acquire the position of the speed stat text.
		val (speedStatTextLocation, _) = if (campaign == "Ao Haru") {
			imageUtils.findImage("aoharu_stat_speed", tries = 1, region = imageUtils.regionBottomHalf)
		} else {
			imageUtils.findImage("stat_speed", tries = 1, region = imageUtils.regionBottomHalf)
		}

		if (speedStatTextLocation != null) {
			// Start by selecting Speed training
			if (!imageUtils.confirmLocation("speed_training", tries = 1, region = imageUtils.regionTopHalf, suppressError = true)) {
				findAndTapImage("training_speed", region = imageUtils.regionBottomHalf)
				wait(0.5)
			}

			val initialFailureChance: Int = imageUtils.findTrainingFailureChance()
			if (initialFailureChance == -1) {
				printToLog("[WARNING] Skipping training due to not being able to confirm whether or not the bot is at the Training screen.")
				return
			}

			// Early exit if initial failure is very high - likely all trainings will be high
			// But don't exit early if we have low failure (high energy)
			if (!test && initialFailureChance > 50) {
				printToLog("[TRAINING] Initial failure chance is very high (${initialFailureChance}%). All trainings likely have high failure.")
				printToLog("[TRAINING] Going to rest instead of checking all trainings.")
				trainingMap.clear()  // Clear map to trigger rest
				return
			} else if (initialFailureChance <= 10) {
				printToLog("[TRAINING] Initial failure chance is very low (${initialFailureChance}%). Energy is likely high.")
			}
			
			// Analyze all trainings once to make informed decisions
			printToLog("[TRAINING] Checking all trainings to find the best option...")
			
			// Check all trainings to build a complete picture
			if (test || true) {  // Always check to make informed decisions
				var highFailureCount = 0  // Track consecutive high failures
				
				// Iterate through every training that is not blacklisted.
				trainings.forEachIndexed { index, training ->
					if (blacklist.getOrElse(index) { "" } == training) {
						printToLog("[TRAINING] Skipping $training training due to being blacklisted.")
						return@forEachIndexed
					}

					// Select the Training to make it active except Speed Training since that is already selected at the start.
					val newX: Double = when (training) {
						"Stamina" -> {
							280.0
						}
						"Power" -> {
							402.0
						}
						"Guts" -> {
							591.0
						}
						"Wit" -> {
							779.0
						}
						else -> {
							0.0
						}
					}

					if (newX != 0.0) {
						if (imageUtils.isTablet) {
							if (training == "Stamina") {
								tap(
									speedStatTextLocation.x + imageUtils.relWidth((newX * 1.05).toInt()),
									speedStatTextLocation.y + imageUtils.relHeight((319 * 1.50).toInt()),
									"training_option_circular",
									ignoreWaiting = true
								)
							} else {
								tap(
									speedStatTextLocation.x + imageUtils.relWidth((newX * 1.36).toInt()),
									speedStatTextLocation.y + imageUtils.relHeight((319 * 1.50).toInt()),
									"training_option_circular",
									ignoreWaiting = true
								)
							}
						} else {
							tap(
								speedStatTextLocation.x + imageUtils.relWidth(newX.toInt()),
								speedStatTextLocation.y + imageUtils.relHeight(319),
								"training_option_circular",
								ignoreWaiting = true
							)
						}
					}

					// Update the object in the training map.
					// Use CountDownLatch to run the 3 operations in parallel to cut down on processing time.
					val latch = CountDownLatch(3)

					// Variables to store results from parallel threads.
					var statGains: IntArray = intArrayOf()
					var failureChance: Int = -1
					var relationshipBars: ArrayList<ImageUtils.BarFillResult> = arrayListOf()

					// Get the Points and source Bitmap beforehand before starting the threads to make them safe for parallel processing.
					val (skillPointsLocation, sourceBitmap) = imageUtils.findImage("skill_points", tries = 1, region = imageUtils.regionMiddle)
					val (trainingSelectionLocation, _) = imageUtils.findImage("training_failure_chance", tries = 1, region = imageUtils.regionBottomHalf)

					// Thread 1: Determine stat gains.
					Thread {
						try {
							statGains = imageUtils.determineStatGainFromTraining(training, sourceBitmap, skillPointsLocation!!)
						} catch (e: Exception) {
							printToLog("[ERROR] Error in determineStatGainFromTraining: ${e.stackTraceToString()}", isError = true)
							statGains = intArrayOf(0, 0, 0, 0, 0)
						} finally {
							latch.countDown()
						}
					}.start()

					// Thread 2: Find failure chance.
					Thread {
						try {
							failureChance = imageUtils.findTrainingFailureChance(sourceBitmap, trainingSelectionLocation!!)
						} catch (e: Exception) {
							printToLog("[ERROR] Error in findTrainingFailureChance: ${e.stackTraceToString()}", isError = true)
							failureChance = -1
						} finally {
							latch.countDown()
						}
					}.start()

					// Thread 3: Analyze relationship bars.
					Thread {
						try {
							relationshipBars = imageUtils.analyzeRelationshipBars(sourceBitmap)
						} catch (e: Exception) {
							printToLog("[ERROR] Error in analyzeRelationshipBars: ${e.stackTraceToString()}", isError = true)
							relationshipBars = arrayListOf()
						} finally {
							latch.countDown()
						}
					}.start()

					// Wait for all threads to complete.
					try {
						latch.await(10, TimeUnit.SECONDS)
					} catch (_: InterruptedException) {
						printToLog("[ERROR] Parallel training analysis timed out", isError = true)
					}

					val newTraining = Training(
						name = training,
						statGains = statGains,
						failureChance = failureChance,
						relationshipBars = relationshipBars
					)
					trainingMap.put(training, newTraining)
					
					// Track high failures and potentially skip remaining checks
					if (failureChance > 40) {
						highFailureCount++
						if (highFailureCount >= 3 && training != "Wit") {
							printToLog("[TRAINING] Found 3+ consecutive trainings with >40% failure. Skipping remaining checks.")
							// Still need to check Wit training for recovery option
							if (index < trainings.size - 1 && trainings[trainings.size - 1] == "Wit") {
								// Jump to Wit training directly
								printToLog("[TRAINING] Checking Wit training for recovery option...")
								// Will check Wit in the next iteration
							} else {
								return@forEachIndexed
							}
						}
					} else {
						highFailureCount = 0  // Reset counter if we find acceptable training
					}
				}

				// After analyzing all trainings, decide what to do
				printToLog("[TRAINING] Process to analyze all 5 Trainings complete.")
				
				// Count friendship trainings available (80%+ friendships provide stat bonuses)
				val friendshipTrainingCount = trainingMap.values.count { training ->
					training.relationshipBars.any { bar -> bar.fillPercent >= 80 }
				}
				
				// Separate counts for better decision making
				val nonMaxedFriendshipCount = trainingMap.values.count { training ->
					training.relationshipBars.any { bar -> bar.fillPercent >= 80 && bar.fillPercent < 100 }
				}
				
				val maxedFriendshipCount = trainingMap.values.count { training ->
					training.relationshipBars.any { bar -> bar.fillPercent >= 100 }
				}
				
				if (maxedFriendshipCount > 0) {
					printToLog("[TRAINING] $maxedFriendshipCount trainings have maxed friendships (100% = max stat bonus)")
				}
				if (nonMaxedFriendshipCount > 0) {
					printToLog("[TRAINING] $nonMaxedFriendshipCount trainings have non-maxed friendships (80-99% = relationship progress)")
				}
				
				// Calculate average failure rate to understand energy state
				val avgFailureRate = trainingMap.values
					.filter { it.failureChance >= 0 }
					.map { it.failureChance }
					.average()
				
				// Estimate current energy based on failure rates
				// Formula: Energy ≈ 50 - (FailureRate * 2)
				val estimatedEnergy = (50 - avgFailureRate * 2).coerceIn(0.0, 100.0)
				printToLog("[TRAINING] Estimated energy: ${estimatedEnergy.toInt()}% (avg failure: ${avgFailureRate.toInt()}%)")
				
				// Calculate the value of the best friendship training
				// Both maxed and non-maxed friendships are valuable, but for different reasons
				val bestFriendshipValue = trainingMap.values
					.filter { training -> training.relationshipBars.any { it.fillPercent >= 80 } }
					.maxOfOrNull { training -> 
						val nonMaxedFriends = training.relationshipBars.count { it.fillPercent >= 80 && it.fillPercent < 100 }
						val maxedFriends = training.relationshipBars.count { it.fillPercent >= 100 }
						val statValue = training.statGains.sum()
						
						// Calculate value based on context
						val friendshipValue = when {
							// Year 1: Relationship progress is more valuable
							currentDate.year == 1 -> nonMaxedFriends * 60 + maxedFriends * 30 + statValue
							// Year 2: Balanced - both are valuable
							currentDate.year == 2 -> nonMaxedFriends * 50 + maxedFriends * 40 + statValue
							// Year 3: Stat gains are more valuable (maxed friendships give max stats)
							currentDate.year == 3 -> nonMaxedFriends * 40 + maxedFriends * 50 + statValue
							else -> nonMaxedFriends * 50 + maxedFriends * 35 + statValue
						}
						friendshipValue
					} ?: 0
				
				// Check if training camp is approaching
				// There are TWO training camps in Uma Musume that provide enhanced training benefits (Lv5 training):
				// 1. Summer Training Camp: Starts at Late June (month 6, phase "Late") and continues into July
				// 2. Winter Training Camp: Starts at Late December (month 12, phase "Late") and continues into January
				// Each month has 2 phases (Early and Late), so we need to prepare 2-3 turns before
				
				val isTrainingCampApproaching = when {
					// SUMMER TRAINING CAMP
					// 1 turn before summer: Early June
					currentDate.month == 6 && currentDate.phase == "Early" -> {
						printToLog("[TRAINING] Summer training camp starts NEXT TURN (currently Early June)!")
						true
					}
					// 2 turns before summer: Late May
					currentDate.month == 5 && currentDate.phase == "Late" -> {
						printToLog("[TRAINING] Summer training camp in 2 turns (currently Late May)")
						true
					}
					// 3 turns before summer: Early May (start preparing)
					currentDate.month == 5 && currentDate.phase == "Early" -> {
						printToLog("[TRAINING] Summer training camp in 3 turns (currently Early May) - begin preparation")
						true
					}
					
					// WINTER TRAINING CAMP
					// 1 turn before winter: Early December
					currentDate.month == 12 && currentDate.phase == "Early" -> {
						printToLog("[TRAINING] Winter training camp starts NEXT TURN (currently Early December)!")
						true
					}
					// 2 turns before winter: Late November
					currentDate.month == 11 && currentDate.phase == "Late" -> {
						printToLog("[TRAINING] Winter training camp in 2 turns (currently Late November)")
						true
					}
					// 3 turns before winter: Early November (start preparing)
					currentDate.month == 11 && currentDate.phase == "Early" -> {
						printToLog("[TRAINING] Winter training camp in 3 turns (currently Early November) - begin preparation")
						true
					}
					
					else -> false
				}
				
				// Pre-training camp rest strategy based on FAILURE RATES, not estimated energy
				if (isTrainingCampApproaching) {
					// Check actual failure rates to decide on rest
					val lowestFailure = trainingMap.values.minOfOrNull { it.failureChance } ?: 100
					val turnsUntilCamp = getTurnsUntilTrainingCamp()

					val shouldRestForCamp = when (turnsUntilCamp) {
						1 -> lowestFailure > 10  // 1 turn before: rest if ALL trainings >10% failure
						2 -> lowestFailure > 10  // 2 turns before: rest if ALL trainings >10% failure
						3 -> lowestFailure > 15  // 3 turns before: rest if ALL trainings >15% failure
						else -> false
					}

					if (shouldRestForCamp) {
						printToLog("[TRAINING] PRE-CAMP: ${turnsUntilCamp} turns until camp. Lowest failure: ${lowestFailure}%")
						printToLog("[TRAINING] Will prioritize rest to have energy for Lv5 training facilities")
					}
				}
				
				// Check if we're currently IN a training camp (not just approaching)
				// Training camps occur at:
				// - Summer: Late June (month 6, phase "Late") and July (month 7)
				// - Winter: Late December (month 12, phase "Late") and January (month 1)
				val isInTrainingCamp = when {
					// Summer training camp
					currentDate.month == 6 && currentDate.phase == "Late" -> true
					currentDate.month == 7 -> true
					// Winter training camp
					currentDate.month == 12 && currentDate.phase == "Late" -> true
					currentDate.month == 1 -> true
					// Also check for summer image indicator as backup
					else -> imageUtils.findImage("recover_energy_summer", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true).first != null
				}
				
				// Dynamic risk management based on training value
				val adjustedFailureThreshold = when {
					// EXCEPTIONAL VALUE - Worth up to 30% risk
					friendshipTrainingCount >= 3 && bestFriendshipValue > 300 -> {
						printToLog("[TRAINING] EXCEPTIONAL: $friendshipTrainingCount friendships (value: $bestFriendshipValue) - accepting up to 30% failure")
						30
					}
					
					// DURING TRAINING CAMP - Don't risk training failure above 20%
					// Better to train Wit or Rest than waste the Lv5 opportunity
					isInTrainingCamp -> {
						printToLog("[TRAINING] CAMP LV5: Maximum 20% failure threshold (avoid wasting Lv5 training)")
						20
					}
					
					// PRE-TRAINING CAMP PREPARATION - Use failure rates directly
					isTrainingCampApproaching -> {
						val turnsUntilCamp = getTurnsUntilTrainingCamp()
						val threshold = when (turnsUntilCamp) {
							1 -> 10  // Next turn is camp: very conservative
							2 -> 10  // 2 turns: still conservative
							3 -> 15  // 3 turns: slightly more relaxed
							else -> 20
						}
						printToLog("[TRAINING] PRE-CAMP: ${turnsUntilCamp} turns until camp, using ${threshold}% failure threshold")
						threshold
					}
					
					// HIGH VALUE OPPORTUNITIES - Dynamic based on value
					friendshipTrainingCount >= 3 && bestFriendshipValue > 200 -> {
						printToLog("[TRAINING] HIGH VALUE: $friendshipTrainingCount friendships - up to 25% failure")
						25
					}
					friendshipTrainingCount >= 2 && bestFriendshipValue > 150 -> {
						printToLog("[TRAINING] GOOD VALUE: $friendshipTrainingCount friendships - up to 20% failure")
						20
					}
					friendshipTrainingCount >= 1 && bestFriendshipValue > 100 -> {
						printToLog("[TRAINING] MODERATE VALUE: 1 friendship - up to 18% failure")
						18
					}
					
					// GAME PHASE ADJUSTMENTS - Base thresholds
					currentDate.year == 3 && currentDate.month == 12 && currentDate.phase == "Early" -> {
						// URA Finals next turn - ultra conservative
						printToLog("[TRAINING] URA FINALS NEXT TURN: Ultra conservative 10% failure")
						10
					}
					currentDate.year == 3 && currentDate.month >= 11 -> {
						// Late game: preparing for URA Finals
						printToLog("[TRAINING] LATE GAME: Year 3, Month 11+ - base 15% failure")
						15
					}
					currentDate.year == 3 -> {
						// Late game: can accept more risk for stat gains
						printToLog("[TRAINING] Year 3: Stat maximization - base 20% failure")
						20
					}
					currentDate.year == 2 -> {
						// Mid game: balanced approach
						printToLog("[TRAINING] Year 2: Balanced - base 18% failure")
						18
					}
					currentDate.year == 1 -> {
						// Early game: focus on relationships
						printToLog("[TRAINING] Year 1: Relationship focus - base 18% failure")
						18
					}
					
					// DEFAULT - Standard threshold
					else -> {
						printToLog("[TRAINING] Default threshold - base 15% failure")
						15
					}
				}
				
				// Check for special conditions that might affect risk tolerance
				val riskAdjustment = when {
					// If we're close to stat caps, be more conservative
					currentStatsMap.values.any { it >= currentStatCap - 50 } -> {
						printToLog("[TRAINING] Near stat cap - reducing risk tolerance by 3%")
						-3
					}
					// If we have high stat deficits, accept more risk
					else -> {
						val maxDeficit = trainings.withIndex().maxOfOrNull { (index, stat) ->
							statTargets[index] - currentStatsMap.getOrDefault(stat, 0)
						} ?: 0
						if (maxDeficit > 300) {
							printToLog("[TRAINING] High stat deficit ($maxDeficit) - increasing risk tolerance by 3%")
							3
						} else {
							0
						}
					}
				}
				
				// Stricter limit: Hard cap at 22% with rare exceptions
				val dynamicMaxLimit = when {
					// Only allow >22% for truly exceptional opportunities
					friendshipTrainingCount >= 4 && bestFriendshipValue > 400 -> 25  // Ultra exceptional
					friendshipTrainingCount >= 3 && bestFriendshipValue > 350 &&
						trainingMap.values.any { it.statGains.sum() > 90 } -> 24  // Exceptional with high stats
					else -> 22  // Standard hard cap at 22%
				}
				val finalThreshold = (adjustedFailureThreshold + riskAdjustment).coerceIn(5, dynamicMaxLimit)
				printToLog("[TRAINING] Final failure threshold: $finalThreshold% (dynamic limit: $dynamicMaxLimit%)")
				
				// Check if we should prioritize recovery
				val needsRecovery = shouldPrioritizeRecovery(avgFailureRate, estimatedEnergy)
				
				// Check if we have any acceptable training options with adjusted threshold
				val acceptableTrainings = trainingMap.values.filter { it.failureChance <= finalThreshold && it.failureChance >= 0 }
				val witTraining = trainingMap["Wit"]
				
				// Calculate risk-reward ratios for acceptable trainings
				val trainingsWithRatios = acceptableTrainings.map { training ->
					training to calculateRiskRewardRatio(training)
				}.sortedByDescending { it.second }
				
				// Log the best risk-reward options
				if (trainingsWithRatios.isNotEmpty()) {
					printToLog("[TRAINING] Best risk-reward ratios:")
					trainingsWithRatios.take(3).forEach { (training, ratio) ->
						printToLog("  - ${training.name}: ${ratio.toInt()} (${training.failureChance}% risk, ${training.statGains.sum()} stats, ${training.relationshipBars.size} friends)")
					}
				}
				
				// Use the new evaluation function for Wit vs Rest decision
				val witIsBetterThanRest = evaluateWitVsRest(witTraining, estimatedEnergy)
				
				// Decision logic based on recovery needs and available options
				if (needsRecovery) {
					if (witIsBetterThanRest) {
						printToLog("[RECOVERY] Need recovery but Wit training provides good value - will do Wit")
						// Keep only Wit in the map
						trainingMap.clear()
						trainingMap["Wit"] = witTraining!!
					} else {
						printToLog("[RECOVERY] Need recovery and no good Wit option - will rest")
						trainingMap.clear()
					}
				} else if (acceptableTrainings.isNotEmpty()) {
					// Filter training map to only acceptable trainings with good risk-reward
					val minRiskReward = if (estimatedEnergy > 40) 30.0 else 40.0
					val goodTrainings = trainingsWithRatios
						.filter { it.second >= minRiskReward }
						.map { it.first }
					
					if (goodTrainings.isNotEmpty()) {
						printToLog("[TRAINING] Found ${goodTrainings.size} training(s) with good risk-reward ratios (>= $minRiskReward)")
						// Keep only good trainings in the map
						val goodTrainingNames = goodTrainings.map { it.name }.toSet()
						trainingMap.entries.removeIf { it.key !in goodTrainingNames }
					} else {
						printToLog("[TRAINING] Found ${acceptableTrainings.size} acceptable training(s) but risk-reward ratios are low")
						// If energy is high, keep the acceptable trainings anyway
						if (estimatedEnergy >= 70) {
							printToLog("[TRAINING] Energy is high (~${estimatedEnergy.toInt()}%), keeping acceptable trainings despite low ratios")
							val acceptableNames = acceptableTrainings.map { it.name }.toSet()
							trainingMap.entries.removeIf { it.key !in acceptableNames }
						}
					}
				} else if (witIsBetterThanRest) {
					// Wit training is better than resting - keep it in the map for scoring
					printToLog("[TRAINING] No trainings within normal range, but Wit training is better than resting:")
					printToLog("[TRAINING] - Wit has ${witTraining!!.relationshipBars.size} friends, ${witTraining.statGains.sum()} total stats, ${witTraining.failureChance}% failure")
					// Keep only Wit
					trainingMap.clear()
					trainingMap["Wit"] = witTraining
				} else if (witTraining != null && witTraining.failureChance <= 40) {
					// Wit is available but not amazing - still consider it
					printToLog("[TRAINING] No trainings within acceptable range. Wit training available at ${witTraining.failureChance}% failure.")
					printToLog("[TRAINING] Wit has ${witTraining.relationshipBars.size} friends and ${witTraining.statGains.sum()} total stats.")
					if (witTraining.statGains.sum() > 30 || witTraining.relationshipBars.isNotEmpty()) {
						printToLog("[TRAINING] Will consider Wit training as it provides some value.")
					} else {
						printToLog("[TRAINING] Wit training doesn't provide enough value. Will rest instead.")
						trainingMap.clear()
					}
				} else {
					// No good options - need to recover energy
					printToLog("[TRAINING] No viable training options. All failure chances too high. Proceeding to recover energy.")
					trainingMap.clear()
				}
			} else {
				// This shouldn't happen anymore since we always check all trainings
				printToLog("[TRAINING] Unable to analyze trainings properly.")
				trainingMap.clear()
			}
		}
	}

	/**
	 * Recommends the best training option based on current game state and strategic priorities.
	 *
	 * This function implements a sophisticated training recommendation system that adapts to different
	 * phases of the game. It uses different scoring algorithms depending on the current game year:
	 *
	 * **Early Game (Pre-Debut/Year 1):**
	 * - Focuses on relationship building using `scoreFriendshipTraining()`
	 * - Prioritizes training options that build friendship bars, especially blue bars
	 * - Ignores stat gains in favor of relationship development
	 *
	 * **Mid/Late Game (Year 2+):**
	 * - Uses comprehensive scoring via `scoreStatTrainingEnhanced()`
	 * - Combines stat efficiency (60-70%), relationship building (10%), and context bonuses (30%)
	 * - Adapts weighting based on whether relationship bars are present
	 *
	 * The scoring system considers multiple factors:
	 * - **Stat Efficiency:** How well training helps achieve target stats for the preferred race distance
	 * - **Relationship Building:** Value of friendship bar progress with diminishing returns
	 * - **Context Bonuses:** Phase-specific bonuses and stat gain thresholds
	 * - **Blacklist Compliance:** Excludes blacklisted training options
	 * - **Stat Cap Respect:** Avoids training that would exceed stat caps when enabled
	 *
	 * @return The name of the recommended training option, or empty string if no suitable option found.
	 */
	private fun recommendTraining(): String {
		/**
		 * Scores the currently selected training option during Junior Year based on friendship bar progress.
		 *
		 * This algorithm prefers training options with the least relationship progress (especially blue bars).
		 * It ignores stat gains unless all else is equal.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A score representing relationship-building value.
		 */
		fun scoreFriendshipTraining(training: Training): Double {
			// Ignore the blacklist in favor of making sure we build up the relationship bars as fast as possible.
			printToLog("\n[TRAINING] Starting process to score ${training.name} Training with a focus on building relationship bars.")

			val barResults = training.relationshipBars
			if (barResults.isEmpty()) return Double.NEGATIVE_INFINITY

			var score = 0.0
			for (bar in barResults) {
				val contribution = when (bar.dominantColor) {
					"orange" -> 0.0
					"green" -> 1.0
					"blue" -> 2.5
					else -> 0.0
				}
				score += contribution
			}

			printToLog("[TRAINING] ${training.name} Training has a score of ${decimalFormat.format(score)} with a focus on building relationship bars.")
			return score
		}

		/**
		 * Calculates the efficiency score for stat gains based on target achievement and priority weights.
		 *
		 * This function evaluates how well a training option helps achieve stat targets by considering:
		 * - The gap between current stats and target stats
		 * - Priority weights that vary by game year (higher priority in later years)
		 * - Efficiency bonuses for closing gaps vs diminishing returns for overage
		 * - Spark stat target focus when enabled (Speed, Stamina, Power to 600+)
		 * - Enhanced priority weighting for top 3 stats to prevent target completion from overriding large gains
		 *
		 * @param training The training option to evaluate.
		 * @param target Array of target stat values for the preferred race distance.
		 *
		 * @return A normalized score (0-100) representing stat efficiency.
		 */
		fun calculateStatEfficiencyScore(training: Training, target: IntArray): Double {
			// Algorithm: Weighted Normalized Utility Function with Diminishing Returns
			// This algorithm balances multiple objectives:
			// 1. Maximize total stat gains (efficiency)
			// 2. Prioritize stats with larger deficits (urgency)
			// 3. Respect stat prioritization (strategy)
			// 4. Apply diminishing returns as stats approach targets

			var totalScore = 0.0
			val totalStatGain = training.statGains.sum()

			// Base score from total stat gain to ensure high-value trainings are preferred
			val efficiencyBase = totalStatGain * 2.0

			for ((index, stat) in trainings.withIndex()) {
				val currentStat = currentStatsMap.getOrDefault(stat, 0)
				val targetStat = target.getOrElse(index) { 0 }
				val statGain = training.statGains.getOrElse(index) { 0 }

				if (statGain > 0 && targetStat > 0) {
					// Calculate normalized progress (0-1 scale)
					val currentProgress = (currentStat.toDouble() / targetStat).coerceIn(0.0, 1.0)
					val newProgress = ((currentStat + statGain).toDouble() / targetStat).coerceIn(0.0, 1.0)
					val progressGain = newProgress - currentProgress

					// Marginal utility with diminishing returns (logarithmic utility function)
					// This naturally balances between high-deficit and high-gain trainings
					val marginalUtility = if (currentProgress < 1.0) {
						// Using log(1 + x) to avoid log(0) and provide smooth diminishing returns
						val currentUtility = -Math.log(1.01 - currentProgress)  // Approaches infinity as we near completion
						val newUtility = -Math.log(1.01 - newProgress)
						(newUtility - currentUtility) * 100  // Scale up for meaningful scores
					} else {
						// Over target - minimal value
						progressGain * 10
					}

					// Priority weighting based on stat importance
					val priorityIndex = statPrioritization.indexOf(stat)
					val priorityMultiplier = when (priorityIndex) {
						0 -> 3.0   // Highest priority stat
						1 -> 2.5   // Second priority
						2 -> 2.0   // Third priority
						3 -> 1.5   // Fourth priority
						4 -> 1.0   // Fifth priority
						else -> 0.7  // Non-prioritized
					}

					// Year-based adjustment (early game vs late game focus)
					val yearMultiplier = when (currentDate.year) {
						1 -> 0.8  // Year 1: Less stat focus, more friendship focus
						2 -> 1.0  // Year 2: Balanced
						3 -> 1.2  // Year 3: Heavy stat focus
						else -> 1.0
					}

					// Calculate stat-specific score
					val statScore = marginalUtility * priorityMultiplier * yearMultiplier

					// Special handling for Spark stats (Speed, Stamina, Power to 600)
					if (focusOnSparkStatTarget && (stat == "Speed" || stat == "Stamina" || stat == "Power")) {
						val sparkTarget = 600
						if (currentStat < sparkTarget) {
							val sparkProgress = currentStat.toDouble() / sparkTarget
							val sparkNewProgress = (currentStat + statGain).toDouble() / sparkTarget
							val sparkUtility = if (sparkProgress < 1.0) {
								val currentSparkUtility = -Math.log(1.01 - sparkProgress)
								val newSparkUtility = -Math.log(1.01 - Math.min(sparkNewProgress, 1.0))
								(newSparkUtility - currentSparkUtility) * 100
							} else {
								0.0
							}
							// Use the higher utility between normal target and spark target
							totalScore += Math.max(statScore, sparkUtility * priorityMultiplier * yearMultiplier)
						} else {
							totalScore += statScore
						}
					} else {
						totalScore += statScore
					}

					Log.d(tag, "[DEBUG] $stat: Gain=$statGain, Progress=${(currentProgress*100).toInt()}%→${(newProgress*100).toInt()}%, Utility=$marginalUtility, Priority=$priorityMultiplier")
				}
			}

			// Combine efficiency base with utility scores
			// This ensures that a training giving 50 points to a needed stat beats
			// a training giving 30 points to a slightly more needed stat
			val finalScore = efficiencyBase + totalScore

			// Apply penalty if all stats are near completion to encourage other activities
			val allStatsNearComplete = trainings.all { stat ->
				val current = currentStatsMap.getOrDefault(stat, 0)
				val targetValue = target.getOrElse(trainings.indexOf(stat)) { 0 }
				targetValue == 0 || (current.toDouble() / targetValue) > 0.9
			}

			if (allStatsNearComplete) {
				return finalScore * 0.5  // Reduce score to encourage friendship building or rest
			}

			Log.d(tag, "[DEBUG] Training ${training.name}: Total Gain=$totalStatGain, Final Score=$finalScore")
			return finalScore.coerceAtMost(1000.0)
		}

		/**
		 * Calculates relationship building score with diminishing returns.
		 *
		 * Evaluates the value of relationship bars based on their color and fill level:
		 * - Blue bars: 2.5 points (highest priority)
		 * - Green bars: 1.0 points (medium priority)  
		 * - Orange bars: 0.0 points (no value)
		 *
		 * Applies diminishing returns as bars fill up and early game bonuses for relationship building.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A normalized score (0-100) representing relationship building value.
		 */
		fun calculateRelationshipScore(training: Training): Double {
			if (training.relationshipBars.isEmpty()) return 0.0

			var score = 0.0
			var maxScore = 0.0

			for (bar in training.relationshipBars) {
				// Relationship bar values from the guide
				val baseValue = when (bar.dominantColor) {
					"blue" -> 2.5    // Blue bars: Always highest priority
					"green" -> 1.0   // Green bars: Secondary priority
					"orange" -> 0.3  // Orange bars: Minimal value
					else -> 0.0
				}

				if (baseValue > 0) {
					// Apply diminishing returns for relationship building
					val fillLevel = bar.fillPercent / 100.0
					val diminishingFactor = 1.0 - (fillLevel * 0.5) // Less valuable as bars fill up

					// Year-based focus from the guide
					// Year 1: 55% relationship focus, Year 2: 50/50, Year 3: 30% relationships
					val yearMultiplier = when {
						currentDate.year == 1 || currentDate.phase == "Pre-Debut" -> 1.55  // 55% focus
						currentDate.year == 2 -> 1.0   // 50/50 balanced
						currentDate.year == 3 -> 0.6   // 30% relationships, 70% stats
						else -> 1.0
					}

					val contribution = baseValue * diminishingFactor * yearMultiplier
					score += contribution
					maxScore += 2.5 * 1.55  // Max possible value
				}
			}

			return if (maxScore > 0) (score / maxScore * 100.0) else 0.0
		}

		/**
		 * Calculates context-aware bonuses and penalties based on game phase and training properties.
		 *
		 * Applies various bonuses including:
		 * - Phase-specific bonuses (relationship focus in early game, stat efficiency in later years)
		 * - Stat gain thresholds that provide additional bonuses
		 * - Mood effects on training gains
		 * - Energy recovery bonus for Wit training when energy is low
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A context score between 0-200 representing situational bonuses.
		 */
		fun calculateContextScore(training: Training): Double {
			// Start with neutral score.
			var score = 100.0

			// Apply mood multiplier based on current mood
			val currentMood: String = when {
				imageUtils.findImage("mood_great", tries = 1, region = imageUtils.regionTopHalf, suppressError = true).first != null -> "Great"
				imageUtils.findImage("mood_good", tries = 1, region = imageUtils.regionTopHalf, suppressError = true).first != null -> "Good"
				imageUtils.findImage("mood_normal", tries = 1, region = imageUtils.regionTopHalf, suppressError = true).first != null -> "Normal"
				else -> "Bad/Awful"
			}
			
			// More accurate mood multipliers from the guide
			val moodMultiplier = when (currentMood) {
				"Great" -> 1.2    // +20% to all stat gains (絶好調)
				"Good" -> 1.1     // +10% to all stat gains (好調)
				"Normal" -> 1.0   // No modifier (普通)
				"Bad" -> 0.9      // -10% to all stat gains (不調)
				"Awful" -> 0.8    // -20% to all stat gains (絶不調)
				else -> 0.9       // Assume Bad if unknown
			}
			
			// Apply mood bonus to score - more impactful for stat-heavy trainings
			if (training.statGains.sum() > 30) {
				score *= moodMultiplier
				printToLog("[TRAINING] Mood multiplier ${moodMultiplier}x applied for ${training.name} (${currentMood} mood)")
			}
			
			// Wit training evaluation - NO energy recovery bonus per user request
			if (training.name == "Wit") {
				// Evaluate Wit ONLY on its training value, not energy recovery
				// Wit provides: 2 Speed, 9 Wisdom, 4 SP at level 1
				val witStatValue = training.statGains.sum()
				val witFriendValue = training.relationshipBars.size * 10  // Each friend bar worth ~10 points

				// Check if Wit has exceptional training value (multiple friends, high stats)
				if (training.relationshipBars.count { it.dominantColor == "blue" } >= 2) {
					score += 30  // Bonus for multiple blue bars
					printToLog("[TRAINING] Wit has ${training.relationshipBars.count { it.dominantColor == "blue" }} blue bars: +30")
				}

				// Additional bonus if Wit stat is below target
				val currentWit = currentStatsMap.getOrDefault("Wit", 0)
				val witTarget = statTargets.getOrNull(4) ?: 600
				val witDeficit = witTarget - currentWit
				if (witDeficit > 100) {
					val deficitBonus = minOf(witDeficit / 10, 30)  // Max 30 bonus
					score += deficitBonus
					printToLog("[TRAINING] Wit deficit ${witDeficit}: +${deficitBonus}")
				}
			}

			// Dynamic bonuses based on game phase
			when {
				currentDate.year == 1 || currentDate.phase == "Pre-Debut" -> {
					// Year 1: Focus on relationship building (55% weight according to guide)
					if (training.relationshipBars.isNotEmpty()) {
						score += training.relationshipBars.size * 15.0  // Dynamic based on friend count
					}
					// Small bonus for decent stat gains
					val statBonus = minOf(training.statGains.sum() * 0.5, 20.0)
					if (statBonus > 0) {
						score += statBonus
						printToLog("[TRAINING] Year 1 stat bonus: +${statBonus.toInt()}")
					}
				}
				currentDate.year == 2 -> {
					// Year 2: Balanced approach (50/50 according to guide)
					// Stat gains become more important
					val statBonus = minOf(training.statGains.sum() * 0.8, 40.0)
					score += statBonus
					if (statBonus > 20) {
						printToLog("[TRAINING] Year 2 stat bonus: +${statBonus.toInt()}")
					}
				}
				currentDate.year == 3 -> {
					// Year 3: Stat maximization (70% weight according to guide)
					// High value on large stat gains
					val statBonus = minOf(training.statGains.sum() * 1.2, 60.0)
					score += statBonus
					// Additional bonus for very high stat trainings
					if (training.statGains.sum() > 40) {
						val extraBonus = (training.statGains.sum() - 40) * 0.5
						score += extraBonus
						printToLog("[TRAINING] Year 3 high stat bonus: +${(statBonus + extraBonus).toInt()}")
					}
				}
			}

			// Bonuses for skill hints - each hint is valuable but not overwhelming
			val skillHintLocations = imageUtils.findAll(
				"stat_skill_hint",
				region = intArrayOf(
					MediaProjectionService.displayWidth - (MediaProjectionService.displayWidth / 3),
					0,
					(MediaProjectionService.displayWidth / 3),
					MediaProjectionService.displayHeight - (MediaProjectionService.displayHeight / 3)
				)
			)
			if (skillHintLocations.isNotEmpty()) {
				val hintBonus = skillHintLocations.size * 25.0  // 25 points per hint
				score += hintBonus
				printToLog("[TRAINING] Skill hint bonus: +${hintBonus.toInt()} for ${skillHintLocations.size} hints")
			}
			
			// Rainbow training (multiple friendship training) bonus
			// This is valuable but should be proportional to the actual benefit
			val highFriendshipBars = training.relationshipBars.count { bar -> 
				bar.dominantColor == "blue" && bar.fillPercent >= 80 
			}
			if (highFriendshipBars >= 2) {
				// Rainbow training typically gives 50-100% more stats
				val rainbowBonus = training.statGains.sum() * 0.5 * highFriendshipBars
				score += rainbowBonus
				printToLog("[TRAINING] Rainbow training bonus: +${rainbowBonus.toInt()} for ${highFriendshipBars} high friendship bars")
			}
			
			// Training camp special handling (summer and winter)
			val isInTrainingCamp = when {
				// Summer training camp
				currentDate.month == 6 && currentDate.phase == "Late" -> true
				currentDate.month == 7 -> true
				// Winter training camp
				currentDate.month == 12 && currentDate.phase == "Late" -> true
				currentDate.month == 1 -> true
				// Also check for summer image indicator as backup
				else -> imageUtils.findImage("recover_energy_summer", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true).first != null
			}
			if (isInTrainingCamp) {
				// During training camps, we have Lv5 training facilities - maximize usage!
				// All trainings are significantly more valuable during camps
				
				// Base camp bonus for ANY training (Lv5 facilities)
				val baseCampBonus = 30.0
				score += baseCampBonus
				val campType = if (currentDate.month in listOf(6, 7)) "Summer" else "Winter"
				printToLog("[TRAINING] $campType Camp Lv5 facility bonus: +${baseCampBonus.toInt()}")
				
				// Extra bonus for high-value camp trainings
				if (training.relationshipBars.size >= 2) {
					// Multiple friends during camps are extremely valuable
					val campFriendBonus = training.relationshipBars.size * 20.0
					score += campFriendBonus
					printToLog("[TRAINING] $campType Camp friendship bonus: +${campFriendBonus.toInt()} for ${training.relationshipBars.size} friends")
				}
				
				// Bonus for high stat gains during camps
				if (training.statGains.sum() > 30) {
					val campStatBonus = (training.statGains.sum() - 30) * 0.5
					score += campStatBonus
					printToLog("[TRAINING] $campType Camp high-stat bonus: +${campStatBonus.toInt()}")
				}
				
				// During camps, strictly enforce 20% failure threshold
				// Better to train Wit or Rest than waste Lv5 training opportunity
				if (training.failureChance > 20) {
					// Heavy penalty for risky training during camps
					score *= 0.1  // Reduce score by 90%
					printToLog("[TRAINING] Camp: ${training.failureChance}% failure exceeds 20% threshold - heavily penalized")
				} else if (training.failureChance <= 10) {
					// Bonus for very safe training during camps
					val safetyBonus = 20.0
					score += safetyBonus
					printToLog("[TRAINING] Camp safe training bonus: +${safetyBonus.toInt()}")
				}
			}

			return score.coerceIn(0.0, 500.0)  // Reasonable max with dynamic bonuses
		}

		/**
		 * Performs comprehensive scoring of training options using multiple weighted factors.
		 *
		 * This scoring system combines three main components:
		 * - Stat efficiency (60-70% weight): How well the training helps achieve stat targets
		 * - Relationship building (10% weight): Value of friendship bar progress
		 * - Context bonuses (30% weight): Phase-specific bonuses, etc.
		 *
		 * The weighting changes based on whether relationship bars are present:
		 * - With relationship bars: 60% stat, 10% relationship, 30% context
		 * - Without relationship bars: 70% stat, 0% relationship, 30% context
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A normalized score (1-1000) representing overall training value.
		 */
		fun scoreStatTraining(training: Training): Double {
			if (training.name in blacklist) return 0.0

			// Don't score for stats that are maxed or would be maxed.
			if ((disableTrainingOnMaxedStat && currentStatsMap[training.name]!! >= currentStatCap) ||
				(currentStatsMap.getOrDefault(training.name, 0) + training.statGains[trainings.indexOf(training.name)] >= currentStatCap)) {
				return 0.0
			}

			printToLog("\n[TRAINING] Starting scoring for ${training.name} Training.")

			val target = statTargets

			var totalScore = 0.0
			var maxPossibleScore = 0.0

			// 1. Stat Efficiency scoring
			val statScore = calculateStatEfficiencyScore(training, target)

			// 2. Friendship scoring
			val relationshipScore = calculateRelationshipScore(training)

			// 3. Context-aware scoring
			val contextScore = calculateContextScore(training)

			// Apply year-based weights
			val (friendshipWeight, statWeight) = getYearBasedWeights()

			if (training.relationshipBars.isNotEmpty()) {
				// Adjust weights based on year progression
				totalScore += statScore * statWeight
				maxPossibleScore += 100.0 * statWeight

				totalScore += relationshipScore * friendshipWeight
				maxPossibleScore += 100.0 * friendshipWeight

				val contextWeight = 1.0 - statWeight - friendshipWeight
				totalScore += contextScore * contextWeight
				maxPossibleScore += 100.0 * contextWeight
			} else {
				// No friendships - focus more on stats
				totalScore += statScore * (statWeight + friendshipWeight * 0.5)
				maxPossibleScore += 100.0 * (statWeight + friendshipWeight * 0.5)

				totalScore += contextScore * (1.0 - statWeight - friendshipWeight * 0.5)
				maxPossibleScore += 100.0 * (1.0 - statWeight - friendshipWeight * 0.5)
			}

			printToLog(
				"[TRAINING] Scores | Current Stat: ${currentStatsMap[training.name]}, Target Stat: ${target[trainings.indexOf(training.name)]}, " +
					"Stat Efficiency: ${decimalFormat.format(statScore)}, Relationship: ${decimalFormat.format(relationshipScore)}, " +
					"Context: ${decimalFormat.format(contextScore)}"
			)

			// Normalize the score.
			val normalizedScore = (totalScore / maxPossibleScore * 100.0).coerceIn(1.0, 1000.0)

			printToLog("[TRAINING] Enhanced final score for ${training.name} Training: ${decimalFormat.format(normalizedScore)}/1000.0")

			return normalizedScore
		}

		// Adaptive failure cap based on individual training value
		val getAdaptiveFailureCap: (Training) -> Int = { training ->
			val baseValue = training.statGains.sum() + training.relationshipBars.size * 30
			val friendCount = training.relationshipBars.count { it.fillPercent >= 80 }
			val blueBars = training.relationshipBars.count { it.dominantColor == "blue" }
			val levelProgress = trainingCountForLevel.getOrDefault(training.name, 0)
			val priorityIndex = statPrioritization.indexOf(training.name)

			when {
				// Exceptional value: Allow up to 30%
				baseValue > 150 || blueBars >= 4 -> minOf(maximumFailureChance, 30)

				// About to level up priority stat: Allow 25%
				levelProgress == 3 && priorityIndex <= 1 -> minOf(maximumFailureChance, 25)

				// High value training: Allow 24%
				friendCount >= 3 || training.statGains.sum() > 90 -> minOf(maximumFailureChance, 24)

				// Standard cap at 22%
				else -> minOf(maximumFailureChance, 22)
			}
		}

		// Filter with adaptive caps per training
		val acceptableTrainings = trainingMap.values.filter { training ->
			val maxCap = getAdaptiveFailureCap(training)
			training.failureChance >= 0 && training.failureChance <= maxCap && training.name !in blacklist
		}
		
		if (acceptableTrainings.isEmpty()) {
			printToLog("[TRAINING] WARNING: No acceptable trainings found - must rest")
			return ""
		}
		
		// Check recent training history to prevent loops
		if (recentTrainings.size >= 3) {
			// Check if we're stuck in a pattern (e.g., Speed->Stamina->Speed->Stamina)
			val lastThree = recentTrainings.takeLast(3)
			if (lastThree.size == 3 && lastThree[0] == lastThree[2]) {
				printToLog("[TRAINING] Pattern detected in last 3 trainings: $lastThree")
				// Will apply penalty during scoring
			}
		}

		// Hybrid scoring with multiple algorithms
		val scoringFunction: (Training) -> Double = { training ->
			// Base score from traditional methods
			val baseScore = if (currentDate.phase == "Pre-Debut" || currentDate.year == 1) {
				scoreFriendshipTraining(training)
			} else {
				scoreStatTraining(training)
			}

			// Apply level bonuses (rewards, not penalties)
			var enhancedScore = applyTrainingLevelAdjustments(training, baseScore)

			// Thompson Sampling for exploration-exploitation
			val thompsonScore = calculateThompsonScore(training)

			// UCB1 for exploration bonus
			val ucbScore = calculateUCBBonus(training)

			// Dynamic Programming for long-term value
			val dpScore = calculateDynamicValue(training)

			// Combine scores with weighted average
			val combinedScore = when {
				// Early game: More exploration
				currentDate.year == 1 -> {
					enhancedScore * 0.4 + thompsonScore * 0.2 + ucbScore * 0.2 + dpScore * 0.2
				}
				// Mid game: Balanced
				currentDate.year == 2 -> {
					enhancedScore * 0.5 + thompsonScore * 0.15 + ucbScore * 0.1 + dpScore * 0.25
				}
				// Late game: Focus on optimization
				else -> {
					enhancedScore * 0.6 + thompsonScore * 0.1 + ucbScore * 0.05 + dpScore * 0.25
				}
			}

			// Apply soft constraints and loop prevention
			val finalScore = applyLoopPreventionPenalties(training, combinedScore)

			// Log composite score for high-value trainings
			if (finalScore > 200 || training.relationshipBars.count { it.dominantColor == "blue" } >= 2) {
				printToLog("[SCORING] ${training.name}: Base=${baseScore.toInt()}, Enhanced=${enhancedScore.toInt()}, Final=${finalScore.toInt()}")
			}

			finalScore
		}

		val best = acceptableTrainings.maxByOrNull(scoringFunction)

		return if (best != null) {
			// Update training level tracking
			updateTrainingLevel(best.name)

			// Update success history for Thompson Sampling
			val history = trainingSuccessHistory.getOrDefault(best.name, Pair(0, 0))
			val successEstimate = if (best.failureChance <= 10) 1 else 0  // Simplified success tracking
			trainingSuccessHistory[best.name] = Pair(history.first + successEstimate, history.second + 1)

			// Update value history for UCB1
			val currentValue = calculateImmediateValue(best)
			val oldAvg = trainingValueHistory.getOrDefault(best.name, currentValue)
			val count = historicalTrainingCounts.getOrDefault(best.name, 0) + 1
			trainingValueHistory[best.name] = (oldAvg * (count - 1) + currentValue) / count

			// Increment total trainings counter
			totalTrainingsDone++

			// Update training history
			if (best.name == lastTrainingName) {
				consecutiveSameTraining++
			} else {
				consecutiveSameTraining = 1
				lastTrainingName = best.name
			}

			// Update recent trainings list
			recentTrainings.add(best.name)
			if (recentTrainings.size > maxRecentHistory) {
				recentTrainings.removeAt(0)  // Remove oldest
			}

			// Log detailed information about the selection
			val statIndex = trainings.indexOf(best.name)
			val currentStat = currentStatsMap.getOrDefault(best.name, 0)
			val targetStat = statTargets.getOrElse(statIndex) { 600 }
			val completionPercent = if (targetStat > 0) (currentStat.toDouble() / targetStat * 100) else 100.0
			val currentLevel = trainingLevels[best.name] ?: 1
			val levelProgress = trainingCountForLevel[best.name] ?: 0

			printToLog("[TRAINING] Selected ${best.name} training:")
			printToLog("  - Level: Lv$currentLevel (${levelProgress}/$trainingsPerLevel to next level)")
			printToLog("  - Failure: ${best.failureChance}% (max allowed: 22%)")
			printToLog("  - Current stat: $currentStat/$targetStat (${completionPercent.toInt()}% complete)")
			if (currentStat >= absoluteStatCap - 100) {
				printToLog("  - WARNING: Approaching absolute cap ($absoluteStatCap)!")
			}
			printToLog("  - Stat gains: ${best.statGains.sum()} total")
			printToLog("  - Friendships: ${best.relationshipBars.size} (${best.relationshipBars.count { it.dominantColor == "blue" }} blue)")
			if (consecutiveSameTraining > 1) {
				printToLog("  - WARNING: Done $consecutiveSameTraining times in a row")
			}

			historicalTrainingCounts.put(best.name, historicalTrainingCounts.getOrDefault(best.name, 0) + 1)
			best.name
		} else {
			printToLog("[TRAINING] No acceptable training found under 22% failure - will rest")
			""
		}
	}

	/**
	 * Execute the training with the highest stat weight.
	 */
	private fun executeTraining() {
		printToLog("\n********************")
		printToLog("[TRAINING] Now starting process to execute training...")
		val trainingSelected = recommendTraining()

		if (trainingSelected != "") {
			printTrainingMap()
			printToLog("[TRAINING] Executing the $trainingSelected Training.")

			// Update friendship levels for support cards in this training
			val training = trainingMap[trainingSelected]
			if (training != null) {
				updateFriendshipLevels(training)
			}

			findAndTapImage("training_${trainingSelected.lowercase()}", region = imageUtils.regionBottomHalf, taps = 3)
			printToLog("[TRAINING] Process to execute training completed.")
		} else {
			printToLog("[TRAINING] Conditions have not been met so training will not be done.")
		}

		printToLog("********************\n")

		// Now reset the Training map.
		trainingMap.clear()
	}

	/**
	 * Updates friendship levels for support cards after training.
	 * Tracks each support card's friendship percentage (0-100%).
	 */
	private fun updateFriendshipLevels(training: Training) {
		training.relationshipBars.forEachIndexed { index, bar ->
			val supportId = "support_$index" // In real implementation, would need to identify actual support card
			val currentFriendship = supportFriendships.getOrDefault(supportId, 0)

			// Update based on bar fill percentage
			if (bar.fillPercent < 100) {
				val newFriendship = minOf(currentFriendship + friendshipGainPerTraining, maxFriendship)
				supportFriendships[supportId] = newFriendship

				if (bar.dominantColor == "blue" && newFriendship >= 80) {
					printToLog("[FRIENDSHIP] Support #$index reached orange level (${newFriendship}%)")
				} else if (newFriendship >= 100) {
					printToLog("[FRIENDSHIP] Support #$index reached rainbow/max level (100%)")
				}
			}
		}

		// Log friendship progress summary
		val avgFriendship = if (supportFriendships.isNotEmpty()) {
			supportFriendships.values.average()
		} else 0.0

		if (totalTrainingsDone % 5 == 0) { // Every 5 trainings
			printToLog("[FRIENDSHIP] Average friendship: ${avgFriendship.toInt()}%")
			printToLog("[FRIENDSHIP] Max friendships: ${supportFriendships.values.count { it >= 100 }}/${supportFriendships.size}")
		}
	}

	/**
	 * Gets year-based training weights for balanced progression.
	 * Year 1: Focus on friendships (55% weight)
	 * Year 2: Balanced approach (35% friendship, 65% stats)
	 * Year 3: Stat maximization (20% friendship, 80% stats)
	 */
	private fun getYearBasedWeights(): Pair<Double, Double> {
		return when (currentDate.year) {
			1 -> Pair(0.55, 0.45)  // 55% friendship, 45% stats
			2 -> Pair(0.35, 0.65)  // 35% friendship, 65% stats
			3 -> Pair(0.20, 0.80)  // 20% friendship, 80% stats
			else -> Pair(0.35, 0.65) // Default balanced
		}
	}

	/**
	 * Tracks skill points and provides warnings when below expected thresholds.
	 * Updates every 5 turns to avoid excessive OCR calls.
	 */
	private fun trackSkillPoints() {
		val turnNumber = currentDate.turnNumber

		// Only check every 5 turns or if it's been a while
		if (turnNumber - lastSkillPointCheck < 5) {
			return
		}

		currentSkillPoints = imageUtils.determineSkillPoints()
		lastSkillPointCheck = turnNumber

		if (currentSkillPoints < 0) {
			printToLog("[SKILL POINTS] Unable to determine skill points via OCR")
			return
		}

		printToLog("[SKILL POINTS] Current: $currentSkillPoints")

		// Check against thresholds and warn if behind
		when {
			currentDate.year == 2 && currentDate.month >= 6 && currentSkillPoints < 200 -> {
				printToLog("[SKILL POINTS] ⚠️ WARNING: Only $currentSkillPoints SP by mid Year 2 (target: 200+)")
				printToLog("[SKILL POINTS] Consider prioritizing SP gain in events")
			}
			currentDate.year == 3 && currentDate.month <= 3 && currentSkillPoints < 400 -> {
				printToLog("[SKILL POINTS] ⚠️ WARNING: Only $currentSkillPoints SP at Year 3 start (target: 400+)")
				printToLog("[SKILL POINTS] Prioritize skill point events!")
			}
			currentDate.year == 3 && currentDate.month >= 10 && currentSkillPoints < 600 -> {
				printToLog("[SKILL POINTS] ⚠️ CRITICAL: Only $currentSkillPoints SP before URA Finals (target: 600+)")
				printToLog("[SKILL POINTS] Urgently need skill points for competitive build!")
			}
			currentDate.year == 3 && currentDate.month >= 11 && currentSkillPoints >= 750 -> {
				printToLog("[SKILL POINTS] ✅ Excellent! $currentSkillPoints SP ready for URA Finals")
			}
		}

		// Provide suggestions based on skill point status
		if (currentSkillPoints < getExpectedSkillPoints()) {
			printToLog("[SKILL POINTS] TIP: Choose +SP options in events when available")
		}
	}

	/**
	 * Returns expected skill points based on current game progress.
	 */
	private fun getExpectedSkillPoints(): Int {
		val turnNumber = currentDate.turnNumber
		return when {
			turnNumber <= 24 -> turnNumber * 8  // ~8 SP per turn in Year 1
			turnNumber <= 48 -> 200 + (turnNumber - 24) * 12  // ~12 SP per turn in Year 2
			else -> 500 + (turnNumber - 48) * 15  // ~15 SP per turn in Year 3
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Functions to handle Training Events with the help of the TextDetection class.

	/**
	 * Start text detection to determine what Training Event it is and the event rewards for each option.
	 * It will then select the best option according to the user's preferences. By default, it will choose the first option.
	 */
	fun handleTrainingEvent() {
		printToLog("\n[TRAINING-EVENT] Starting Training Event process...")

		val (eventRewards, confidence) = textDetection.start()

		val regex = Regex("[a-zA-Z]+")
		var optionSelected = 0

		// Double check if the bot is at the Main screen or not.
		if (checkMainScreen()) {
			return
		}

		if (eventRewards.isNotEmpty() && eventRewards[0] != "") {
			// Initialize the List.
			val selectionWeight = List(eventRewards.size) { 0 }.toMutableList()

			// Sum up the stat gains with additional weight applied to stats that are prioritized.
			eventRewards.forEach { reward ->
				val formattedReward: List<String> = reward.split("\n")

				formattedReward.forEach { line ->
					val formattedLine: String = regex
						.replace(line, "")
						.replace("(", "")
						.replace(")", "")
						.trim()
						.lowercase()

					printToLog("[TRAINING-EVENT] Original line is \"$line\".")
					printToLog("[TRAINING-EVENT] Formatted line is \"$formattedLine\".")

					var priorityStatCheck = false
					if (line.lowercase().contains("energy")) {
						val finalEnergyValue = try {
							val energyValue = if (formattedLine.contains("/")) {
								val splits = formattedLine.split("/")
								var sum = 0
								for (split in splits) {
									sum += try {
										split.trim().toInt()
									} catch (_: NumberFormatException) {
										printToLog("[WARNING] Could not convert $formattedLine to a number for energy with a forward slash.")
										20
									}
								}
								sum
							} else {
								formattedLine.toInt()
							}

							if (enablePrioritizeEnergyOptions) {
								energyValue * 100
							} else {
								energyValue * 3
							}
						} catch (_: NumberFormatException) {
							printToLog("[WARNING] Could not convert $formattedLine to a number for energy.")
							20
						}
						printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of $finalEnergyValue for energy.")
						selectionWeight[optionSelected] += finalEnergyValue
					} else if (line.lowercase().contains("mood")) {
						val moodWeight = if (formattedLine.contains("-")) -50 else 50
						printToLog("[TRAINING-EVENT Adding weight for option#${optionSelected + 1} of $moodWeight for ${if (moodWeight > 0) "positive" else "negative"} mood gain.")
						selectionWeight[optionSelected] += moodWeight
					} else if (line.lowercase().contains("bond")) {
						printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of 20 for bond.")
						selectionWeight[optionSelected] += 20
					} else if (line.lowercase().contains("event chain ended")) {
						printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of -50 for event chain ending.")
						selectionWeight[optionSelected] += -50
					} else if (line.lowercase().contains("(random)")) {
						printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of -10 for random reward.")
						selectionWeight[optionSelected] += -10
					} else if (line.lowercase().contains("randomly")) {
						printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of 50 for random options.")
						selectionWeight[optionSelected] += 50
					} else if (line.lowercase().contains("hint")) {
						printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of 25 for skill hint(s).")
						selectionWeight[optionSelected] += 25
					} else if (line.lowercase().contains("skill")) {
						val finalSkillPoints = if (formattedLine.contains("/")) {
							val splits = formattedLine.split("/")
							var sum = 0
							for (split in splits) {
								sum += try {
									split.trim().toInt()
								} catch (_: NumberFormatException) {
									printToLog("[WARNING] Could not convert $formattedLine to a number for skill points with a forward slash.")
									10
								}
							}
							sum
						} else {
							formattedLine.toInt()
						}
						printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of $finalSkillPoints for skill points.")
						selectionWeight[optionSelected] += finalSkillPoints
					} else {
						// Apply inflated weights to the prioritized stats based on their order.
						statPrioritization.forEachIndexed { index, stat ->
							if (line.contains(stat)) {
								// Get current stat and target for context-aware scoring
								val currentStat = currentStatsMap.getOrDefault(stat, 0)
								val statIndex = when (stat) {
									"Speed" -> 0
									"Stamina" -> 1
									"Power" -> 2
									"Guts" -> 3
									"Wit", "Wisdom" -> 4
									else -> 0
								}
								val targetStat = statTargets.getOrNull(statIndex) ?: 600
								val completionPercent = if (targetStat > 0) (currentStat * 100 / targetStat) else 100

								// Calculate weight bonus based on position AND deficit
								val priorityBonus = when (index) {
									0 -> 50
									1 -> 40
									2 -> 30
									3 -> 20
									else -> 10
								}

								// Deficit multiplier for urgent needs
								val deficitMultiplier = when {
									completionPercent < 30 -> 2.0
									completionPercent < 50 -> 1.5
									completionPercent < 70 -> 1.2
									else -> 1.0
								}

								val finalStatValue = try {
									priorityStatCheck = true
									val baseValue = if (formattedLine.contains("/")) {
										val splits = formattedLine.split("/")
										var sum = 0
										for (split in splits) {
											sum += try {
												split.trim().toInt()
											} catch (_: NumberFormatException) {
												printToLog("[WARNING] Could not convert $formattedLine to a number for a priority stat with a forward slash.")
												10
											}
										}
										sum
									} else {
										formattedLine.toInt()
									}
									((baseValue + priorityBonus) * deficitMultiplier).toInt()
								} catch (_: NumberFormatException) {
									printToLog("[WARNING] Could not convert $formattedLine to a number for a priority stat.")
									priorityStatCheck = false
									10
								}
								printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of $finalStatValue for prioritized stat (${completionPercent}% complete).")
								selectionWeight[optionSelected] += finalStatValue
							}
						}

						// Apply normal weights to the rest of the stats.
						if (!priorityStatCheck) {
							val finalStatValue = try {
								if (formattedLine.contains("/")) {
									val splits = formattedLine.split("/")
									var sum = 0
									for (split in splits) {
										sum += try {
											split.trim().toInt()
										} catch (_: NumberFormatException) {
											printToLog("[WARNING] Could not convert $formattedLine to a number for non-prioritized stat with a forward slash.")
											10
										}
									}
									sum
								} else {
									formattedLine.toInt()
								}
							} catch (_: NumberFormatException) {
								printToLog("[WARNING] Could not convert $formattedLine to a number for non-prioritized stat.")
								10
							}
							printToLog("[TRAINING-EVENT] Adding weight for option #${optionSelected + 1} of $finalStatValue for non-prioritized stat.")
							selectionWeight[optionSelected] += finalStatValue
						}
					}

					printToLog("[TRAINING-EVENT] Final weight for option #${optionSelected + 1} is: ${selectionWeight[optionSelected]}.")
				}

				optionSelected++
			}

			// Select the best option that aligns with the stat prioritization made in the Training options.
			var max: Int? = selectionWeight.maxOrNull()
			if (max == null) {
				max = 0
				optionSelected = 0
			} else {
				optionSelected = selectionWeight.indexOf(max)
			}

			// Print the selection weights.
			printToLog("[TRAINING-EVENT] Selection weights for each option:")
			selectionWeight.forEachIndexed { index, weight ->
				printToLog("Option ${index + 1}: $weight")
			}

			// Format the string to display each option's rewards.
			var eventRewardsString = ""
			var optionNumber = 1
			eventRewards.forEach { reward ->
				eventRewardsString += "Option $optionNumber: \"$reward\"\n"
				optionNumber += 1
			}

			val minimumConfidence = sharedPreferences.getInt("confidence", 80).toDouble() / 100.0
			val resultString = if (confidence >= minimumConfidence) {
				"[TRAINING-EVENT] For this Training Event consisting of:\n$eventRewardsString\nThe bot will select Option ${optionSelected + 1}: \"${eventRewards[optionSelected]}\" with a " +
						"selection weight of $max."
			} else {
				"[TRAINING-EVENT] Since the confidence was less than the set minimum, first option will be selected."
			}

			printToLog(resultString)
		} else {
			printToLog("[TRAINING-EVENT] First option will be selected since OCR failed to detect anything.")
			optionSelected = 0
		}

		val trainingOptionLocations: ArrayList<Point> = imageUtils.findAll("training_event_active")
		val selectedLocation: Point? = if (trainingOptionLocations.isNotEmpty()) {
			// Account for the situation where it could go out of bounds if the detected event options is incorrect and gives too many results.
			try {
				trainingOptionLocations[optionSelected]
			} catch (_: IndexOutOfBoundsException) {
				// Default to the first option.
				trainingOptionLocations[0]
			}
		} else {
			imageUtils.findImage("training_event_active", tries = 5, region = imageUtils.regionMiddle).first
		}

		if (selectedLocation != null) {
			tap(selectedLocation.x + imageUtils.relWidth(100), selectedLocation.y, "training_event_active")
		}

		printToLog("[TRAINING-EVENT] Process to handle detected Training Event completed.")
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Functions to handle Race Events.

	/**
	 * The entry point for handling mandatory or extra races.
	 *
	 * @return True if the mandatory/extra race was completed successfully. Otherwise false.
	 */
	fun handleRaceEvents(): Boolean {
		printToLog("\n[RACE] Starting Racing process...")
		if (encounteredRacingPopup) {
			// Dismiss the insufficient fans popup here and head to the Race Selection screen.
			findAndTapImage("race_confirm", tries = 1, region = imageUtils.regionBottomHalf)
			encounteredRacingPopup = false
			wait(1.0)
		}

		// If there are no races available, cancel the racing process.
		if (imageUtils.findImage("race_none_available", tries = 1, region = imageUtils.regionMiddle, suppressError = true).first != null) {
			printToLog("[RACE] There are no races to compete in. Canceling the racing process and doing something else.")
			return false
		}

		skipRacing = false

		// First, check if there is a mandatory or a extra race available. If so, head into the Race Selection screen.
		// Note: If there is a mandatory race, the bot would be on the Home screen.
		// Otherwise, it would have found itself at the Race Selection screen already (by way of the insufficient fans popup).
		if (findAndTapImage("race_select_mandatory", tries = 1, region = imageUtils.regionBottomHalf)) {
			printToLog("\n[RACE] Starting process for handling a mandatory race.")

			if (enableStopOnMandatoryRace) {
				detectedMandatoryRaceCheck = true
				return false
			} else if (enableForceRacing) {
				findAndTapImage("ok", tries = 1, region = imageUtils.regionMiddle)
				wait(1.0)
			}

			// There is a mandatory race. Now confirm the selection and the resultant popup and then wait for the game to load.
			wait(2.0)
			printToLog("[RACE] Confirming the mandatory race selection.")
			findAndTapImage("race_confirm", tries = 3, region = imageUtils.regionBottomHalf)
			wait(1.0)
			printToLog("[RACE] Confirming any popup from the mandatory race selection.")
			findAndTapImage("race_confirm", tries = 3, region = imageUtils.regionBottomHalf)
			wait(2.0)

			waitForLoading()

			// Skip the race if possible, otherwise run it manually.
			val resultCheck: Boolean = if (imageUtils.findImage("race_skip_locked", tries = 5, region = imageUtils.regionBottomHalf).first == null) {
				skipRace()
			} else {
				manualRace()
			}

			finishRace(resultCheck)

			printToLog("[RACE] Racing process for Mandatory Race is completed.")
			return true
		} else if (currentDate.phase != "Pre-Debut" && findAndTapImage("race_select_extra", tries = 1, region = imageUtils.regionBottomHalf)) {
			printToLog("\n[RACE] Starting process for handling a extra race.")

			// If there is a popup warning about repeating races 3+ times, stop the process and do something else other than racing.
			if (imageUtils.findImage("race_repeat_warning").first != null) {
				if (!enableForceRacing) {
					raceRepeatWarningCheck = true
					printToLog("\n[RACE] Closing popup warning of doing more than 3+ races and setting flag to prevent racing for now. Canceling the racing process and doing something else.")
					findAndTapImage("cancel", region = imageUtils.regionBottomHalf)
					return false
				} else {
					findAndTapImage("ok", tries = 1, region = imageUtils.regionMiddle)
					wait(1.0)
				}
			}

			// There is a extra race.
			// Swipe up the list to get to the top and then select the first option.
			val statusLocation = imageUtils.findImage("race_status").first
			if (statusLocation == null) {
				printToLog("[ERROR] Unable to determine existence of list of extra races. Canceling the racing process and doing something else.", isError = true)
				return false
			}
			gestureUtils.swipe(statusLocation.x.toFloat(), statusLocation.y.toFloat() + 300, statusLocation.x.toFloat(), statusLocation.y.toFloat() + 888)
			wait(1.0)

			// Intelligent race selection with strategic planning
			val currentFans = getCurrentFans() // You'll need to implement this OCR function
			val fanTargets = getFanTargetsForGrade()
			val turnsRemaining = getTurnsUntilNextMandatory()

			printToLog("[RACE] Current fans: $currentFans, Next target: ${fanTargets.first}, Turns remaining: $turnsRemaining")

			// First find all available races
			var count = 0
			val maxCount = imageUtils.findAll("race_selection_fans", region = imageUtils.regionBottomHalf).size
			if (maxCount == 0) {
				printToLog("[WARNING] Was unable to find any extra races to select. Canceling the racing process and doing something else.", isError = true)
				return false
			} else {
				printToLog("[RACE] There are $maxCount extra race options currently on screen.")
			}
			val listOfFans = mutableListOf<Int>()
			val extraRaceLocation = mutableListOf<Point>()
			val doublePredictionLocations = imageUtils.findAll("race_extra_double_prediction")

			// Quick selection if only one double prediction race
			if (doublePredictionLocations.size == 1 && !needsSpecificFanCount(currentFans, fanTargets)) {
				printToLog("[RACE] There is only one race with double predictions so selecting that one.")
				tap(
					doublePredictionLocations[0].x,
					doublePredictionLocations[0].y,
					"race_extra_double_prediction",
					ignoreWaiting = true
				)
			} else {
				val (sourceBitmap, templateBitmap) = imageUtils.getBitmaps("race_extra_double_prediction")
				val listOfRaces: ArrayList<ImageUtils.RaceDetails> = arrayListOf()
				while (count < maxCount) {
					// Save the location of the selected extra race.
					val selectedExtraRace = imageUtils.findImage("race_extra_selection", region = imageUtils.regionBottomHalf).first
					if (selectedExtraRace == null) {
						printToLog("[ERROR] Unable to find the location of the selected extra race. Canceling the racing process and doing something else.", isError = true)
						break
					}
					extraRaceLocation.add(selectedExtraRace)

					// Determine its fan gain and save it.
					val raceDetails: ImageUtils.RaceDetails = imageUtils.determineExtraRaceFans(extraRaceLocation[count], sourceBitmap, templateBitmap!!, forceRacing = enableForceRacing)
					listOfRaces.add(raceDetails)
					if (count == 0 && raceDetails.fans == -1) {
						// If the fans were unable to be fetched or the race does not have double predictions for the first attempt, skip racing altogether.
						listOfFans.add(raceDetails.fans)
						break
					}
					listOfFans.add(raceDetails.fans)

					// Select the next extra race.
					if (count + 1 < maxCount) {
						if (imageUtils.isTablet) {
							tap(
								imageUtils.relX(extraRaceLocation[count].x, (-100 * 1.36).toInt()).toDouble(),
								imageUtils.relY(extraRaceLocation[count].y, (150 * 1.50).toInt()).toDouble(),
								"race_extra_selection",
								ignoreWaiting = true
							)
						} else {
							tap(
								imageUtils.relX(extraRaceLocation[count].x, -100).toDouble(),
								imageUtils.relY(extraRaceLocation[count].y, 150).toDouble(),
								"race_extra_selection",
								ignoreWaiting = true
							)
						}
					}

					wait(0.5)

					count++
				}

				val fansList = listOfRaces.joinToString(", ") { it.fans.toString() }
				printToLog("[RACE] Number of fans detected for each extra race are: $fansList")

				// Next determine the maximum fans and select the extra race.
				val maxFans: Int? = listOfFans.maxOrNull()
				if (maxFans != null) {
					if (maxFans == -1) {
						printToLog("[WARNING] Max fans was returned as -1. Canceling the racing process and doing something else.")
						return false
					}

					// Intelligent race selection based on multiple factors
					val index = selectBestRace(listOfRaces, currentFans, fanTargets, turnsRemaining)

					printToLog("[RACE] Selecting the extra race at option #${index + 1} based on strategic evaluation.")

					// Select the extra race that matches the double star prediction and the most fan gain.
					tap(
						extraRaceLocation[index].x - imageUtils.relWidth((100 * 1.36).toInt()),
						extraRaceLocation[index].y - imageUtils.relHeight(70),
						"race_extra_selection",
						ignoreWaiting = true
					)
				} else if (extraRaceLocation.isNotEmpty()) {
					// If no maximum is determined, select the very first extra race.
					printToLog("[RACE] Selecting the first extra race on the list by default.")
					tap(
						extraRaceLocation[0].x - imageUtils.relWidth((100 * 1.36).toInt()),
						extraRaceLocation[0].y - imageUtils.relHeight(70),
						"race_extra_selection",
						ignoreWaiting = true
					)
				} else {
					printToLog("[WARNING] No extra races detected and thus no fan maximums were calculated. Canceling the racing process and doing something else.")
					return false
				}
			}

			// Confirm the selection and the resultant popup and then wait for the game to load.
			findAndTapImage("race_confirm", tries = 30, region = imageUtils.regionBottomHalf)
			findAndTapImage("race_confirm", tries = 10, region = imageUtils.regionBottomHalf)
			wait(2.0)

			// Skip the race if possible, otherwise run it manually.
			val resultCheck: Boolean = if (imageUtils.findImage("race_skip_locked", tries = 5, region = imageUtils.regionBottomHalf).first == null) {
				skipRace()
			} else {
				manualRace()
			}

			finishRace(resultCheck, isExtra = true)

			printToLog("[RACE] Racing process for Extra Race is completed.")
			return true
		}

		return false
	}

	/**
	 * The entry point for handling standalone races if the user started the bot on the Racing screen.
	 */
	fun handleStandaloneRace() {
		printToLog("\n[RACE] Starting Standalone Racing process...")

		// Skip the race if possible, otherwise run it manually.
		val resultCheck: Boolean = if (imageUtils.findImage("race_skip_locked", tries = 5, region = imageUtils.regionBottomHalf).first == null) {
			skipRace()
		} else {
			manualRace()
		}

		finishRace(resultCheck)

		printToLog("[RACE] Racing process for Standalone Race is completed.")
	}

	/**
	 * Skips the current race to get to the results screen.
	 *
	 * @return True if the bot completed the race with retry attempts remaining. Otherwise false.
	 */
	private fun skipRace(): Boolean {
		while (raceRetries >= 0) {
			printToLog("[RACE] Skipping race...")

			// Press the skip button and then wait for your result of the race to show.
			if (findAndTapImage("race_skip", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Race was able to be skipped.")
			}
			wait(2.0)

			// Now tap on the screen to get past the Race Result screen.
			tap(350.0, 450.0, "ok", taps = 3)

			// Check if the race needed to be retried.
			if (imageUtils.findImage("race_retry", tries = 5, region = imageUtils.regionBottomHalf, suppressError = true).first != null) {
				if (disableRaceRetries) {
					printToLog("\n[END] Stopping the bot due to failing a mandatory race.")
					notificationMessage = "Stopping the bot due to failing a mandatory race."
					throw IllegalStateException()
				}
				findAndTapImage("race_retry", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)
				printToLog("[RACE] The skipped race failed and needs to be run again. Attempting to retry...")
				wait(3.0)
				raceRetries--
			} else {
				return true
			}
		}

		return false
	}

	/**
	 * Manually runs the current race to get to the results screen.
	 *
	 * @return True if the bot completed the race with retry attempts remaining. Otherwise false.
	 */
	private fun manualRace(): Boolean {
		while (raceRetries >= 0) {
			printToLog("[RACE] Skipping manual race...")

			// Press the manual button.
			if (findAndTapImage("race_manual", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Started the manual race.")
			}
			wait(2.0)

			// Confirm the Race Playback popup if it appears.
			if (findAndTapImage("ok", tries = 1, region = imageUtils.regionMiddle, suppressError = true)) {
				printToLog("[RACE] Confirmed the Race Playback popup.")
				wait(5.0)
			}

			waitForLoading()

			// Now press the confirm button to get past the list of participants.
			if (findAndTapImage("race_confirm", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Dismissed the list of participants.")
			}
			waitForLoading()
			wait(1.0)
			waitForLoading()
			wait(1.0)

			// Skip the part where it reveals the name of the race.
			if (findAndTapImage("race_skip_manual", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Skipped the name reveal of the race.")
			}
			// Skip the walkthrough of the starting gate.
			if (findAndTapImage("race_skip_manual", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Skipped the walkthrough of the starting gate.")
			}
			wait(3.0)
			// Skip the start of the race.
			if (findAndTapImage("race_skip_manual", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Skipped the start of the race.")
			}
			// Skip the lead up to the finish line.
			if (findAndTapImage("race_skip_manual", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Skipped the lead up to the finish line.")
			}
			wait(2.0)
			// Skip the result screen.
			if (findAndTapImage("race_skip_manual", tries = 30, region = imageUtils.regionBottomHalf)) {
				printToLog("[RACE] Skipped the results screen.")
			}
			wait(2.0)

			waitForLoading()
			wait(1.0)

			// Check if the race needed to be retried.
			if (imageUtils.findImage("race_retry", tries = 5, region = imageUtils.regionBottomHalf, suppressError = true).first != null) {
				if (disableRaceRetries) {
					printToLog("\n[END] Stopping the bot due to failing a mandatory race.")
					notificationMessage = "Stopping the bot due to failing a mandatory race."
					throw IllegalStateException()
				}
				findAndTapImage("race_retry", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)
				printToLog("[RACE] Manual race failed and needs to be run again. Attempting to retry...")
				wait(5.0)
				raceRetries--
			} else {
				// Check if a Trophy was acquired.
				if (findAndTapImage("race_accept_trophy", tries = 5, region = imageUtils.regionBottomHalf)) {
					printToLog("[RACE] Closing popup to claim trophy...")
				}

				return true
			}
		}

		return false
	}

	/**
	 * Finishes up and confirms the results of the race and its success.
	 *
	 * @param resultCheck Flag to see if the race was completed successfully. Throws an IllegalStateException if it did not.
	 * @param isExtra Flag to determine the following actions to finish up this mandatory or extra race.
	 */
	private fun finishRace(resultCheck: Boolean, isExtra: Boolean = false) {
		printToLog("\n[RACE] Now performing cleanup and finishing the race.")
		if (!resultCheck) {
			notificationMessage = "Bot has run out of retry attempts for racing. Stopping the bot now..."
			throw IllegalStateException()
		}

		// Bot will be at the screen where it shows the final positions of all participants.
		// Press the confirm button and wait to see the triangle of fans.
		printToLog("[RACE] Now attempting to confirm the final positions of all participants and number of gained fans")
		if (findAndTapImage("next", tries = 30, region = imageUtils.regionBottomHalf)) {
			wait(0.5)

			// Now tap on the screen to get to the next screen.
			tap(350.0, 750.0, "ok", taps = 3)

			// Now press the end button to finish the race.
			findAndTapImage("race_end", tries = 30, region = imageUtils.regionBottomHalf)

			if (!isExtra) {
				printToLog("[RACE] Seeing if a Training Goal popup will appear.")
				// Wait until the popup showing the completion of a Training Goal appears and confirm it.
				// There will be dialog before it so the delay should be longer.
				wait(5.0)
				if (findAndTapImage("next", tries = 10, region = imageUtils.regionBottomHalf)) {
					wait(2.0)

					// Now confirm the completion of a Training Goal popup.
					printToLog("[RACE] There was a Training Goal popup. Confirming it now.")
					findAndTapImage("next", tries = 10, region = imageUtils.regionBottomHalf)
				}
			} else if (findAndTapImage("next", tries = 10, region = imageUtils.regionBottomHalf)) {
				// Same as above but without the longer delay.
				wait(2.0)
				findAndTapImage("race_end", tries = 10, region = imageUtils.regionBottomHalf)
			}
		} else {
			printToLog("[ERROR] Cannot start the cleanup process for finishing the race. Moving on...", isError = true)
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Helper Functions


	/**
	 * Updates the current stat value mapping by reading the character's current stats from the Main screen.
	 */
	fun updateStatValueMapping() {
		printToLog("\n[STATS] Updating stat value mapping.")
		currentStatsMap = imageUtils.determineStatValues(currentStatsMap)
		// Print the updated stat value mapping here.
		currentStatsMap.forEach { it ->
			printToLog("[STATS] ${it.key}: ${it.value}")
		}
		printToLog("[STATS] Stat value mapping updated.\n")
	}

	/**
	 * Updates the stored date in memory by keeping track of the current year, phase, month and current turn number.
	 */
	fun updateDate() {
		printToLog("\n[DATE] Updating the current turn number.")
		val dateString = imageUtils.determineDayNumber()
		currentDate = textDetection.determineDateFromString(dateString)
		printToLog("\n[DATE] It is currently $currentDate.")
		
		// Check if URA Finals is approaching
		// URA Finals happens AFTER Late December Year 3 (after turn 72)
		// Late December is the last training turn
		if (currentDate.year == 3) {
			val turnsUntilURA = when {
				// URA Finals happens after Late December (Late Dec is last training turn)
				currentDate.month == 12 && currentDate.phase == "Early" -> 1
				currentDate.month == 11 && currentDate.phase == "Late" -> 2
				currentDate.month == 11 && currentDate.phase == "Early" -> 3
				currentDate.month == 10 && currentDate.phase == "Late" -> 4
				currentDate.month == 10 && currentDate.phase == "Early" -> 5
				else -> -1
			}
			
			if (turnsUntilURA in 1..5) {
				printToLog("[URA FINALS] WARNING: URA Finals in $turnsUntilURA turns!")
				
				// Only 1 turn before URA Finals - critical warning
				if (turnsUntilURA == 1) {
					printToLog("[URA FINALS] CRITICAL: URA Finals NEXT TURN! Must spend skill points NOW!")
					notificationMessage = "URA Finals next turn - SPEND SKILL POINTS NOW!"
				} else if (turnsUntilURA <= 3) {
					printToLog("[URA FINALS] Consider spending skill points soon.")
				}
			}
		}
	}

	/**
	 * Handles the Inheritance event if detected on the screen.
	 *
	 * @return True if the Inheritance event happened and was accepted. Otherwise false.
	 */
	fun handleInheritanceEvent(): Boolean {
		return if (inheritancesDone < 2) {
			if (findAndTapImage("inheritance", tries = 1, region = imageUtils.regionBottomHalf)) {
				inheritancesDone++
				true
			} else {
				false
			}
		} else {
			false
		}
	}

	/**
	 * Attempt to recover energy.
	 * During summer, be more selective about resting to maximize Lv5 training benefits.
	 *
	 * @param forceRest If true, will rest regardless of energy estimation (used when training map is empty)
	 * @return True if the bot successfully recovered energy. Otherwise false.
	 */
	private fun recoverEnergy(forceRest: Boolean = false): Boolean {
		printToLog("\n[ENERGY] Now starting attempt to recover energy.")
		
		// If forced rest (e.g., from empty training map), just rest immediately
		if (forceRest) {
			printToLog("[ENERGY] Forced rest requested due to no viable trainings.")
			val restImage = imageUtils.findImage("recover_energy_summer", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true).first != null
			val restButton = if (restImage) "recover_energy_summer" else "recover_energy"
			return when {
				findAndTapImage(restButton, tries = 1, imageUtils.regionBottomHalf) -> {
					findAndTapImage("ok")
					printToLog("[ENERGY] Successfully recovered energy (forced rest).")
					raceRepeatWarningCheck = false
					true
				}
				else -> {
					printToLog("[ENERGY] Failed to recover energy despite forced rest request.")
					false
				}
			}
		}
		
		// Check if we're in a training camp (summer or winter)
		val isInTrainingCamp = when {
			// Summer training camp
			currentDate.month == 6 && currentDate.phase == "Late" -> true
			currentDate.month == 7 -> true
			// Winter training camp
			currentDate.month == 12 && currentDate.phase == "Late" -> true
			currentDate.month == 1 -> true
			// Also check for summer image indicator as backup
			else -> imageUtils.findImage("recover_energy_summer", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true).first != null
		}
		
		if (isInTrainingCamp) {
			// During training camps, be more flexible with rest decisions
			// Summer rest gives +40 energy and +1 mood level
			printToLog("[ENERGY] Training camp active. Rest provides +40 energy and +1 mood.")
			
			// During training camp, be stricter about failure rates (20% threshold)
			// Better to rest or train Wit than waste Lv5 training opportunities
			val hasTrainings = trainingMap.isNotEmpty()
			val allHighFailures = hasTrainings && trainingMap.values.all { it.failureChance > 20 }
			
			if (allHighFailures) {
				printToLog("[ENERGY] Training Camp: All trainings have >20% failure rate. Must rest or train Wit.")
				val restImage = if (currentDate.month in listOf(6, 7)) "recover_energy_summer" else "recover_energy"
				if (findAndTapImage(restImage, tries = 1, imageUtils.regionBottomHalf)) {
					findAndTapImage("ok")
					printToLog("[ENERGY] Training Camp: Resting due to universally high failure rates.")
					raceRepeatWarningCheck = false
					return true
				}
			}
			
			// Otherwise, estimate energy and make a decision
			val avgFailureRate = trainingMap.values
				.filter { it.failureChance >= 0 }
				.map { it.failureChance }
				.average()
			
			if (!avgFailureRate.isNaN() && hasTrainings) {
				// Estimate energy based on failure rates: 0% failure = ~100% energy, 50% failure = ~0% energy
				val estimatedEnergy = (100 - avgFailureRate * 2).coerceIn(0.0, 100.0)
				printToLog("[ENERGY] Estimated energy: ${estimatedEnergy.toInt()}% based on training failure rates")
				
				// During camp, rest if energy is low or failures exceed 20% threshold
				// This ensures we don't waste Lv5 training opportunities
				if (estimatedEnergy < 40 || avgFailureRate > 20) {
					printToLog("[ENERGY] Training Camp: Low energy or high failures warrant rest (Energy: ~${estimatedEnergy.toInt()}%, Avg Failure: ${avgFailureRate.toInt()}%)")
					val restImage = if (currentDate.month in listOf(6, 7)) "recover_energy_summer" else "recover_energy"
					if (findAndTapImage(restImage, tries = 1, imageUtils.regionBottomHalf)) {
						findAndTapImage("ok")
						printToLog("[ENERGY] Training Camp: Resting to improve training conditions.")
						raceRepeatWarningCheck = false
						return true
					}
				} else {
					printToLog("[ENERGY] Training Camp: Good conditions for training (Energy: ~${estimatedEnergy.toInt()}%, Avg Failure: ${avgFailureRate.toInt()}%)")
					return false
				}
			}
			
			// If we can't estimate (no training data), don't force rest - let normal logic handle it
			printToLog("[ENERGY] Training Camp: No training data available for energy estimation.")
			return false
		}
		
		// Normal energy recovery (non-summer)
		return when {
			findAndTapImage("recover_energy", tries = 1, imageUtils.regionBottomHalf) -> {
				findAndTapImage("ok")
				printToLog("[ENERGY] Successfully recovered energy.")
				raceRepeatWarningCheck = false
				true
			}
			else -> {
				printToLog("[ENERGY] Failed to recover energy. Moving on...")
				false
			}
		}
	}

	/**
	 * Attempt to recover mood to always maintain at least Above Normal mood.
	 * Never recovers mood on turn 1 to avoid wasting the random chance opportunity.
	 *
	 * @return True if the bot successfully recovered mood. Otherwise false.
	 */
	fun recoverMood(): Boolean {
		printToLog("\n[MOOD] Detecting current mood.")

		// Detect what Mood the bot is at.
		val currentMood: String = when {
			imageUtils.findImage("mood_normal", tries = 1, region = imageUtils.regionTopHalf, suppressError = true).first != null -> {
				"Normal"
			}
			imageUtils.findImage("mood_good", tries = 1, region = imageUtils.regionTopHalf, suppressError = true).first != null -> {
				"Good"
			}
			imageUtils.findImage("mood_great", tries = 1, region = imageUtils.regionTopHalf, suppressError = true).first != null -> {
				"Great"
			}
			else -> {
				"Bad/Awful"
			}
		}

		printToLog("[MOOD] Detected mood to be $currentMood.")

		// Never recover mood on turn 1 (random chance opportunity)
		if (currentDate.turnNumber == 1) {
			printToLog("[MOOD] Turn 1 detected. Never recovering mood on turn 1 to utilize random chance opportunity.")
			return false
		}

		// Check if we're in a training camp
		val isInTrainingCamp = when {
			// Summer training camp
			currentDate.month == 6 && currentDate.phase == "Late" -> true
			currentDate.month == 7 -> true
			// Winter training camp
			currentDate.month == 12 && currentDate.phase == "Late" -> true
			currentDate.month == 1 -> true
			// Also check for summer image indicator as backup
			else -> imageUtils.findImage("recover_energy_summer", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true).first != null
		}
		
		// During training camps, only recover mood if it's Bad/Awful (not Normal) since we want to maximize training
		if (isInTrainingCamp) {
			if (currentMood == "Bad/Awful") {
				printToLog("[MOOD] Training Camp: Current mood is Bad/Awful. Using rest for mood recovery.")
				val restImage = if (currentDate.month in listOf(6, 7)) "recover_energy_summer" else "recover_energy"
				findAndTapImage(restImage, tries = 1, region = imageUtils.regionBottomHalf)
				findAndTapImage("ok", region = imageUtils.regionMiddle, suppressError = true)
				raceRepeatWarningCheck = false
				return true
			} else {
				printToLog("[MOOD] Training Camp: Current mood is $currentMood. Skipping rest to maximize Lv5 training opportunities.")
				return false
			}
		}
		
		// Normal (non-summer) mood recovery logic
		return if (firstTrainingCheck && currentMood == "Normal") {
			printToLog("[MOOD] Current mood is Normal. Not recovering mood due to firstTrainingCheck flag being active. Will need to complete a training first before being allowed to recover mood.")
			false
		} else if (currentMood == "Bad/Awful" || currentMood == "Normal") {
			printToLog("[MOOD] Current mood is not good. Recovering mood now.")
			if (!findAndTapImage("recover_mood", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
				// Fallback to summer rest if available (shouldn't happen since we check isSummer above)
				findAndTapImage("recover_energy_summer", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)
			}

			// Do the date if it is unlocked.
			if (findAndTapImage("recover_mood_date", tries = 1, region = imageUtils.regionMiddle, suppressError = true)) {
				wait(1.0)
			}

			findAndTapImage("ok", region = imageUtils.regionMiddle, suppressError = true)
			raceRepeatWarningCheck = false
			true
		} else {
			printToLog("[MOOD] Current mood is good enough. Moving on...")
			false
		}
	}

	/**
	 * Prints the training map object for informational purposes.
	 */
	private fun printTrainingMap() {
		printToLog("\n[INFO] Stat Gains by Training:")
		trainingMap.forEach { name, training ->
			printToLog("[TRAINING] $name Training stat gains: ${training.statGains.contentToString()}, failure chance: ${training.failureChance}%.")
		}
	}

	/**
	 * Perform misc checks to potentially fix instances where the bot is stuck.
	 *
	 * @return True if the checks passed. Otherwise false if the bot encountered a warning popup and needs to exit.
	 */
	fun performMiscChecks(): Boolean {
		printToLog("\n[INFO] Beginning check for misc cases...")

		if (enablePopupCheck && imageUtils.findImage("cancel", tries = 1, region = imageUtils.regionBottomHalf).first != null &&
			imageUtils.findImage("recover_mood_date", tries = 1, region = imageUtils.regionMiddle).first == null) {
			printToLog("\n[END] Bot may have encountered a warning popup. Exiting now...")
			notificationMessage = "Bot may have encountered a warning popup"
			return false
		} else if (findAndTapImage("next", tries = 1, region = imageUtils.regionBottomHalf)) {
			// Now confirm the completion of a Training Goal popup.
			wait(2.0)
			findAndTapImage("next", tries = 1, region = imageUtils.regionBottomHalf)
			wait(1.0)
		} else if (imageUtils.findImage("crane_game", tries = 1, region = imageUtils.regionBottomHalf).first != null) {
			// Stop when the bot has reached the Crane Game Event.
			printToLog("\n[END] Bot will stop due to the detection of the Crane Game Event. Please complete it and restart the bot.")
			notificationMessage = "Bot will stop due to the detection of the Crane Game Event. Please complete it and restart the bot."
			return false
		} else if (findAndTapImage("race_retry", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			printToLog("[INFO] There is a race retry popup.")
			wait(5.0)
		} else if (findAndTapImage("race_accept_trophy", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			printToLog("[INFO] There is a possible popup to accept a trophy.")
			finishRace(true, isExtra = true)
		} else if (findAndTapImage("race_end", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			printToLog("[INFO] Ended a leftover race.")
		} else if (imageUtils.findImage("connection_error", tries = 1, region = imageUtils.regionMiddle, suppressError = true).first != null) {
			printToLog("\n[END] Bot will stop due to detecting a connection error.")
			notificationMessage = "Bot will stop due to detecting a connection error."
			return false
		} else if (imageUtils.findImage("race_not_enough_fans", tries = 1, region = imageUtils.regionMiddle, suppressError = true).first != null) {
			printToLog("[INFO] There was a popup about insufficient fans.")
			encounteredRacingPopup = true
			findAndTapImage("cancel", region = imageUtils.regionBottomHalf)
		} else if (findAndTapImage("back", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			wait(1.0)
		} else if (!BotService.isRunning) {
			throw InterruptedException()
		} else {
			printToLog("[INFO] Did not detect any popups or the Crane Game on the screen. Moving on...")
		}

		return true
	}

	/**
	 * Bot will begin automation here.
	 *
	 * @return True if all automation goals have been met. False otherwise.
	 */
	fun start(): Boolean {
		// Print current app settings at the start of the run.
		SettingsPrinter.printCurrentSettings(myContext) { message ->
			printToLog(message)
		}

		// Load the stat targets from preferences.
		loadStatTargets()

		// If debug mode is off, then it is necessary to wait a few seconds for the Toast message to disappear from the screen to prevent it obstructing anything beneath it.
		if (!debugMode) {
			wait(5.0)
		}

		// Print device and version information.
		printToLog("[INFO] Device Information: ${MediaProjectionService.displayWidth}x${MediaProjectionService.displayHeight}, DPI ${MediaProjectionService.displayDPI}")
		if (MediaProjectionService.displayWidth != 1080) printToLog("[WARNING] ⚠️ Bot performance will be severely degraded since display width is not 1080p unless an appropriate scale is set for your device.")
		if (debugMode) printToLog("[WARNING] ⚠️ Debug Mode is enabled. All bot operations will be significantly slower as a result.")
		if (sharedPreferences.getInt("customScale", 100).toDouble() / 100.0 != 1.0) printToLog("[INFO] Manual scale has been set to ${sharedPreferences.getInt("customScale", 100).toDouble() / 100.0}")
		printToLog("[WARNING] ⚠️ Note that certain Android notification styles (like banners) are big enough that they cover the area that contains the Mood which will interfere with mood recovery logic in the beginning.")
		val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			myContext.packageManager.getPackageInfo(myContext.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
		} else {
			myContext.packageManager.getPackageInfo(myContext.packageName, 0)
		}
		val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
			packageInfo.longVersionCode
		} else {
			@Suppress("DEPRECATION")
			packageInfo.versionCode.toLong()
		}
		printToLog("[INFO] Bot version: ${packageInfo.versionName} ($versionCode)\n\n")

		val startTime: Long = System.currentTimeMillis()

		// Start debug tests here if enabled.
		if (sharedPreferences.getBoolean("debugMode_startTemplateMatchingTest", false)) {
			startTemplateMatchingTest()
		} else if (sharedPreferences.getBoolean("debugMode_startSingleTrainingFailureOCRTest", false)) {
			startSingleTrainingFailureOCRTest()
		} else if (sharedPreferences.getBoolean("debugMode_startComprehensiveTrainingFailureOCRTest", false)) {
			startComprehensiveTrainingFailureOCRTest()
		}
		// Otherwise, proceed with regular bot operations.
		else if (campaign == "Ao Haru") {
			val aoHaruCampaign = AoHaru(this)
			aoHaruCampaign.start()
		} else {
			val uraFinaleCampaign = Campaign(this)
			uraFinaleCampaign.start()
		}

		val endTime: Long = System.currentTimeMillis()
		Log.d(tag, "Total Runtime: ${endTime - startTime}ms")

		return true
	}

	/**
	 * Get current fans count via OCR
	 */
	private fun getCurrentFans(): Int {
		// This would need OCR implementation similar to determineSkillPoints
		// For now, return a default value
		return 5000
	}

	/**
	 * Get fan targets based on current progress
	 */
	private fun getFanTargetsForGrade(): Pair<Int, String> {
		return when {
			currentDate.year == 1 -> Pair(1000, "G3")
			currentDate.year == 2 && currentDate.month <= 6 -> Pair(2000, "G2")
			currentDate.year == 2 -> Pair(5000, "G1")
			else -> Pair(10000, "URA Finals")
		}
	}

	/**
	 * Get turns until next mandatory race
	 */
	private fun getTurnsUntilNextMandatory(): Int {
		// Estimate based on current date
		// Mandatory races typically happen every 4-6 turns
		return when {
			currentDate.phase == "Early" -> 3
			else -> 2
		}
	}

	/**
	 * Check if we need a specific fan count for upcoming race
	 */
	private fun needsSpecificFanCount(currentFans: Int, targets: Pair<Int, String>): Boolean {
		val (targetFans, grade) = targets
		val deficit = targetFans - currentFans

		// Need specific fans if close to target but not quite there
		return deficit in 100..500
	}

	/**
	 * Select best race based on multiple strategic factors
	 */
	private fun selectBestRace(
		races: List<ImageUtils.RaceDetails>,
		currentFans: Int,
		fanTargets: Pair<Int, String>,
		turnsRemaining: Int
	): Int {
		if (races.isEmpty()) return 0

		val (targetFans, grade) = fanTargets
		val fanDeficit = targetFans - currentFans

		// Score each race
		val raceScores = races.mapIndexed { index, race ->
			var score = 0.0

			// Base fan value
			score += race.fans * 1.0

			// Double prediction bonus (huge boost)
			if (race.hasDoublePredictions) {
				score *= 2.5
				printToLog("[RACE] Race ${index + 1}: Double prediction bonus applied")
			}

			// Fan planning bonus
			if (fanDeficit > 0) {
				val fanProgress = race.fans.toDouble() / fanDeficit
				if (fanProgress in 0.5..1.2) {
					// This race would get us close to target
					score *= 1.3
					printToLog("[RACE] Race ${index + 1}: Good fan progress toward $grade (${race.fans}/$fanDeficit)")
				}
			}

			// Urgency multiplier if we're running out of time
			if (turnsRemaining <= 2 && fanDeficit > 0) {
				val urgencyBonus = 1.0 + (race.fans.toDouble() / fanDeficit * 0.5)
				score *= urgencyBonus
				printToLog("[RACE] Race ${index + 1}: Urgency bonus for $grade requirement")
			}

			// Force racing override - prioritize any race we can win
			if (enableForceRacing && race.hasDoublePredictions) {
				score *= 3.0 // Heavy bias toward winnable races
			}

			printToLog("[RACE] Race ${index + 1} final score: $score (fans: ${race.fans}, double: ${race.hasDoublePredictions})")
			Pair(index, score)
		}

		// Select race with highest score
		val bestRace = raceScores.maxByOrNull { it.second } ?: Pair(0, 0.0)
		printToLog("[RACE] Selected race ${bestRace.first + 1} with score ${bestRace.second}")
		return bestRace.first
	}
}