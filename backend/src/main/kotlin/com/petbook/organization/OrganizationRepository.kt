package com.petbook.organization

import com.petbook.db.TypeDBService
import com.petbook.post.Post
import org.slf4j.LoggerFactory
import java.util.UUID

class OrganizationRepository(private val db: TypeDBService) {

    private val logger = LoggerFactory.getLogger(OrganizationRepository::class.java)

    fun createOrganization(
        name: String,
        orgType: String,
        managerId: String,
        bio: String?,
        location: String?
    ): Organization? {
        val orgId = UUID.randomUUID().toString()

        val success = db.writeTransaction { tx ->
            // Build optional clauses
            val bioClause = if (bio != null) """, has bio "$bio"""" else ""
            val locationClause = if (location != null) """, has location "$location"""" else ""

            // Insert organization
            tx.query("""
                insert
                ${"$"}org isa organization,
                    has id "$orgId",
                    has name "$name",
                    has user_type "$orgType"$bioClause$locationClause;
            """.trimIndent()).resolve()

            // Create management relation
            tx.query("""
                match
                ${"$"}user isa individual, has id "$managerId";
                ${"$"}org isa organization, has id "$orgId";
                insert
                (manager: ${"$"}user, org: ${"$"}org) isa org_management,
                    has role_type "owner";
            """.trimIndent()).resolve()

            true
        }

        return if (success == true) {
            logger.info("Created organization: $name by manager: $managerId")
            Organization(orgId, name, orgType, bio, location)
        } else {
            logger.error("Failed to create organization: $name")
            null
        }
    }

    fun findById(orgId: String): Organization? {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization,
                    has id "$orgId",
                    has name ${"$"}name,
                    has user_type ${"$"}orgType;
                select ${"$"}name, ${"$"}orgType;
            """.trimIndent()).resolve()

            val rows = answer.asConceptRows()
            val iterator = rows.iterator()

            if (iterator.hasNext()) {
                val row = iterator.next()
                val name = row.get("name").get().tryGetString().get()
                val orgType = row.get("orgType").get().tryGetString().get()

                // Get optional fields
                val bio = getOrgAttribute(orgId, "bio")
                val location = getOrgAttribute(orgId, "location")

                // Get establishment date fields
                val establishedYear = getOrgIntAttribute(orgId, "established_year")
                val establishedMonth = getOrgIntAttribute(orgId, "established_month")
                val establishedDay = getOrgIntAttribute(orgId, "established_day")

                Organization(orgId, name, orgType, bio, location, establishedYear, establishedMonth, establishedDay)
            } else {
                null
            }
        }
    }

    private fun getOrgAttribute(orgId: String, attribute: String): String? {
        return db.readTransaction { tx ->
            try {
                val answer = tx.query("""
                    match
                    ${"$"}org isa organization, has id "$orgId", has $attribute ${"$"}val;
                    select ${"$"}val;
                """.trimIndent()).resolve()

                val rows = answer.asConceptRows()
                val iterator = rows.iterator()
                if (iterator.hasNext()) {
                    iterator.next().get("val").get().tryGetString().get()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getOrgIntAttribute(orgId: String, attribute: String): Int? {
        return db.readTransaction { tx ->
            try {
                val answer = tx.query("""
                    match
                    ${"$"}org isa organization, has id "$orgId", has $attribute ${"$"}val;
                    select ${"$"}val;
                """.trimIndent()).resolve()

                val rows = answer.asConceptRows()
                val iterator = rows.iterator()
                if (iterator.hasNext()) {
                    iterator.next().get("val").get().tryGetInteger().get().toInt()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // Helper methods for pet attributes
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
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getPetIntAttribute(petId: String, attribute: String): Int? {
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
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun buildOrgPetSummary(petId: String, name: String, species: String): OrgPetSummary {
        val status = getPetStringAttribute(petId, "pet_status") ?: "owned"
        val breed = getPetStringAttribute(petId, "breed")
        val bio = getPetStringAttribute(petId, "bio")
        val sex = getPetStringAttribute(petId, "sex")
        val birthYear = getPetIntAttribute(petId, "birth_year")
        val birthMonth = getPetIntAttribute(petId, "birth_month")
        val birthDay = getPetIntAttribute(petId, "birth_day")

        return OrgPetSummary(petId, name, species, status, breed, bio, sex, birthYear, birthMonth, birthDay)
    }

    fun getOrganizationsByManager(managerId: String): List<OrganizationWithRole> {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id "$managerId";
                ${"$"}mgmt (manager: ${"$"}user, org: ${"$"}org) isa org_management,
                    has role_type ${"$"}role;
                ${"$"}org has id ${"$"}orgId, has name ${"$"}name, has user_type ${"$"}orgType;
                select ${"$"}orgId, ${"$"}name, ${"$"}orgType, ${"$"}role;
            """.trimIndent()).resolve()

            val orgs = mutableListOf<OrganizationWithRole>()
            for (row in answer.asConceptRows()) {
                val orgId = row.get("orgId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val orgType = row.get("orgType").get().tryGetString().get()
                val role = row.get("role").get().tryGetString().get()

                val org = Organization(orgId, name, orgType, null, null)
                orgs.add(OrganizationWithRole(org, role))
            }
            orgs
        } ?: emptyList()
    }

    fun canManageOrg(userId: String, orgId: String): Boolean {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id "$userId";
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}mgmt (manager: ${"$"}user, org: ${"$"}org) isa org_management;
                select ${"$"}mgmt;
            """.trimIndent()).resolve()

            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun getOrgWithPets(orgId: String): OrganizationWithPets? {
        val org = findById(orgId) ?: return null

        val pets = db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}ownership (owner: ${"$"}org, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                ${"$"}pet has id ${"$"}petId, has name ${"$"}name, has species ${"$"}species;
                select ${"$"}petId, ${"$"}name, ${"$"}species;
            """.trimIndent()).resolve()

            val petList = mutableListOf<OrgPetSummary>()
            for (row in answer.asConceptRows()) {
                val petId = row.get("petId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                petList.add(buildOrgPetSummary(petId, name, species))
            }
            petList
        } ?: emptyList()

        val followerCount = getFollowerCount(orgId)

        return OrganizationWithPets(org, pets, followerCount)
    }

    private fun getFollowerCount(orgId: String): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}follows (follower: ${"$"}user, following: ${"$"}org) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    fun countAll(): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization, has id ${"$"}orgId;
                select ${"$"}orgId;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    fun findAll(offset: Int = 0, limit: Int = 50): List<Organization> {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization,
                    has id ${"$"}orgId,
                    has name ${"$"}name,
                    has user_type ${"$"}orgType;
                select ${"$"}orgId, ${"$"}name, ${"$"}orgType;
            """.trimIndent()).resolve()

            val allOrgs = mutableListOf<Organization>()
            for (row in answer.asConceptRows()) {
                val orgId = row.get("orgId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val orgType = row.get("orgType").get().tryGetString().get()
                allOrgs.add(Organization(orgId, name, orgType, null, null))
            }

            // Sort by name and apply pagination
            allOrgs.sortedBy { it.name.lowercase() }
                .drop(offset)
                .take(limit)
        } ?: emptyList()
    }

    fun searchByName(query: String, limit: Int = 20): List<Organization> {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization,
                    has id ${"$"}orgId,
                    has name ${"$"}name,
                    has user_type ${"$"}orgType;
                ${"$"}name contains "$query";
                select ${"$"}orgId, ${"$"}name, ${"$"}orgType;
            """.trimIndent()).resolve()

            val orgs = mutableListOf<Organization>()
            var count = 0
            for (row in answer.asConceptRows()) {
                if (count >= limit) break
                val orgId = row.get("orgId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val orgType = row.get("orgType").get().tryGetString().get()
                orgs.add(Organization(orgId, name, orgType, null, null))
                count++
            }
            orgs
        } ?: emptyList()
    }

    // Filtered and sorted query methods
    fun countAllFiltered(query: String?, orgType: String?): Int {
        val allOrgs = getAllOrgsInternal()
        return filterOrgs(allOrgs, query, orgType).size
    }

    fun findAllFiltered(
        offset: Int,
        limit: Int,
        query: String?,
        orgType: String?,
        sort: String?
    ): List<Organization> {
        val allOrgs = getAllOrgsInternal()
        val filtered = filterOrgs(allOrgs, query, orgType)
        return sortOrgs(filtered, sort).drop(offset).take(limit)
    }

    fun findAllFilteredUnsorted(query: String?, orgType: String?): List<Organization> {
        val allOrgs = getAllOrgsInternal()
        return filterOrgs(allOrgs, query, orgType)
    }

    private fun getAllOrgsInternal(): List<Organization> {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization,
                    has id ${"$"}orgId,
                    has name ${"$"}name,
                    has user_type ${"$"}orgType;
                select ${"$"}orgId, ${"$"}name, ${"$"}orgType;
            """.trimIndent()).resolve()

            val allOrgs = mutableListOf<Organization>()
            for (row in answer.asConceptRows()) {
                val orgId = row.get("orgId").get().tryGetString().get()
                val name = row.get("name").get().tryGetString().get()
                val orgTypeVal = row.get("orgType").get().tryGetString().get()
                allOrgs.add(Organization(orgId, name, orgTypeVal, null, null))
            }
            allOrgs
        } ?: emptyList()
    }

    private fun filterOrgs(orgs: List<Organization>, query: String?, orgType: String?): List<Organization> {
        return orgs.filter { org ->
            val matchesQuery = query.isNullOrBlank() ||
                org.name.contains(query, ignoreCase = true)
            val matchesOrgType = orgType.isNullOrBlank() ||
                org.orgType.equals(orgType, ignoreCase = true)
            matchesQuery && matchesOrgType
        }
    }

    private fun sortOrgs(orgs: List<Organization>, sort: String?): List<Organization> {
        return when (sort) {
            "name_asc" -> orgs.sortedBy { it.name.lowercase() }
            "name_desc" -> orgs.sortedByDescending { it.name.lowercase() }
            "most_pets" -> orgs // Would need to join with pet counts - keeping default for now
            else -> orgs.sortedBy { it.name.lowercase() }
        }
    }

    fun getOrgFeed(orgId: String): List<Post> {
        return db.readTransaction { tx ->
            val posts = mutableListOf<Post>()

            // Posts authored by the org (not on pet walls)
            val orgPostsAnswer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId", has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}org, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                not { ${"$"}pp (post: ${"$"}post) isa pet_post; };
                select ${"$"}postId, ${"$"}content, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in orgPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(
                    id = postId,
                    content = content,
                    authorId = orgId,
                    authorName = authorName,
                    authorType = "organization",
                    petId = null,
                    petName = null,
                    createdAt = createdAt
                ))
            }

            // Posts on org's pets' walls
            val petPostsAnswer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}ownership (owner: ${"$"}org, pet: ${"$"}owned_pet) isa ownership,
                    has ownership_status "current";
                ${"$"}owned_pet isa pet, has id ${"$"}petId, has name ${"$"}petName;
                ${"$"}pet_post (pet: ${"$"}owned_pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in petPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(
                    id = postId,
                    content = content,
                    authorId = authorId,
                    authorName = authorName,
                    authorType = "individual",
                    petId = petId,
                    petName = petName,
                    createdAt = createdAt
                ))
            }

            // Org's own pet posts
            val orgPetPostsAnswer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId", has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}org, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}pet_post (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                ${"$"}pet has id ${"$"}petId, has name ${"$"}petName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in orgPetPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(
                    id = postId,
                    content = content,
                    authorId = orgId,
                    authorName = authorName,
                    authorType = "organization",
                    petId = petId,
                    petName = petName,
                    createdAt = createdAt
                ))
            }

            posts.distinctBy { it.id }.sortedByDescending { it.createdAt }
        } ?: emptyList()
    }

    fun updateOrganization(
        orgId: String,
        name: String?,
        bio: String?,
        location: String?,
        establishedYear: Int? = null,
        establishedMonth: Int? = null,
        establishedDay: Int? = null,
        clearEstablishedDate: Boolean = false
    ): Boolean {
        return db.writeTransaction { tx ->
            if (name != null) {
                // Delete old name first, then insert new one (TypeDB 3.x syntax: just variable)
                try {
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId", has name ${"$"}oldName;
                        delete
                        ${"$"}oldName;
                    """.trimIndent()).resolve()
                } catch (e: Exception) {
                    logger.warn("Could not delete old name (may not exist): ${e.message}")
                }
                tx.query("""
                    match
                    ${"$"}org isa organization, has id "$orgId";
                    insert
                    ${"$"}org has name "$name";
                """.trimIndent()).resolve()
            }
            if (bio != null) {
                // Delete old bio if exists (TypeDB 3.x syntax)
                try {
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId", has bio ${"$"}oldBio;
                        delete
                        ${"$"}oldBio;
                    """.trimIndent()).resolve()
                } catch (e: Exception) {
                    logger.warn("Could not delete old bio (may not exist): ${e.message}")
                }
                tx.query("""
                    match
                    ${"$"}org isa organization, has id "$orgId";
                    insert
                    ${"$"}org has bio "$bio";
                """.trimIndent()).resolve()
            }
            if (location != null) {
                // Delete old location if exists (TypeDB 3.x syntax)
                try {
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId", has location ${"$"}oldLocation;
                        delete
                        ${"$"}oldLocation;
                    """.trimIndent()).resolve()
                } catch (e: Exception) {
                    logger.warn("Could not delete old location (may not exist): ${e.message}")
                }
                tx.query("""
                    match
                    ${"$"}org isa organization, has id "$orgId";
                    insert
                    ${"$"}org has location "$location";
                """.trimIndent()).resolve()
            }

            // Handle establishment date fields
            if (clearEstablishedDate) {
                // Delete all establishment date fields
                try {
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId", has established_year ${"$"}oldYear;
                        delete
                        ${"$"}oldYear;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                try {
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId", has established_month ${"$"}oldMonth;
                        delete
                        ${"$"}oldMonth;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
                try {
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId", has established_day ${"$"}oldDay;
                        delete
                        ${"$"}oldDay;
                    """.trimIndent()).resolve()
                } catch (e: Exception) { /* May not exist */ }
            } else {
                // Update establishment date fields if provided
                if (establishedYear != null) {
                    try {
                        tx.query("""
                            match
                            ${"$"}org isa organization, has id "$orgId", has established_year ${"$"}oldYear;
                            delete
                            ${"$"}oldYear;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId";
                        insert
                        ${"$"}org has established_year $establishedYear;
                    """.trimIndent()).resolve()
                }
                if (establishedMonth != null) {
                    try {
                        tx.query("""
                            match
                            ${"$"}org isa organization, has id "$orgId", has established_month ${"$"}oldMonth;
                            delete
                            ${"$"}oldMonth;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId";
                        insert
                        ${"$"}org has established_month $establishedMonth;
                    """.trimIndent()).resolve()
                }
                if (establishedDay != null) {
                    try {
                        tx.query("""
                            match
                            ${"$"}org isa organization, has id "$orgId", has established_day ${"$"}oldDay;
                            delete
                            ${"$"}oldDay;
                        """.trimIndent()).resolve()
                    } catch (e: Exception) { /* May not exist */ }
                    tx.query("""
                        match
                        ${"$"}org isa organization, has id "$orgId";
                        insert
                        ${"$"}org has established_day $establishedDay;
                    """.trimIndent()).resolve()
                }
            }
            true
        } ?: false
    }

    fun isOrgOwner(userId: String, orgId: String): Boolean {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id "$userId";
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}mgmt (manager: ${"$"}user, org: ${"$"}org) isa org_management,
                    has role_type "owner";
                select ${"$"}mgmt;
            """.trimIndent()).resolve()

            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun transferOrganization(orgId: String, fromUserId: String, toUserId: String): Boolean {
        return db.writeTransaction { tx ->
            // Delete the old owner's management relation
            tx.query("""
                match
                ${"$"}user isa individual, has id "$fromUserId";
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}mgmt (manager: ${"$"}user, org: ${"$"}org) isa org_management,
                    has role_type "owner";
                delete
                ${"$"}mgmt isa org_management;
            """.trimIndent()).resolve()

            // Create new owner's management relation
            tx.query("""
                match
                ${"$"}user isa individual, has id "$toUserId";
                ${"$"}org isa organization, has id "$orgId";
                insert
                (manager: ${"$"}user, org: ${"$"}org) isa org_management,
                    has role_type "owner";
            """.trimIndent()).resolve()

            logger.info("Transferred organization $orgId from $fromUserId to $toUserId")
            true
        } ?: false
    }
}
