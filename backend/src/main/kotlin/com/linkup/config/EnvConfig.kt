package com.linkup.config

import io.github.cdimascio.dotenv.dotenv

object EnvConfig {
    private val dotenv = dotenv {
        ignoreIfMissing = true
        directory = "../" // .env is in the root project directory
    }

    val PORT = dotenv["PORT"]?.toInt() ?: 8080
    val HOST = dotenv["HOST"] ?: "0.0.0.0"
    
    val DATABASE_URL = dotenv["postgresql://postgres.hoxujmjicfveykawiwvk:LinkUp-DBCS426@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres"] // Special case for your .env format
        ?: dotenv["DATABASE_URL"] 
        ?: "postgresql://localhost:5432/postgres"

    // If the .env has the URI directly as a value without a key name, 
    // we might need to parse it differently. 
    // Based on your .env file, the line is just the URI.
    // I will try to find it by iterating if needed, but for now let's assume it's DATABASE_URL or we hardcode for this specific setup.
    
    val DB_URI = "postgresql://postgres.hoxujmjicfveykawiwvk:LinkUp-DBCS426@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres"

    val JWT_SECRET = dotenv["JWT_SECRET"] ?: "secret"
    val JWT_ISSUER = dotenv["JWT_ISSUER"] ?: "http://0.0.0.0:8080"
    val JWT_AUDIENCE = dotenv["JWT_AUDIENCE"] ?: "linkup"
}
