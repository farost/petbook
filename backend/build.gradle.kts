plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "com.petbook"
version = "0.1.0"

application {
    mainClass.set("com.petbook.ApplicationKt")
}

repositories {
    mavenCentral()
    maven("https://repo.typedb.com/public/public-release/maven/")
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-html-builder:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")
    implementation("io.ktor:ktor-server-cors:3.0.3")

    // JSON serialization
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    // Authentication & JWT
    implementation("io.ktor:ktor-server-auth:3.0.3")
    implementation("io.ktor:ktor-server-auth-jwt:3.0.3")

    // Password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // TypeDB driver
    implementation("com.typedb:typedb-driver:3.7.0-alpha-3")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Helper function to load .env file
fun loadEnvFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val (key, value) = line.split("=", limit = 2)
            key.trim() to value.trim()
        }
}

tasks.register<JavaExec>("loadSchema") {
    description = "Connect to TypeDB and load the Petbook schema"
    mainClass.set("com.petbook.SchemaLoaderKt")
    classpath = sourceSets["main"].runtimeClasspath

    // Load environment variables from .env file
    loadEnvFile(file(".env")).forEach { (key, value) ->
        environment(key, value)
    }
}

tasks.register<JavaExec>("diagnostic") {
    description = "Run diagnostic queries to find corrupted data"
    mainClass.set("com.petbook.DiagnosticQueryKt")
    classpath = sourceSets["main"].runtimeClasspath

    // Load environment variables from .env file
    loadEnvFile(file(".env")).forEach { (key, value) ->
        environment(key, value)
    }
}

tasks.register<JavaExec>("resetDatabase") {
    description = "Reset the TypeDB database (DELETE ALL DATA and reload schema)"
    mainClass.set("com.petbook.ResetDatabaseKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`

    // Load environment variables from .env file
    loadEnvFile(file(".env")).forEach { (key, value) ->
        environment(key, value)
    }
}

tasks.test {
    // Load environment variables from .env file for integration tests
    loadEnvFile(file(".env")).forEach { (key, value) ->
        environment(key, value)
    }
}

tasks.named<JavaExec>("run") {
    // Load environment variables from .env file
    loadEnvFile(file(".env")).forEach { (key, value) ->
        environment(key, value)
    }
}
