package com.linkup.database

import com.linkup.config.EnvConfig
import java.sql.DriverManager

/** Explicit additive migration; never runs during backend startup. */
fun commentReactionsMigration(args: Array<String>) {
    require("--confirm" in args) { "Refusing to modify the database without --confirm." }
    val config = EnvConfig.database
    val sql = object {}.javaClass.getResource("/db/migrations/005_comment_reactions.sql")!!.readText()
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
            val tables = connection.prepareStatement("""SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN ('comment_reactions','reel_comment_reactions')""").use { statement ->
                statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
            }
            check(tables == setOf("comment_reactions", "reel_comment_reactions")) { "Comment reaction tables were not created." }
            connection.commit()
            println("Comment reactions migration applied for Post and Reel comments.")
        } catch (error: Exception) {
            connection.rollback(); throw error
        }
    }
}

fun main(args: Array<String>) = commentReactionsMigration(args)
