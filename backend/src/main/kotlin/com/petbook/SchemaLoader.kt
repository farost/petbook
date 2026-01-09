package com.petbook

import com.petbook.db.TypeDBConfig
import com.petbook.db.TypeDBService

/**
 * Utility to connect to TypeDB and load the schema.
 * Run with: ./gradlew run -PmainClass=com.petbook.SchemaLoaderKt
 */
fun main() {
    val config = TypeDBConfig.fromEnvironment()
    println("TypeDB Configuration:")
    println("  Addresses: ${config.addresses}")
    println("  Database: ${config.database}")
    println("  Username: ${config.username}")
    println("  TLS: ${config.useTls}")

    val service = TypeDBService(config)

    println("\nConnecting to TypeDB...")
    if (!service.connect()) {
        println("ERROR: Failed to connect to TypeDB")
        return
    }
    println("Connected successfully!")

    println("\nEnsuring database '${config.database}' exists...")
    if (!service.ensureDatabaseExists()) {
        println("ERROR: Failed to ensure database exists")
        service.close()
        return
    }
    println("Database ready!")

    val schemaPath = "../schema/petbook.tql"
    println("\nLoading schema from $schemaPath...")
    if (!service.loadSchemaFromFile(schemaPath)) {
        println("ERROR: Failed to load schema")
        service.close()
        return
    }
    println("Schema loaded successfully!")

    service.close()
    println("\nDone! TypeDB is ready for Petbook.")
}
