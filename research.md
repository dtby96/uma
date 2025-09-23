# Uma Musume Game Mechanics Research (Global Server)

## Training System

### Energy Costs (Global Server - Current)
```
Speed:   -21 energy, +10 Speed, +5 Power, +2 SP
Stamina: -19 energy, +9 Stamina, +4 Guts, +2 SP
Power:   -20 energy, +5 Stamina, +8 Power, +2 SP
Guts:    -22 energy, +4 Speed, +4 Power, +8 Guts, +2 SP
Wisdom:  +5 energy, +2 Speed, +9 Wisdom, +4 SP
```

### Energy Management
- Starting energy: 100
- Failure Formula: `Failure% = max(0, (50 - Energy) * 0.5)`
- Energy < 50: Failure chance starts increasing
- Energy < 30: High risk territory (15%+ failure)
- Rest Recovery: +30 energy (normal), +40 energy (training camps)
- Summer Camp: Late June + July (Lv5 facilities, +40 energy rest)
- Winter Camp: Late December + January (Lv5 facilities, +40 energy rest)

### Training Facility Levels
- Start at Level 1
- Level up every 4 trainings of same type
- Maximum Level 5
- Level Multipliers (approximate):
  - Lv1: 1.0x (base stats)
  - Lv2: 1.15x (+15%)
  - Lv3: 1.35x (+35%)
  - Lv4: 1.55x (+55%)
  - Lv5: 1.75x (+75%)

### Mood System Effects
- Great (絶好調): +20% to all stat gains
- Good (好調): +10% to all stat gains
- Normal (普通): No modifier
- Bad (不調): -10% to all stat gains
- Awful (絶不調): -20% to all stat gains

### Stat Caps (Global Server)
- All stats capped at 1200
- Stats beyond 1200 provide 0 gains
- Will be increased to 1400 in future update

## Training Strategy by Year

### Junior Year (Year 1)
**Focus: Relationship Building (55% weight)**
- Prioritize trainings with multiple support cards
- Target 80% friendship gauge by year end
- Blue bars (non-maxed friendships) are highest priority
- Build foundation stats evenly

### Classic Year (Year 2)
**Focus: Balanced Development (50/50)**
- Complete remaining friendships
- Focus on 2-3 main stats
- Strategic racing for fan accumulation
- Target 600+ in primary stats

### Senior Year (Year 3)
**Focus: Stat Maximization (70% weight)**
- Push Speed to maximum
- Complete secondary stats to targets
- Acquire final skills with skill points
- Prepare for URA Finals

## Support Card Mechanics

### Friendship System
- 0-100% friendship gauge
- 80% = Orange bar (good bonuses)
- 100% = Rainbow/maxed (best bonuses)
- Blue bars = Non-maxed friendships (prioritize in Year 1)

### Training Together
- More support cards in training = better gains
- Rainbow training: 3+ support cards present
- Friendship training: Support card appears with character icon

## Event Decision Making

### Priority Hierarchy
1. **Energy** (if below 40)
2. **Skill points** (if below 30)
3. **Mood improvement** (if Bad/Awful)
4. **Primary stat gains** (based on deficit)
5. **Secondary stat gains**
6. **Relationship points**

### Event Scoring Weights
- Energy: 3-5x multiplier based on current energy
- Mood change: ±50 points
- Skill points: 1:1 value
- Stats: Based on deficit from target
- Random rewards: -10 penalty
- Event chain end: -50 penalty

## Recreation Options

### Types Available
- **Rest**: +30/40 energy, risk of bad condition
- **Infirmary**: +20 energy, cure one bad condition (when available)
- **Summer Training**: No energy loss, all trainings Lv5
- **Date**: Mood recovery, relationship boost
- **Shrine/Karaoke/Walk**: Small energy + mood benefits

## URA Finals Mechanics

### Fan Requirements for Skill Level-ups
- 60,000 fans by Early February Year 3
- 70,000 fans by Early April Year 3
- 120,000 fans by Late December Year 3

### URA Finals Schedule
- Qualifiers every 6 months in Year 2-3
- Must win to advance to semi-finals
- Grand Finals at end of Year 3

## Stat Targets by Distance

### Sprint (短距離)
- Speed: 1200 (Critical)
- Stamina: 300-400
- Power: 800-900
- Guts: 300-400
- Wisdom: 600+

### Mile (マイル)
- Speed: 1000-1100
- Stamina: 400-500
- Power: 700-800
- Guts: 400-500
- Wisdom: 600+

### Medium (中距離)
- Speed: 900-1000
- Stamina: 600-700
- Power: 600-700
- Guts: 400-500
- Wisdom: 600+

### Long (長距離)
- Speed: 700-800
- Stamina: 900-1000
- Power: 500-600
- Guts: 500-600
- Wisdom: 600+

## Key Formulas

### Risk-Reward Ratio
```
RiskReward = (TotalStatGain * (1 + Friends * 0.2)) / (FailureChance + 1)
```

### Training Value Score
```
Score = BaseStatValue * LevelMultiplier * MoodMultiplier * DeficitMultiplier
```

### Deficit Multiplier
- Deficit > 300: 3.0x (Critical priority)
- Deficit 200-300: 2.5x (High priority)
- Deficit 100-200: 2.0x (Moderate priority)
- Deficit 50-100: 1.5x (Low priority)
- Deficit < 50: 1.1x (Maintenance)
- Surplus: 0.5x (Diminishing returns)

## Training Camp Optimization

### Pre-Camp Strategy (3 turns before)
- 3 turns before: Continue if ANY training <15% failure
- 2 turns before: Rest if ALL trainings >10% failure
- 1 turn before: Rest if ALL trainings >10% failure

### During Camp
- Only train if <20% failure rate
- Prioritize high-value trainings (3+ friends, rainbow)
- Rest provides +40 energy instead of +30

## Common Pitfalls to Avoid
1. Training with >22% failure chance
2. Ignoring relationship building in Year 1
3. Recovering mood on turn 1 (random chance)
4. Over-training single stats (diminishing returns)
5. Poor energy management before training camps
6. Insufficient skill points for URA Finals
7. Not tracking training levels (4 uses per level)

## Bot-Controllable Elements

### What Bot CAN Control
✅ Training selection and timing
✅ Rest vs training decisions
✅ Event choice selection
✅ Energy management
✅ Mood recovery timing
✅ Support card friendship prioritization
✅ Stat targeting and balancing
✅ Pre-camp preparation
✅ Skill point accumulation tracking

### What Bot CANNOT Control (User Decisions)
❌ Race selection (user chooses which races)
❌ Skill purchasing (user spends skill points)
❌ Inheritance/parent selection
❌ Support card deck selection
❌ Character selection
❌ Aptitude improvements
❌ Happy Meek duel choices
❌ Starting the races (bot can only skip/run them)

## Data Sources
- GameTora: https://gametora.com/umamusume
- Game8: https://game8.co/games/Umamusume-Pretty-Derby
- Community testing and documentation