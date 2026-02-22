package com.tripmgr.di

import android.content.Context
import com.tripmgr.data.drive.DriveServiceWrapper
import com.tripmgr.data.drive.GoogleAuthHelper
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
    fun provideGoogleAuthHelper(@ApplicationContext context: Context): GoogleAuthHelper =
        GoogleAuthHelper(context)

    @Provides
    @Singleton
    fun provideDriveServiceWrapper(): DriveServiceWrapper = DriveServiceWrapper()

    @Provides
    @Singleton
    fun provideTripRepository(
        driveService: DriveServiceWrapper
    ): TripRepository = TripRepository(driveService)
}
