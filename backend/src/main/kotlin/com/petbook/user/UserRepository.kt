package com.petbook.user

import com.petbook.db.TypeDBService
import org.slf4j.LoggerFactory
import java.util.UUID

class UserRepository(private val db: TypeDBService) {

    private val logger = LoggerFactory.getLogger(UserRepository::class.java)

    fun createUser(email: String, passwordHash: String, name: String): User? {
        val id = UUID.randomUUID().toString()

        val success = db.writeTransaction { tx ->
            tx.query("""
                insert
                ${"$"}user isa individual,
                    has id "$id",
                    has email "$email",
                    has password_hash "$passwordHash",
                    has name "$name";
            """.trimIndent()).resolve()
            true
        }

        return if (success == true) {
            logger.info("Created user: $email")
            User(id, email, passwordHash, name)
        } else {
            logger.error("Failed to create user: $email")
            null
        }
    }

    fun findByEmail(email: String): User? {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual,
                    has id ${"$"}id,
                    has email "$email",
                    has password_hash ${"$"}hash,
                    has name ${"$"}name;
                select ${"$"}id, ${"$"}hash, ${"$"}name;
            """.trimIndent()).resolve()

            val rows = answer.asConceptRows()
            val iterator = rows.iterator()

            if (iterator.hasNext()) {
                val row = iterator.next()
                val id = row.get("id").get().tryGetString().get()
                val hash = row.get("hash").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                User(id, email, hash, name)
            } else {
                null
            }
        }
    }

    fun findById(id: String): User? {
        return db.readTransaction { tx ->
            // First get required fields
            val answer = tx.query("""
                match
                ${"$"}user isa individual,
                    has id "$id",
                    has email ${"$"}email,
                    has password_hash ${"$"}hash,
                    has name ${"$"}name;
                select ${"$"}email, ${"$"}hash, ${"$"}name;
            """.trimIndent()).resolve()

            val rows = answer.asConceptRows()
            val iterator = rows.iterator()

            if (iterator.hasNext()) {
                val row = iterator.next()
                val email = row.get("email").get().tryGetString().get()
                val hash = row.get("hash").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()

                // Try to get optional bio
                var bio: String? = null
                try {
                    val bioAnswer = tx.query("""
                        match
                        ${"$"}user isa individual, has id "$id", has bio ${"$"}bio;
                        select ${"$"}bio;
                    """.trimIndent()).resolve()
                    val bioRows = bioAnswer.asConceptRows().iterator()
                    if (bioRows.hasNext()) {
                        bio = bioRows.next().get("bio").get().tryGetString().get()
                    }
                } catch (e: Exception) { /* Optional field not present */ }

                // Try to get optional location
                var location: String? = null
                try {
                    val locationAnswer = tx.query("""
                        match
                        ${"$"}user isa individual, has id "$id", has location ${"$"}location;
                        select ${"$"}location;
                    """.trimIndent()).resolve()
                    val locationRows = locationAnswer.asConceptRows().iterator()
                    if (locationRows.hasNext()) {
                        location = locationRows.next().get("location").get().tryGetString().get()
                    }
                } catch (e: Exception) { /* Optional field not present */ }

                // Try to get optional birthday fields
                var birthYear: Int? = null
                try {
                    val birthYearAnswer = tx.query("""
                        match
                        ${"$"}user isa individual, has id "$id", has birth_year ${"$"}birthYear;
                        select ${"$"}birthYear;
                    """.trimIndent()).resolve()
                    val birthYearRows = birthYearAnswer.asConceptRows().iterator()
                    if (birthYearRows.hasNext()) {
                        birthYear = birthYearRows.next().get("birthYear").get().tryGetInteger().get().toInt()
                    }
                } catch (e: Exception) { /* Optional field not present */ }

                var birthMonth: Int? = null
                try {
                    val birthMonthAnswer = tx.query("""
                        match
                        ${"$"}user isa individual, has id "$id", has birth_month ${"$"}birthMonth;
                        select ${"$"}birthMonth;
                    """.trimIndent()).resolve()
                    val birthMonthRows = birthMonthAnswer.asConceptRows().iterator()
                    if (birthMonthRows.hasNext()) {
                        birthMonth = birthMonthRows.next().get("birthMonth").get().tryGetInteger().get().toInt()
                    }
                } catch (e: Exception) { /* Optional field not present */ }

                var birthDay: Int? = null
                try {
                    val birthDayAnswer = tx.query("""
                        match
                        ${"$"}user isa individual, has id "$id", has birth_day ${"$"}birthDay;
                        select ${"$"}birthDay;
                    """.trimIndent()).resolve()
                    val birthDayRows = birthDayAnswer.asConceptRows().iterator()
                    if (birthDayRows.hasNext()) {
                        birthDay = birthDayRows.next().get("birthDay").get().tryGetInteger().get().toInt()
                    }
                } catch (e: Exception) { /* Optional field not present */ }

                User(id, email, hash, name, bio, location, birthYear, birthMonth, birthDay)
            } else {
                null
            }
        }
    }

    fun emailExists(email: String): Boolean {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has email "$email";
                select ${"$"}user;
            """.trimIndent()).resolve()

            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun countAll(): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id ${"$"}id;
                select ${"$"}id;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    fun findAll(offset: Int = 0, limit: Int = 50): List<User> {
        return findAllUnsorted()
            .sortedBy { it.name.lowercase() }
            .drop(offset)
            .take(limit)
    }

    fun findAllUnsorted(): List<User> {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual,
                    has id ${"$"}id,
                    has email ${"$"}email,
                    has name ${"$"}name;
                select ${"$"}id, ${"$"}email, ${"$"}name;
            """.trimIndent()).resolve()

            val allUsers = mutableListOf<User>()
            for (row in answer.asConceptRows()) {
                val id = row.get("id").get().tryGetString().get()
                val email = row.get("email").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                allUsers.add(User(id, email, "", name)) // Don't expose password hash
            }
            allUsers
        } ?: emptyList()
    }

    fun searchByName(query: String, limit: Int = 20): List<User> {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual,
                    has id ${"$"}id,
                    has email ${"$"}email,
                    has name ${"$"}name;
                ${"$"}name contains "$query";
                select ${"$"}id, ${"$"}email, ${"$"}name;
            """.trimIndent()).resolve()

            val users = mutableListOf<User>()
            var count = 0
            for (row in answer.asConceptRows()) {
                if (count >= limit) break
                val id = row.get("id").get().tryGetString().get()
                val email = row.get("email").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                users.add(User(id, email, "", name))
                count++
            }
            users
        } ?: emptyList()
    }

    fun updateUser(
        userId: String,
        name: String?,
        bio: String?,
        location: String?,
        birthYear: Int? = null,
        birthMonth: Int? = null,
        birthDay: Int? = null,
        clearBirthday: Boolean = false,
        clearBirthMonth: Boolean = false,
        clearBirthDay: Boolean = false
    ): Boolean {
        return db.writeTransaction { tx ->
            if (name != null) {
                // Delete old name first (TypeDB 3.x syntax: just variable in delete)
                try {
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId", has name ${"$"}oldName;
                        delete
                        ${"$"}oldName;
                    """.trimIndent()).resolve()
                } catch (e: Exception) {
                    logger.warn("Could not delete old name (may not exist): ${e.message}")
                }
                tx.query("""
                    match
                    ${"$"}user isa individual, has id "$userId";
                    insert
                    ${"$"}user has name "$name";
                """.trimIndent()).resolve()
            }
            if (bio != null) {
                // Delete old bio first (if exists)
                try {
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId", has bio ${"$"}oldBio;
                        delete
                        ${"$"}oldBio;
                    """.trimIndent()).resolve()
                } catch (e: Exception) {
                    logger.warn("Could not delete old bio (may not exist): ${e.message}")
                }
                tx.query("""
                    match
                    ${"$"}user isa individual, has id "$userId";
                    insert
                    ${"$"}user has bio "$bio";
                """.trimIndent()).resolve()
            }
            if (location != null) {
                // Delete old location first (if exists)
                try {
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId", has location ${"$"}oldLocation;
                        delete
                        ${"$"}oldLocation;
                    """.trimIndent()).resolve()
                } catch (e: Exception) {
                    logger.warn("Could not delete old location (may not exist): ${e.message}")
                }
                tx.query("""
                    match
                    ${"$"}user isa individual, has id "$userId";
                    insert
                    ${"$"}user has location "$location";
                """.trimIndent()).resolve()
            }

            // Handle birthday fields
            if (clearBirthday) {
                // Delete all birthday fields
                try {
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId", has birth_year ${"$"}oldYear;
                        delete
                        ${"$"}oldYear;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                try {
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId", has birth_month ${"$"}oldMonth;
                        delete
                        ${"$"}oldMonth;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                try {
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId", has birth_day ${"$"}oldDay;
                        delete
                        ${"$"}oldDay;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
            } else {
                // Update birthday fields if provided
                if (birthYear != null) {
                    // Delete old birth_year first
                    try {
                        tx.query("""
                            match
                            ${"$"}user isa individual, has id "$userId", has birth_year ${"$"}oldYear;
                            delete
                            ${"$"}oldYear;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId";
                        insert
                        ${"$"}user has birth_year $birthYear;
                    """.trimIndent()).resolve()
                }
                if (birthMonth != null) {
                    // Delete old birth_month first
                    try {
                        tx.query("""
                            match
                            ${"$"}user isa individual, has id "$userId", has birth_month ${"$"}oldMonth;
                            delete
                            ${"$"}oldMonth;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId";
                        insert
                        ${"$"}user has birth_month $birthMonth;
                    """.trimIndent()).resolve()
                } else if (clearBirthMonth) {
                    // Clear birth_month only
                    try {
                        tx.query("""
                            match
                            ${"$"}user isa individual, has id "$userId", has birth_month ${"$"}oldMonth;
                            delete
                            ${"$"}oldMonth;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                }
                if (birthDay != null) {
                    // Delete old birth_day first
                    try {
                        tx.query("""
                            match
                            ${"$"}user isa individual, has id "$userId", has birth_day ${"$"}oldDay;
                            delete
                            ${"$"}oldDay;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                    tx.query("""
                        match
                        ${"$"}user isa individual, has id "$userId";
                        insert
                        ${"$"}user has birth_day $birthDay;
                    """.trimIndent()).resolve()
                } else if (clearBirthDay) {
                    // Clear birth_day only
                    try {
                        tx.query("""
                            match
                            ${"$"}user isa individual, has id "$userId", has birth_day ${"$"}oldDay;
                            delete
                            ${"$"}oldDay;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                }
            }
            true
        } ?: false
    }
}
