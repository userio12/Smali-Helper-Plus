package com.riyadm.smalihelperplus.data.library

import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import java.time.Instant
import java.time.ZoneId

enum class HistoryBucket(val title: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    EARLIER("Earlier"),
}

data class HistoryGroup(val bucket: HistoryBucket, val items: List<RecentViewEntity>)

/** Groups recently viewed items by calendar day relative to "now", newest first, omitting empty groups. */
object HistoryGrouping {
    fun group(items: List<RecentViewEntity>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<HistoryGroup> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
        val sorted = items.sortedByDescending { it.viewedAtMillis }
        val buckets = sorted.groupBy { item ->
            val day = Instant.ofEpochMilli(item.viewedAtMillis).atZone(zone).toLocalDate().toEpochDay()
            when {
                day >= today -> HistoryBucket.TODAY
                day == today - 1 -> HistoryBucket.YESTERDAY
                else -> HistoryBucket.EARLIER
            }
        }
        return HistoryBucket.entries.mapNotNull { bucket -> buckets[bucket]?.let { HistoryGroup(bucket, it) } }
    }
}
