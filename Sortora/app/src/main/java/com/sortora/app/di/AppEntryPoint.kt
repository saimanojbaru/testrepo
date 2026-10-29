package com.sortora.app.di

import com.sortora.app.domain.repository.SessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Access to singletons from code Hilt can't inject into (Robolectric smoke tests today; widgets
 * or quick-settings tiles later). Obtain with EntryPointAccessors.fromApplication(context, …).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun sessions(): SessionRepository
}
