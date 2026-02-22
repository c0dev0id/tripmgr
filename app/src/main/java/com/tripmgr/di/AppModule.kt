package com.tripmgr.di

import android.content.Context
import com.tripmgr.data.storage.StorageService
import com.tripmgr.data.repository.TripRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideStorageService(@ApplicationContext context: Context): StorageService =
        StorageService(context)

    @Provides
    @Singleton
    fun provideTripRepository(storageService: StorageService): TripRepository =
        TripRepository(storageService)
}
