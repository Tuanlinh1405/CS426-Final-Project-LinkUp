package com.example.linkup.feature.dating

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatingNetworkModule {
    @Provides
    @Singleton
    fun provideDatingApiService(retrofit: Retrofit): DatingApiService =
        retrofit.create(DatingApiService::class.java)
}