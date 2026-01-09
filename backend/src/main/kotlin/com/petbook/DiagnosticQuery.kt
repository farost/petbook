package com.petbook

import com.petbook.db.TypeDBConfig
import com.petbook.db.TypeDBService
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DiagnosticQuery")

fun main() {
    val config = TypeDBConfig.fromEnvironment()
    val typedbService = TypeDBService(config)

    if (!typedbService.connect()) {
        println("Failed to connect to TypeDB")
        return
    }

    println("Connected to TypeDB successfully")
    println()

    // 1. Check for individuals with multiple emails
    println("=== Check 1: Finding individuals with duplicate emails ===")
    println()

    val duplicateEmails = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}user isa individual, has id ${"$"}id, has email ${"$"}email1, has email ${"$"}email2;
            ${"$"}email1 != ${"$"}email2;
            select ${"$"}id, ${"$"}email1, ${"$"}email2;
        """.trimIndent()).resolve()

        val duplicates = mutableListOf<Triple<String, String, String>>()
        for (row in answer.asConceptRows()) {
            val id = row.get("id").get().tryGetString().get()
            val email1 = row.get("email1").get().tryGetString().get()
            val email2 = row.get("email2").get().tryGetString().get()
            duplicates.add(Triple(id, email1, email2))
        }
        duplicates
    }

    if (duplicateEmails.isNullOrEmpty()) {
        println("No individuals with duplicate emails found.")
    } else {
        println("Found ${duplicateEmails.size} duplicate email entries:")
        val byUser = duplicateEmails.groupBy { it.first }
        for ((userId, entries) in byUser) {
            val allEmails = entries.flatMap { listOf(it.second, it.third) }.toSet()
            println("User ID: $userId")
            println("  Emails: ${allEmails.joinToString(", ")}")
        }
    }

    println()

    // 1b. Check for individuals with multiple names
    println("=== Check 1b: Finding individuals with duplicate names ===")
    println()

    val duplicateNames = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}user isa individual, has id ${"$"}id, has name ${"$"}name1, has name ${"$"}name2;
            ${"$"}name1 != ${"$"}name2;
            select ${"$"}id, ${"$"}name1, ${"$"}name2;
        """.trimIndent()).resolve()

        val duplicates = mutableListOf<Triple<String, String, String>>()
        for (row in answer.asConceptRows()) {
            val id = row.get("id").get().tryGetString().get()
            val name1 = row.get("name1").get().tryGetString().get()
            val name2 = row.get("name2").get().tryGetString().get()
            duplicates.add(Triple(id, name1, name2))
        }
        duplicates
    }

    if (duplicateNames.isNullOrEmpty()) {
        println("No individuals with duplicate names found.")
    } else {
        println("Found ${duplicateNames.size} duplicate name entries:")
        val byUser = duplicateNames.groupBy { it.first }
        for ((userId, entries) in byUser) {
            val allNames = entries.flatMap { listOf(it.second, it.third) }.toSet()
            println("User ID: $userId")
            println("  Names: ${allNames.joinToString(", ")}")
        }
    }

    println()

    // 2. Check for emails shared between different users
    println("=== Check 2: Finding emails shared by multiple users ===")
    println()

    val sharedEmails = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}user1 isa individual, has id ${"$"}id1, has email ${"$"}email;
            ${"$"}user2 isa individual, has id ${"$"}id2, has email ${"$"}email;
            ${"$"}id1 != ${"$"}id2;
            select ${"$"}email, ${"$"}id1, ${"$"}id2;
        """.trimIndent()).resolve()

        val shared = mutableListOf<Triple<String, String, String>>()
        for (row in answer.asConceptRows()) {
            val email = row.get("email").get().tryGetString().get()
            val id1 = row.get("id1").get().tryGetString().get()
            val id2 = row.get("id2").get().tryGetString().get()
            shared.add(Triple(email, id1, id2))
        }
        shared
    }

    if (sharedEmails.isNullOrEmpty()) {
        println("No emails shared by multiple users found.")
    } else {
        println("Found ${sharedEmails.size} shared email entries:")
        val byEmail = sharedEmails.groupBy { it.first }
        for ((email, entries) in byEmail) {
            val allUserIds = entries.flatMap { listOf(it.second, it.third) }.toSet()
            println("Email: $email")
            println("  User IDs: ${allUserIds.joinToString(", ")}")
        }
    }

    println()

    // 3. List all individuals with their emails
    println("=== Check 3: Listing all individuals ===")
    println()

    val allUsers = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}user isa individual, has id ${"$"}id, has email ${"$"}email, has name ${"$"}name;
            select ${"$"}id, ${"$"}email, ${"$"}name;
        """.trimIndent()).resolve()

        val users = mutableListOf<Triple<String, String, String>>()
        for (row in answer.asConceptRows()) {
            val id = row.get("id").get().tryGetString().get()
            val email = row.get("email").get().tryGetString().get()
            val name = row.get("name").get().tryGetString().get()
            users.add(Triple(id, email, name))
        }
        users
    }

    println("Found ${allUsers?.size ?: 0} users total")

    println()

    // 4. Check for any orphaned or problematic email attributes
    println("=== Check 4: Querying email attribute instances directly ===")
    println()

    val emailCount = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}email isa email;
            select ${"$"}email;
        """.trimIndent()).resolve()

        var count = 0
        for (row in answer.asConceptRows()) {
            count++
        }
        count
    }

    println("Total email attribute instances: $emailCount")
    println("Total users: ${allUsers?.size ?: 0}")

    if (emailCount != allUsers?.size) {
        println("WARNING: Mismatch between email count and user count!")
        println("This could indicate orphaned emails or constraint issues.")
    }

    println()
    println("=== Check 5: Count name attributes in database ===")
    println()

    val nameCount = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}name isa name;
            select ${"$"}name;
        """.trimIndent()).resolve()

        var count = 0
        for (row in answer.asConceptRows()) {
            count++
        }
        count
    }
    println("Total name attribute instances: $nameCount")
    println("Total users: ${allUsers?.size ?: 0}")
    println("Difference: ${(nameCount ?: 0) - (allUsers?.size ?: 0)}")

    // Check for names with specific values
    println()
    println("Looking for duplicate 'Diagnostic Test' names or common test names...")

    val testNameCount = typedbService.readTransaction { tx ->
        // Check how many users have the name "Diagnostic Test"
        val answer = tx.query("""
            match
            ${"$"}user isa individual, has name "Diagnostic Test";
            select ${"$"}user;
        """.trimIndent()).resolve()

        var count = 0
        for (row in answer.asConceptRows()) {
            count++
        }
        count
    }
    println("Users with name 'Diagnostic Test': $testNameCount")

    println()
    println("=== Check 6: Finding pets with duplicate species ===")
    println()

    // First check for different species values
    val duplicateSpecies = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}pet isa pet, has id ${"$"}id, has name ${"$"}name, has species ${"$"}species1, has species ${"$"}species2;
            ${"$"}species1 != ${"$"}species2;
            select ${"$"}id, ${"$"}name, ${"$"}species1, ${"$"}species2;
        """.trimIndent()).resolve()

        val duplicates = mutableListOf<Map<String, String>>()
        for (row in answer.asConceptRows()) {
            val id = row.get("id").get().tryGetString().get()
            val name = row.get("name").get().tryGetString().get()
            val species1 = row.get("species1").get().tryGetString().get()
            val species2 = row.get("species2").get().tryGetString().get()
            duplicates.add(mapOf("id" to id, "name" to name, "species1" to species1, "species2" to species2))
        }
        duplicates
    }

    // Check for pets with multiple species count (including same value duplicates)
    println("Checking species count per pet...")
    val petsWithSpeciesCount = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}pet isa pet, has id ${"$"}id, has name ${"$"}name, has species ${"$"}species;
            select ${"$"}id, ${"$"}name, ${"$"}species;
        """.trimIndent()).resolve()

        val petSpecies = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for (row in answer.asConceptRows()) {
            val id = row.get("id").get().tryGetString().get()
            val name = row.get("name").get().tryGetString().get()
            val species = row.get("species").get().tryGetString().get()
            petSpecies.getOrPut(id) { mutableListOf() }.add(Pair(name, species))
        }
        petSpecies
    }

    val petsWithMultipleSpecies = petsWithSpeciesCount?.filter { it.value.size > 1 }

    if (!petsWithMultipleSpecies.isNullOrEmpty()) {
        println("Found ${petsWithMultipleSpecies.size} pets with multiple species attribute instances:")
        for ((petId, speciesList) in petsWithMultipleSpecies) {
            val name = speciesList.first().first
            val allSpecies = speciesList.map { it.second }
            println("  Pet ID: $petId, Name: $name")
            println("    Species values (${allSpecies.size}): ${allSpecies.joinToString(", ")}")
        }
    } else {
        println("No pets with multiple species attribute instances found via grouping.")
    }

    if (duplicateSpecies.isNullOrEmpty()) {
        println("No pets with duplicate species found.")
    } else {
        println("Found ${duplicateSpecies.size} pets with duplicate species:")
        for (pet in duplicateSpecies) {
            println("  Pet ID: ${pet["id"]}, Name: ${pet["name"]}")
            println("    Species: ${pet["species1"]}, ${pet["species2"]}")
        }

        println()
        println("Attempting to fix pets with duplicate species...")

        for (pet in duplicateSpecies) {
            val petId = pet["id"]!!
            val species1 = pet["species1"]!!
            val species2 = pet["species2"]!!
            // Keep the first species, delete the second
            val speciestoDelete = species2

            println("  Fixing pet $petId: keeping '$species1', deleting '$speciestoDelete'")

            typedbService.writeTransaction { tx ->
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId", has species "$speciestoDelete";
                    delete
                    ${"$"}pet has species "$speciestoDelete";
                """.trimIndent()).resolve()
                tx.commit()
            }
        }

        println("Fix completed. Please re-run diagnostic to verify.")
    }

    println()
    println("=== Check 7: Finding ownership relations with duplicate start_date ===")
    println()

    val duplicateStartDates = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}ownership isa ownership, has start_date ${"$"}date1, has start_date ${"$"}date2;
            ${"$"}date1 != ${"$"}date2;
            select ${"$"}ownership, ${"$"}date1, ${"$"}date2;
        """.trimIndent()).resolve()

        val duplicates = mutableListOf<Triple<String, String, String>>()
        for (row in answer.asConceptRows()) {
            val ownership = row.get("ownership").get().toString()
            val date1 = row.get("date1").get().tryGetDatetime().get().toString()
            val date2 = row.get("date2").get().tryGetDatetime().get().toString()
            duplicates.add(Triple(ownership, date1, date2))
        }
        duplicates
    }

    if (duplicateStartDates.isNullOrEmpty()) {
        println("No ownership relations with duplicate start_date found.")
    } else {
        println("Found ${duplicateStartDates.size} ownership relations with duplicate start_date:")
        for ((ownership, date1, date2) in duplicateStartDates) {
            println("  Ownership: $ownership")
            println("    Dates: $date1, $date2")
        }

        println()
        println("Attempting to fix ownership relations with duplicate start_date...")

        // Delete one of the duplicate start_dates from each ownership
        for ((_, date1, _) in duplicateStartDates) {
            println("  Deleting duplicate start_date: $date1")

            typedbService.writeTransaction { tx ->
                // Delete ownership relations with this duplicate pattern
                // We need to delete one of the dates - delete by matching the specific ownership
                tx.query("""
                    match
                    ${"$"}ownership isa ownership, has start_date ${"$"}date1, has start_date ${"$"}date2;
                    ${"$"}date1 != ${"$"}date2;
                    delete
                    ${"$"}ownership has ${"$"}date1;
                """.trimIndent()).resolve()
                tx.commit()
            }
        }

        println("Fix completed. Please re-run diagnostic to verify.")
    }

    // List all ownerships with their attributes for debugging
    println()
    println("=== Listing all ownership relations ===")
    val allOwnerships = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}ownership (owner: ${"$"}owner, pet: ${"$"}pet) isa ownership;
            ${"$"}pet has id ${"$"}petId, has name ${"$"}petName;
            select ${"$"}ownership, ${"$"}petId, ${"$"}petName;
        """.trimIndent()).resolve()

        val ownerships = mutableListOf<Triple<String, String, String>>()
        for (row in answer.asConceptRows()) {
            val ownership = row.get("ownership").get().toString()
            val petId = row.get("petId").get().tryGetString().get()
            val petName = row.get("petName").get().tryGetString().get()
            ownerships.add(Triple(ownership, petId, petName))
        }
        ownerships
    }
    println("Found ${allOwnerships?.size ?: 0} ownership relations:")
    allOwnerships?.forEach { (ownership, petId, petName) ->
        println("  Ownership: $ownership - Pet: $petName ($petId)")
    }

    // Also check for ownership relations with same start_date value listed multiple times
    println()
    println("Checking ownership start_date count (for same-value duplicates)...")

    val ownershipWithMultipleDates = typedbService.readTransaction { tx ->
        val answer = tx.query("""
            match
            ${"$"}ownership isa ownership, has start_date ${"$"}date;
            select ${"$"}ownership, ${"$"}date;
        """.trimIndent()).resolve()

        val datesByOwnership = mutableMapOf<String, MutableList<String>>()
        for (row in answer.asConceptRows()) {
            val ownership = row.get("ownership").get().toString()
            val date = row.get("date").get().tryGetDatetime().get().toString()
            datesByOwnership.getOrPut(ownership) { mutableListOf() }.add(date)
        }
        datesByOwnership.filter { it.value.size > 1 }
    }

    if (ownershipWithMultipleDates.isNullOrEmpty()) {
        println("No ownership relations with multiple start_date instances found.")
    } else {
        println("Found ${ownershipWithMultipleDates.size} ownership relations with multiple start_date instances:")
        for ((ownership, dates) in ownershipWithMultipleDates) {
            println("  Ownership: $ownership")
            println("    Dates (${dates.size}): ${dates.joinToString(", ")}")
        }
    }

    typedbService.close()
}
