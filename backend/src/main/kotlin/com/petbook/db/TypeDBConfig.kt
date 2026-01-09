package com.petbook.db

data class TypeDBConfig(
    val addresses: Set<String>,
    val database: String,
    val username: String,
    val password: String,
    val useTls: Boolean = true
) {
    companion object {
        fun fromEnvironment(): TypeDBConfig {
            val addressesStr = System.getenv("TYPEDB_ADDRESSES")
                ?: System.getenv("TYPEDB_ADDRESS")
                ?: "localhost:1729"

            return TypeDBConfig(
                addresses = addressesStr.split(",").map { it.trim() }.toSet(),
                database = System.getenv("TYPEDB_DATABASE") ?: "petbook",
                username = System.getenv("TYPEDB_USERNAME") ?: "",
                password = System.getenv("TYPEDB_PASSWORD") ?: "",
                useTls = System.getenv("TYPEDB_USE_TLS")?.toBoolean() ?: true
            )
        }

        /**
         * Create a config for tests using a separate test database.
         * IMPORTANT: Tests should NEVER run against the production database.
         */
        fun forTests(): TypeDBConfig {
            val baseConfig = fromEnvironment()
            val testDbName = System.getenv("TYPEDB_TEST_DATABASE") ?: "petbook_test"

            // Safety check: never use production database name
            require(testDbName != "petbook") {
                "Test database name cannot be 'petbook' (production). Set TYPEDB_TEST_DATABASE environment variable."
            }

            return baseConfig.copy(database = testDbName)
        }
    }
}
