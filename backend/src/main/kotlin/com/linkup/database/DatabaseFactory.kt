package com.linkup.database

import com.linkup.config.EnvConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.Executors

object DatabaseFactory {
    private const val POOL_SIZE = 10

    private lateinit var dataSource: HikariDataSource

    // Bounded to POOL_SIZE so coroutines can't oversubscribe the pool and time out waiting.
    private val dbDispatcher: CoroutineDispatcher =
        Executors.newFixedThreadPool(POOL_SIZE).asCoroutineDispatcher()

    fun init() {
        val driverClassName = "org.postgresql.Driver"
        val rawUri = EnvConfig.DATABASE_URL

        val hikariConfig = HikariConfig().apply {
            this.driverClassName = driverClassName

            val regex = Regex("""postgresql://([^:]+):([^@]+)@([^:/]+)(?::(\d+))?/(.+)""")
            val match = regex.find(rawUri)

            if (match != null) {
                val (user, password, host, portStr, dbName) = match.destructured
                val port = if (portStr.isNotEmpty()) portStr else "6543"
                val cleanDb = dbName.substringBefore("?")
                jdbcUrl = "jdbc:postgresql://$host:$port/$cleanDb?sslmode=require&prepareThreshold=0"
                username = user
                this.password = password
            } else if (rawUri.startsWith("jdbc:")) {
                jdbcUrl = rawUri
            } else {
                jdbcUrl = rawUri.replace("postgresql://", "jdbc:postgresql://")
            }

            // HikariCP pool configuration. Connections to the Supabase pooler are expensive
            // (~1.5-2s each over TLS), so the pool never shrinks below POOL_SIZE and Hikari
            // sends a lightweight keepalive probe before the pooler's idle timeout drops them.
            maximumPoolSize = POOL_SIZE
            minimumIdle = POOL_SIZE
            idleTimeout = 600000
            maxLifetime = 600000
            keepaliveTime = 120000
            connectionTimeout = 5000
            isAutoCommit = false
        }

        dataSource = HikariDataSource(hikariConfig)
        val database = Database.connect(dataSource)

        transaction(database) {
            // Auto-migrate schema changes for existing PostgreSQL tables
            try {
                exec("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS name VARCHAR(100);")
                exec("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'DIRECT';")
                exec("""
                    CREATE TABLE IF NOT EXISTS message_receipts (
                        message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                        user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        status VARCHAR(20) NOT NULL DEFAULT 'SENT',
                        delivered_at TIMESTAMP WITH TIME ZONE NULL,
                        read_at TIMESTAMP WITH TIME ZONE NULL,
                        PRIMARY KEY (message_id, user_id)
                    );
                """.trimIndent())
            } catch (e: Exception) {
                // Log/ignore if table or column alteration is handled
            }

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
                MessageReceiptsTable,
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
        newSuspendedTransaction(dbDispatcher) { block() }

    /**
     * Read-only single-statement path that bypasses Exposed's BEGIN/COMMIT wrapper.
     * Each transaction round trip to the Supabase pooler costs ~170ms; a bare
     * auto-commit statement costs one trip instead of three. Measured 2026-09-02:
     * exposed_tx=364ms vs raw_jdbc=153ms for the same SELECT.
     */
    suspend fun <T> rawRead(block: (java.sql.Connection) -> T): T =
        kotlinx.coroutines.withContext(dbDispatcher) {
            dataSource.connection.use { conn ->
                conn.autoCommit = true
                block(conn)
            }
        }
}
