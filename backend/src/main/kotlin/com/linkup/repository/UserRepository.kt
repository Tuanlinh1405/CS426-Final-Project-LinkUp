package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.DatabaseFactory.rawRead
import com.linkup.database.UserEntity
import com.linkup.database.UsersTable
import com.linkup.model.UserRegistrationRequest
import org.mindrot.jbcrypt.BCrypt
import java.util.*

/** Minimal user projection for the auth routes; avoids returning a detached Exposed entity. */
class AuthUser(
    val id: UUID,
    val email: String,
    val username: String,
    val fullName: String?,
    val createdAt: String
)

class UserRepository {
    suspend fun registerUser(request: UserRegistrationRequest): UserEntity? = dbQuery {
        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())
        UserEntity.new {
            email = request.email
            username = request.username
            this.passwordHash = passwordHash
            fullName = request.fullName
        }
    }

    suspend fun getUserById(id: UUID): UserEntity? = dbQuery {
        UserEntity.findById(id)
    }

    suspend fun getUserByEmail(email: String): UserEntity? = dbQuery {
        UserEntity.find { UsersTable.email eq email }.firstOrNull()
    }

    suspend fun getUserByUsername(username: String): UserEntity? = dbQuery {
        UserEntity.find { UsersTable.username eq username }.firstOrNull()
    }

    /**
     * Login lookup on a single auto-commit statement. Exposed would wrap this in
     * BEGIN/COMMIT, which costs two extra round trips (~170ms each to the Supabase
     * pooler). BCrypt runs after the connection is released.
     */
    suspend fun validateUser(emailOrUsername: String, password: String): AuthUser? {
        val row = rawRead { conn ->
            conn.prepareStatement(
                "SELECT id, email, username, password_hash, full_name, created_at " +
                    "FROM users WHERE email = ? OR username = ? LIMIT 1"
            ).use { ps ->
                ps.setString(1, emailOrUsername)
                ps.setString(2, emailOrUsername)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) null
                    else arrayOf(
                        rs.getString("id"),
                        rs.getString("email"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("full_name"),
                        rs.getTimestamp("created_at")?.toInstant()?.toString() ?: ""
                    )
                }
            }
        } ?: return null

        val passwordHash = row[3] as String
        if (!BCrypt.checkpw(password, passwordHash)) return null

        return AuthUser(
            id = UUID.fromString(row[0] as String),
            email = row[1] as String,
            username = row[2] as String,
            fullName = row[4] as String?,
            createdAt = row[5] as String
        )
    }

    suspend fun updateUser(id: UUID, fullName: String?): Boolean = dbQuery {
        val user = UserEntity.findById(id) ?: return@dbQuery false
        user.fullName = fullName
        true
    }

    suspend fun deleteUser(id: UUID): Boolean = dbQuery {
        val user = UserEntity.findById(id) ?: return@dbQuery false
        user.delete()
        true
    }
}
