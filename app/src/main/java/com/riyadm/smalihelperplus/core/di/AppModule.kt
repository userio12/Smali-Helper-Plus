package com.riyadm.smalihelperplus.core.di

import android.content.Context
import androidx.room.Room
import com.riyadm.smalihelperplus.core.common.ApplicationScope
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

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
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
}
