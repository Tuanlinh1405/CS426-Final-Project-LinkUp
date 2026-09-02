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
        val rawUri = EnvConfig.DATABASE_URL
        
        // Parse postgresql://user:password@host:port/database URI format
        val regex = Regex("""postgresql://([^:]+):([^@]+)@([^:/]+)(?::(\d+))?/(.+)""")
        val match = regex.find(rawUri)

        val database = if (match != null) {
            val (user, password, host, portStr, dbName) = match.destructured
            val port = if (portStr.isNotEmpty()) portStr else "5432"
            val jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
            Database.connect(
                url = jdbcUrl,
                driver = driverClassName,
                user = user,
                password = password
            )
        } else {
            val jdbcURL = if (rawUri.startsWith("jdbc:")) rawUri else rawUri.replace("postgresql://", "jdbc:postgresql://")
            Database.connect(jdbcURL, driverClassName)
        }
        
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
