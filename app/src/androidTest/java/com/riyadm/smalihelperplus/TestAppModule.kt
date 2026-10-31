package com.riyadm.smalihelperplus

import android.content.Context
import androidx.room.Room
import com.riyadm.smalihelperplus.core.common.ApplicationScope
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.database.AppDatabase
import com.riyadm.smalihelperplus.core.di.AppModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Replaces [AppModule] in instrumented tests. The only difference is an in-memory Room database, so every test starts
 * from an empty database (the bundled content is seeded again by the app) and nothing leaks between tests.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Provides fun opcodeDao(db: AppDatabase) = db.opcodeDao()
    @Provides fun lessonDao(db: AppDatabase) = db.lessonDao()
    @Provides fun contentMetadataDao(db: AppDatabase) = db.contentMetadataDao()
    @Provides fun bookmarkDao(db: AppDatabase) = db.bookmarkDao()
    @Provides fun recentViewDao(db: AppDatabase) = db.recentViewDao()
    @Provides fun searchHistoryDao(db: AppDatabase) = db.searchHistoryDao()
    @Provides fun snippetDao(db: AppDatabase) = db.snippetDao()
    @Provides fun learningDao(db: AppDatabase) = db.learningDao()
    @Provides fun recentFileDao(db: AppDatabase) = db.recentFileDao()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
}
