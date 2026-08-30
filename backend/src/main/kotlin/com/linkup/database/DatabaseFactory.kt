package com.linkup.database

import com.linkup.config.EnvConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"
        val jdbcURL = EnvConfig.DB_URI.replace("postgresql://", "jdbc:postgresql://")
        
        val database = Database.connect(jdbcURL, driverClassName)
        
        transaction(database) {
            // Create all tables if they don't exist
            SchemaUtils.create(
                UsersTable,
                RefreshTokensTable,
                ProfilesTable,
                FollowsTable,
                MediaTable,
                PostsTable,
                PostMediaTable,
                CommentsTable,
                PostReactionsTable,
                ReelsTable,
                ConversationsTable,
                ConversationMembersTable,
                MessagesTable,
                DatingProfilesTable,
                DatingPhotosTable,
                DatingSwipesTable,
                DatingMatchesTable,
                AIConversationsTable,
                AIMessagesTable,
                NotificationsTable
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
