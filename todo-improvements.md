# Uma Musume Bot Implementation Tracking

## Current Implementation Status

### ✅ Already Implemented
- [x] Basic training selection
- [x] Energy cost system (corrected values)
- [x] Training level tracking (Lv1-5)
- [x] Mood detection
- [x] Rest vs training decisions
- [x] Event text detection and choice
- [x] Pre-camp preparation logic
- [x] Wit evaluation without energy bonus
- [x] Thompson Sampling for exploration
- [x] Dynamic programming for future value
- [x] Risk-reward ratio calculations
- [x] Character event data loading

### ⚠️ Partially Implemented
- [ ] Support card friendship tracking (detects but doesn't track %)
- [ ] Year-based strategy (exists but could be improved)
- [ ] Stat deficit calculations (basic version exists)
- [ ] Fan milestone awareness (checks but doesn't plan)
- [ ] Event scoring (basic weights, needs context awareness)

### ❌ Not Implemented
- [ ] Friendship gauge percentage tracking (0-100%)
- [ ] Support card bonus calculations
- [ ] Skill point optimization suggestions
- [ ] Recreation timing optimization
- [ ] Training history pattern analysis
- [ ] Stat target auto-adjustment based on distance
- [ ] Event chain tracking
- [ ] Bad condition management

## Priority Improvements (Bot-Controllable Only)

### Phase 1: Core Training Improvements 🔴 HIGH PRIORITY

#### 1. Support Card Friendship Tracking
**Files**: `Game.kt`, `ImageUtils.kt`
**Implementation**:
```kotlin
// Track friendship percentages for each support card
private val supportFriendships: MutableMap<String, Int> = mutableMapOf()

// Update friendship after each training
fun updateFriendshipGauge(supportName: String, fillPercent: Int) {
    supportFriendships[supportName] = fillPercent
}
```
**Benefits**:
- Better Year 1 friendship prioritization
- Rainbow training detection
- Optimal support utilization

#### 2. Enhanced Event Scoring System
**Files**: `Game.kt` (handleTrainingEvent function)
**Current Issues**:
- Fixed weights don't adapt to game state
- No consideration of current deficits
- Energy context not fully utilized

**Improvements Needed**:
```kotlin
// Context-aware event scoring
fun scoreEventOption(option: EventOption, gameState: GameState): Double {
    var score = 0.0

    // Energy context
    if (gameState.energy < 40 && option.hasEnergy) {
        score += option.energyGain * 5.0
    }

    // Skill point context
    if (gameState.skillPoints < 50 && option.hasSkillPoints) {
        score += option.skillPoints * 2.0
    }

    // Stat deficit context
    option.stats.forEach { stat ->
        val deficit = getStatDeficit(stat.name)
        score += stat.value * getDeficitMultiplier(deficit)
    }

    return score
}
```

#### 3. Skill Point Tracking & Suggestions
**Files**: `Game.kt`, `ImageUtils.kt`
**Implementation**:
- Track current skill points via OCR
- Warn when approaching URA Finals with low SP
- Suggest when to prioritize SP gain in events

### Phase 2: Strategic Enhancements 🟡 MEDIUM PRIORITY

#### 4. Year-Based Training Weights
**Files**: `Game.kt` (calculateTrainingScore)
**Improvements**:
```kotlin
private fun getYearBasedWeights(): TrainingWeights {
    return when (currentDate.year) {
        1 -> TrainingWeights(
            friendship = 0.55,  // 55% weight on friendships
            stats = 0.45        // 45% weight on stats
        )
        2 -> TrainingWeights(
            friendship = 0.35,  // 35% weight on friendships
            stats = 0.65        // 65% weight on stats
        )
        3 -> TrainingWeights(
            friendship = 0.20,  // 20% weight on friendships
            stats = 0.80        // 80% weight on stats
        )
    }
}
```

#### 5. Bad Condition Management
**Files**: `Game.kt`
**Add detection for**:
- Overweight (太り気味)
- Night Owl (夜ふかし)
- Practice Poor (練習下手)
- Slow Metabolism
**Strategy**: Prioritize infirmary when available

#### 6. Recreation Optimization
**Files**: `Game.kt`
**Logic**:
- Track mood history
- Calculate mood recovery value
- Compare rest vs date vs other options
- Consider upcoming important turns

### Phase 3: Advanced Optimization 🟢 LOW PRIORITY

#### 7. Training Pattern Analysis
**Files**: `Game.kt`
- Track sequences that lead to good outcomes
- Identify and avoid failure patterns
- Learn from successful runs

#### 8. Dynamic Stat Target Adjustment
**Files**: `Game.kt`
- Detect character's distance specialization
- Auto-adjust stat targets based on race requirements
- Warn if targets seem misaligned

## File Structure Overview

### Core Bot Files
```
/bot/
├── Game.kt (3600+ lines) - Main bot logic
│   ├── Training selection
│   ├── Energy management
│   ├── Event handling
│   └── State tracking
├── Campaign.kt - Campaign flow control
├── TextDetection.kt - OCR and event matching
└── campaigns/AoHaru.kt - Specific campaign logic
```

### Data Management
```
/data/
├── CharacterData.kt - Character events storage
├── SkillData.kt - Skill information
├── SupportData.kt - Support card data
└── TrainingPresets.kt - Stat target configs
```

### Utilities
```
/utils/
├── ImageUtils.kt - Image recognition & OCR
├── SettingsPrinter.kt - Settings display
└── MessageLog.kt - Logging system
```

### Assets
```
/assets/
├── data/
│   ├── characters.json - Event choices
│   ├── supports.json - Support events
│   ├── skills.json - Skill data
│   └── main.py - GameTora scraper
└── images/ - Template matching images
```

## Testing Checklist

### Basic Functionality
- [ ] Energy costs calculate correctly
- [ ] Training levels increment properly
- [ ] Mood detection works
- [ ] Event choices are made
- [ ] Rest decisions are logical

### Advanced Features
- [ ] Pre-camp rest triggers appropriately
- [ ] Thompson Sampling explores options
- [ ] Risk-reward calculations are accurate
- [ ] Wit training evaluated without energy bonus
- [ ] Strategic spamming works when needed

### Edge Cases
- [ ] Bot handles 0 energy situations
- [ ] Bot handles all bad mood states
- [ ] Bot handles no valid training options
- [ ] Bot stops at URA Finals for skill spending
- [ ] Bot handles training camp transitions

## Implementation Order

1. **Week 1**: Support card friendship tracking
2. **Week 2**: Enhanced event scoring
3. **Week 3**: Skill point tracking
4. **Week 4**: Year-based weights
5. **Week 5**: Bad condition management
6. **Week 6**: Recreation optimization
7. **Week 7**: Testing and refinement
8. **Week 8**: Pattern analysis (optional)

## Success Metrics

### Training Efficiency
- Average stat gains per turn
- Friendship completion by year end
- Training failure rate < 5%
- Energy never drops below 20

### Strategic Success
- Year 1: 80%+ friendships achieved
- Year 2: Primary stats > 600
- Year 3: Speed maximized
- URA Finals: 750+ skill points

### Bot Reliability
- No infinite loops
- No stuck states
- Handles all game screens
- Clear logging of decisions

## Notes

### What We DON'T Need to Implement
- Race selection logic (user chooses)
- Skill purchasing (user spends points)
- Inheritance system (user selects)
- Happy Meek duels (not on Global yet)
- Aptitude improvements (user controlled)

### Focus Areas
The bot's main job is to:
1. Make optimal training choices
2. Manage energy efficiently
3. Choose best event options
4. Build friendships strategically
5. Prepare for training camps
6. Track progress toward goals

## Resources

### Documentation
- research.md - Game mechanics reference
- README.md - Project overview
- umamusume-training-guide.md - Strategy guide

### External References
- GameTora: https://gametora.com/umamusume
- Game8: https://game8.co/games/Umamusume-Pretty-Derby
- GitHub: https://github.com/JordanRO2/uma-android-automation