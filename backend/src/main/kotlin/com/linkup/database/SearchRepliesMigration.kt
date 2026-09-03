package com.linkup.database

import com.linkup.config.EnvConfig
import java.sql.DriverManager

/** Explicit additive migration; never runs during normal backend startup. */
fun searchRepliesMigration(args: Array<String>) {
    require("--confirm" in args) { "Refusing to modify the database without --confirm." }
    val config = EnvConfig.database
    val sql = object {}.javaClass.getResource("/db/migrations/004_search_and_comment_replies.sql")!!.readText()
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
            val columns = connection.prepareStatement("""SELECT table_name FROM information_schema.columns
                WHERE table_schema='public' AND column_name='parent_comment_id' AND table_name IN ('comments','reel_comments')""").use { statement ->
                statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
            }
            check(columns == setOf("comments", "reel_comments")) { "Reply columns were not created on both comment tables." }
            connection.commit()
            println("Search/replies migration applied: comments and reel_comments now support replies.")
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }
}

fun main(args: Array<String>) = searchRepliesMigration(args)
