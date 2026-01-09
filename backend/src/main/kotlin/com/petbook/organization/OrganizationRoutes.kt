package com.petbook.organization

import com.petbook.pet.PetRepository
import com.petbook.user.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateOrganizationRequest(
    val name: String,
    val orgType: String,  // shelter, rescue, breeder, vet_clinic
    val bio: String? = null,
    val location: String? = null
)

@Serializable
data class UpdateOrganizationRequest(
    val name: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val establishedYear: Int? = null,
    val establishedMonth: Int? = null,
    val establishedDay: Int? = null,
    val clearEstablishedDate: Boolean = false
)

@Serializable
data class OrganizationResponse(
    val id: String,
    val name: String,
    val orgType: String,
    val bio: String? = null,
    val location: String? = null,
    val petCount: Int = 0,
    val establishedYear: Int? = null,
    val establishedMonth: Int? = null,
    val establishedDay: Int? = null
)

@Serializable
data class OrganizationWithRoleResponse(
    val organization: OrganizationResponse,
    val role: String
)

@Serializable
data class OrgPetSummaryResponse(
    val id: String,
    val name: String,
    val species: String,
    val status: String,
    val breed: String? = null,
    val bio: String? = null,
    val sex: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null
)

@Serializable
data class OrganizationProfileResponse(
    val organization: OrganizationResponse,
    val pets: List<OrgPetSummaryResponse>,
    val followerCount: Int
)

@Serializable
data class PaginatedOrganizationsResponse(
    val organizations: List<OrganizationResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

@Serializable
data class OrgFeedResponse(
    val posts: List<OrgPostResponse>
)

@Serializable
data class TransferOrganizationRequest(
    val toUserId: String
)

@Serializable
data class OrgPostResponse(
    val id: String,
    val content: String,
    val authorId: String,
    val authorName: String,
    val authorType: String,
    val petId: String? = null,
    val petName: String? = null,
    val createdAt: String? = null
)

fun Route.organizationRoutes(organizationRepository: OrganizationRepository, petRepository: PetRepository) {

    route("/api/organizations") {

        // List all organizations (public) with pagination, filtering, and sorting
        get {
            val query = call.request.queryParameters["q"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 25
            val orgType = call.request.queryParameters["orgType"]
            val sort = call.request.queryParameters["sort"]

            // Validate page size
            val validPageSize = when {
                pageSize <= 10 -> 10
                pageSize <= 25 -> 25
                pageSize <= 50 -> 50
                else -> 100
            }

            // Fetch all filtered orgs (without pagination) for sorting
            val allOrgs = organizationRepository.findAllFilteredUnsorted(query, orgType)

            // Batch fetch pet counts
            val petCounts = petRepository.countPetsByOrgIds(allOrgs.map { it.id })

            // Create responses with pet counts
            var response = allOrgs.map {
                OrganizationResponse(it.id, it.name, it.orgType, it.bio, it.location, petCounts[it.id] ?: 0, it.establishedYear, it.establishedMonth, it.establishedDay)
            }

            // Apply sorting (now we have pet counts for most_pets sort)
            response = when (sort) {
                "name_asc" -> response.sortedBy { it.name.lowercase() }
                "name_desc" -> response.sortedByDescending { it.name.lowercase() }
                "most_pets" -> response.sortedByDescending { it.petCount }
                "recent" -> response.reversed() // Recently created (newest IDs last in DB typically)
                else -> response.sortedBy { it.name.lowercase() }
            }

            val total = response.size
            val offset = (page - 1) * validPageSize
            val paginatedResponse = response.drop(offset).take(validPageSize)
            val totalPages = if (total == 0) 1 else (total + validPageSize - 1) / validPageSize

            call.respond(HttpStatusCode.OK, PaginatedOrganizationsResponse(
                organizations = paginatedResponse,
                total = total,
                page = page,
                pageSize = validPageSize,
                totalPages = totalPages
            ))
        }

        // Get current user's organizations
        authenticate("auth-jwt") {
            get("/my") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val orgs = organizationRepository.getOrganizationsByManager(userId)
                val response = orgs.map {
                    OrganizationWithRoleResponse(
                        organization = OrganizationResponse(
                            it.organization.id,
                            it.organization.name,
                            it.organization.orgType,
                            it.organization.bio,
                            it.organization.location,
                            0,
                            it.organization.establishedYear,
                            it.organization.establishedMonth,
                            it.organization.establishedDay
                        ),
                        role = it.role
                    )
                }
                call.respond(HttpStatusCode.OK, response)
            }
        }

        // Create organization
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val request = call.receive<CreateOrganizationRequest>()

                if (request.name.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization name is required"))
                }

                val validOrgTypes = listOf("shelter", "rescue", "breeder", "vet_clinic")
                if (request.orgType !in validOrgTypes) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid organization type. Must be one of: ${validOrgTypes.joinToString(", ")}")
                    )
                }

                val org = organizationRepository.createOrganization(
                    name = request.name,
                    orgType = request.orgType,
                    managerId = userId,
                    bio = request.bio,
                    location = request.location
                )

                if (org != null) {
                    call.respond(
                        HttpStatusCode.Created,
                        OrganizationResponse(org.id, org.name, org.orgType, org.bio, org.location, 0, org.establishedYear, org.establishedMonth, org.establishedDay)
                    )
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create organization"))
                }
            }
        }

        // Get organization profile (public)
        get("/{id}") {
            val orgId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization ID required"))

            val orgWithPets = organizationRepository.getOrgWithPets(orgId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Organization not found"))

            val response = OrganizationProfileResponse(
                organization = OrganizationResponse(
                    orgWithPets.organization.id,
                    orgWithPets.organization.name,
                    orgWithPets.organization.orgType,
                    orgWithPets.organization.bio,
                    orgWithPets.organization.location,
                    orgWithPets.pets.size,
                    orgWithPets.organization.establishedYear,
                    orgWithPets.organization.establishedMonth,
                    orgWithPets.organization.establishedDay
                ),
                pets = orgWithPets.pets.map { OrgPetSummaryResponse(
                    it.id, it.name, it.species, it.status, it.breed, it.bio, it.sex, it.birthYear, it.birthMonth, it.birthDay
                ) },
                followerCount = orgWithPets.followerCount
            )
            call.respond(HttpStatusCode.OK, response)
        }

        // Get organization feed (managers only)
        authenticate("auth-jwt") {
            get("/{id}/feed") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val orgId = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization ID required"))

                // Verify user can manage this org
                if (!organizationRepository.canManageOrg(userId, orgId)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't manage this organization"))
                }

                val posts = organizationRepository.getOrgFeed(orgId)
                val response = OrgFeedResponse(
                    posts = posts.map {
                        OrgPostResponse(it.id, it.content, it.authorId, it.authorName, it.authorType, it.petId, it.petName, it.createdAt)
                    }
                )
                call.respond(HttpStatusCode.OK, response)
            }
        }

        // Update organization
        authenticate("auth-jwt") {
            put("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val orgId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization ID required"))

                // Verify user can manage this org
                if (!organizationRepository.canManageOrg(userId, orgId)) {
                    return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't manage this organization"))
                }

                val request = call.receive<UpdateOrganizationRequest>()

                val success = organizationRepository.updateOrganization(
                    orgId = orgId,
                    name = request.name,
                    bio = request.bio,
                    location = request.location,
                    establishedYear = request.establishedYear,
                    establishedMonth = request.establishedMonth,
                    establishedDay = request.establishedDay,
                    clearEstablishedDate = request.clearEstablishedDate
                )

                if (success) {
                    val org = organizationRepository.findById(orgId)
                    if (org != null) {
                        call.respond(HttpStatusCode.OK, OrganizationResponse(org.id, org.name, org.orgType, org.bio, org.location, 0, org.establishedYear, org.establishedMonth, org.establishedDay))
                    } else {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Organization updated"))
                    }
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update organization"))
                }
            }
        }

        // Transfer organization to another user
        authenticate("auth-jwt") {
            post("/{id}/transfer") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val orgId = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Organization ID required"))

                // Verify user is the owner of this org
                if (!organizationRepository.isOrgOwner(userId, orgId)) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only the owner can transfer this organization"))
                }

                val request = call.receive<TransferOrganizationRequest>()

                if (request.toUserId.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("New owner user ID is required"))
                }

                if (request.toUserId == userId) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot transfer to yourself"))
                }

                val success = organizationRepository.transferOrganization(orgId, userId, request.toUserId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Organization transferred successfully"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to transfer organization"))
                }
            }
        }
    }
}
