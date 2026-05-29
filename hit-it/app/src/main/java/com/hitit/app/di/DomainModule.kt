package com.hitit.app.di

import com.hitit.domain.streak.StreakCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideStreakCalculator(): StreakCalculator = StreakCalculator()
}
