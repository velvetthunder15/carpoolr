package com.carpoolr.app.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FareParserTest {
    @Test
    fun readsJsonProducts() {
        val body = """{"data":{"products":{"tiers":[{"products":[
            {"displayName":"Uber Go","fare":"₹212.45","etaString":"4 min"},
            {"displayName":"Auto","fareAmountE5":0,"fareString":"₹118"}]}]}}}"""
        val r = FareParser.fromJson(body)
        assertTrue(r.contains(FareOption("Uber Go", 212)))
        assertTrue(r.contains(FareOption("Auto", 118)))
    }

    @Test
    fun readsNestedFare() {
        val r = FareParser.fromJson("""{"categories":[{"name":"Mini","fare":{"display":"₹189"}}]}""")
        assertEquals(listOf(FareOption("Mini", 189)), r)
    }

    @Test
    fun readsPageText() {
        val text = "Choose a ride\nUber Go\n4 min away\n₹212.45\nPremier\n₹305\nPayment"
        val r = FareParser.fromText(text)
        assertEquals(listOf(FareOption("Uber Go", 212), FareOption("Premier", 305)), r)
    }
}
