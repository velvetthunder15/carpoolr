package com.carpoolr.app.pool

import kotlin.math.floor

/**
 * Splits one cab fare between pool riders by the distance each rides.
 *
 * Everyone boards at the shared pickup and the cab drops riders in order of distance.
 * The fare becomes a per-km rate (fare / total route km). Each stretch of road is then
 * split equally among the riders still in the cab on that stretch, so a rider who gets
 * off early never pays for road they didn't ride.
 *
 * Shares are whole rupees, and they always add up to exactly the fare (largest remainder).
 */
object FareSplit {

    data class Rider(val id: String, val dropKm: Double)

    fun split(totalFareRupees: Int, riders: List<Rider>): Map<String, Int> {
        require(totalFareRupees >= 0) { "fare can't be negative" }
        require(riders.isNotEmpty()) { "need at least one rider" }
        require(riders.all { it.dropKm > 0.0 }) { "every drop must be past the pickup" }
        require(riders.map { it.id }.toSet().size == riders.size) { "rider ids must be unique" }

        val sorted = riders.sortedBy { it.dropKm }
        val routeKm = sorted.last().dropKm
        val perKm = totalFareRupees / routeKm

        val exact = HashMap<String, Double>()
        var prev = 0.0
        sorted.forEachIndexed { i, rider ->
            val stretchKm = rider.dropKm - prev
            val onBoard = sorted.size - i
            val each = stretchKm * perKm / onBoard
            for (j in i until sorted.size) {
                val id = sorted[j].id
                exact[id] = (exact[id] ?: 0.0) + each
            }
            prev = rider.dropKm
        }

        val floors = exact.mapValues { floor(it.value).toInt() }.toMutableMap()
        var left = totalFareRupees - floors.values.sum()
        val byRemainder = exact.entries.sortedByDescending { it.value - floor(it.value) }
        var k = 0
        while (left > 0) {
            val id = byRemainder[k % byRemainder.size].key
            floors[id] = floors.getValue(id) + 1
            left--
            k++
        }
        return riders.associate { it.id to floors.getValue(it.id) }
    }

    /** A rider aboard from [onKm] to [offKm], measured along the cab's route. */
    data class Leg(val id: String, val onKm: Double, val offKm: Double)

    /**
     * General split for pools where riders board at different points on the way.
     * The route is cut at every boarding and drop point; each stretch's cost (per-km rate)
     * is shared equally by the riders aboard on it. Whole rupees, summing to the fare.
     */
    fun splitLegs(totalFareRupees: Int, legs: List<Leg>): Map<String, Int> {
        require(totalFareRupees >= 0) { "fare can't be negative" }
        require(legs.isNotEmpty()) { "need at least one rider" }
        require(legs.map { it.id }.toSet().size == legs.size) { "rider ids must be unique" }
        val clean = legs.map { Leg(it.id, maxOf(0.0, minOf(it.onKm, it.offKm)), maxOf(it.onKm, it.offKm)) }
        val cuts = clean.flatMap { listOf(it.onKm, it.offKm) }.distinct().sorted()
        val start = cuts.first(); val end = cuts.last()
        val routeKm = end - start
        val exact = HashMap<String, Double>().apply { clean.forEach { put(it.id, 0.0) } }
        if (routeKm <= 0.0) {
            clean.forEach { exact[it.id] = totalFareRupees.toDouble() / clean.size }
        } else {
            val perKm = totalFareRupees / routeKm
            for (k in 0 until cuts.size - 1) {
                val a = cuts[k]; val b = cuts[k + 1]
                val aboard = clean.filter { it.onKm <= a + 1e-9 && it.offKm >= b - 1e-9 }
                // a gap nobody rides (shouldn't happen on a matched pool): spread over everyone
                val payers = aboard.ifEmpty { clean }
                val each = (b - a) * perKm / payers.size
                payers.forEach { exact[it.id] = exact.getValue(it.id) + each }
            }
        }
        val floors = exact.mapValues { floor(it.value).toInt() }.toMutableMap()
        var left = totalFareRupees - floors.values.sum()
        val byRemainder = exact.entries.sortedByDescending { it.value - floor(it.value) }
        var k = 0
        while (left > 0) {
            val id = byRemainder[k % byRemainder.size].key
            floors[id] = floors.getValue(id) + 1
            left--; k++
        }
        return legs.associate { it.id to floors.getValue(it.id) }
    }
}
