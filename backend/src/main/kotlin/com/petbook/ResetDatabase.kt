package com.petbook

import com.petbook.db.TypeDBConfig
import com.petbook.db.TypeDBService
import com.typedb.driver.TypeDB
import com.typedb.driver.api.Credentials
import com.typedb.driver.api.DriverOptions
import com.typedb.driver.api.DriverTlsConfig
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ResetDatabase")

fun main() {
    val config = TypeDBConfig.fromEnvironment()

    println("=== TypeDB Database Reset Tool ===")
    println()
    println("Database: ${config.database}")
    println("Addresses: ${config.addresses}")
    println()
    println("WARNING: This will DELETE all data in the '${config.database}' database!")
    println("Press Enter to continue or Ctrl+C to cancel...")
    readLine()

    val credentials = Credentials(config.username, config.password)
    val tlsConfig = if (config.useTls) {
        DriverTlsConfig.enabledWithNativeRootCA()
    } else {
        DriverTlsConfig.disabled()
    }
    val options = DriverOptions(tlsConfig)

    val driver = TypeDB.driver(config.addresses, credentials, options)

    try {
        println("Connecting to TypeDB...")

        // Check if database exists
        val databases = driver.databases()
        if (databases.contains(config.database)) {
            println("Deleting database '${config.database}'...")
            databases.get(config.database).delete()
            println("Database deleted.")
        } else {
            println("Database '${config.database}' does not exist.")
        }

        // Recreate database
        println("Creating database '${config.database}'...")
        databases.create(config.database)
        println("Database created.")

        // Load schema
        println("Loading schema from ../schema/petbook.tql...")
        val schemaContent = java.io.File("../schema/petbook.tql").readText()

        driver.transaction(config.database, com.typedb.driver.api.Transaction.Type.SCHEMA).use { tx ->
            tx.query(schemaContent).resolve()
            tx.commit()
        }
        println("Schema loaded successfully.")

        println()
        println("=== Database reset complete! ===")
        println("The database is now empty with a fresh schema.")

    } finally {
        driver.close()
    }
}
