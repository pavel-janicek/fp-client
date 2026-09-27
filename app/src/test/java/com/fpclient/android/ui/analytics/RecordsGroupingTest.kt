package com.fpclient.android.ui.analytics

import com.fpclient.android.data.dto.PersonalRecordDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Records are grouped under their activity type. A 10 km run and a 10 km hike are not
 * comparable, and the flat list made the screen read as one undifferentiated pile.
 */
class RecordsGroupingTest {

    private fun record(id: String, type: String?, value: Double = 10.0) = PersonalRecordDto(
        id = id,
        activityType = type,
        recordType = "DISTANCE",
        value = value,
        unit = "m",
    )

    @Test
    fun recordsAreGroupedUnderTheirActivityType() {
        val groups = recordsByActivityType(
            listOf(
                record("1", "RUN"),
                record("2", "HIKE", 500.0),
                record("3", "RUN", 21_097.0),
                record("4", "WALK", 5_000.0),
            ),
        )
        assertEquals(listOf("RUN", "HIKE", "WALK"), groups.map { it.type })
        assertEquals(2, groups[0].records.size)
        assertEquals(1, groups[1].records.size)
        assertEquals(1, groups[2].records.size)
    }

    @Test
    fun groupsFollowTheAppOrderRatherThanServerOrder() {
        // Server happens to send WALK first; the user should still meet RUN first.
        val groups = recordsByActivityType(
            listOf(record("1", "WALK"), record("2", "RIDE"), record("3", "RUN")),
        )
        assertEquals(listOf("RUN", "WALK", "RIDE"), groups.map { it.type })
    }

    @Test
    fun recordsKeepTheServersOrderWithinTheirGroup() {
        val groups = recordsByActivityType(
            listOf(
                record("newest", "RUN", 21_097.0),
                record("older", "RUN", 10_000.0),
            ),
        )
        assertEquals(listOf("newest", "older"), groups[0].records.map { it.id })
    }

    @Test
    fun aRecordWithNoTypeIsStillShown() {
        val groups = recordsByActivityType(listOf(record("1", null), record("2", "RUN")))
        assertEquals(listOf("RUN", "Other"), groups.map { it.type })
        assertEquals(1, groups[1].records.size)
    }

    @Test
    fun anEmptyListProducesNoGroups() {
        assertEquals(emptyList<RecordGroup>(), recordsByActivityType(emptyList()))
    }
}
