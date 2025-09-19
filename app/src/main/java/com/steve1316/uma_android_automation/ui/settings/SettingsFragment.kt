package com.steve1316.uma_android_automation.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.R

class SettingsFragment : PreferenceFragmentCompat() {
	private val logTag: String = "[${MainActivity.loggerTag}]SettingsFragment"
	
	private lateinit var sharedPreferences: SharedPreferences
	
	// This listener is triggered whenever the user changes a Preference setting in the Settings Page.
	private val onSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
		if (key != null) {
			when (key) {
				"campaign" -> {
					val campaignListPreference = findPreference<ListPreference>("campaign")!!
					campaignListPreference.summary = "Selected: ${campaignListPreference.value}"
					sharedPreferences.edit {
						putString("campaign", campaignListPreference.value)
						commit()
					}
				}
				"strategy" -> {
					val strategyListPreference = findPreference<ListPreference>("strategy")!!
					strategyListPreference.summary = "Selected ${strategyListPreference.value}"
					sharedPreferences.edit {
						putString("strategy", strategyListPreference.value)
						commit()
					}
				}
				"enableFarmingFans" -> {
					val enableFarmingFansPreference = findPreference<CheckBoxPreference>("enableFarmingFans")!!
					val daysToRunExtraRacesPreference = findPreference<SeekBarPreference>("daysToRunExtraRaces")!!
					
					daysToRunExtraRacesPreference.isEnabled = enableFarmingFansPreference.isChecked
					
					sharedPreferences.edit {
						putBoolean("enableFarmingFans", enableFarmingFansPreference.isChecked)
						commit()
					}
				}
				"enableScheduledExtraRaces" -> {
					val enableScheduledExtraRacesPreference = findPreference<CheckBoxPreference>("enableScheduledExtraRaces")!!
					sharedPreferences.edit {
						putBoolean("enableScheduledExtraRaces", enableScheduledExtraRacesPreference.isChecked)
						commit()
					}
				}
				"daysToRunExtraRaces" -> {
					val daysToRunExtraRacesPreference = findPreference<SeekBarPreference>("daysToRunExtraRaces")!!
					
					sharedPreferences.edit {
						putInt("daysToRunExtraRaces", daysToRunExtraRacesPreference.value)
						commit()
					}
				}
				"enableSkillPointCheck" -> {
					val enableSkillPointCheckPreference = findPreference<CheckBoxPreference>("enableSkillPointCheck")!!
					val skillPointCheckPreference = findPreference<SeekBarPreference>("skillPointCheck")!!
					skillPointCheckPreference.isEnabled = enableSkillPointCheckPreference.isChecked
					
					sharedPreferences.edit {
						putBoolean("enableSkillPointCheck", enableSkillPointCheckPreference.isChecked)
						commit()
					}
				}
				"skillPointCheck" -> {
					val skillPointCheckPreference = findPreference<SeekBarPreference>("skillPointCheck")!!
					
					sharedPreferences.edit {
						putInt("skillPointCheck", skillPointCheckPreference.value)
						commit()
					}
				}
				"enablePopupCheck" -> {
					val enablePopupCheckPreference = findPreference<CheckBoxPreference>("enablePopupCheck")!!
					
					sharedPreferences.edit {
						putBoolean("enablePopupCheck", enablePopupCheckPreference.isChecked)
						commit()
					}
				}

				"disableRaceRetries" -> {
					val disableRaceRetriesPreference = findPreference<CheckBoxPreference>("disableRaceRetries")!!

					sharedPreferences.edit {
						putBoolean("disableRaceRetries", disableRaceRetriesPreference.isChecked)
						commit()
					}
				}
				"enableStopOnMandatoryRace" -> {
					val enableStopOnMandatoryRacePreference = findPreference<CheckBoxPreference>("enableStopOnMandatoryRace")!!
					
					sharedPreferences.edit {
						putBoolean("enableStopOnMandatoryRace", enableStopOnMandatoryRacePreference.isChecked)
						commit()
					}
				}
				"enablePrioritizeEnergyOptions" -> {
					val enablePrioritizeEnergyOptionsPreference = findPreference<CheckBoxPreference>("enablePrioritizeEnergyOptions")!!

					sharedPreferences.edit {
						putBoolean("enablePrioritizeEnergyOptions", enablePrioritizeEnergyOptionsPreference.isChecked)
						commit()
					}
				}
				"enableForceRacing" -> {
					val enableForceRacingPreference = findPreference<CheckBoxPreference>("enableForceRacing")!!

					sharedPreferences.edit {
						putBoolean("enableForceRacing", enableForceRacingPreference.isChecked)
						commit()
					}
				}
				"debugMode" -> {
					val debugModePreference = findPreference<CheckBoxPreference>("debugMode")!!
					
					sharedPreferences.edit {
						putBoolean("debugMode", debugModePreference.isChecked)
						commit()
					}
				}
				"confidence" -> {
					val confidencePreference = findPreference<SeekBarPreference>("confidence")!!

					sharedPreferences.edit {
						putInt("confidence", confidencePreference.value)
						commit()
					}
				}
				"customScale" -> {
					val customScalePreference = findPreference<SeekBarPreference>("customScale")!!

					sharedPreferences.edit {
						putInt("customScale", customScalePreference.value)
						commit()
					}
				}
				"debugMode_startTemplateMatchingTest" -> {
					val debugModeStartTemplateMatchingTestPreference = findPreference<CheckBoxPreference>("debugMode_startTemplateMatchingTest")!!

					sharedPreferences.edit {
						putBoolean("debugMode_startTemplateMatchingTest", debugModeStartTemplateMatchingTestPreference.isChecked)
						commit()
					}
				}
				"debugMode_startSingleTrainingFailureOCRTest" -> {
					val debugModeStartSingleTrainingFailureOCRTestPreference = findPreference<CheckBoxPreference>("debugMode_startSingleTrainingFailureOCRTest")!!

					sharedPreferences.edit {
						putBoolean("debugMode_startSingleTrainingFailureOCRTest", debugModeStartSingleTrainingFailureOCRTestPreference.isChecked)
						commit()
					}
				}
				"debugMode_startComprehensiveTrainingFailureOCRTest" -> {
					val debugModeStartComprehensiveTrainingFailureOCRTestPreference = findPreference<CheckBoxPreference>("debugMode_startComprehensiveTrainingFailureOCRTest")!!

					sharedPreferences.edit {
						putBoolean("debugMode_startComprehensiveTrainingFailureOCRTest", debugModeStartComprehensiveTrainingFailureOCRTestPreference.isChecked)
						commit()
					}
				}
				"hideComparisonResults" -> {
					val hideComparisonResultsPreference = findPreference<CheckBoxPreference>("hideComparisonResults")!!
					
					sharedPreferences.edit {
						putBoolean("hideComparisonResults", hideComparisonResultsPreference.isChecked)
						commit()
					}
				}
			}
		}
	}
	
	override fun onResume() {
		super.onResume()
		
		// Makes sure that OnSharedPreferenceChangeListener works properly and avoids the situation where the app suddenly stops triggering the listener.
		preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
	}
	
	override fun onPause() {
		super.onPause()
		preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
	}
	
	// This function is called right after the user navigates to the SettingsFragment.
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		// Display the layout using the preferences xml.
		setPreferencesFromResource(R.xml.preferences, rootKey)
		
		// Get the SharedPreferences.
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		
		// Grab the saved preferences from the previous time the user used the app.
		val campaign: String = sharedPreferences.getString("campaign", "")!!
		val strategy: String = sharedPreferences.getString("strategy", "")!!
		val enableScheduledExtraRaces: Boolean = sharedPreferences.getBoolean("enableScheduledExtraRaces", false)
		val enableFarmingFans: Boolean = sharedPreferences.getBoolean("enableFarmingFans", false)
		val daysToRunExtraRaces: Int = sharedPreferences.getInt("daysToRunExtraRaces", 4)
		val enableSkillPointCheck: Boolean = sharedPreferences.getBoolean("enableSkillPointCheck", false)
		val skillPointCheck: Int = sharedPreferences.getInt("skillPointCheck", 750)
		val enablePopupCheck: Boolean = sharedPreferences.getBoolean("enablePopupCheck", false)
		val disableRaceRetries: Boolean = sharedPreferences.getBoolean("disableRaceRetries", false)
		val enableStopOnMandatoryRace: Boolean = sharedPreferences.getBoolean("enableStopOnMandatoryRace", false)
		val enablePrioritizeEnergyOptions: Boolean = sharedPreferences.getBoolean("enablePrioritizeEnergyOptions", false)
		val enableForceRacing: Boolean = sharedPreferences.getBoolean("enableForceRacing", false)
		val debugMode: Boolean = sharedPreferences.getBoolean("debugMode", false)
		val confidence: Int = sharedPreferences.getInt("confidence", 80)
		val customScale: Int = sharedPreferences.getInt("customScale", 100)
		val debugModeStartTemplateMatchingTest: Boolean = sharedPreferences.getBoolean("debugMode_startTemplateMatchingTest", false)
		val debugModeStartSingleTrainingFailureOCRTest: Boolean = sharedPreferences.getBoolean("debugMode_startSingleTrainingFailureOCRTest", false)
		val debugModeStartComprehensiveTrainingFailureOCRTest: Boolean = sharedPreferences.getBoolean("debugMode_startComprehensiveTrainingFailureOCRTest", false)
		val hideComparisonResults: Boolean = sharedPreferences.getBoolean("hideComparisonResults", true)
		
		// Get references to the Preference components.
		val campaignListPreference = findPreference<ListPreference>("campaign")!!
		val strategyListPreference = findPreference<ListPreference>("strategy")!!
		val enableScheduledExtraRacesPreference = findPreference<CheckBoxPreference>("enableExtraScheduledRaces")!!
		val enableFarmingFansPreference = findPreference<CheckBoxPreference>("enableFarmingFans")!!
		val daysToRunExtraRacesPreference = findPreference<SeekBarPreference>("daysToRunExtraRaces")!!
		val enableSkillPointCheckPreference = findPreference<CheckBoxPreference>("enableSkillPointCheck")!!
		val skillPointCheckPreference = findPreference<SeekBarPreference>("skillPointCheck")!!
		val enablePopupCheckPreference = findPreference<CheckBoxPreference>("enablePopupCheck")!!
		val disableRaceRetriesPreference = findPreference<CheckBoxPreference>("disableRaceRetries")!!
		val enableStopOnMandatoryRacePreference = findPreference<CheckBoxPreference>("enableStopOnMandatoryRace")!!
		val enablePrioritizeEnergyOptionsPreference = findPreference<CheckBoxPreference>("enablePrioritizeEnergyOptions")!!
		val enableForceRacingPreference = findPreference<CheckBoxPreference>("enableForceRacing")!!
		val debugModePreference = findPreference<CheckBoxPreference>("debugMode")!!
		val confidencePreference = findPreference<SeekBarPreference>("confidence")!!
		val customScalePreference = findPreference<SeekBarPreference>("customScale")!!
		val debugModeStartTemplateMatchingTestPreference = findPreference<CheckBoxPreference>("debugMode_startTemplateMatchingTest")!!
		val debugModeStartSingleTrainingFailureOCRTestPreference = findPreference<CheckBoxPreference>("debugMode_startSingleTrainingFailureOCRTest")!!
		val debugModeStartComprehensiveTrainingFailureOCRTestPreference = findPreference<CheckBoxPreference>("debugMode_startComprehensiveTrainingFailureOCRTest")!!
		val hideComparisonResultsPreference = findPreference<CheckBoxPreference>("hideComparisonResults")!!
		
		// Now set the following values from the shared preferences.
		campaignListPreference.value = campaign
		if (campaign != "") {
			campaignListPreference.summary = "Selected: ${campaignListPreference.value}"
		}
		strategyListPreference.value = strategy
		if (strategy != "") {
			strategyListPreference.summary = "Selected: ${strategyListPreference.value}"
		}
		enableFarmingFansPreference.isChecked = enableFarmingFans
		enableScheduledExtraRacesPreference.isChecked = enableScheduledExtraRaces
		daysToRunExtraRacesPreference.isEnabled = enableFarmingFansPreference.isChecked
		daysToRunExtraRacesPreference.value = daysToRunExtraRaces
		enableSkillPointCheckPreference.isChecked = enableSkillPointCheck
		skillPointCheckPreference.value = skillPointCheck
		enablePopupCheckPreference.isChecked = enablePopupCheck
		disableRaceRetriesPreference.isChecked = disableRaceRetries
		enableStopOnMandatoryRacePreference.isChecked = enableStopOnMandatoryRace
		enablePrioritizeEnergyOptionsPreference.isChecked = enablePrioritizeEnergyOptions
		enableForceRacingPreference.isChecked = enableForceRacing
		debugModePreference.isChecked = debugMode
		confidencePreference.value = confidence
		customScalePreference.value = customScale
		debugModeStartTemplateMatchingTestPreference.isChecked = debugModeStartTemplateMatchingTest
		debugModeStartSingleTrainingFailureOCRTestPreference.isChecked = debugModeStartSingleTrainingFailureOCRTest
		debugModeStartComprehensiveTrainingFailureOCRTestPreference.isChecked = debugModeStartComprehensiveTrainingFailureOCRTest
		hideComparisonResultsPreference.isChecked = hideComparisonResults
		skillPointCheckPreference.isEnabled = enableSkillPointCheckPreference.isChecked
		
		// Solution courtesy of https://stackoverflow.com/a/63368599
		// In short, Fragments via the mobile_navigation.xml are children of NavHostFragment, not MainActivity's supportFragmentManager.
		// This is why using the method described in official Google docs via OnPreferenceStartFragmentCallback and using the supportFragmentManager is not correct for this instance.
		findPreference<Preference>("trainingOptions")?.setOnPreferenceClickListener {
			// Navigate to the TrainingFragment.
			findNavController().navigate(R.id.nav_training)
			true
		}
		findPreference<Preference>("trainingEventOptions")?.setOnPreferenceClickListener {
			// Navigate to the TrainingEventFragment.
			findNavController().navigate(R.id.nav_training_event)
			true
		}
		findPreference<Preference>("ocrOptions")?.setOnPreferenceClickListener {
			// Navigate to the OCRFragment.
			findNavController().navigate(R.id.nav_ocr)
			true
		}
		
		Log.d(logTag, "Main Preferences created successfully.")
	}
}