package com.petbook.post

import com.petbook.db.TypeDBService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class PostRepository(private val db: TypeDBService) {

    private val logger = LoggerFactory.getLogger(PostRepository::class.java)

    // Image URL validation regex - accepts common image hosting domains and direct image URLs
    private val imageUrlPattern = Regex(
        """^https?://[\w-]+(\.[\w-]+)+(:\d+)?(/[\w\-.~:/?#\[\]@!$&'()*+,;=%]*)?$""",
        RegexOption.IGNORE_CASE
    )

    // Allowed image file extensions
    private val allowedExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg")

    fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true // null/empty is valid (no image)
        if (url.length > 2048) return false // URL too long

        // Must match URL pattern
        if (!imageUrlPattern.matches(url)) return false

        // Check for common image hosting domains or direct image extensions
        val lowercaseUrl = url.lowercase()
        val hasImageExtension = allowedExtensions.any { lowercaseUrl.contains(it) }
        val isTrustedDomain = listOf(
            "imgur.com", "i.imgur.com",
            "cloudinary.com",
            "unsplash.com", "images.unsplash.com",
            "pexels.com", "images.pexels.com",
            "flickr.com", "staticflickr.com",
            "googleusercontent.com",
            "amazonaws.com", "s3.amazonaws.com",
            "cloudfront.net",
            "githubusercontent.com",
            "blob.core.windows.net",
            "storage.googleapis.com"
        ).any { lowercaseUrl.contains(it) }

        return hasImageExtension || isTrustedDomain
    }

    fun createPost(
        content: String,
        authorId: String,
        authorIsOrg: Boolean = false,
        petId: String? = null,
        targetUserId: String? = null,
        targetOrgId: String? = null,
        imageUrl: String? = null
    ): Post? {
        val postId = UUID.randomUUID().toString()
        val createdAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0)  // Truncate to seconds
        val createdAtStr = createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val authorType = if (authorIsOrg) "organization" else "individual"

        // Build image URL clause if provided
        val imageUrlClause = if (!imageUrl.isNullOrBlank()) ",\n                    has image_url \"$imageUrl\"" else ""

        val success = db.writeTransaction { tx ->
            // Insert post with timestamp and optional image
            tx.query("""
                insert
                ${"$"}post isa post,
                    has id "$postId",
                    has content "$content",
                    has created_at $createdAtStr$imageUrlClause;
            """.trimIndent()).resolve()

            // Create authorship relation
            tx.query("""
                match
                ${"$"}author isa $authorType, has id "$authorId";
                ${"$"}post isa post, has id "$postId";
                insert
                (author: ${"$"}author, content: ${"$"}post) isa authorship;
            """.trimIndent()).resolve()

            // If posting on a pet's wall, create pet_post relation
            if (petId != null) {
                tx.query("""
                    match
                    ${"$"}pet isa pet, has id "$petId";
                    ${"$"}post isa post, has id "$postId";
                    insert
                    (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                """.trimIndent()).resolve()
            }

            // If posting on a user's wall, create user_wall_post relation
            if (targetUserId != null) {
                tx.query("""
                    match
                    ${"$"}user isa individual, has id "$targetUserId";
                    ${"$"}post isa post, has id "$postId";
                    insert
                    (wall_owner: ${"$"}user, wall_post: ${"$"}post) isa user_wall_post;
                """.trimIndent()).resolve()
            }

            // If posting on an org's wall, create org_wall_post relation
            if (targetOrgId != null) {
                tx.query("""
                    match
                    ${"$"}org isa organization, has id "$targetOrgId";
                    ${"$"}post isa post, has id "$postId";
                    insert
                    (wall_owner: ${"$"}org, wall_post: ${"$"}post) isa org_wall_post;
                """.trimIndent()).resolve()
            }

            true
        }

        return if (success == true) {
            logger.info("Created post by ${if (authorIsOrg) "org" else "user"}: $authorId -> target: user=$targetUserId org=$targetOrgId pet=$petId")
            // Return post with minimal info - caller can fetch full details
            Post(postId, content, authorId, "", authorType, petId, null, null, targetUserId, null, targetOrgId, null, createdAtStr, imageUrl)
        } else {
            logger.error("Failed to create post")
            null
        }
    }

    fun findById(postId: String): Post? {
        return db.readTransaction { tx ->
            // Try to find post with individual author first
            val individualAnswer = tx.query("""
                match
                ${"$"}post isa post,
                    has id "$postId",
                    has content ${"$"}content,
                    has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            val indRows = individualAnswer.asConceptRows()
            val indIterator = indRows.iterator()

            if (indIterator.hasNext()) {
                val row = indIterator.next()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                val petInfo = getPetForPost(postId)
                return@readTransaction Post(postId, content, authorId, authorName, "individual", petInfo?.id, petInfo?.name, petInfo?.species, null, null, null, null, createdAt, null)
            }

            // Try organization author
            val orgAnswer = tx.query("""
                match
                ${"$"}post isa post,
                    has id "$postId",
                    has content ${"$"}content,
                    has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            val orgRows = orgAnswer.asConceptRows()
            val orgIterator = orgRows.iterator()

            if (orgIterator.hasNext()) {
                val row = orgIterator.next()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                val petInfo = getPetForPost(postId)
                return@readTransaction Post(postId, content, authorId, authorName, "organization", petInfo?.id, petInfo?.name, petInfo?.species, null, null, null, null, createdAt, null)
            }

            null
        }
    }

    data class PetInfo(val id: String, val name: String, val species: String)

    private fun getPetForPost(postId: String): PetInfo? {
        return db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}post isa post, has id "$postId";
                ${"$"}pet_post (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                ${"$"}pet has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                select ${"$"}petId, ${"$"}petName, ${"$"}petSpecies;
            """.trimIndent()).resolve()

            val rows = answer.asConceptRows()
            val iterator = rows.iterator()

            if (iterator.hasNext()) {
                val row = iterator.next()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                PetInfo(petId, petName, petSpecies)
            } else {
                null
            }
        }
    }

    fun getPostsByUser(userId: String): List<Post> {
        return db.readTransaction { tx ->
            val posts = mutableListOf<Post>()

            // Get user's authored non-pet posts
            val authoredPostsAnswer = tx.query("""
                match
                ${"$"}user isa individual, has id "$userId", has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}user, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                not { ${"$"}pp (post: ${"$"}post) isa pet_post; };
                select ${"$"}postId, ${"$"}content, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in authoredPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, userId, authorName, "individual", null, null, null, userId, null, null, null, createdAt, null))
            }

            // Get user's pet posts
            val petPostsAnswer = tx.query("""
                match
                ${"$"}user isa individual, has id "$userId", has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}user, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}pet_post (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                ${"$"}pet has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                select ${"$"}postId, ${"$"}content, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in petPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, userId, authorName, "individual", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Get posts ON the user's wall by individual authors (via user_wall_post)
            try {
                val wallPostsIndividualAnswer = tx.query("""
                    match
                    ${"$"}wallOwner isa individual, has id "$userId";
                    ${"$"}user_wall_post (wall_owner: ${"$"}wallOwner, wall_post: ${"$"}post) isa user_wall_post;
                    ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                    ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                    ${"$"}author isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                    select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}createdAt;
                """.trimIndent()).resolve()

                for (row in wallPostsIndividualAnswer.asConceptRows()) {
                    val postId = row.get("postId").get().tryGetString().get()
                    val content = row.get("content").get().tryGetString().get()
                    val authorId = row.get("authorId").get().tryGetString().get()
                    val authorName = row.get("authorName").get().tryGetString().get()
                    val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                    posts.add(Post(postId, content, authorId, authorName, "individual", null, null, null, userId, null, null, null, createdAt, null))
                }
            } catch (e: Exception) {
                logger.debug("No user_wall_post relation found for individual authors")
            }

            // Get posts ON the user's wall by organization authors (via user_wall_post)
            try {
                val wallPostsOrgAnswer = tx.query("""
                    match
                    ${"$"}wallOwner isa individual, has id "$userId";
                    ${"$"}user_wall_post (wall_owner: ${"$"}wallOwner, wall_post: ${"$"}post) isa user_wall_post;
                    ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                    ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                    ${"$"}author isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                    select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}createdAt;
                """.trimIndent()).resolve()

                for (row in wallPostsOrgAnswer.asConceptRows()) {
                    val postId = row.get("postId").get().tryGetString().get()
                    val content = row.get("content").get().tryGetString().get()
                    val authorId = row.get("authorId").get().tryGetString().get()
                    val authorName = row.get("authorName").get().tryGetString().get()
                    val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                    posts.add(Post(postId, content, authorId, authorName, "organization", null, null, null, userId, null, null, null, createdAt, null))
                }
            } catch (e: Exception) {
                logger.debug("No user_wall_post relation found for org authors")
            }

            posts.distinctBy { it.id }.sortedByDescending { it.createdAt }
        } ?: emptyList()
    }

    fun getPostsForPet(petId: String): List<Post> {
        return db.readTransaction { tx ->
            val posts = mutableListOf<Post>()

            // Posts by individual authors
            val individualAnswer = tx.query("""
                match
                ${"$"}pet isa pet, has id "$petId", has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in individualAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "individual", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Posts by organization authors
            val orgAnswer = tx.query("""
                match
                ${"$"}pet isa pet, has id "$petId", has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in orgAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "organization", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            posts.sortedByDescending { it.createdAt }
        } ?: emptyList()
    }

    fun getFeedForUser(userId: String): List<Post> {
        // Get posts from users and pets that the current user follows, plus owned pets
        return db.readTransaction { tx ->
            val posts = mutableListOf<Post>()

            // Posts from followed users (non-pet posts only to avoid duplicates)
            val userPostsAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}followed_user) isa follows;
                ${"$"}followed_user isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}followed_user, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                not { ${"$"}pp (post: ${"$"}post) isa pet_post; };
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in userPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "individual", null, null, null, null, null, null, null, createdAt, null))
            }

            // Posts from followed organizations (non-pet posts only)
            val orgPostsAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}followed_org) isa follows;
                ${"$"}followed_org isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}followed_org, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                not { ${"$"}pp (post: ${"$"}post) isa pet_post; };
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in orgPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "organization", null, null, null, null, null, null, null, createdAt, null))
            }

            // Posts on pets owned by followed users (by individual authors)
            val followedUserPetPostsIndividualAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}followed_user) isa follows;
                ${"$"}followed_user isa individual;
                ${"$"}ownership (owner: ${"$"}followed_user, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                ${"$"}pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in followedUserPetPostsIndividualAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "individual", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Posts on pets owned by followed users (by organization authors)
            val followedUserPetPostsOrgAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}followed_user) isa follows;
                ${"$"}followed_user isa individual;
                ${"$"}ownership (owner: ${"$"}followed_user, pet: ${"$"}pet) isa ownership,
                    has ownership_status "current";
                ${"$"}pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in followedUserPetPostsOrgAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "organization", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Posts on followed pets' walls (by individual authors)
            val petPostsIndividualAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}followed_pet) isa follows;
                ${"$"}followed_pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}followed_pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in petPostsIndividualAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "individual", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Posts on followed pets' walls (by organization authors)
            val petPostsOrgAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}follows (follower: ${"$"}me, following: ${"$"}followed_pet) isa follows;
                ${"$"}followed_pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}followed_pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in petPostsOrgAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "organization", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Posts on owned pets' walls (by individual authors)
            val ownedPetPostsIndividualAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}ownership (owner: ${"$"}me, pet: ${"$"}owned_pet) isa ownership;
                ${"$"}owned_pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}owned_pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in ownedPetPostsIndividualAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "individual", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Posts on owned pets' walls (by organization authors)
            val ownedPetPostsOrgAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}ownership (owner: ${"$"}me, pet: ${"$"}owned_pet) isa ownership;
                ${"$"}owned_pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}owned_pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in ownedPetPostsOrgAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "organization", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // User's own non-pet posts
            val ownPostsAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId", has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}me, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                not { ${"$"}pp (post: ${"$"}post) isa pet_post; };
                select ${"$"}postId, ${"$"}content, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in ownPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, userId, authorName, "individual", null, null, null, null, null, null, null, createdAt, null))
            }

            // Posts from orgs the user manages (non-pet posts)
            val managedOrgPostsAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}org_management (manager: ${"$"}me, org: ${"$"}managed_org) isa org_management;
                ${"$"}managed_org isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                ${"$"}authorship (author: ${"$"}managed_org, content: ${"$"}post) isa authorship;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                not { ${"$"}pp (post: ${"$"}post) isa pet_post; };
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in managedOrgPostsAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "organization", null, null, null, null, null, null, null, createdAt, null))
            }

            // Posts on managed orgs' pets' walls (by individual authors)
            val managedOrgPetPostsIndividualAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}org_management (manager: ${"$"}me, org: ${"$"}managed_org) isa org_management;
                ${"$"}managed_org isa organization;
                ${"$"}ownership (owner: ${"$"}managed_org, pet: ${"$"}org_pet) isa ownership;
                ${"$"}org_pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}org_pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa individual, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in managedOrgPetPostsIndividualAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "individual", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            // Posts on managed orgs' pets' walls (by organization authors)
            val managedOrgPetPostsOrgAnswer = tx.query("""
                match
                ${"$"}me isa individual, has id "$userId";
                ${"$"}org_management (manager: ${"$"}me, org: ${"$"}managed_org) isa org_management;
                ${"$"}managed_org isa organization;
                ${"$"}ownership (owner: ${"$"}managed_org, pet: ${"$"}org_pet) isa ownership;
                ${"$"}org_pet isa pet, has id ${"$"}petId, has name ${"$"}petName, has species ${"$"}petSpecies;
                ${"$"}pet_post (pet: ${"$"}org_pet, post: ${"$"}post) isa pet_post;
                ${"$"}post has id ${"$"}postId, has content ${"$"}content, has created_at ${"$"}createdAt;
                ${"$"}authorship (author: ${"$"}author, content: ${"$"}post) isa authorship;
                ${"$"}author isa organization, has id ${"$"}authorId, has name ${"$"}authorName;
                select ${"$"}postId, ${"$"}content, ${"$"}authorId, ${"$"}authorName, ${"$"}petId, ${"$"}petName, ${"$"}petSpecies, ${"$"}createdAt;
            """.trimIndent()).resolve()

            for (row in managedOrgPetPostsOrgAnswer.asConceptRows()) {
                val postId = row.get("postId").get().tryGetString().get()
                val content = row.get("content").get().tryGetString().get()
                val authorId = row.get("authorId").get().tryGetString().get()
                val authorName = row.get("authorName").get().tryGetString().get()
                val petId = row.get("petId").get().tryGetString().get()
                val petName = row.get("petName").get().tryGetString().get()
                val petSpecies = row.get("petSpecies").get().tryGetString().get()
                val createdAt = row.get("createdAt").get().tryGetDatetime().get().toString()
                posts.add(Post(postId, content, authorId, authorName, "organization", petId, petName, petSpecies, null, null, null, null, createdAt, null))
            }

            posts.distinctBy { it.id }.sortedByDescending { it.createdAt }
        } ?: emptyList()
    }

    fun deletePost(postId: String, userId: String): Boolean {
        // First verify the user is the author (directly)
        val isDirectAuthor = db.readTransaction { tx ->
            val answer = tx.query("""
                match
                ${"$"}user isa individual, has id "$userId";
                ${"$"}post isa post, has id "$postId";
                ${"$"}authorship (author: ${"$"}user, content: ${"$"}post) isa authorship;
                select ${"$"}authorship;
            """.trimIndent()).resolve()
            answer.asConceptRows().iterator().hasNext()
        } ?: false

        // Check if post was authored by an org that the user manages
        val canDeleteAsOrgManager = if (!isDirectAuthor) {
            db.readTransaction { tx ->
                val answer = tx.query("""
                    match
                    ${"$"}user isa individual, has id "$userId";
                    ${"$"}org_management (manager: ${"$"}user, org: ${"$"}org) isa org_management;
                    ${"$"}org isa organization;
                    ${"$"}post isa post, has id "$postId";
                    ${"$"}authorship (author: ${"$"}org, content: ${"$"}post) isa authorship;
                    select ${"$"}authorship;
                """.trimIndent()).resolve()
                answer.asConceptRows().iterator().hasNext()
            } ?: false
        } else false

        if (!isDirectAuthor && !canDeleteAsOrgManager) return false

        // Delete pet_post relation if exists (separate transaction)
        try {
            db.writeTransaction { tx ->
                tx.query("""
                    match
                    ${"$"}the_post isa post, has id "$postId";
                    ${"$"}rel isa pet_post, links (post: ${"$"}the_post);
                    delete
                    ${"$"}rel;
                """.trimIndent()).resolve()
                true
            }
        } catch (e: Exception) {
            // No pet_post relation exists, continue
        }

        return db.writeTransaction { tx ->
            // Delete authorship relation
            tx.query("""
                match
                ${"$"}the_post isa post, has id "$postId";
                ${"$"}rel isa authorship, links (content: ${"$"}the_post);
                delete
                ${"$"}rel;
            """.trimIndent()).resolve()

            // Delete post
            tx.query("""
                match
                ${"$"}the_post isa post, has id "$postId";
                delete
                ${"$"}the_post;
            """.trimIndent()).resolve()

            true
        } ?: false
    }
}
