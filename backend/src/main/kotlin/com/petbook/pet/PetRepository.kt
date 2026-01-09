package com.petbook.pet

import com.petbook.db.TypeDBService
import org.slf4j.LoggerFactory
import java.util.UUID

class PetRepository(private val db: TypeDBService) {

    private val logger = LoggerFactory.getLogger(PetRepository::class.java)

    fun createPet(
        name: String,
        species: String,
        breed: String?,
        bio: String?,
        sex: String?,
        birthYear: Int?,
        birthMonth: Int?,
        birthDay: Int?,
        petStatus: String?,
        ownerId: String,
        ownerIsOrg: Boolean = false
    ): Pet? {
        val petId = UUID.randomUUID().toString()
        val startDate = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
            .withNano(0)  // Truncate to seconds to avoid TypeDB precision issues
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val success = db.writeTransaction { tx ->
            // Build optional attribute clauses
            val breedClause = if (breed != null) """, has breed "$breed"""" else ""
            val bioClause = if (bio != null) """, has bio "$bio"""" else ""
            val sexClause = if (sex != null) """, has sex "$sex"""" else ""
            val birthYearClause = if (birthYear != null) ", has birth_year $birthYear" else ""
            val birthMonthClause = if (birthMonth != null) ", has birth_month $birthMonth" else ""
            val birthDayClause = if (birthDay != null) ", has birth_day $birthDay" else ""
            val statusValue = petStatus ?: "owned"

            tx.query("""
                insert
                ${"$"}pet isa pet,
                    has id "$petId",
                    has name "$name",
                    has species "$species",
                    has pet_status "$statusValue"$breedClause$bioClause$sexClause$birthYearClause$birthMonthClause$birthDayClause;
            """.trimIndent()).resolve()

            // Create ownership relation - match either individual or organization
            val ownerType = if (ownerIsOrg) "organization" else "individual"
            tx.query("""
                match
                ${"$"}user isa $ownerType, has id "$ownerId";
                ${"$"}pet isa pet, has id "$petId";
                insert
                (owner: ${"$"}user, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current",
                    has start_date $startDate;
            """.trimIndent()).resolve()

            true
        }

        return if (success == true) {
            logger.info("Created pet: $name for owner: $ownerId (org: $ownerIsOrg)")
            Pet(petId, name, species, breed, bio, petStatus ?: "owned", sex, birthYear, birthMonth, birthDay, null)
        } else {
            logger.error("Failed to create pet: $name")
            null
        }
    }

    // Helper to get an optional string attribute for a pet
    private fun getPetStringAttribute(petId: String, attribute: String): String? {
        return db.readTransaction { tx ->
            try {
                val answer = tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId", has $attribute ${"$"}val;
                    select ${"$"}val;
                """.trimIndent()).resolve()
                val rows = answer.asConceptRows()
                val iterator = rows.iterator()
                if (iterator.hasNext()) {
                    iterator.next().get("val").get().tryGetString().get()
                } else null
            } catch (e: Exception) { null }
        }
    }

    // Helper to get an optional long/int attribute for a pet
    private fun getPetLongAttribute(petId: String, attribute: String): Int? {
        return db.readTransaction { tx ->
            try {
                val answer = tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId", has $attribute ${"$"}val;
                    select ${"$"}val;
                """.trimIndent()).resolve()
                val rows = answer.asConceptRows()
                val iterator = rows.iterator()
                if (iterator.hasNext()) {
                    iterator.next().get("val").get().tryGetInteger().get().toInt()
                } else null
            } catch (e: Exception) { null }
        }
    }

    // Build full Pet object with all attributes
    private fun buildFullPet(petId: String, name: String, species: String): Pet {
        val breed = getPetStringAttribute(petId, "breed")
        val bio = getPetStringAttribute(petId, "bio")
        val petStatus = getPetStringAttribute(petId, "pet_status") ?: "owned"
        val sex = getPetStringAttribute(petId, "sex")
        val birthYear = getPetLongAttribute(petId, "birth_year")
        val birthMonth = getPetLongAttribute(petId, "birth_month")
        val birthDay = getPetLongAttribute(petId, "birth_day")

        return Pet(petId, name, species, breed, bio, petStatus, sex, birthYear, birthMonth, birthDay, null)
    }

    fun findById(petId: String): PetWithOwner? {
        return db.readTransaction { tx ->
            // Try individual owner first
            var answer = tx.query("""
                match
                ${"$"}pet isa pet,
                    has id "$petId",
                    has name ${"$"}name,
                    has species ${"$"}species;
                ${"$"}ownership isa ownership, links (owner: ${"$"}user, pet: ${"$"}pet),
                    has ownership_status "current";
                ${"$"}user isa individual, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName;
            """.trimIndent()).resolve()

            var rows = answer.asConceptRows()
            var iterator = rows.iterator()

            if (iterator.hasNext()) {
                val row = iterator.next()
                val name = row.get("name").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()

                val pet = buildFullPet(petId, name, species)
                return@readTransaction PetWithOwner(pet, ownerId, ownerName, "individual", "current")
            }

            // Try organization owner
            answer = tx.query("""
                match
                ${"$"}pet isa pet,
                    has id "$petId",
                    has name ${"$"}name,
                    has species ${"$"}species;
                ${"$"}ownership isa ownership, links (owner: ${"$"}org, pet: ${"$"}pet),
                    has ownership_status "current";
                ${"$"}org isa organization, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName;
            """.trimIndent()).resolve()

            rows = answer.asConceptRows()
            iterator = rows.iterator()

            if (iterator.hasNext()) {
                val row = iterator.next()
                val name = row.get("name").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()

                val pet = buildFullPet(petId, name, species)
                return@readTransaction PetWithOwner(pet, ownerId, ownerName, "organization", "current")
            }

            null
        }
    }

    fun findByOwnerId(ownerId: String): List<Pet> {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id "$ownerId";
                ${"$"}ownership (owner: ${"$"}user, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                ${"$"}pet has id ${"$"}petId, has name ${"$"}name, has species ${"$"}species;
                select ${"$"}petId, ${"$"}name, ${"$"}species;
            """.trimIndent()).resolve()

            val pets = mutableListOf<Pet>()
            val rows = answer.asConceptRows()

            for (row in rows) {
                val petId = row.get("petId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                pets.add(Pet(petId, name, species, null, null, "owned", null))
            }

            pets
        } ?: emptyList()
    }

    fun countAll(): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}pet isa pet, has id ${"$"}petId;
                ${"$"}ownership isa ownership, links (pet: ${"$"}pet),
                    has ownership_status "current";
                select ${"$"}petId;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    fun countByOwnerId(ownerId: String): Int {
        return db.readTransaction { tx ->
            // Count pets owned by individual
            val indivAnswer = tx.query("""
                match
                ${"$"}user isa individual, has id "$ownerId";
                ${"$"}pet isa pet;
                ${"$"}ownership isa ownership, links (owner: ${"$"}user, pet: ${"$"}pet),
                    has ownership_status "current";
                select ${"$"}pet;
            """.trimIndent()).resolve()

            var count = 0
            for (row in indivAnswer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    fun countByOrgId(orgId: String): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}pet isa pet;
                ${"$"}ownership isa ownership, links (owner: ${"$"}org, pet: ${"$"}pet),
                    has ownership_status "current";
                select ${"$"}pet;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    // Batch method to count pets for multiple user IDs at once - eliminates N+1 queries
    fun countPetsByOwnerIds(ownerIds: List<String>): Map<String, Int> {
        if (ownerIds.isEmpty()) return emptyMap()

        return db.readTransaction { tx ->
            val counts = mutableMapOf<String, Int>()
            // Initialize all to 0
            ownerIds.forEach { counts[it] = 0 }

            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id ${"$"}userId;
                ${"$"}pet isa pet;
                ${"$"}ownership isa ownership, links (owner: ${"$"}user, pet: ${"$"}pet),
                    has ownership_status "current";
                select ${"$"}userId;
            """.trimIndent()).resolve()

            for (row in answer.asConceptRows()) {
                val userId = row.get("userId").get().tryGetString().get()
                if (userId in counts) {
                    counts[userId] = counts[userId]!! + 1
                }
            }
            counts
        } ?: emptyMap()
    }

    // Batch method to count pets for multiple organization IDs at once - eliminates N+1 queries
    fun countPetsByOrgIds(orgIds: List<String>): Map<String, Int> {
        if (orgIds.isEmpty()) return emptyMap()

        return db.readTransaction { tx ->
            val counts = mutableMapOf<String, Int>()
            // Initialize all to 0
            orgIds.forEach { counts[it] = 0 }

            val answer = tx.query("""
                match
                ${"$"}org isa organization, has id ${"$"}orgId;
                ${"$"}pet isa pet;
                ${"$"}ownership isa ownership, links (owner: ${"$"}org, pet: ${"$"}pet),
                    has ownership_status "current";
                select ${"$"}orgId;
            """.trimIndent()).resolve()

            for (row in answer.asConceptRows()) {
                val orgId = row.get("orgId").get().tryGetString().get()
                if (orgId in counts) {
                    counts[orgId] = counts[orgId]!! + 1
                }
            }
            counts
        } ?: emptyMap()
    }

    fun findAll(offset: Int = 0, limit: Int = 50): List<PetWithOwner> {
        return db.readTransaction { tx ->
            val allPets = mutableListOf<PetWithOwner>()

            // Get pets owned by individuals
            val indivAnswer = tx.query("""
                match
                ${"$"}pet isa pet,
                    has id ${"$"}petId,
                    has name ${"$"}name,
                    has species ${"$"}species;
                ${"$"}ownership isa ownership, links (owner: ${"$"}user, pet: ${"$"}pet),
                    has ownership_status ${"$"}ownershipStatus;
                ${"$"}user isa individual, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}petId, ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName, ${"$"}ownershipStatus;
            """.trimIndent()).resolve()

            for (row in indivAnswer.asConceptRows()) {
                val petId = row.get("petId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()
                val ownershipStatus = row.get("ownershipStatus").get().tryGetString().get()

                val pet = Pet(petId, name, species, null, null, "owned", null)
                allPets.add(PetWithOwner(pet, ownerId, ownerName, "individual", ownershipStatus))
            }

            // Get pets owned by organizations
            val orgAnswer = tx.query("""
                match
                ${"$"}pet isa pet,
                    has id ${"$"}petId,
                    has name ${"$"}name,
                    has species ${"$"}species;
                ${"$"}ownership isa ownership, links (owner: ${"$"}org, pet: ${"$"}pet),
                    has ownership_status ${"$"}ownershipStatus;
                ${"$"}org isa organization, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}petId, ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName, ${"$"}ownershipStatus;
            """.trimIndent()).resolve()

            for (row in orgAnswer.asConceptRows()) {
                val petId = row.get("petId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()
                val ownershipStatus = row.get("ownershipStatus").get().tryGetString().get()

                val pet = Pet(petId, name, species, null, null, "owned", null)
                allPets.add(PetWithOwner(pet, ownerId, ownerName, "organization", ownershipStatus))
            }

            // Sort by name and apply pagination
            allPets.sortedBy { it.pet.name.lowercase() }
                .drop(offset)
                .take(limit)
        } ?: emptyList()
    }

    fun searchByName(query: String, limit: Int = 20): List<PetWithOwner> {
        return db.readTransaction { tx ->
            val pets = mutableListOf<PetWithOwner>()

            // Search pets owned by individuals
            val indivAnswer = tx.query("""
                match
                ${"$"}pet isa pet,
                    has id ${"$"}petId,
                    has name ${"$"}name,
                    has species ${"$"}species;
                ${"$"}name contains "$query";
                ${"$"}ownership isa ownership, links (owner: ${"$"}user, pet: ${"$"}pet),
                    has ownership_status ${"$"}ownershipStatus;
                ${"$"}user isa individual, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}petId, ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName, ${"$"}ownershipStatus;
            """.trimIndent()).resolve()

            for (row in indivAnswer.asConceptRows()) {
                if (pets.size >= limit) break
                val petId = row.get("petId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()
                val ownershipStatus = row.get("ownershipStatus").get().tryGetString().get()

                val pet = Pet(petId, name, species, null, null, "owned", null)
                pets.add(PetWithOwner(pet, ownerId, ownerName, "individual", ownershipStatus))
            }

            // Search pets owned by organizations
            if (pets.size < limit) {
                val orgAnswer = tx.query("""
                    match
                    ${"$"}pet isa pet,
                        has id ${"$"}petId,
                        has name ${"$"}name,
                        has species ${"$"}species;
                    ${"$"}name contains "$query";
                    ${"$"}ownership isa ownership, links (owner: ${"$"}org, pet: ${"$"}pet),
                        has ownership_status ${"$"}ownershipStatus;
                    ${"$"}org isa organization, has id ${"$"}ownerId, has name ${"$"}ownerName;
                    select ${"$"}petId, ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName, ${"$"}ownershipStatus;
                """.trimIndent()).resolve()

                for (row in orgAnswer.asConceptRows()) {
                    if (pets.size >= limit) break
                    val petId = row.get("petId").get().tryGetString().get()
                    val name = row.get("name").get().tryGetString().get()
                    val species = row.get("species").get().tryGetString().get()
                    val ownerId = row.get("ownerId").get().tryGetString().get()
                    val ownerName = row.get("ownerName").get().tryGetString().get()
                    val ownershipStatus = row.get("ownershipStatus").get().tryGetString().get()

                    val pet = Pet(petId, name, species, null, null, "owned", null)
                    pets.add(PetWithOwner(pet, ownerId, ownerName, "organization", ownershipStatus))
                }
            }

            pets
        } ?: emptyList()
    }

    // Filtered and sorted query methods
    fun countAllFiltered(query: String?, species: String?, ownerType: String?, orgType: String?): Int {
        val allPets = getAllPetsInternal()
        return filterPets(allPets, query, species, ownerType, orgType).size
    }

    fun findAllFiltered(
        offset: Int,
        limit: Int,
        query: String?,
        species: String?,
        ownerType: String?,
        orgType: String?,
        sort: String?
    ): List<PetWithOwner> {
        val allPets = getAllPetsInternal()
        val filtered = filterPets(allPets, query, species, ownerType, orgType)
        val sorted = sortPets(filtered, sort)
        return sorted.drop(offset).take(limit)
    }

    private fun getAllPetsInternal(): List<PetWithOwnerWithOrgType> {
        return db.readTransaction { tx ->
            val allPets = mutableListOf<PetWithOwnerWithOrgType>()

            // Get pets owned by individuals
            val indivAnswer = tx.query("""
                match
                ${"$"}pet isa pet,
                    has id ${"$"}petId,
                    has name ${"$"}name,
                    has species ${"$"}species;
                ${"$"}ownership isa ownership, links (owner: ${"$"}user, pet: ${"$"}pet),
                    has ownership_status ${"$"}ownershipStatus;
                ${"$"}user isa individual, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}petId, ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName, ${"$"}ownershipStatus;
            """.trimIndent()).resolve()

            for (row in indivAnswer.asConceptRows()) {
                val petId = row.get("petId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val speciesVal = row.get("species").get().tryGetString().get()
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()
                val ownershipStatus = row.get("ownershipStatus").get().tryGetString().get()

                val pet = Pet(petId, name, speciesVal, null, null, "owned", null)
                allPets.add(PetWithOwnerWithOrgType(
                    PetWithOwner(pet, ownerId, ownerName, "individual", ownershipStatus),
                    null
                ))
            }

            // Get pets owned by organizations (including org type)
            val orgAnswer = tx.query("""
                match
                ${"$"}pet isa pet,
                    has id ${"$"}petId,
                    has name ${"$"}name,
                    has species ${"$"}species;
                ${"$"}ownership isa ownership, links (owner: ${"$"}org, pet: ${"$"}pet),
                    has ownership_status ${"$"}ownershipStatus;
                ${"$"}org isa organization, has id ${"$"}ownerId, has name ${"$"}ownerName, has user_type ${"$"}orgType;
                select ${"$"}petId, ${"$"}name, ${"$"}species, ${"$"}ownerId, ${"$"}ownerName, ${"$"}ownershipStatus, ${"$"}orgType;
            """.trimIndent()).resolve()

            for (row in orgAnswer.asConceptRows()) {
                val petId = row.get("petId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val speciesVal = row.get("species").get().tryGetString().get()
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()
                val ownershipStatus = row.get("ownershipStatus").get().tryGetString().get()
                val orgTypeVal = row.get("orgType").get().tryGetString().get()

                val pet = Pet(petId, name, speciesVal, null, null, "owned", null)
                allPets.add(PetWithOwnerWithOrgType(
                    PetWithOwner(pet, ownerId, ownerName, "organization", ownershipStatus),
                    orgTypeVal
                ))
            }

            allPets
        } ?: emptyList()
    }

    private fun filterPets(
        pets: List<PetWithOwnerWithOrgType>,
        query: String?,
        species: String?,
        ownerType: String?,
        orgType: String?
    ): List<PetWithOwnerWithOrgType> {
        return pets.filter { petWithOrgType ->
            val pet = petWithOrgType.petWithOwner

            // Query filter (name contains)
            val matchesQuery = query.isNullOrBlank() ||
                pet.pet.name.contains(query, ignoreCase = true)

            // Species filter
            val matchesSpecies = species.isNullOrBlank() ||
                pet.pet.species.equals(species, ignoreCase = true)

            // Owner type filter
            val matchesOwnerType = ownerType.isNullOrBlank() ||
                pet.ownerType.equals(ownerType, ignoreCase = true)

            // Org type filter (only applies if owner is organization)
            val matchesOrgType = orgType.isNullOrBlank() ||
                (pet.ownerType == "organization" && petWithOrgType.orgType?.equals(orgType, ignoreCase = true) == true)

            matchesQuery && matchesSpecies && matchesOwnerType && matchesOrgType
        }
    }

    private fun sortPets(pets: List<PetWithOwnerWithOrgType>, sort: String?): List<PetWithOwner> {
        val sorted = when (sort) {
            "name_asc" -> pets.sortedBy { it.petWithOwner.pet.name.lowercase() }
            "name_desc" -> pets.sortedByDescending { it.petWithOwner.pet.name.lowercase() }
            "recent" -> pets.reversed() // Assuming newer pets are at end
            else -> pets.sortedBy { it.petWithOwner.pet.name.lowercase() }
        }
        return sorted.map { it.petWithOwner }
    }

    // Helper class for internal filtering
    private data class PetWithOwnerWithOrgType(
        val petWithOwner: PetWithOwner,
        val orgType: String?
    )

    fun isOwner(petId: String, userId: String): Boolean {
        return db.readTransaction { tx ->
            // Check individual ownership
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id "$userId";
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}ownership (owner: ${"$"}user, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                select ${"$"}ownership;
            """.trimIndent()).resolve()

            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun isOrgOwner(petId: String, orgId: String): Boolean {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}ownership (owner: ${"$"}org, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                select ${"$"}ownership;
            """.trimIndent()).resolve()

            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun getCurrentOwnerId(petId: String): Pair<String, Boolean>? {
        // Returns (ownerId, isOrg)
        return db.readTransaction { tx ->
            // Try individual
            var answer = tx.query("""
                match
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}ownership (owner: ${"$"}user, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                ${"$"}user isa individual, has id ${"$"}ownerId;
                select ${"$"}ownerId;
            """.trimIndent()).resolve()

            var iterator = answer.asConceptRows().iterator()
            if (iterator.hasNext()) {
                val ownerId = iterator.next().get("ownerId").get().tryGetString().get()
                return@readTransaction Pair(ownerId, false)
            }

            // Try organization
            answer = tx.query("""
                match
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}ownership (owner: ${"$"}org, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                ${"$"}org isa organization, has id ${"$"}ownerId;
                select ${"$"}ownerId;
            """.trimIndent()).resolve()

            iterator = answer.asConceptRows().iterator()
            if (iterator.hasNext()) {
                val ownerId = iterator.next().get("ownerId").get().tryGetString().get()
                return@readTransaction Pair(ownerId, true)
            }

            null
        }
    }

    fun transferPet(petId: String, toOwnerId: String, toOwnerIsOrg: Boolean, reason: String): Boolean {
        val now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
            .withNano(0)  // Truncate to seconds to avoid TypeDB precision issues
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        // Get current owner info first
        val currentOwner = getCurrentOwnerId(petId)
        if (currentOwner == null) {
            logger.error("Cannot transfer pet $petId: no current owner found")
            return false
        }
        val (currentOwnerId, currentOwnerIsOrg) = currentOwner
        val currentOwnerType = if (currentOwnerIsOrg) "organization" else "individual"

        // Delete the current ownership relation entirely and recreate it with past status
        val deleteQuery = if (currentOwnerIsOrg) """
            match
            ${"$"}pet isa pet, has id "$petId";
            ${"$"}owner isa organization, has id "$currentOwnerId";
            ${"$"}rel isa ownership, links (owner: ${"$"}owner, pet: ${"$"}pet);
            delete
            ${"$"}rel;
        """.trimIndent() else """
            match
            ${"$"}pet isa pet, has id "$petId";
            ${"$"}owner isa individual, has id "$currentOwnerId";
            ${"$"}rel isa ownership, links (owner: ${"$"}owner, pet: ${"$"}pet);
            delete
            ${"$"}rel;
        """.trimIndent()

        val insertQuery = if (currentOwnerIsOrg) """
            match
            ${"$"}pet isa pet, has id "$petId";
            ${"$"}owner isa organization, has id "$currentOwnerId";
            insert
            (owner: ${"$"}owner, pet: ${"$"}pet) isa ownership,
                has ownership_status "past",
                has end_date $now,
                has transfer_reason "$reason";
        """.trimIndent() else """
            match
            ${"$"}pet isa pet, has id "$petId";
            ${"$"}owner isa individual, has id "$currentOwnerId";
            insert
            (owner: ${"$"}owner, pet: ${"$"}pet) isa ownership,
                has ownership_status "past",
                has end_date $now,
                has transfer_reason "$reason";
        """.trimIndent()

        val deleteAndMarkPastResult = db.writeTransaction { tx ->
            tx.query(deleteQuery).resolve()
            tx.query(insertQuery).resolve()
            logger.info("Deleted old ownership and created past record for pet: $petId")
            true
        }

        if (deleteAndMarkPastResult != true) {
            logger.error("Failed to update ownership to past for pet: $petId")
            return false
        }

        logger.info("Creating new ownership for pet $petId to $toOwnerId (org: $toOwnerIsOrg)")

        // Create new ownership
        val newOwnershipQuery = if (toOwnerIsOrg) """
            match
            ${"$"}newOwner isa organization, has id "$toOwnerId";
            ${"$"}pet isa pet, has id "$petId";
            insert
            (owner: ${"$"}newOwner, pet: ${"$"}pet) isa ownership,
                has ownership_status "current",
                has start_date $now,
                has transfer_reason "$reason";
        """.trimIndent() else """
            match
            ${"$"}newOwner isa individual, has id "$toOwnerId";
            ${"$"}pet isa pet, has id "$petId";
            insert
            (owner: ${"$"}newOwner, pet: ${"$"}pet) isa ownership,
                has ownership_status "current",
                has start_date $now,
                has transfer_reason "$reason";
        """.trimIndent()

        val result = db.writeTransaction { tx ->
            tx.query(newOwnershipQuery).resolve()
            logger.info("Created new current ownership for pet: $petId")
            true
        } ?: false

        if (!result) {
            logger.error("Failed to create new current ownership for pet: $petId")
        }
        return result
    }

    fun getOwnershipHistory(petId: String): List<OwnershipRecord> {
        return db.readTransaction { tx ->
            val history = mutableListOf<OwnershipRecord>()

            // Get individual ownerships
            val indivAnswer = tx.query("""
                match
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}ownership (owner: ${"$"}user, pet: ${"$"}pet) isa ownership,
                    has ownership_status ${"$"}status;
                ${"$"}user isa individual, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}ownerId, ${"$"}ownerName, ${"$"}status, ${"$"}ownership;
            """.trimIndent()).resolve()

            for (row in indivAnswer.asConceptRows()) {
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()
                val status = row.get("status").get().tryGetString().get()

                // Get dates and reason from ownership
                val dates = getOwnershipDates(petId, ownerId, false)

                history.add(OwnershipRecord(
                    ownerId = ownerId,
                    ownerName = ownerName,
                    ownerType = "individual",
                    status = status,
                    startDate = dates?.first,
                    endDate = dates?.second,
                    transferReason = dates?.third
                ))
            }

            // Get organization ownerships
            val orgAnswer = tx.query("""
                match
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}ownership (owner: ${"$"}org, pet: ${"$"}pet) isa ownership,
                    has ownership_status ${"$"}status;
                ${"$"}org isa organization, has id ${"$"}ownerId, has name ${"$"}ownerName;
                select ${"$"}ownerId, ${"$"}ownerName, ${"$"}status, ${"$"}ownership;
            """.trimIndent()).resolve()

            for (row in orgAnswer.asConceptRows()) {
                val ownerId = row.get("ownerId").get().tryGetString().get()
                val ownerName = row.get("ownerName").get().tryGetString().get()
                val status = row.get("status").get().tryGetString().get()

                val dates = getOwnershipDates(petId, ownerId, true)

                history.add(OwnershipRecord(
                    ownerId = ownerId,
                    ownerName = ownerName,
                    ownerType = "organization",
                    status = status,
                    startDate = dates?.first,
                    endDate = dates?.second,
                    transferReason = dates?.third
                ))
            }

            // Sort by start date descending (most recent first), current owner first
            history.sortedWith(compareBy(
                { if (it.status == "current") 0 else 1 },
                { -(it.startDate?.let { d -> try { java.time.LocalDateTime.parse(d).toEpochSecond(java.time.ZoneOffset.UTC) } catch (e: Exception) { 0L } } ?: 0L) }
            ))
        } ?: emptyList()
    }

    private fun getOwnershipDates(petId: String, ownerId: String, isOrg: Boolean): Triple<String?, String?, String?>? {
        return db.readTransaction { tx ->
            val ownerType = if (isOrg) "organization" else "individual"
            var startDate: String? = null
            var endDate: String? = null
            var reason: String? = null

            // Get start_date
            try {
                val startAnswer = tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    ${"$"}owner isa $ownerType, has id "$ownerId";
                    ${"$"}ownership (owner: ${"$"}owner, pet: ${"$"}pet) isa ownership,
                        has start_date ${"$"}startDate;
                    select ${"$"}startDate;
                """.trimIndent()).resolve()
                val iter = startAnswer.asConceptRows().iterator()
                if (iter.hasNext()) {
                    startDate = iter.next().get("startDate").get().tryGetDatetime().get().toString()
                }
            } catch (e: Exception) { }

            // Get end_date
            try {
                val endAnswer = tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    ${"$"}owner isa $ownerType, has id "$ownerId";
                    ${"$"}ownership (owner: ${"$"}owner, pet: ${"$"}pet) isa ownership,
                        has end_date ${"$"}endDate;
                    select ${"$"}endDate;
                """.trimIndent()).resolve()
                val iter = endAnswer.asConceptRows().iterator()
                if (iter.hasNext()) {
                    endDate = iter.next().get("endDate").get().tryGetDatetime().get().toString()
                }
            } catch (e: Exception) { }

            // Get transfer_reason
            try {
                val reasonAnswer = tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    ${"$"}owner isa $ownerType, has id "$ownerId";
                    ${"$"}ownership (owner: ${"$"}owner, pet: ${"$"}pet) isa ownership,
                        has transfer_reason ${"$"}reason;
                    select ${"$"}reason;
                """.trimIndent()).resolve()
                val iter = reasonAnswer.asConceptRows().iterator()
                if (iter.hasNext()) {
                    reason = iter.next().get("reason").get().tryGetString().get()
                }
            } catch (e: Exception) { }

            Triple(startDate, endDate, reason)
        }
    }

    fun updatePet(
        petId: String,
        name: String?,
        species: String?,
        breed: String?,
        bio: String?,
        petStatus: String?,
        sex: String? = null,
        birthYear: Int? = null,
        birthMonth: Int? = null,
        birthDay: Int? = null
    ): Boolean {
        return db.writeTransaction { tx ->
            if (name != null) {
                // Delete old name first, then insert new one
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId", has name ${"$"}oldName;
                    delete
                    ${"$"}pet has ${"$"}oldName;
                """.trimIndent()).resolve()
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has name "$name";
                """.trimIndent()).resolve()
            }
            if (species != null) {
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId", has species ${"$"}oldSpecies;
                    delete
                    ${"$"}pet has ${"$"}oldSpecies;
                """.trimIndent()).resolve()
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has species "$species";
                """.trimIndent()).resolve()
            }
            if (breed != null) {
                // Delete old breed if exists
                try {
                    tx.query("""
                        match
                        ${"$"}pet isa pet, has id "$petId", has breed ${"$"}oldBreed;
                        delete
                        ${"$"}oldBreed;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has breed "$breed";
                """.trimIndent()).resolve()
            }
            if (bio != null) {
                try {
                    tx.query("""
                        match
                        ${"$"}pet isa pet, has id "$petId", has bio ${"$"}oldBio;
                        delete
                        ${"$"}oldBio;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has bio "$bio";
                """.trimIndent()).resolve()
            }
            if (petStatus != null) {
                try {
                    tx.query("""
                        match
                        ${"$"}pet isa pet, has id "$petId", has pet_status ${"$"}oldStatus;
                        delete
                        ${"$"}oldStatus;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has pet_status "$petStatus";
                """.trimIndent()).resolve()
            }
            if (sex != null) {
                try {
                    tx.query("""
                        match
                        ${"$"}pet isa pet, has id "$petId", has sex ${"$"}oldSex;
                        delete
                        ${"$"}oldSex;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has sex "$sex";
                """.trimIndent()).resolve()
            }
            if (birthYear != null) {
                try {
                    tx.query("""
                        match
                        ${"$"}pet isa pet, has id "$petId", has birth_year ${"$"}oldYear;
                        delete
                        ${"$"}oldYear;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has birth_year $birthYear;
                """.trimIndent()).resolve()
            }
            if (birthMonth != null) {
                try {
                    tx.query("""
                        match
                        ${"$"}pet isa pet, has id "$petId", has birth_month ${"$"}oldMonth;
                        delete
                        ${"$"}oldMonth;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has birth_month $birthMonth;
                """.trimIndent()).resolve()
            }
            if (birthDay != null) {
                try {
                    tx.query("""
                        match
                        ${"$"}pet isa pet, has id "$petId", has birth_day ${"$"}oldDay;
                        delete
                        ${"$"}oldDay;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    insert
                    ${"$"}pet has birth_day $birthDay;
                """.trimIndent()).resolve()
            }
            true
        } ?: false
    }

    fun deletePet(petId: String): Boolean {
        // Delete pet_post relations in separate transaction (optional)
        try {
            db.writeTransaction { tx ->
                tx.query("""
                    match
                    ${"$"}the_pet isa pet, has id "$petId";
                    ${"$"}rel isa pet_post, links (pet: ${"$"}the_pet);
                    delete
                    ${"$"}rel;
                """.trimIndent()).resolve()
                true
            }
        } catch (e: Exception) {
            // No pet_post relations exist, continue
        }

        // Delete follow relations in separate transaction (optional)
        try {
            db.writeTransaction { tx ->
                tx.query("""
                    match
                    ${"$"}the_pet isa pet, has id "$petId";
                    ${"$"}rel isa follows, links (following: ${"$"}the_pet);
                    delete
                    ${"$"}rel;
                """.trimIndent()).resolve()
                true
            }
        } catch (e: Exception) {
            // No follow relations exist, continue
        }

        // Delete ownership relations and pet
        return db.writeTransaction { tx ->
            // Delete ownership relations
            tx.query("""
                match
                ${"$"}the_pet isa pet, has id "$petId";
                ${"$"}rel isa ownership, links (pet: ${"$"}the_pet);
                delete
                ${"$"}rel;
            """.trimIndent()).resolve()

            // Delete pet
            tx.query("""
                match
                ${"$"}the_pet isa pet, has id "$petId";
                delete
                ${"$"}the_pet;
            """.trimIndent()).resolve()
            true
        } ?: false
    }
}
