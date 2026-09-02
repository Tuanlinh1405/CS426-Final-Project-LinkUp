package com.linkup.config

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnvConfigTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `finds backend env from project root`() {
        val root = temporary.root.toPath()
        temporary.newFolder("backend")
        val file = temporary.newFile("backend/.env").toPath()
        assertEquals(file, EnvConfig.findEnvFile(root, null))
    }

    @Test fun `finds env when launched inside backend`() {
        val file = temporary.newFile(".env").toPath()
        assertEquals(file, EnvConfig.findEnvFile(temporary.root.toPath(), null))
    }

    @Test fun `explicit env file overrides conventional location`() {
        temporary.newFile(".env")
        val file = temporary.newFile("custom.env").toPath()
        assertEquals(file, EnvConfig.findEnvFile(temporary.root.toPath(), "custom.env"))
    }

    @Test fun `missing file still allows configuration via OS environment`() {
        val root = temporary.root.toPath()
        assertEquals(root.resolve(".env"), EnvConfig.findEnvFile(root, null))
    }

    @Test fun `missing explicit env file fails clearly`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnvConfig.findEnvFile(temporary.root.toPath(), "missing.env")
        }
    }
}
