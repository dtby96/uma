# Testing the Uma Musume Bot Improvements

Since we can't easily run unit tests without setting up the full testing framework, here are several ways to test the improvements before installing the APK:

## 1. Enable Debug Logging (Recommended)

The bot already has extensive logging. To test the improvements:

### Step 1: Enable Verbose Logging
In the app settings, enable:
- **Debug Mode** (if available)
- **Enable Logs**
- **Verbose Logging**

### Step 2: Monitor Logs in Real-Time
Connect your device and use Android Studio's Logcat:
```bash
adb logcat | grep -E "\[FRIENDSHIP\]|\[SKILL POINTS\]|\[BAD CONDITION\]|\[TRAINING\]|\[RECOVERY\]|\[INFIRMARY\]"
```

Or save logs to a file:
```bash
adb logcat -d > test_logs.txt
```

## 2. What to Look For in Logs

### A. Friendship Tracking
Look for these log messages:
```
[FRIENDSHIP] Support #0 reached orange level (82%)
[FRIENDSHIP] Support #2 reached rainbow/max level (100%)
[FRIENDSHIP] Average friendship: 76%
[FRIENDSHIP] Max friendships: 2/6
```

### B. Skill Point Warnings
Watch for:
```
[SKILL POINTS] Current: 180
[SKILL POINTS] ⚠️ WARNING: Only 180 SP by mid Year 2 (target: 200+)
[SKILL POINTS] TIP: Choose +SP options in events when available
[SKILL POINTS] ✅ Excellent! 850 SP ready for URA Finals
```

### C. Year-Based Strategy Changes
Monitor training decisions:
```
[TRAINING] Year 1: Prioritizing friendships (55% weight)
[TRAINING] Year 2: Balanced approach (35% friendship, 65% stats)
[TRAINING] Year 3: Stat maximization (20% friendship, 80% stats)
```

### D. Bad Condition Detection
Check for:
```
[BAD CONDITION] Detected: Practice Poor (練習下手)
[BAD CONDITION] Total conditions: 2
[BAD CONDITION] Infirmary available - recommend using it
[INFIRMARY] Critical condition detected - should use infirmary
[INFO] Using infirmary to cure bad conditions
```

### E. Context-Aware Event Scoring
Look for enhanced scoring logs:
```
[TRAINING-EVENT] Speed: 400/800 (50%) - Multiplier: 1.5x
[TRAINING-EVENT] Energy context: Low energy (35) - prioritizing +Energy
[TRAINING-EVENT] Skill point context: Low SP (45) - bonus weight applied
```

## 3. Test Scenarios to Try

### Scenario 1: Early Game (Year 1)
1. Start a new training session
2. Watch logs for friendship prioritization
3. Verify bot chooses trainings with blue bars
4. Check friendship progress updates every 5 trainings

### Scenario 2: Mid Game (Year 2)
1. Continue to Year 2
2. Monitor skill point warnings if below 200 SP
3. Observe balanced training selection
4. Check event choices prioritize needed resources

### Scenario 3: Late Game (Year 3)
1. Progress to Year 3
2. Verify stat-focused training selection
3. Watch for pre-URA skill point warnings
4. Test bad condition detection and infirmary use

### Scenario 4: Bad Conditions
1. If a bad condition appears, check detection logs
2. Verify infirmary is prioritized when available
3. Check if Practice Poor adds +5% to failure calculations

## 4. Quick Validation Checklist

Before full testing:
- [x] APK builds successfully ✅
- [ ] Bot starts without crashes
- [ ] Training selection works
- [ ] Logs show new features active
- [ ] No infinite loops or stuck states

During first run:
- [ ] Friendship tracking messages appear
- [ ] Skill point checks occur every 5 turns
- [ ] Year-based weights change at year transitions
- [ ] Bad conditions are detected when present
- [ ] Event scoring shows context awareness

## 5. Safe Testing Mode

To minimize risk:

1. **Test on Secondary Account**: Use a test account first
2. **Manual Override Ready**: Keep manual control accessible
3. **Short Test Runs**: Do 10-15 turns initially
4. **Monitor Closely**: Watch for unusual behavior
5. **Save Logs**: Keep logs for debugging if issues occur

## 6. Expected Behavior Changes

Compared to the old version, you should see:

### Year 1 Changes:
- More blue bar trainings selected
- Less focus on high stat trainings
- Friendship milestones logged regularly

### Year 2 Changes:
- Balanced training selection
- Skill point warnings if behind schedule
- Mixed friendship and stat priorities

### Year 3 Changes:
- Heavy stat focus
- Critical skill point warnings if < 600
- Immediate infirmary use for any bad condition
- Maximum stat gain prioritization

## 7. Performance Validation

The improvements should NOT cause:
- Increased battery drain
- Slower processing
- Memory leaks
- App crashes

Monitor device performance during testing.

## 8. Rollback Plan

If issues occur:
1. Stop the bot immediately
2. Save the logs for debugging
3. Reinstall previous APK version
4. Report issues with log excerpts

## 9. Success Indicators

You'll know the improvements work when:
- Average friendship reaches 80%+ by end of Year 1
- Skill points stay above expected thresholds
- Bad conditions are promptly addressed
- Training selection adapts by year
- No training loops or stuck states

## 10. Debug Commands

If the bot gets stuck, try these in the app:
- Force refresh state
- Clear training cache
- Reset decision memory
- Reload stat targets

## Summary

The safest way to test is:
1. **Build the APK** ✅ (Already successful)
2. **Install on test device/emulator**
3. **Enable verbose logging**
4. **Run for 10-15 turns**
5. **Check logs for new features**
6. **Gradually increase test duration**

The extensive logging added means you can verify all improvements are working without risking a full training run.