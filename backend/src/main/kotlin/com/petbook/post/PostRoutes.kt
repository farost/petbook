package com.petbook.post

import com.petbook.organization.OrganizationRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)

fun Route.postRoutes(postRepository: PostRepository, organizationRepository: OrganizationRepository) {

    route("/api/posts") {

        // Create a new post (authenticated)
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val request = call.receive<CreatePostRequest>()

                if (request.content.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Post content is required"))
                }

                if (request.content.length > 1000) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Post content too long (max 1000 chars)"))
                }

                // Validate image URL if provided
                if (!postRepository.isValidImageUrl(request.imageUrl)) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image URL. Must be a valid URL from a trusted image hosting service or have a common image extension (.jpg, .png, .gif, etc.)"))
                }

                // Check if posting as an organization
                val authorId: String
                val authorIsOrg: Boolean

                if (request.actingAsOrgId != null) {
                    // Verify user can manage this org
                    if (!organizationRepository.canManageOrg(userId, request.actingAsOrgId)) {
                        return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't manage this organization"))
                    }
                    authorId = request.actingAsOrgId
                    authorIsOrg = true
                } else {
                    authorId = userId
                    authorIsOrg = false
                }

                val post = postRepository.createPost(
                    content = request.content,
                    authorId = authorId,
                    authorIsOrg = authorIsOrg,
                    petId = request.petId,
                    targetUserId = request.targetUserId,
                    targetOrgId = request.targetOrgId,
                    imageUrl = request.imageUrl
                )

                if (post != null) {
                    // Fetch the full post with author name
                    val fullPost = postRepository.findById(post.id)
                    call.respond(HttpStatusCode.Created, fullPost ?: post)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create post"))
                }
            }
        }

        // Get feed for current user (posts from followed users/pets)
        authenticate("auth-jwt") {
            get("/feed") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val posts = postRepository.getFeedForUser(userId)
                call.respond(HttpStatusCode.OK, PostFeed(posts))
            }
        }

        // Get posts by a user
        get("/user/{userId}") {
            val userId = call.parameters["userId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("User ID required"))

            val posts = postRepository.getPostsByUser(userId)
            call.respond(HttpStatusCode.OK, PostFeed(posts))
        }

        // Get posts on a pet's wall
        get("/pet/{petId}") {
            val petId = call.parameters["petId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

            val posts = postRepository.getPostsForPet(petId)
            call.respond(HttpStatusCode.OK, PostFeed(posts))
        }

        // Get single post by ID
        get("/{id}") {
            val postId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Post ID required"))

            val post = postRepository.findById(postId)

            if (post != null) {
                call.respond(HttpStatusCode.OK, post)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Post not found"))
            }
        }

        // Delete post (author only)
        authenticate("auth-jwt") {
            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val postId = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Post ID required"))

                val success = postRepository.deletePost(postId, userId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Post deleted"))
                } else {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cannot delete this post"))
                }
            }
        }
    }
}
