package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.UserEntity
import com.linkup.database.UsersTable
import com.linkup.model.UserRegistrationRequest
import org.jetbrains.exposed.sql.or
import org.mindrot.jbcrypt.BCrypt
import java.util.*

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
        UserEntity.find { UsersTable.email eq email }.singleOrNull()
    }

    suspend fun getUserByUsername(username: String): UserEntity? = dbQuery {
        UserEntity.find { UsersTable.username eq username }.singleOrNull()
    }

    suspend fun validateUser(emailOrUsername: String, password: String): UserEntity? = dbQuery {
        val user = UserEntity.find { 
            (UsersTable.email eq emailOrUsername) or (UsersTable.username eq emailOrUsername)
        }.singleOrNull() ?: return@dbQuery null

        if (BCrypt.checkpw(password, user.passwordHash)) {
            user
        } else {
            null
        }
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
