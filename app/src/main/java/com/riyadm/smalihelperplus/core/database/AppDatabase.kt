package com.riyadm.smalihelperplus.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.riyadm.smalihelperplus.core.database.dao.BookmarkDao
import com.riyadm.smalihelperplus.core.database.dao.ContentMetadataDao
import com.riyadm.smalihelperplus.core.database.dao.LearningDao
import com.riyadm.smalihelperplus.core.database.dao.LessonDao
import com.riyadm.smalihelperplus.core.database.dao.OpcodeDao
import com.riyadm.smalihelperplus.core.database.dao.RecentFileDao
import com.riyadm.smalihelperplus.core.database.dao.RecentViewDao
import com.riyadm.smalihelperplus.core.database.dao.SearchHistoryDao
import com.riyadm.smalihelperplus.core.database.dao.SnippetDao
import com.riyadm.smalihelperplus.core.database.entity.AchievementEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.OpcodeEntity
import com.riyadm.smalihelperplus.core.database.entity.OpcodeFtsEntity
import com.riyadm.smalihelperplus.core.database.entity.QuizResultEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentFileEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.SearchHistoryEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.core.database.entity.StudyDayEntity

@Database(
    entities = [
        OpcodeEntity::class,
        OpcodeFtsEntity::class,
        LessonEntity::class,
        FlashcardEntity::class,
        ContentMetadataEntity::class,
        BookmarkEntity::class,
        RecentViewEntity::class,
        SearchHistoryEntity::class,
        SnippetEntity::class,
        LessonProgressEntity::class,
        QuizResultEntity::class,
        FlashcardProgressEntity::class,
        StudyDayEntity::class,
        AchievementEntity::class,
        RecentFileEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun opcodeDao(): OpcodeDao
    abstract fun lessonDao(): LessonDao
    abstract fun contentMetadataDao(): ContentMetadataDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentViewDao(): RecentViewDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun snippetDao(): SnippetDao
    abstract fun learningDao(): LearningDao
    abstract fun recentFileDao(): RecentFileDao

    companion object {
        const val NAME = "smali_helper_plus.db"
    }
}
