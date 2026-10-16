package com.pczumbel.navigator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseCoordinatesTest {
    private fun check(text: String, lat: Double, lon: Double) {
        val r = parseCoordinates(text) ?: error("failed to parse '$text'")
        assertEquals(lat, r.first, 1e-4)
        assertEquals(lon, r.second, 1e-4)
    }

    @Test fun decimalWithComma() = check("47.5329, 19.0161", 47.5329, 19.0161)
    @Test fun decimalWithSpace() = check("47.5329 19.0161", 47.5329, 19.0161)
    @Test fun hemisphereLetters() = check("N47.5329 E19.0161", 47.5329, 19.0161)
    @Test fun dms() = check("47°31'58.4\"N 19°00'58.1\"E", 47.53289, 19.01614)
    @Test fun southWestFlips() = check("33.9 S, 18.4 W", -33.9, -18.4)
    @Test fun sInsideNameDoesNotFlip() = check("47.5329, 19.0161 Kiscelli SZ", 47.5329, 19.0161)
    @Test fun garbage() = assertNull(parseCoordinates("hello"))
    @Test fun outOfRange() = assertNull(parseCoordinates("147.5, 19.0"))
}
