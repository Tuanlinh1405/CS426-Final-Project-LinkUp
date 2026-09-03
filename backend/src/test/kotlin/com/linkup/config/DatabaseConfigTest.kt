package com.linkup.config

import org.junit.Assert.*
import org.junit.Test
import org.postgresql.Driver

class DatabaseConfigTest {
    @Test fun `Supabase URI becomes JDBC URL without credentials`() {
        val config = DatabaseConfig.fromUrl("postgresql://postgres.test:example-password@pooler.example.test:5432/postgres")
        assertEquals("postgres.test", config.username)
        assertEquals("example-password", config.password)
        assertFalse(config.jdbcUrl.contains("example-password"))
        assertFalse(config.jdbcUrl.contains("postgres.test:"))
        assertTrue(config.jdbcUrl.contains("sslmode=require"))
        assertTrue(config.jdbcUrl.contains("connectTimeout=10"))
        assertTrue(Driver().acceptsURL(config.jdbcUrl))
        val properties = requireNotNull(Driver.parseURL(config.jdbcUrl, config.connectionProperties()))
        assertEquals("postgres.test", properties.getProperty("user"))
        assertEquals("example-password", properties.getProperty("password"))
    }

    @Test fun `credentials decode reserved characters without changing plus`() {
        val config = DatabaseConfig.fromUrl("postgres://user%40example.test:p%40ss%3A%23%2F+%25@db.example.test/postgres")
        assertEquals("user@example.test", config.username)
        assertEquals("p@ss:#/+%", config.password)
        assertTrue(config.jdbcUrl.startsWith("jdbc:postgresql://db.example.test:5432/"))
    }

    @Test fun `preserves SSL verification and explicit timeout`() {
        val config = DatabaseConfig.fromUrl("postgresql://user:pass@db.example.test:5432/postgres?sslmode=verify-full&connectTimeout=5&sslrootcert=C%3A%2Fcerts%2Froot.crt")
        val properties = requireNotNull(Driver.parseURL(config.jdbcUrl, config.connectionProperties()))
        assertEquals("verify-full", properties.getProperty("sslmode"))
        assertEquals("5", properties.getProperty("connectTimeout"))
        assertEquals("C:/certs/root.crt", properties.getProperty("sslrootcert"))
    }

    @Test fun `transaction pooler disables server prepared statements`() {
        val config = DatabaseConfig.fromUrl("postgresql://user:pass@pooler.example.test:6543/postgres?prepareThreshold=5")
        assertTrue(config.jdbcUrl.contains("prepareThreshold=0"))
    }

    @Test fun `local database may explicitly disable SSL`() {
        val config = DatabaseConfig.fromUrl("postgresql://user:pass@localhost:5432/postgres?sslmode=disable")
        assertTrue(config.jdbcUrl.contains("sslmode=disable"))
    }

    @Test fun `supports IPv6 host`() {
        val config = DatabaseConfig.fromUrl("postgresql://user:pass@[::1]:5432/postgres?sslmode=disable")
        assertTrue(Driver().acceptsURL(config.jdbcUrl))
    }

    @Test fun `invalid URLs fail without echoing input or cause`() {
        val invalid = listOf(
            "", "https://user:private-value@example.test/postgres",
            "postgresql://user:private-value@db.example.test",
            "postgresql://db.example.test/postgres", "postgresql://user:@db.example.test/postgres",
            "postgresql://user:private value@db.example.test/postgres",
            "postgresql://user:private-value@db.example.test:70000/postgres",
            "postgresql://user:private-value@db.example.test/postgres?sslmode=disable",
            "postgresql://user:private-value@db.example.test/postgres?password=private-value",
            "postgresql://user:private-value@db.example.test/postgres?sslmode=require&sslmode=disable",
            "postgresql://user:private-value@db.example.test/postgres#private-value",
        )
        invalid.forEach { value ->
            val error = assertThrows(IllegalArgumentException::class.java) { DatabaseConfig.fromUrl(value) }
            assertFalse(error.message.orEmpty().contains("private-value"))
            assertNull(error.cause)
        }
    }

    @Test fun `config string representation redacts credentials`() {
        val config = DatabaseConfig.fromUrl("postgresql://user:private-value@db.example.test/postgres")
        assertEquals("DatabaseConfig([REDACTED])", config.toString())
    }
}
