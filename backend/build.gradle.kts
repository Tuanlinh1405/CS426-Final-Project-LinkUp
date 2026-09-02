plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    kotlin("jvm")
}

group = "com.linkup"
version = "0.0.1"

application {
    mainClass.set("com.linkup.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("linkup-backend.jar")
    }
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
    testImplementation(libs.junit)
    implementation("io.minio:minio:8.5.17")
    implementation("org.jcodec:jcodec:0.2.5")
    implementation(platform("software.amazon.awssdk:bom:2.51.3"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:url-connection-client")
    testImplementation("io.ktor:ktor-server-test-host:3.1.1")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.jcodec:jcodec-javase:0.2.5")
}

kotlin {
    jvmToolchain(17)
}

tasks.register<JavaExec>("dbCheck") {
    group = "verification"
    description = "Check database connectivity and schema with read-only queries (no user data)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.linkup.database.DatabaseCheckKt")
    workingDir(projectDir)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
}

tasks.register<JavaExec>("storageCheck") {
    group = "verification"
    description = "Upload, read and delete one temporary object in the configured Reels storage."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.linkup.reels.StorageCheckKt")
    workingDir(projectDir)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
}

tasks.register<JavaExec>("reelsDurationMigration") {
    group = "database"
    description = "Explicitly apply migration 003 that removes Reels duration caps. Requires --args=--confirm."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.linkup.database.ReelsDurationMigrationKt")
    workingDir(projectDir)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
}
