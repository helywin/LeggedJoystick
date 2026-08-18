package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertEquals
import org.junit.Test

class MappingFreshnessTest {
    @Test
    fun ageMs_usesNewestMonotonicObservationAndNeverReportsNegativeAge() {
        assertEquals(25L, MappingFreshness.ageMs(1_000L, 1_025L, 1_000L))
        assertEquals(0L, MappingFreshness.ageMs(1_000L, 1_005L, 1_010L))
    }
}
