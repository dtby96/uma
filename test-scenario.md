# Algorithm Behavior Example

## Scenario: Near-Complete vs Far-From-Complete Stats

### Given:
- Stamina: 1100/1200 (92% complete, deficit = 100)
- Power: 500/800 (62% complete, deficit = 300)
- Training A: +30 Stamina
- Training B: +30 Power

### Algorithm Calculation:

#### Training A (Stamina):
- Current Progress: 1100/1200 = 0.917
- New Progress: 1130/1200 = 0.942
- Current Utility: -log(1.01 - 0.917) = -log(0.093) = 2.375
- New Utility: -log(1.01 - 0.942) = -log(0.068) = 2.688
- Marginal Utility: (2.688 - 2.375) * 100 = **31.3**

#### Training B (Power):
- Current Progress: 500/800 = 0.625
- New Progress: 530/800 = 0.663
- Current Utility: -log(1.01 - 0.625) = -log(0.385) = 0.954
- New Utility: -log(1.01 - 0.663) = -log(0.347) = 1.058
- Marginal Utility: (1.058 - 0.954) * 100 = **104.0**

### Result:
Training B (Power) wins decisively because:
- Even though Stamina is closer to completion, the logarithmic function gives diminishing returns
- Power at 62% complete has more "room to improve"
- The algorithm naturally shifts focus to less-complete stats

## Why This Works:

The logarithmic utility function `-log(1.01 - progress)`:
- Approaches infinity as progress → 100%
- But the MARGINAL gains (derivative) decrease
- This creates natural diminishing returns

### Visual Representation:
```
Progress:  0% → 50% → 75% → 90% → 95% → 99%
Utility:   Low but growing fast → Moderate growth → Slow growth → Very slow
```

When a stat is 90%+ complete, even small gains produce minimal utility increase, automatically shifting priority to less-complete stats.