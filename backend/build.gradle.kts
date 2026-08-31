plugins {
    alias(libs.plugins.kotlin.serialization)
    kotlin("jvm")
    application
}

group = "com.linkup"
version = "0.0.1"

application {
    mainClass.set("com.linkup.ApplicationKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.dotenv.kotlin)
    implementation(libs.logback)
    implementation("org.mindrot:jbcrypt:0.4")
}

kotlin {
    jvmToolchain(17)
}
