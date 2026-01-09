package com.petbook.db

import com.typedb.driver.TypeDB
import com.typedb.driver.api.Driver
import com.typedb.driver.api.Credentials
import com.typedb.driver.api.DriverOptions
import com.typedb.driver.api.DriverTlsConfig
import java.io.File

/**
 * Helper for TypeDB integration tests.
 *
 * Safety features:
 * - Only operates on databases with "_test" suffix
 * - Fails if test database already exists (prevents accidental reuse)
 * - Cleans up test database after tests
 */
class TypeDBTestHelper private constructor(
    private val driver: Driver,
    val testDatabaseName: String
) : AutoCloseable {

    companion object {
        private const val TEST_DB_SUFFIX = "_test"
        private const val PROD_DB_NAME = "petbook"

        /**
         * Creates a test helper with a fresh test database.
         *
         * @param baseName Base name for the test database (will append "_test" and timestamp)
         * @throws IllegalStateException if test database already exists
         * @throws IllegalArgumentException if trying to use production database name
         */
        fun create(baseName: String = "petbook"): TypeDBTestHelper {
            val timestamp = System.currentTimeMillis()
            val testDbName = "${baseName}_${timestamp}${TEST_DB_SUFFIX}"

            // Safety check: never allow production database name
            require(testDbName != PROD_DB_NAME) {
                "Cannot use production database name '$PROD_DB_NAME' for tests!"
            }
            require(testDbName.endsWith(TEST_DB_SUFFIX)) {
                "Test database name must end with '$TEST_DB_SUFFIX', got: $testDbName"
            }

            val config = TypeDBConfig.fromEnvironment()
            val credentials = Credentials(config.username, config.password)
            val tlsConfig = if (config.useTls) {
                DriverTlsConfig.enabledWithNativeRootCA()
            } else {
                DriverTlsConfig.disabled()
            }
            val options = DriverOptions(tlsConfig)

            val driver = TypeDB.driver(config.addresses, credentials, options)

            // Safety check: fail if test database already exists
            val databases = driver.databases()
            if (databases.contains(testDbName)) {
                driver.close()
                throw IllegalStateException(
                    "Test database '$testDbName' already exists! " +
                    "This might indicate a previous test run didn't clean up properly. " +
                    "Please manually delete it or use a different test database name."
                )
            }

            // Create the test database
            databases.create(testDbName)
            println("Created test database: $testDbName")

            return TypeDBTestHelper(driver, testDbName)
        }
    }

    /**
     * Load schema from file into the test database.
     */
    fun loadSchema(schemaPath: String) {
        val schemaContent = File(schemaPath).readText()
        loadSchemaContent(schemaContent)
    }

    /**
     * Load schema content into the test database.
     */
    fun loadSchemaContent(schemaContent: String) {
        driver.transaction(testDatabaseName, com.typedb.driver.api.Transaction.Type.SCHEMA).use { tx ->
            tx.query(schemaContent).resolve()
            tx.commit()
        }
        println("Schema loaded into test database: $testDatabaseName")
    }

    /**
     * Execute a read query and return results using a mapper.
     */
    fun <T> readQuery(query: String, mapper: (com.typedb.driver.api.answer.QueryAnswer) -> T): T {
        return driver.transaction(testDatabaseName, com.typedb.driver.api.Transaction.Type.READ).use { tx ->
            val answer = tx.query(query).resolve()
            mapper(answer)
        }
    }

    /**
     * Check if a query returns any results.
     */
    fun queryHasResults(query: String): Boolean {
        return driver.transaction(testDatabaseName, com.typedb.driver.api.Transaction.Type.READ).use { tx ->
            val answer = tx.query(query).resolve()
            val rows = answer.asConceptRows()
            rows.iterator().hasNext()
        }
    }

    /**
     * Execute a write query.
     */
    fun writeQuery(query: String) {
        driver.transaction(testDatabaseName, com.typedb.driver.api.Transaction.Type.WRITE).use { tx ->
            tx.query(query).resolve()
            tx.commit()
        }
    }

    /**
     * Check if a type exists in the schema.
     */
    fun entityTypeExists(typeName: String): Boolean {
        return try {
            driver.transaction(testDatabaseName, com.typedb.driver.api.Transaction.Type.READ).use { tx ->
                // In TypeDB 3.x, use 'label' to match type labels
                val answer = tx.query("match \$t label $typeName; select \$t;").resolve()
                answer.asConceptRows().iterator().hasNext()
            }
        } catch (e: Exception) {
            println("Type check failed for $typeName: ${e.message}")
            false
        }
    }

    /**
     * Check if a relation type exists in the schema.
     */
    fun relationTypeExists(typeName: String): Boolean {
        return entityTypeExists(typeName)
    }

    /**
     * Check if an attribute type exists in the schema.
     */
    fun attributeTypeExists(typeName: String): Boolean {
        return entityTypeExists(typeName)
    }

    /**
     * Cleanup: delete the test database.
     * Safe because we verified the name ends with "_test".
     */
    fun cleanup() {
        // Double-check safety before deletion
        require(testDatabaseName.endsWith(TEST_DB_SUFFIX)) {
            "SAFETY VIOLATION: Attempting to delete non-test database: $testDatabaseName"
        }
        require(testDatabaseName != PROD_DB_NAME) {
            "SAFETY VIOLATION: Attempting to delete production database!"
        }

        val databases = driver.databases()
        if (databases.contains(testDatabaseName)) {
            databases.get(testDatabaseName).delete()
            println("Deleted test database: $testDatabaseName")
        }
    }

    override fun close() {
        cleanup()
        driver.close()
    }
}
