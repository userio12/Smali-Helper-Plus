package com.riyadm.smalihelperplus.data.library

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryGroupingTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun view(id: String, at: Long) = RecentViewEntity("opcode", id, id, "", at)

    @Test
    fun groupsByCalendarDayNotByHours() {
        val now = millis(2026, 3, 10, 0, 30)
        val groups = HistoryGrouping.group(
            listOf(
                view("old", millis(2026, 3, 1, 12)),
                view("lateYesterday", millis(2026, 3, 9, 23, 55)), // 35 minutes ago but yesterday
                view("today", millis(2026, 3, 10, 0, 10)),
                view("yesterdayMorning", millis(2026, 3, 9, 8)),
            ),
            now,
            zone,
        )
        assertThat(groups.map { it.bucket }).containsExactly(HistoryBucket.TODAY, HistoryBucket.YESTERDAY, HistoryBucket.EARLIER).inOrder()
        assertThat(groups[0].items.map { it.targetId }).containsExactly("today")
        assertThat(groups[1].items.map { it.targetId }).containsExactly("lateYesterday", "yesterdayMorning").inOrder()
        assertThat(groups[2].items.map { it.targetId }).containsExactly("old")
    }

    @Test
    fun omitsEmptyGroupsAndHandlesEmptyInput() {
        assertThat(HistoryGrouping.group(emptyList(), 0, ZoneOffset.UTC)).isEmpty()
        val now = millis(2026, 3, 10, 12)
        val groups = HistoryGrouping.group(listOf(view("a", millis(2026, 3, 10, 9))), now, zone)
        assertThat(groups.map { it.bucket }).containsExactly(HistoryBucket.TODAY)
    }

    @Test
    fun futureTimestampsCountAsToday() {
        val now = millis(2026, 3, 10, 12)
        val groups = HistoryGrouping.group(listOf(view("future", millis(2026, 3, 11, 1))), now, zone)
        assertThat(groups.single().bucket).isEqualTo(HistoryBucket.TODAY)
    }
}
