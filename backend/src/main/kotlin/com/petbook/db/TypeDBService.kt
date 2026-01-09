package com.petbook.db

import com.typedb.driver.TypeDB
import com.typedb.driver.api.Driver
import com.typedb.driver.api.Credentials
import com.typedb.driver.api.DriverOptions
import com.typedb.driver.api.DriverTlsConfig
import com.typedb.driver.api.Transaction
import com.typedb.driver.api.Transaction.Type.*
import org.slf4j.LoggerFactory
import java.io.Closeable

class TypeDBService(private val config: TypeDBConfig) : Closeable {

    private val logger = LoggerFactory.getLogger(TypeDBService::class.java)
    private var driver: Driver? = null

    fun connect(): Boolean {
        return try {
            logger.info("Connecting to TypeDB at ${config.addresses}")

            val credentials = Credentials(config.username, config.password)
            val tlsConfig = if (config.useTls) {
                DriverTlsConfig.enabledWithNativeRootCA()
            } else {
                DriverTlsConfig.disabled()
            }
            val options = DriverOptions(tlsConfig)

            driver = TypeDB.driver(config.addresses, credentials, options)
            logger.info("Successfully connected to TypeDB")
            true
        } catch (e: Exception) {
            logger.error("Failed to connect to TypeDB: ${e.message}", e)
            false
        }
    }

    fun isConnected(): Boolean = driver != null

    fun ensureDatabaseExists(): Boolean {
        val d = driver ?: return false
        return try {
            val databases = d.databases()
            if (!databases.contains(config.database)) {
                logger.info("Creating database: ${config.database}")
                databases.create(config.database)
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to ensure database exists: ${e.message}", e)
            false
        }
    }

    fun loadSchema(schemaContent: String): Boolean {
        val d = driver ?: return false
        return try {
            d.transaction(config.database, SCHEMA).use { tx ->
                tx.query(schemaContent).resolve()
                tx.commit()
            }
            logger.info("Schema loaded successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to load schema: ${e.message}", e)
            false
        }
    }

    fun loadSchemaFromFile(schemaPath: String): Boolean {
        val schemaContent = java.io.File(schemaPath).readText()
        return loadSchema(schemaContent)
    }

    fun <T> readTransaction(block: (Transaction) -> T): T? {
        val d = driver ?: throw IllegalStateException("Not connected to TypeDB")
        return try {
            d.transaction(config.database, READ).use { tx ->
                block(tx)
            }
        } catch (e: Exception) {
            logger.error("Read transaction failed: ${e.message}", e)
            null
        }
    }

    fun <T> writeTransaction(block: (Transaction) -> T): T? {
        val d = driver ?: throw IllegalStateException("Not connected to TypeDB")
        return try {
            d.transaction(config.database, WRITE).use { tx ->
                val result = block(tx)
                tx.commit()
                result
            }
        } catch (e: Exception) {
            logger.error("Write transaction failed: ${e.message}", e)
            null
        }
    }

    override fun close() {
        driver?.close()
        driver = null
        logger.info("TypeDB connection closed")
    }
}
