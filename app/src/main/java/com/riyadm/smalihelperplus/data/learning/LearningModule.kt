package com.riyadm.smalihelperplus.data.learning

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LearningModule {
    @Binds
    abstract fun bindDailyGoalSource(impl: PreferencesDailyGoalSource): DailyGoalSource
}
