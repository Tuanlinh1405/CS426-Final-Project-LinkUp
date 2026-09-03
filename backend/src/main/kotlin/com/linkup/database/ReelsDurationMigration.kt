package com.linkup.database

import com.linkup.config.EnvConfig
import java.sql.DriverManager

/** Explicit one-off migration command; never runs during backend startup. */
fun reelsDurationMigration(args: Array<String>) {
    require("--confirm" in args) { "Refusing to modify the database without --confirm." }
    val config = EnvConfig.database
    val sql = object {}.javaClass.getResource("/db/migrations/003_reels_unbounded_duration.sql")!!.readText()
    val statements = sql.lineSequence()
        .filterNot { it.trimStart().startsWith("--") }
        .joinToString("\n")
        .split(';')
        .map(String::trim)
        .filter { it.isNotEmpty() && it.uppercase() !in setOf("BEGIN", "COMMIT") }

    DriverManager.getConnection(config.jdbcUrl, config.connectionProperties()).use { connection ->
        connection.autoCommit = false
        try {
            statements.forEach { statement -> connection.createStatement().use { it.execute(statement) } }
            connection.commit()
            val checks = connection.createStatement().use { statement ->
                statement.executeQuery("""
                    SELECT conname FROM pg_constraint
                    WHERE conname IN ('reel_assets_duration_ms_positive_check',
                                      'reel_watch_events_watched_ms_nonnegative_check')
                """.trimIndent()).use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
            }
            check(checks.size == 2) { "Migration committed but expected constraints were not found." }
            println("Reels duration migration applied: duration > 0 and watched_ms >= 0.")
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }
}

fun main(args: Array<String>) = reelsDurationMigration(args)
