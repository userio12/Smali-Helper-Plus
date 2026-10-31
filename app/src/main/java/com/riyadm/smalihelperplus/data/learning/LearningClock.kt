package com.riyadm.smalihelperplus.data.learning

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Time source for learning features; study days use the device's local calendar. */
@Singleton
open class LearningClock @Inject constructor() {
    open fun nowMillis(): Long = System.currentTimeMillis()

    open fun zone(): ZoneId = ZoneId.systemDefault()

    fun todayEpochDay(): Long = epochDayOf(nowMillis())

    fun epochDayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toEpochDay()

    /** Emits the local day; rechecks each minute to notice timezone and wall-clock changes. */
    val today: Flow<Long> = flow {
        while (true) {
            val now = nowMillis()
            val today = epochDayOf(now)
            emit(today)
            val nextMidnight = LocalDate.ofEpochDay(today + 1).atStartOfDay(zone()).toInstant().toEpochMilli()
            delay((nextMidnight - now).coerceIn(1_000L, 60_000L) + 500L)
        }
    }.distinctUntilChanged()
}
