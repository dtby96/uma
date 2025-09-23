package com.steve1316.uma_android_automation.bot

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Unit tests for Uma Musume Bot Improvements
 * Tests the new functionality without requiring Android environment
 */
class GameImprovementsTest {

    // Mock classes for testing
    data class MockDate(
        val year: Int,
        val phase: String,
        val month: Int,
        val turnNumber: Int
    )

    data class MockBarFillResult(
        val fillPercent: Double,
        val dominantColor: String
    )

    private lateinit var game: GameImprovementsMock

    @Before
    fun setup() {
        game = GameImprovementsMock()
    }

    /**
     * Mock implementation of Game improvements for testing
     */
    inner class GameImprovementsMock {
        // Support Card Friendship Tracking
        val supportFriendships = mutableMapOf<String, Int>()
        private val friendshipGainPerTraining = 4
        private val maxFriendship = 100

        // Bad Condition Management
        val badConditions = mutableSetOf<String>()

        // Skill Points
        var currentSkillPoints = 0

        // Current date
        var currentDate = MockDate(1, "Early", 6, 12)

        // Stat tracking
        val currentStatsMap = mutableMapOf(
            "Speed" to 400,
            "Stamina" to 300,
            "Power" to 350,
            "Guts" to 200,
            "Wit" to 500
        )

        val statTargets = intArrayOf(800, 600, 700, 400, 600)

        fun updateFriendshipLevels(friendshipBars: List<MockBarFillResult>) {
            friendshipBars.forEachIndexed { index, bar ->
                val supportId = "support_$index"
                val currentFriendship = supportFriendships.getOrDefault(supportId, bar.fillPercent.toInt())

                if (bar.fillPercent < 100) {
                    val newFriendship = minOf(currentFriendship + friendshipGainPerTraining, maxFriendship)
                    supportFriendships[supportId] = newFriendship
                }
            }
        }

        fun getYearBasedWeights(): Pair<Double, Double> {
            return when (currentDate.year) {
                1 -> Pair(0.55, 0.45)
                2 -> Pair(0.35, 0.65)
                3 -> Pair(0.20, 0.80)
                else -> Pair(0.35, 0.65)
            }
        }

        fun shouldWarnSkillPoints(): String? {
            return when {
                currentDate.year == 2 && currentDate.month >= 6 && currentSkillPoints < 200 ->
                    "WARNING: Only $currentSkillPoints SP by mid Year 2 (target: 200+)"
                currentDate.year == 3 && currentDate.month <= 3 && currentSkillPoints < 400 ->
                    "WARNING: Only $currentSkillPoints SP at Year 3 start (target: 400+)"
                currentDate.year == 3 && currentDate.month >= 10 && currentSkillPoints < 600 ->
                    "CRITICAL: Only $currentSkillPoints SP before URA Finals (target: 600+)"
                currentDate.year == 3 && currentDate.month >= 11 && currentSkillPoints >= 750 ->
                    "Excellent! $currentSkillPoints SP ready for URA Finals"
                else -> null
            }
        }

        fun getDeficitMultiplier(completionPercent: Int): Double {
            return when {
                completionPercent < 30 -> 2.0
                completionPercent < 50 -> 1.5
                completionPercent < 70 -> 1.2
                else -> 1.0
            }
        }

        fun shouldUseInfirmary(): Boolean {
            if (badConditions.isEmpty()) return false

            // Multiple bad conditions
            if (badConditions.size >= 2) return true

            // Critical conditions
            val criticalConditions = setOf("practice_poor", "lazy", "overweight")
            if (badConditions.any { it in criticalConditions }) return true

            // Year 3 with any condition
            if (currentDate.year == 3 && badConditions.isNotEmpty()) return true

            return false
        }
    }

    @Test
    fun testFriendshipTracking() {
        // Start with empty friendships
        assertTrue("Friendships should start empty", game.supportFriendships.isEmpty())

        // Simulate training with 3 support cards
        val bars = listOf(
            MockBarFillResult(60.0, "blue"),
            MockBarFillResult(75.0, "blue"),
            MockBarFillResult(90.0, "orange")
        )

        // Train 5 times
        repeat(5) {
            game.updateFriendshipLevels(bars)
        }

        // Check friendships increased
        assertEquals("Should have 3 support cards", 3, game.supportFriendships.size)
        assertTrue("Support 0 should have gained friendship",
            game.supportFriendships["support_0"]!! > 60)
        assertTrue("Support 1 should have gained friendship",
            game.supportFriendships["support_1"]!! > 75)
    }

    @Test
    fun testYearBasedWeights() {
        // Year 1 - Focus on friendships
        game.currentDate = MockDate(1, "Mid", 6, 12)
        val (friendship1, stats1) = game.getYearBasedWeights()
        assertEquals("Year 1 friendship weight", 0.55, friendship1, 0.01)
        assertEquals("Year 1 stat weight", 0.45, stats1, 0.01)

        // Year 2 - Balanced
        game.currentDate = MockDate(2, "Mid", 6, 36)
        val (friendship2, stats2) = game.getYearBasedWeights()
        assertEquals("Year 2 friendship weight", 0.35, friendship2, 0.01)
        assertEquals("Year 2 stat weight", 0.65, stats2, 0.01)

        // Year 3 - Focus on stats
        game.currentDate = MockDate(3, "Mid", 6, 60)
        val (friendship3, stats3) = game.getYearBasedWeights()
        assertEquals("Year 3 friendship weight", 0.20, friendship3, 0.01)
        assertEquals("Year 3 stat weight", 0.80, stats3, 0.01)
    }

    @Test
    fun testSkillPointWarnings() {
        // Test Year 2 Mid warning
        game.currentDate = MockDate(2, "Mid", 7, 38)
        game.currentSkillPoints = 150
        assertNotNull("Should warn at Year 2 Mid with low SP", game.shouldWarnSkillPoints())
        assertTrue("Should contain WARNING", game.shouldWarnSkillPoints()!!.contains("WARNING"))

        // Test Year 3 Start warning
        game.currentDate = MockDate(3, "Early", 2, 50)
        game.currentSkillPoints = 350
        assertNotNull("Should warn at Year 3 Start with low SP", game.shouldWarnSkillPoints())

        // Test Pre-URA critical warning
        game.currentDate = MockDate(3, "Late", 11, 70)
        game.currentSkillPoints = 550
        assertNotNull("Should critically warn before URA", game.shouldWarnSkillPoints())
        assertTrue("Should contain CRITICAL", game.shouldWarnSkillPoints()!!.contains("CRITICAL"))

        // Test good SP
        game.currentSkillPoints = 800
        assertNotNull("Should congratulate high SP", game.shouldWarnSkillPoints())
        assertTrue("Should contain Excellent", game.shouldWarnSkillPoints()!!.contains("Excellent"))
    }

    @Test
    fun testDeficitMultipliers() {
        // Test different completion percentages
        assertEquals("< 30% completion", 2.0, game.getDeficitMultiplier(25), 0.01)
        assertEquals("< 50% completion", 1.5, game.getDeficitMultiplier(45), 0.01)
        assertEquals("< 70% completion", 1.2, game.getDeficitMultiplier(65), 0.01)
        assertEquals(">= 70% completion", 1.0, game.getDeficitMultiplier(85), 0.01)
        assertEquals("100% completion", 1.0, game.getDeficitMultiplier(100), 0.01)
    }

    @Test
    fun testBadConditionManagement() {
        // No conditions - shouldn't use infirmary
        assertFalse("No conditions - no infirmary", game.shouldUseInfirmary())

        // Single minor condition - no infirmary
        game.badConditions.add("headache")
        assertFalse("Single minor condition - no infirmary", game.shouldUseInfirmary())

        // Critical condition - use infirmary
        game.badConditions.clear()
        game.badConditions.add("practice_poor")
        assertTrue("Critical condition - use infirmary", game.shouldUseInfirmary())

        // Multiple conditions - use infirmary
        game.badConditions.clear()
        game.badConditions.addAll(listOf("headache", "night_owl"))
        assertTrue("Multiple conditions - use infirmary", game.shouldUseInfirmary())

        // Year 3 with any condition - use infirmary
        game.badConditions.clear()
        game.badConditions.add("headache")
        game.currentDate = MockDate(3, "Early", 6, 54)
        assertTrue("Year 3 with condition - use infirmary", game.shouldUseInfirmary())
    }

    @Test
    fun testContextAwareScoring() {
        val stats = listOf("Speed", "Stamina", "Power", "Guts", "Wit")

        for (i in stats.indices) {
            val current = game.currentStatsMap[stats[i]]!!
            val target = game.statTargets[i]
            val completion = (current * 100 / target)
            val multiplier = game.getDeficitMultiplier(completion)

            // Verify multipliers are appropriate for completion levels
            // Note: 50% exactly falls into the 50-70% range (1.2x), not the < 50% range (1.5x)
            when (stats[i]) {
                "Speed" -> assertEquals("Speed at 50% should have 1.2x", 1.2, multiplier, 0.01)
                "Stamina" -> assertEquals("Stamina at 50% should have 1.2x", 1.2, multiplier, 0.01)
                "Power" -> assertEquals("Power at 50% should have 1.2x", 1.2, multiplier, 0.01)
                "Guts" -> assertEquals("Guts at 50% should have 1.2x", 1.2, multiplier, 0.01)
                "Wit" -> assertEquals("Wit at 83% should have 1.0x", 1.0, multiplier, 0.01)
            }
        }
    }

    @Test
    fun testIntegration() {
        // Test full integration of year weights + friendship + bad conditions

        // Year 1 scenario
        game.currentDate = MockDate(1, "Mid", 8, 16)
        game.badConditions.clear()

        val (f1, s1) = game.getYearBasedWeights()
        assertTrue("Year 1 should prioritize friendships", f1 > s1)

        // Simulate friendship building
        val bars = listOf(
            MockBarFillResult(50.0, "blue"),
            MockBarFillResult(60.0, "blue"),
            MockBarFillResult(70.0, "blue")
        )

        repeat(10) {
            game.updateFriendshipLevels(bars)
        }

        val avgFriendship = game.supportFriendships.values.average()
        assertTrue("Friendships should have increased", avgFriendship > 70)

        // Year 3 scenario with bad condition
        game.currentDate = MockDate(3, "Late", 10, 68)
        game.badConditions.add("practice_poor")
        game.currentSkillPoints = 500

        val (f3, s3) = game.getYearBasedWeights()
        assertTrue("Year 3 should prioritize stats", s3 > f3)
        assertTrue("Should use infirmary in Year 3", game.shouldUseInfirmary())
        assertNotNull("Should warn about low SP", game.shouldWarnSkillPoints())
    }
}