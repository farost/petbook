package com.petbook.follow

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class FollowStatus(
    val isFollowing: Boolean,
    val followerCount: Int = 0
)

fun Route.followRoutes(followRepository: FollowRepository) {

    route("/api/follow") {

        // Get who current user is following
        authenticate("auth-jwt") {
            get("/following") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val following = followRepository.getFollowing(userId)
                call.respond(HttpStatusCode.OK, following)
            }
        }

        // Follow a user
        authenticate("auth-jwt") {
            post("/user/{targetId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val targetId = call.parameters["targetId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Target user ID required"))

                if (userId == targetId) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot follow yourself"))
                }

                val success = followRepository.followUser(userId, targetId)

                if (success) {
                    val count = followRepository.getFollowerCount(targetId)
                    call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = true, followerCount = count))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to follow user"))
                }
            }
        }

        // Unfollow a user
        authenticate("auth-jwt") {
            delete("/user/{targetId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val targetId = call.parameters["targetId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Target user ID required"))

                val success = followRepository.unfollowUser(userId, targetId)

                if (success) {
                    val count = followRepository.getFollowerCount(targetId)
                    call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = false, followerCount = count))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to unfollow user"))
                }
            }
        }

        // Check if following a user
        authenticate("auth-jwt") {
            get("/user/{targetId}/status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val targetId = call.parameters["targetId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Target user ID required"))

                val isFollowing = followRepository.isFollowingUser(userId, targetId)
                val count = followRepository.getFollowerCount(targetId)
                call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = isFollowing, followerCount = count))
            }
        }

        // Follow a pet
        authenticate("auth-jwt") {
            post("/pet/{petId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val petId = call.parameters["petId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

                val success = followRepository.followPet(userId, petId)

                if (success) {
                    val count = followRepository.getPetFollowerCount(petId)
                    call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = true, followerCount = count))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to follow pet"))
                }
            }
        }

        // Unfollow a pet
        authenticate("auth-jwt") {
            delete("/pet/{petId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val petId = call.parameters["petId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

                val success = followRepository.unfollowPet(userId, petId)

                if (success) {
                    val count = followRepository.getPetFollowerCount(petId)
                    call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = false, followerCount = count))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to unfollow pet"))
                }
            }
        }

        // Check if following a pet
        authenticate("auth-jwt") {
            get("/pet/{petId}/status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val petId = call.parameters["petId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

                val isFollowing = followRepository.isFollowingPet(userId, petId)
                val count = followRepository.getPetFollowerCount(petId)
                call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = isFollowing, followerCount = count))
            }
        }

        // Follow an organization
        authenticate("auth-jwt") {
            post("/organization/{orgId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val orgId = call.parameters["orgId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization ID required"))

                val success = followRepository.followOrganization(userId, orgId)

                if (success) {
                    val count = followRepository.getOrganizationFollowerCount(orgId)
                    call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = true, followerCount = count))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to follow organization"))
                }
            }
        }

        // Unfollow an organization
        authenticate("auth-jwt") {
            delete("/organization/{orgId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val orgId = call.parameters["orgId"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization ID required"))

                val success = followRepository.unfollowOrganization(userId, orgId)

                if (success) {
                    val count = followRepository.getOrganizationFollowerCount(orgId)
                    call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = false, followerCount = count))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to unfollow organization"))
                }
            }
        }

        // Check if following an organization
        authenticate("auth-jwt") {
            get("/organization/{orgId}/status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val orgId = call.parameters["orgId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization ID required"))

                val isFollowing = followRepository.isFollowingOrganization(userId, orgId)
                val count = followRepository.getOrganizationFollowerCount(orgId)
                call.respond(HttpStatusCode.OK, FollowStatus(isFollowing = isFollowing, followerCount = count))
            }
        }
    }
}
