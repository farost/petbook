package com.petbook.user

import com.petbook.pet.PetRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val bio: String? = null,
    val location: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null
)

@Serializable
data class UserProfileWithPets(
    val user: UserProfile,
    val pets: List<PetSummary>
)

@Serializable
data class PetSummary(
    val id: String,
    val name: String,
    val species: String,
    val breed: String? = null,
    val bio: String? = null,
    val sex: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val petStatus: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val clearBirthday: Boolean = false,
    val clearBirthMonth: Boolean = false,
    val clearBirthDay: Boolean = false
)

@Serializable
data class UserSearchResult(
    val id: String,
    val name: String,
    val email: String,
    val petCount: Int = 0
)

@Serializable
data class PaginatedUsersResponse(
    val users: List<UserSearchResult>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

fun Route.userRoutes(userRepository: UserRepository, petRepository: PetRepository) {

    route("/api/users") {

        // List all users (public, for discover page) with pagination and sorting
        get {
            val query = call.request.queryParameters["q"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 25
            val sort = call.request.queryParameters["sort"]

            // Validate page size
            val validPageSize = when {
                pageSize <= 10 -> 10
                pageSize <= 25 -> 25
                pageSize <= 50 -> 50
                else -> 100
            }

            // For sorting by pet count, we need to fetch all users and sort in memory
            val allUsers = if (query.isNullOrBlank()) {
                userRepository.findAllUnsorted()
            } else {
                userRepository.searchByName(query, 1000)
            }

            // Batch fetch pet counts
            val petCounts = petRepository.countPetsByOwnerIds(allUsers.map { it.id })

            // Create results with pet counts
            var results = allUsers.map { user ->
                UserSearchResult(user.id, user.name, user.email, petCounts[user.id] ?: 0)
            }

            // Apply sorting
            results = when (sort) {
                "name_asc" -> results.sortedBy { it.name.lowercase() }
                "name_desc" -> results.sortedByDescending { it.name.lowercase() }
                "most_pets" -> results.sortedByDescending { it.petCount }
                "recent" -> results.reversed() // Recently joined (newest IDs last in DB typically)
                else -> results.sortedBy { it.name.lowercase() }
            }

            val total = results.size
            val offset = (page - 1) * validPageSize
            val paginatedResults = results.drop(offset).take(validPageSize)
            val totalPages = if (total == 0) 1 else (total + validPageSize - 1) / validPageSize

            call.respond(HttpStatusCode.OK, PaginatedUsersResponse(
                users = paginatedResults,
                total = total,
                page = page,
                pageSize = validPageSize,
                totalPages = totalPages
            ))
        }

        // Get current user's profile with pets
        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val user = userRepository.findById(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

                val pets = petRepository.findByOwnerId(userId)
                val petSummaries = pets.map { PetSummary(it.id, it.name, it.species, it.breed, it.bio, it.sex, it.birthYear, it.birthMonth, it.birthDay, it.petStatus) }

                val profile = UserProfile(user.id, user.name, user.email, user.bio, user.location, user.birthYear, user.birthMonth, user.birthDay)
                call.respond(HttpStatusCode.OK, UserProfileWithPets(profile, petSummaries))
            }
        }

        // Update current user's profile
        authenticate("auth-jwt") {
            put("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val request = call.receive<UpdateProfileRequest>()

                val success = userRepository.updateUser(
                    userId = userId,
                    name = request.name,
                    bio = request.bio,
                    location = request.location,
                    birthYear = request.birthYear,
                    birthMonth = request.birthMonth,
                    birthDay = request.birthDay,
                    clearBirthday = request.clearBirthday,
                    clearBirthMonth = request.clearBirthMonth,
                    clearBirthDay = request.clearBirthDay
                )

                if (success) {
                    val user = userRepository.findById(userId)
                    if (user != null) {
                        val profile = UserProfile(user.id, user.name, user.email, user.bio, user.location, user.birthYear, user.birthMonth, user.birthDay)
                        call.respond(HttpStatusCode.OK, profile)
                    } else {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Profile updated"))
                    }
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update profile"))
                }
            }
        }

        // Get user profile by ID (public)
        get("/{id}") {
            val userId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("User ID required"))

            val user = userRepository.findById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            val pets = petRepository.findByOwnerId(userId)
            val petSummaries = pets.map { PetSummary(it.id, it.name, it.species, it.breed, it.bio, it.sex, it.birthYear, it.birthMonth, it.birthDay, it.petStatus) }

            val profile = UserProfile(user.id, user.name, user.email, user.bio, user.location, user.birthYear, user.birthMonth, user.birthDay)
            call.respond(HttpStatusCode.OK, UserProfileWithPets(profile, petSummaries))
        }
    }
}
