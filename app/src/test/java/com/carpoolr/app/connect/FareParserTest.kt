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

    @Test
    fun skipsTaglinesPromosAndStrikethroughFares() {
        val text = """
            Choose a ride
            Uber Go
            4
            2 mins away · 10:42 am
            Affordable, compact rides
            10% off
            ₹212.45
            ₹236.00
            Premier
            3 mins away
            Comfortable sedans, top-quality drivers
            ₹305
            Payment
        """.trimIndent()
        val r = FareParser.fromText(text)
        assertEquals(listOf(FareOption("Uber Go", 212), FareOption("Premier", 305)), r)
    }

    @Test
    fun readsNumericAndE5JsonFares() {
        val body = """{"options":[
            {"name":"Cab Economy","fare":245.4},
            {"displayName":"Go Sedan","fareAmountE5":27800000},
            {"serviceName":"Bike","fare":{"payableAmount":68}}
        ]}"""
        val r = FareParser.fromJson(body)
        assertEquals(
            listOf(
                FareOption("Cab Economy", 245),
                FareOption("Go Sedan", 278),
                FareOption("Bike", 68)
            ),
            r
        )
    }

    @Test
    fun ignoresChromeHeadingsInJsonAndText() {
        val json = """{"header":{"title":"Choose a ride","fare":"₹100"},"item":{"displayName":"UberXL","fare":"₹410"}}"""
        assertEquals(listOf(FareOption("UberXL", 410)), FareParser.fromJson(json))
        val text = "Payment\n₹200\nAuto - ₹118"
        assertEquals(listOf(FareOption("Auto", 118)), FareParser.fromText(text))
    }
}
