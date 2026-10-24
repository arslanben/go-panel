package dev.opencodegometer

import dev.opencodegometer.data.GoStatusParser
import dev.opencodegometer.data.MeterKind
import dev.opencodegometer.data.UsageMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoStatusParserTest {

    private val sample = """
        {
          "subscriberUserId": "acc_test",
          "product": "go",
          "cancelAtPeriodEnd": true,
          "access": {
            "startsAt": "2026-10-07T10:05:30.000Z",
            "endsAt": "2026-11-07T10:05:30.000Z",
            "meters": {
              "fiveHour": {
                "startsAt": "2026-10-24T20:02:06.000Z",
                "resetsAt": "2026-10-25T01:02:06.000Z",
                "limitMicroCents": "1200000000",
                "usedMicroCents": "12000000"
              },
              "week": {
                "startsAt": "2026-10-19T00:00:00.000Z",
                "resetsAt": "2026-10-26T00:00:00.000Z",
                "limitMicroCents": "3000000000",
                "usedMicroCents": "1200000000"
              },
              "month": {
                "resetsAt": "2026-11-07T10:05:30.000Z",
                "limitMicroCents": "6000000000",
                "usedMicroCents": "1920000000"
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesLiveShape() {
        val data = GoStatusParser.parse(sample)

        assertNotNull(data)
        data!!
        assertEquals("go", data.product)
        assertEquals(1, data.fiveHour.usedRounded)
        assertEquals(40, data.week.usedRounded)
        assertEquals(32, data.month.usedRounded)
        assertEquals(99, data.fiveHour.remainingRounded)
        assertEquals(60, data.week.remainingRounded)
        assertEquals(68, data.month.remainingRounded)
        assertEquals(MeterKind.WEEK, data.mostUsed().first)
        assertEquals(true, data.cancelAtPeriodEnd)
    }

    @Test
    fun returnsNullWithoutAccess() {
        assertNull(GoStatusParser.parse("""{"product":"go"}"""))
    }

    @Test
    fun returnsNullForInvalidJson() {
        assertNull(GoStatusParser.parse("not json"))
    }

    @Test
    fun zeroLimitIsTreatedAsUnlimited() {
        val json = sample.replace(
            "\"limitMicroCents\": \"1200000000\"",
            "\"limitMicroCents\": \"0\""
        )
        val data = GoStatusParser.parse(json)

        assertNotNull(data)
        assertEquals(0, data!!.fiveHour.usedPercent.toInt())
        assertEquals(0, data.fiveHour.usedRounded)
        assertEquals(100, data.fiveHour.remainingRounded)
    }

    @Test
    fun overuseClampsToZeroRemaining() {
        val meter = UsageMeter(
            usedMicroCents = 7_000_000_000L,
            limitMicroCents = 6_000_000_000L,
            startsAt = null,
            resetsAt = null
        )

        assertEquals(0, meter.remainingRounded)
    }
}
