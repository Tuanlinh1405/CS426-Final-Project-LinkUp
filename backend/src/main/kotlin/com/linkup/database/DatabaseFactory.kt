package com.linkup.database

import com.linkup.config.EnvConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private lateinit var database: Database

    fun init() {
        val config = EnvConfig.database
        database = Database.connect(
            url = config.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = config.username,
            password = config.password,
        )
        // Schema changes are explicit SQL operations, never a side effect of starting the server.
        transaction(database) {
            exec("SELECT 1")
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db = database) { block() }
}
