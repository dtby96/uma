package com.steve1316.uma_android_automation.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import org.json.JSONException
import androidx.core.content.edit
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.beust.klaxon.JsonReader
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.R
import com.steve1316.uma_android_automation.data.CharacterData
import com.steve1316.uma_android_automation.data.SkillData
import com.steve1316.uma_android_automation.data.SupportData
import com.steve1316.uma_android_automation.utils.MediaProjectionService
import com.steve1316.uma_android_automation.utils.MessageLog
import com.steve1316.uma_android_automation.utils.MyAccessibilityService
import java.io.StringReader
import androidx.core.net.toUri
import com.steve1316.uma_android_automation.utils.SettingsPrinter

class HomeFragment : Fragment() {
	private val logTag: String = "[${MainActivity.loggerTag}]HomeFragment"
	private var firstBoot = false
	private var firstRun = true
	
	private lateinit var myContext: Context
	private lateinit var homeFragmentView: View
	private lateinit var startButton: MaterialButton
	
	// UI components for sliders
	private lateinit var speedSlider: Slider
	private lateinit var staminaSlider: Slider
	private lateinit var powerSlider: Slider
	private lateinit var gutsSlider: Slider
	private lateinit var witSlider: Slider
	private lateinit var speedValueText: TextView
	private lateinit var staminaValueText: TextView
	private lateinit var powerValueText: TextView
	private lateinit var gutsValueText: TextView
	private lateinit var witValueText: TextView
	private lateinit var priorityDisplay: TextView
	private lateinit var presetDropdown: AutoCompleteTextView
	private lateinit var savePresetButton: MaterialButton
	private lateinit var deletePresetButton: MaterialButton
	private var presetAdapter: ArrayAdapter<String>? = null
	private var currentSelectedPreset: String? = null
	
	private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		myContext = requireContext()
		
		// Initialize the ActivityResultLauncher
		mediaProjectionLauncher = registerForActivityResult(
			ActivityResultContracts.StartActivityForResult()
		) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				// Start up the MediaProjection service after the user accepts the onscreen prompt.
				result.data?.let { data ->
					myContext.startService(MediaProjectionService.getStartIntent(myContext, result.resultCode, data))
				}
			}
		}
	}
	
	@SuppressLint("SetTextI18n")
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		myContext = requireContext()
		
		homeFragmentView = inflater.inflate(R.layout.fragment_home, container, false)
		
		// Initialize UI components
		initializeUIComponents()
		
		// Start or stop the MediaProjection service via this button.
		startButton = homeFragmentView.findViewById(R.id.start_button)
		startButton.setOnClickListener {
			val readyCheck = startReadyCheck()
			if (readyCheck && !MediaProjectionService.isRunning) {
				// Save current stat targets before starting
				saveStatTargets()
				startProjection()
				startButton.text = getString(R.string.stop)
				
				// This is needed because onResume() is immediately called right after accepting the MediaProjection and it has not been properly
				// initialized yet so it would cause the button's text to revert back to "Start".
				firstBoot = true
			} else if (MediaProjectionService.isRunning) {
				stopProjection()
				startButton.text = getString(R.string.start)
			}
		}
		
		// Load saved preferences
		loadSavedPreferences()
		
		// Setup listeners
		setupListeners()
		
		// Display current settings
		displayCurrentSettings()
		
		return homeFragmentView
	}
	
	private fun initializeUIComponents() {
		// Sliders
		speedSlider = homeFragmentView.findViewById(R.id.speed_seekbar)
		staminaSlider = homeFragmentView.findViewById(R.id.stamina_seekbar)
		powerSlider = homeFragmentView.findViewById(R.id.power_seekbar)
		gutsSlider = homeFragmentView.findViewById(R.id.guts_seekbar)
		witSlider = homeFragmentView.findViewById(R.id.wit_seekbar)
		
		// Value TextViews
		speedValueText = homeFragmentView.findViewById(R.id.speed_value)
		staminaValueText = homeFragmentView.findViewById(R.id.stamina_value)
		powerValueText = homeFragmentView.findViewById(R.id.power_value)
		gutsValueText = homeFragmentView.findViewById(R.id.guts_value)
		witValueText = homeFragmentView.findViewById(R.id.wit_value)
		
		// Priority Display
		priorityDisplay = homeFragmentView.findViewById(R.id.priority_display)
		
		// Preset Management
		presetDropdown = homeFragmentView.findViewById(R.id.preset_dropdown)
		savePresetButton = homeFragmentView.findViewById(R.id.save_preset_button)
		deletePresetButton = homeFragmentView.findViewById(R.id.delete_preset_button)
		
		// Initialize preset dropdown
		initializePresetDropdown()
		
		// Initialize delete button state
		updateDeleteButtonState()
		
		// Save preset button
		savePresetButton.setOnClickListener {
			showSavePresetDialog()
		}
		
		// Delete preset button
		deletePresetButton.setOnClickListener {
			showDeletePresetDialog()
		}
	}
	
	private fun setupListeners() {
		// Setup slider listeners
		val sliderChangeListener = Slider.OnChangeListener { slider, value, fromUser ->
			val intValue = value.toInt()
			when (slider.id) {
				R.id.speed_seekbar -> speedValueText.text = intValue.toString()
				R.id.stamina_seekbar -> staminaValueText.text = intValue.toString()
				R.id.power_seekbar -> powerValueText.text = intValue.toString()
				R.id.guts_seekbar -> gutsValueText.text = intValue.toString()
				R.id.wit_seekbar -> witValueText.text = intValue.toString()
			}
			// Update priority display whenever values change
			updatePriorityDisplay()
		}
		
		val sliderTouchListener = object : Slider.OnSliderTouchListener {
			override fun onStartTrackingTouch(slider: Slider) {}
			override fun onStopTrackingTouch(slider: Slider) {
				saveStatTargets()
				updatePriorityDisplay()
			}
		}
		
		speedSlider.addOnChangeListener(sliderChangeListener)
		staminaSlider.addOnChangeListener(sliderChangeListener)
		powerSlider.addOnChangeListener(sliderChangeListener)
		gutsSlider.addOnChangeListener(sliderChangeListener)
		witSlider.addOnChangeListener(sliderChangeListener)
		
		speedSlider.addOnSliderTouchListener(sliderTouchListener)
		staminaSlider.addOnSliderTouchListener(sliderTouchListener)
		powerSlider.addOnSliderTouchListener(sliderTouchListener)
		gutsSlider.addOnSliderTouchListener(sliderTouchListener)
		witSlider.addOnSliderTouchListener(sliderTouchListener)
	}
	
	private fun loadSavedPreferences() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		
		// Load saved stat targets
		val speedTarget = sharedPreferences.getInt("current_speed_target", 600)
		val staminaTarget = sharedPreferences.getInt("current_stamina_target", 600)
		val powerTarget = sharedPreferences.getInt("current_power_target", 600)
		val gutsTarget = sharedPreferences.getInt("current_guts_target", 300)
		val witTarget = sharedPreferences.getInt("current_wit_target", 600)
		
		// Set slider values
		speedSlider.value = speedTarget.toFloat()
		staminaSlider.value = staminaTarget.toFloat()
		powerSlider.value = powerTarget.toFloat()
		gutsSlider.value = gutsTarget.toFloat()
		witSlider.value = witTarget.toFloat()
		
		// Update value displays
		speedValueText.text = speedTarget.toString()
		staminaValueText.text = staminaTarget.toString()
		powerValueText.text = powerTarget.toString()
		gutsValueText.text = gutsTarget.toString()
		witValueText.text = witTarget.toString()
		
		// Update priority display
		updatePriorityDisplay()
	}
	
	private fun calculateStatPriority(): String {
		// Get current stat values
		val speedTarget = speedSlider.value.toInt()
		val staminaTarget = staminaSlider.value.toInt()
		val powerTarget = powerSlider.value.toInt()
		val gutsTarget = gutsSlider.value.toInt()
		val witTarget = witSlider.value.toInt()
		
		// Create pairs of stat name and value
		val stats = listOf(
			"Speed" to speedTarget,
			"Stamina" to staminaTarget,
			"Power" to powerTarget,
			"Guts" to gutsTarget,
			"Wit" to witTarget
		)
		
		// Sort by value descending (higher value = higher priority)
		val sortedStats = stats.sortedByDescending { it.second }
		
		// Build priority string
		return sortedStats.joinToString("|") { it.first }
	}
	
	private fun updatePriorityDisplay() {
		val priority = calculateStatPriority()
		
		// Get current stat values for display
		val speedTarget = speedSlider.value.toInt()
		val staminaTarget = staminaSlider.value.toInt()
		val powerTarget = powerSlider.value.toInt()
		val gutsTarget = gutsSlider.value.toInt()
		val witTarget = witSlider.value.toInt()
		
		// Create display text showing order with values
		val stats = mapOf(
			"Speed" to speedTarget,
			"Stamina" to staminaTarget,
			"Power" to powerTarget,
			"Guts" to gutsTarget,
			"Wit" to witTarget
		)
		
		val sortedStats = stats.entries.sortedByDescending { it.value }
		val displayText = "Priority Order: " + sortedStats.joinToString(" > ") { "${it.key}(${it.value})" }
		
		priorityDisplay.text = displayText
		
		// Also save the priority to preferences
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		sharedPreferences.edit {
			putString("statPrioritization", priority)
			commit()
		}
	}
	
	private fun saveStatTargets() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		
		val speedTarget = speedSlider.value.toInt()
		val staminaTarget = staminaSlider.value.toInt()
		val powerTarget = powerSlider.value.toInt()
		val gutsTarget = gutsSlider.value.toInt()
		val witTarget = witSlider.value.toInt()
		
		// Calculate and save priority
		val priority = calculateStatPriority()
		
		sharedPreferences.edit {
			putInt("current_speed_target", speedTarget)
			putInt("current_stamina_target", staminaTarget)
			putInt("current_power_target", powerTarget)
			putInt("current_guts_target", gutsTarget)
			putInt("current_wit_target", witTarget)
			putString("statPrioritization", priority)
			commit()
		}
		
		Log.d(logTag, "Saved stat targets - Speed: $speedTarget, Stamina: $staminaTarget, Power: $powerTarget, Guts: $gutsTarget, Wit: $witTarget")
		Log.d(logTag, "Auto-calculated priority: $priority")
	}
	
	private fun initializePresetDropdown() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val presetList = loadPresetList()
		
		presetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, presetList)
		presetDropdown.setAdapter(presetAdapter)
		
		presetDropdown.setOnItemClickListener { _, _, position, _ ->
			val selectedPreset = presetList[position]
			currentSelectedPreset = selectedPreset
			applyPreset(selectedPreset)
			// Enable/disable delete button based on whether it's a custom preset
			updateDeleteButtonState()
		}
	}
	
	private fun loadPresetList(): MutableList<String> {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val presets = mutableListOf<String>()
		
		// Add default presets
		presets.add("Sprint")
		presets.add("Mile")
		presets.add("Medium")
		presets.add("Long")
		presets.add("Balanced")
		
		// Load custom presets
		val customPresets = sharedPreferences.getStringSet("custom_presets", emptySet()) ?: emptySet()
		presets.addAll(customPresets)
		
		return presets
	}
	
	private fun showSavePresetDialog() {
		// Create custom view for the dialog
		val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
		val input = EditText(requireContext()).apply {
			hint = "Enter preset name"
			setSingleLine(true)
			requestFocus()
			// Add padding for better appearance
			setPadding(50, 20, 50, 20)
		}
		
		// Use Material AlertDialog
		val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("Save Preset")
			.setMessage("Enter a name for your current stat configuration:")
			.setView(input)
			.setPositiveButton("Save") { _, _ ->
				val presetName = input.text.toString().trim()
				if (presetName.isNotEmpty()) {
					// Check if preset name already exists
					val existingPresets = loadPresetList()
					if (existingPresets.contains(presetName)) {
						// Ask for confirmation to overwrite
						showOverwriteConfirmDialog(presetName)
					} else {
						saveCustomPreset(presetName)
					}
				} else {
					MessageLog.messageLog.add("[PRESET] Please enter a preset name")
				}
			}
			.setNegativeButton("Cancel", null)
			.create()
		
		// Show keyboard automatically
		dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
		dialog.show()
	}
	
	private fun showOverwriteConfirmDialog(presetName: String) {
		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("Overwrite Preset?")
			.setMessage("A preset named '$presetName' already exists. Do you want to overwrite it?")
			.setPositiveButton("Overwrite") { _, _ ->
				saveCustomPreset(presetName)
			}
			.setNegativeButton("Cancel", null)
			.show()
	}
	
	private fun showDeletePresetDialog() {
		val currentPreset = currentSelectedPreset ?: presetDropdown.text.toString()
		
		// Check if it's a default preset
		val defaultPresets = listOf("Sprint", "Mile", "Medium", "Long", "Balanced")
		if (defaultPresets.contains(currentPreset)) {
			MessageLog.messageLog.add("[PRESET] Cannot delete default preset: $currentPreset")
			return
		}
		
		if (currentPreset.isEmpty()) {
			MessageLog.messageLog.add("[PRESET] Please select a preset to delete")
			return
		}
		
		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("Delete Preset")
			.setMessage("Are you sure you want to delete the preset '$currentPreset'?")
			.setPositiveButton("Delete") { _, _ ->
				deleteCustomPreset(currentPreset)
			}
			.setNegativeButton("Cancel", null)
			.show()
	}
	
	private fun deleteCustomPreset(name: String) {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val editor = sharedPreferences.edit()
		
		// Remove preset data
		editor.remove("preset_$name")
		
		// Remove from custom presets list
		val customPresets = sharedPreferences.getStringSet("custom_presets", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
		customPresets.remove(name)
		editor.putStringSet("custom_presets", customPresets)
		
		editor.commit()
		
		// Update dropdown
		val presetList = loadPresetList()
		presetAdapter?.clear()
		presetAdapter?.addAll(presetList)
		presetAdapter?.notifyDataSetChanged()
		
		// Clear the dropdown text
		presetDropdown.setText("", false)
		currentSelectedPreset = null
		updateDeleteButtonState()
		
		MessageLog.messageLog.add("[PRESET] Deleted custom preset: $name")
	}
	
	private fun updateDeleteButtonState() {
		val currentPreset = currentSelectedPreset ?: presetDropdown.text.toString()
		val defaultPresets = listOf("Sprint", "Mile", "Medium", "Long", "Balanced")
		
		// Disable delete button for default presets or when no preset is selected
		deletePresetButton.isEnabled = currentPreset.isNotEmpty() && !defaultPresets.contains(currentPreset)
	}
	
	private fun saveCustomPreset(name: String) {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		
		// Create preset JSON object
		val preset = JSONObject()
		try {
			preset.put("speed", speedSlider.value.toInt())
			preset.put("stamina", staminaSlider.value.toInt())
			preset.put("power", powerSlider.value.toInt())
			preset.put("guts", gutsSlider.value.toInt())
			preset.put("wit", witSlider.value.toInt())
			
			// Save preset
			val editor = sharedPreferences.edit()
			editor.putString("preset_$name", preset.toString())
			
			// Add to custom presets list
			val customPresets = sharedPreferences.getStringSet("custom_presets", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
			customPresets.add(name)
			editor.putStringSet("custom_presets", customPresets)
			
			editor.commit()
			
			// Update dropdown
			val presetList = loadPresetList()
			presetAdapter?.clear()
			presetAdapter?.addAll(presetList)
			presetAdapter?.notifyDataSetChanged()
			
			MessageLog.messageLog.add("[PRESET] Saved custom preset: $name")
		} catch (e: JSONException) {
			Log.e(logTag, "Failed to save preset: ${e.message}")
		}
	}
	
	private fun applyPreset(name: String) {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		
		// Check if it's a default preset
		when (name) {
			"Sprint" -> {
				// Sprint: High Speed and Power, moderate others
				applyPresetValues(1000, 600, 800, 400, 500)
			}
			"Mile" -> {
				// Mile: Balanced Speed and Stamina, good Power
				applyPresetValues(900, 800, 700, 400, 500)
			}
			"Medium" -> {
				// Medium: Higher Stamina, balanced Speed and Power
				applyPresetValues(800, 900, 600, 400, 600)
			}
			"Long" -> {
				// Long: Very high Stamina, moderate Speed
				applyPresetValues(700, 1000, 500, 500, 600)
			}
			"Balanced" -> {
				// Balanced: All stats equal
				applyPresetValues(600, 600, 600, 600, 600)
			}
			else -> {
				// Load custom preset
				val presetJson = sharedPreferences.getString("preset_$name", null)
				if (presetJson != null) {
					try {
						val preset = JSONObject(presetJson)
						applyPresetValues(
							preset.getInt("speed"),
							preset.getInt("stamina"),
							preset.getInt("power"),
							preset.getInt("guts"),
							preset.getInt("wit")
						)
					} catch (e: JSONException) {
						Log.e(logTag, "Failed to load preset: ${e.message}")
					}
				}
			}
		}
		
		MessageLog.messageLog.add("[PRESET] Applied preset: $name")
	}
	
	private fun applyPresetValues(speed: Int, stamina: Int, power: Int, guts: Int, wit: Int) {
		// Apply to sliders
		speedSlider.value = speed.toFloat()
		staminaSlider.value = stamina.toFloat()
		powerSlider.value = power.toFloat()
		gutsSlider.value = guts.toFloat()
		witSlider.value = wit.toFloat()
		
		// Update displays
		speedValueText.text = speed.toString()
		staminaValueText.text = stamina.toString()
		powerValueText.text = power.toString()
		gutsValueText.text = guts.toString()
		witValueText.text = wit.toString()
		
		// Save the new values
		saveStatTargets()
		
		// Update priority display
		updatePriorityDisplay()
		}
	
	
	private fun displayCurrentSettings() {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
		val settingsStatusTextView = homeFragmentView.findViewById<TextView>(R.id.settings_status)
		
		// Get other important settings
		val campaign = sharedPreferences.getString("campaign", "")
		val statPrioritization = sharedPreferences.getString("statPrioritization", "Speed|Stamina|Power|Guts|Wit")
		
		// Build status text
		val statusText = buildString {
			appendLine("Current Configuration:")
			appendLine("Campaign: $campaign")
			appendLine("Auto-calculated Priority: $statPrioritization")
		}
		
		settingsStatusTextView.text = statusText
		
		// Initialize data
		if (firstRun) {
			initializeData()
			firstRun = false
		}
	}
	
	private fun initializeData() {
		// Initialize the data on a separate thread
		Thread {
			Log.d(logTag, "Loading Character data...")
			CharacterData.characters
			Log.d(logTag, "Character data has been loaded.")
			
			Log.d(logTag, "Loading Support data...")
			SupportData.supports
			Log.d(logTag, "Support data has been loaded.")
			
			Log.d(logTag, "Loading Skill data...")
			SkillData.skills
			Log.d(logTag, "Skill data has been loaded.")
		}.start()
	}
	
	override fun onResume() {
		super.onResume()
		
		// Update the button's text depending on if the MediaProjection service is running.
		if (!firstBoot) {
			if (MediaProjectionService.isRunning) {
				startButton.text = getString(R.string.stop)
			} else {
				startButton.text = getString(R.string.start)
			}
		}
		
		// Setting this false here will ensure that stopping the MediaProjection Service outside of this application will update this button's text.
		firstBoot = false
		
		// Now update the Message Log inside the ScrollView with the latest logging messages from the bot.
		Log.d(logTag, "Now updating the Message Log TextView...")
		val messageLogTextView = homeFragmentView.findViewById<TextView>(R.id.message_log)
		messageLogTextView.text = ""

		// Get a thread-safe copy of the message log.
		val messageLog = MessageLog.getMessageLogCopy()
		messageLog.forEach { message ->
			messageLogTextView.append("\n$message")
		}
		
		// Set up the app updater to check for the latest update from GitHub.
		AppUpdater(myContext)
			.setUpdateFrom(UpdateFrom.XML)
			.setUpdateXML("https://raw.githubusercontent.com/steve1316/uma-android-automation/master/app/update.xml")
			.start()
	}

	
	/**
	 * Checks to see if the application is ready to start.
	 *
	 * @return True if the application has overlay permission and has enabled the Accessibility Service for it. Otherwise, return False.
	 */
	private fun startReadyCheck(): Boolean {
		return !(!checkForOverlayPermission() || !checkForAccessibilityPermission())
	}
	
	/**
	 * Starts the MediaProjection Service.
	 */
	private fun startProjection() {
		val mediaProjectionManager = context?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
		mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
	}
	
	/**
	 * Stops the MediaProjection Service.
	 */
	private fun stopProjection() {
		context?.startService(MediaProjectionService.getStopIntent(requireContext()))
	}
	
	/**
	 * Checks if the application has permission to draw overlays. If not, it will direct the user to enable it.
	 *
	 * Source is from https://github.com/Fate-Grand-Automata/FGA/blob/master/app/src/main/java/com/mathewsachin/fategrandautomata/ui/MainFragment.kt
	 *
	 * @return True if it has permission. False otherwise.
	 */
	private fun checkForOverlayPermission(): Boolean {
		if (!Settings.canDrawOverlays(requireContext())) {
			Log.d(logTag, "Application is missing overlay permission.")
			
			AlertDialog.Builder(requireContext()).apply {
				setTitle(R.string.overlay_disabled)
				setMessage(R.string.overlay_disabled_message)
				setPositiveButton(R.string.go_to_settings) { _, _ ->
					// Send the user to the Overlay Settings.
					val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${requireContext().packageName}".toUri())
					startActivity(intent)
				}
				setNegativeButton(android.R.string.cancel, null)
			}.show()
			
			return false
		}
		
		Log.d(logTag, "Application has permission to draw overlay.")
		return true
	}
	
	/**
	 * Checks if the Accessibility Service for this application is enabled. If not, it will direct the user to enable it.
	 *
	 * Source is from https://stackoverflow.com/questions/18094982/detect-if-my-accessibility-service-is-enabled/18095283#18095283
	 *
	 * @return True if it is enabled. False otherwise.
	 */
	private fun checkForAccessibilityPermission(): Boolean {
		val prefString = Settings.Secure.getString(myContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
		
		if (prefString != null && prefString.isNotEmpty()) {
			// Check the string of enabled accessibility services to see if this application's accessibility service is there.
			val enabled = prefString.contains(myContext.packageName.toString() + "/" + MyAccessibilityService::class.java.name)
			
			if (enabled) {
				Log.d(logTag, "This application's Accessibility Service is currently turned on.")
				return true
			}
		}
		
		Log.d(logTag, "This application's Accessibility Service is currently turned off.")
		
		AlertDialog.Builder(myContext).apply {
			setTitle(R.string.accessibility_disabled)
			setMessage(R.string.accessibility_disabled_message)
			setPositiveButton(R.string.go_to_settings) { _, _ ->
				Log.d(logTag, "Accessibility Service is not detected. Redirecting user to Accessibility Settings.")
				val accessibilitySettingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
				myContext.startActivity(accessibilitySettingsIntent)
			}
			setNegativeButton(android.R.string.cancel, null)
		}.show()
		
		return false
	}
}