package com.hitit.app.di

import com.hitit.app.coach.MediaPipeCoachRephraser
import com.hitit.domain.coach.CoachRephraser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the optional on-device LLM rephraser. It self-disables when no model is present. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoachModule {
    @Binds
    @Singleton
    abstract fun bindCoachRephraser(impl: MediaPipeCoachRephraser): CoachRephraser
}
