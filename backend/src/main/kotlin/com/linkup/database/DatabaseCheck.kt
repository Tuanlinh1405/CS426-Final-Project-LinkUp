package com.linkup.database

import com.linkup.config.EnvConfig
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.system.exitProcess

/** Standalone diagnostic: no Ktor startup, DDL, account creation, or application data reads. */
fun main(args: Array<String>) {
    val config = try {
        EnvConfig.database
    } catch (_: Exception) {
        System.err.println("Invalid/missing database configuration. Check backend/.env and backend/README.md.")
        exitProcess(1)
    }
    try {
        DriverManager.getConnection(config.jdbcUrl, config.connectionProperties()).use { connection ->
            connection.isReadOnly = true
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                statement.queryTimeout = 15
                statement.executeQuery("SELECT 1").use { result -> check(result.next() && result.getInt(1) == 1) }
                println("Database connection: OK (read-only check).")
                val columns = mutableMapOf<String, MutableSet<String>>()
                statement.executeQuery(
                    "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public'",
                ).use { result ->
                    while (result.next()) {
                        columns.getOrPut(result.getString(1)) { mutableSetOf() }.add(result.getString(2))
                    }
                }
                val expected = listOf(
                    UsersTable, RefreshTokensTable, ProfilesTable, FollowsTable, MediaTable,
                    PostsTable, PostMediaTable, CommentsTable, PostReactionsTable, ReelsTable,
                    ConversationsTable, ConversationMembersTable, MessagesTable, DatingProfilesTable,
                    DatingPhotosTable, DatingSwipesTable, DatingMatchesTable, AIConversationsTable,
                    AIMessagesTable, AIAnalysisCacheTable, NotificationsTable,
                )
                val featureColumns = if ("--reels" in args) mapOf(
                    "reel_assets" to setOf("reel_id", "video_key", "thumbnail_key", "storage_backend", "file_size", "duration_ms"),
                    "reel_reactions" to setOf("reel_id", "user_id", "created_at"),
                    "reel_comments" to setOf("id", "reel_id", "author_id", "content", "created_at"),
                    "reel_watch_events" to setOf("id", "reel_id", "user_id", "watched_ms", "skipped", "created_at", "updated_at"),
                    "reel_hidden" to setOf("reel_id", "user_id", "created_at"),
                ) else emptyMap()
                val missing = expected.flatMap { table ->
                    table.columns.filter { it.name !in columns[table.tableName].orEmpty() }
                        .map { "${table.tableName}.${it.name}" }
                } + featureColumns.flatMap { (table, required) -> (required - columns[table].orEmpty()).map { "$table.$it" } }
                connection.rollback()
                if (missing.isNotEmpty()) {
                    System.err.println("Schema missing columns/tables (or role cannot see them): ${missing.joinToString()}")
                    System.err.println("Report this mismatch to the backend/database owner; do not change shared tables yourself.")
                    exitProcess(2)
                }
                println("Schema columns: OK (${expected.size + featureColumns.size} tables). Constraints, RLS and data are not audited by this check.")
            }
        }
    } catch (error: SQLException) {
        val hint = when (error.sqlState) {
            "28P01", "28000" -> "Check the database username/password and Supabase project status."
            "3D000" -> "Check the database name in DATABASE_URL."
            "42501" -> "The database role does not have the required permissions."
            else -> "Check network/DNS, Supabase project status, connection mode and SSL configuration."
        }
        // Never print driver exception messages or stack traces containing connection details.
        System.err.println("Database check failed (SQLSTATE ${error.sqlState ?: "unknown"}). $hint")
        exitProcess(1)
    } catch (_: Exception) {
        System.err.println("Database check failed. See backend/README.md; connection details are hidden.")
        exitProcess(1)
    }
}
