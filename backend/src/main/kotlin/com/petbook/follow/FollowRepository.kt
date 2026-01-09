package com.petbook.follow

import com.petbook.db.TypeDBService
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class FollowingUser(
    val id: String,
    val name: String,
    val type: String = "user"
)

@Serializable
data class FollowingPet(
    val id: String,
    val name: String,
    val species: String,
    val type: String = "pet"
)

@Serializable
data class FollowingList(
    val users: List<FollowingUser>,
    val pets: List<FollowingPet>
)

class FollowRepository(private val db: TypeDBService) {

    private val logger = LoggerFactory.getLogger(FollowRepository::class.java)

    fun followUser(followerId: String, targetUserId: String): Boolean {
        if (followerId == targetUserId) return false // Can't follow yourself

        return db.writeTransaction { tx ->
            // Check if already following
            val existsAnswer = tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}target isa individual, has id "$targetUserId";
                ${"$"}follows (follower: ${"$"}follower, following: ${"$"}target) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()

            if (existsAnswer.asConceptRows().iterator().hasNext()) {
                return@writeTransaction true // Already following
            }

            tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}target isa individual, has id "$targetUserId";
                insert
                (follower: ${"$"}follower, following: ${"$"}target) isa follows;
            """.trimIndent()).resolve()

            logger.info("User $followerId now follows user $targetUserId")
            true
        } ?: false
    }

    fun followPet(followerId: String, petId: String): Boolean {
        return db.writeTransaction { tx ->
            // Check if already following
            val existsAnswer = tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}follows (follower: ${"$"}follower, following: ${"$"}pet) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()

            if (existsAnswer.asConceptRows().iterator().hasNext()) {
                return@writeTransaction true // Already following
            }

            tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}pet isa pet, has id "$petId";
                insert
                (follower: ${"$"}follower, following: ${"$"}pet) isa follows;
            """.trimIndent()).resolve()

            logger.info("User $followerId now follows pet $petId")
            true
        } ?: false
    }

    fun unfollowUser(followerId: String, targetUserId: String): Boolean {
        return db.writeTransaction { tx ->
            tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}target isa individual, has id "$targetUserId";
                ${"$"}rel isa follows, links (follower: ${"$"}follower, following: ${"$"}target);
                delete
                ${"$"}rel;
            """.trimIndent()).resolve()

            logger.info("User $followerId unfollowed user $targetUserId")
            true
        } ?: false
    }

    fun unfollowPet(followerId: String, petId: String): Boolean {
        return db.writeTransaction { tx ->
            tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}rel isa follows, links (follower: ${"$"}follower, following: ${"$"}pet);
                delete
                ${"$"}rel;
            """.trimIndent()).resolve()

            logger.info("User $followerId unfollowed pet $petId")
            true
        } ?: false
    }

    fun isFollowingUser(followerId: String, targetUserId: String): Boolean {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}target isa individual, has id "$targetUserId";
                ${"$"}follows (follower: ${"$"}follower, following: ${"$"}target) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()
            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun isFollowingPet(followerId: String, petId: String): Boolean {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}follows (follower: ${"$"}follower, following: ${"$"}pet) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()
            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun getFollowing(userId: String): FollowingList {
        val users = db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}target) isa follows;
                ${"$"}target isa individual, has id ${"$"}targetId, has name ${"$"}targetName;
                select ${"$"}targetId, ${"$"}targetName;
            """.trimIndent()).resolve()

            val list = mutableListOf<FollowingUser>()
            for (row in answer.asConceptRows()) {
                val id = row.get("targetId").get().tryGetString().get()
                val name = row.get("targetName").get().tryGetString().get()
                list.add(FollowingUser(id, name))
            }
            list
        } ?: emptyList()

        val pets = db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}pet) isa follows;
                ${"$"}pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}species;
                select ${"$"}petId, ${"$"}petName, ${"$"}species;
            """.trimIndent()).resolve()

            val list = mutableListOf<FollowingPet>()
            for (row in answer.asConceptRows()) {
                val id = row.get("petId").get().tryGetString().get()
                val name = row.get("petName").get().tryGetString().get()
                val species = row.get("species").get().tryGetString().get()
                list.add(FollowingPet(id, name, species))
            }
            list
        } ?: emptyList()

        return FollowingList(users, pets)
    }

    fun getFollowerCount(userId: String): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}target isa individual, has id "$userId";
                ${"$"}follows (following: ${"$"}target) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    fun getPetFollowerCount(petId: String): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}pet isa pet, has id "$petId";
                ${"$"}follows (following: ${"$"}pet) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }

    // Organization follow methods
    fun followOrganization(followerId: String, orgId: String): Boolean {
        return db.writeTransaction { tx ->
            // Check if already following
            val existsAnswer = tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}follows (follower: ${"$"}follower, following: ${"$"}org) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()

            if (existsAnswer.asConceptRows().iterator().hasNext()) {
                return@writeTransaction true // Already following
            }

            tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}org isa organization, has id "$orgId";
                insert
                (follower: ${"$"}follower, following: ${"$"}org) isa follows;
            """.trimIndent()).resolve()

            logger.info("User $followerId now follows organization $orgId")
            true
        } ?: false
    }

    fun unfollowOrganization(followerId: String, orgId: String): Boolean {
        return db.writeTransaction { tx ->
            tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}follows (follower: ${"$"}follower, following: ${"$"}org) isa follows;
                delete
                ${"$"}follows isa follows;
            """.trimIndent()).resolve()

            logger.info("User $followerId unfollowed organization $orgId")
            true
        } ?: false
    }

    fun isFollowingOrganization(followerId: String, orgId: String): Boolean {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}follower isa individual, has id "$followerId";
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}follows (follower: ${"$"}follower, following: ${"$"}org) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()
            answer.asConceptRows().iterator().hasNext()
        } ?: false
    }

    fun getOrganizationFollowerCount(orgId: String): Int {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}org isa organization, has id "$orgId";
                ${"$"}follows (following: ${"$"}org) isa follows;
                select ${"$"}follows;
            """.trimIndent()).resolve()

            var count = 0
            for (row in answer.asConceptRows()) {
                count++
            }
            count
        } ?: 0
    }
}
