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
    fatJar { archiveFileName.set("linkup-backend.jar") }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.dotenv.kotlin)
    implementation(libs.logback)
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.minio:minio:8.5.17")
    implementation("org.jcodec:jcodec:0.2.5")
    implementation(platform("software.amazon.awssdk:bom:2.51.3"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:url-connection-client")

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation("io.ktor:ktor-server-test-host:3.1.1")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.jcodec:jcodec-javase:0.2.5")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.12.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

fun registerBackendTool(name: String, groupName: String, descriptionText: String, mainClassName: String) {
    tasks.register<JavaExec>(name) {
        group = groupName
        description = descriptionText
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(mainClassName)
        workingDir(projectDir)
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    }
}

registerBackendTool("dbCheck", "verification", "Check database connectivity and schema with read-only queries.", "com.linkup.database.DatabaseCheckKt")
registerBackendTool("storageCheck", "verification", "Upload, read and delete one temporary Reels storage object.", "com.linkup.reels.StorageCheckKt")
registerBackendTool("reelsDurationMigration", "database", "Apply migration 003; requires --args=--confirm.", "com.linkup.database.ReelsDurationMigrationKt")
registerBackendTool("searchRepliesMigration", "database", "Apply migration 004; requires --args=--confirm.", "com.linkup.database.SearchRepliesMigrationKt")
registerBackendTool("commentReactionsMigration", "database", "Apply migration 005; requires --args=--confirm.", "com.linkup.database.CommentReactionsMigrationKt")
