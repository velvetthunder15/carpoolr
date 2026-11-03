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
}
