package com.linkup.database

import com.linkup.config.EnvConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
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
            // Creates any table that does not exist yet. Existing tables are left alone;
            // new columns on them are handled by applyColumnMigrations() below, because
            // SchemaUtils' column diff tries to re-add `profiles.id` and trips over its
            // existing primary key.
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

            applyColumnMigrations()
        }
    }

    /**
     * Adds columns introduced after a database was first created.
     *
     * Each statement is idempotent, so this is safe to run on every boot and on a
     * fresh database alike. Replace with a real migration tool (Flyway/Liquibase)
     * once the schema starts changing shape rather than just growing.
     */
    private fun Transaction.applyColumnMigrations() {
        listOf(
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(32) NULL",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS location VARCHAR(120) NULL",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS website VARCHAR(255) NULL"
        ).forEach { exec(it) }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
