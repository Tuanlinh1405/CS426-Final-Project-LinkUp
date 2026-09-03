package com.linkup.database

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaMappingTest {
    @Test fun `profile uses the SQL schema user id primary key and foreign key`() {
        assertEquals("user_id", ProfilesTable.id.name)
        assertEquals(UsersTable.id, ProfilesTable.id.referee)
    }
}
