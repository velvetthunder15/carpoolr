package com.carpoolr.app.pool

import com.carpoolr.app.pool.FareSplit.Rider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FareSplitTest {

    @Test
    fun sameDestinationSplitsEqually() {
        val s = FareSplit.split(300, listOf(Rider("a", 10.0), Rider("b", 10.0), Rider("c", 10.0)))
        assertEquals(mapOf("a" to 100, "b" to 100, "c" to 100), s)
    }

    @Test
    fun earlyDropPaysOnlyForSharedStretch() {
        // 20 km for ₹400 = ₹20/km. The first 10 km is shared by two riders (₹100 each).
        // The last 10 km is b's alone (₹200).
        val s = FareSplit.split(400, listOf(Rider("a", 10.0), Rider("b", 20.0)))
        assertEquals(100, s["a"])
        assertEquals(300, s["b"])
    }

    @Test
    fun sharesAlwaysAddUpToFare() {
        val s = FareSplit.split(257, listOf(Rider("a", 3.3), Rider("b", 7.1), Rider("c", 7.1), Rider("d", 12.9)))
        assertEquals(257, s.values.sum())
        assertTrue(s.getValue("a") < s.getValue("b"))
        assertEquals(s.getValue("b").toDouble(), s.getValue("c").toDouble(), 1.0)
        assertTrue(s.getValue("c") < s.getValue("d"))
    }

    @Test
    fun soloRiderPaysEverything() {
        assertEquals(mapOf("a" to 199), FareSplit.split(199, listOf(Rider("a", 4.2))))
    }

    @Test
    fun legsSameStartMatchesSplit() {
        // ₹300, A 0→10 km, B 0→4 km: first 4 km shared (₹60 each), last 6 km A alone
        assertEquals(mapOf("a" to 240, "b" to 60),
            FareSplit.splitLegs(300, listOf(FareSplit.Leg("a", 0.0, 10.0), FareSplit.Leg("b", 0.0, 4.0))))
    }

    @Test
    fun legsBoardingMidway() {
        // ₹300 over 10 km (₹30/km). B boards at 4, gets off at 10: 0-4 A alone ₹120, 4-10 shared ₹90 each
        assertEquals(mapOf("a" to 210, "b" to 90),
            FareSplit.splitLegs(300, listOf(FareSplit.Leg("a", 0.0, 10.0), FareSplit.Leg("b", 4.0, 10.0))))
    }

    @Test
    fun legsAlwaysSumToFare() {
        val r = FareSplit.splitLegs(257, listOf(
            FareSplit.Leg("a", 0.0, 9.3), FareSplit.Leg("b", 1.2, 5.5), FareSplit.Leg("c", 2.0, 9.3)))
        assertEquals(257, r.values.sum())
    }
}
