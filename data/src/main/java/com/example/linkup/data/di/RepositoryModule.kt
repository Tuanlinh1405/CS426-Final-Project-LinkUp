package com.example.linkup.data.di

import com.example.linkup.data.repository.AuthRepository
import com.example.linkup.data.repository.AuthRepositoryImpl
import com.example.linkup.data.repository.FriendRepository
import com.example.linkup.data.repository.FriendRepositoryImpl
import com.example.linkup.data.repository.NotificationRepository
import com.example.linkup.data.repository.NotificationRepositoryImpl
import com.example.linkup.data.repository.ProfileRepository
import com.example.linkup.data.repository.ProfileRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        notificationRepositoryImpl: NotificationRepositoryImpl
    ): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        friendRepositoryImpl: FriendRepositoryImpl
    ): FriendRepository
}
