package com.ivy.data.di

import com.ivy.data.repository.InboxRepository
import com.ivy.data.repository.impl.InboxRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InboxModule {
    @Binds
    @Singleton
    abstract fun bindInboxRepository(
        impl: InboxRepositoryImpl
    ): InboxRepository
}
