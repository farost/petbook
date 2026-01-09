package com.petbook.pet

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

fun Route.petRoutes(petRepository: PetRepository, organizationRepository: OrganizationRepository) {

    route("/api/pets") {

        // List all pets (public, for discover page) with pagination, filtering, and sorting
        get {
            val query = call.request.queryParameters["q"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 25

            // Filter parameters
            val species = call.request.queryParameters["species"]
            val ownerType = call.request.queryParameters["ownerType"]
            val orgType = call.request.queryParameters["orgType"]
            val sort = call.request.queryParameters["sort"]

            // Validate page size
            val validPageSize = when {
                pageSize <= 10 -> 10
                pageSize <= 25 -> 25
                pageSize <= 50 -> 50
                else -> 100
            }

            val offset = (page - 1) * validPageSize

            // Use filtered query
            val total = petRepository.countAllFiltered(query, species, ownerType, orgType)
            val pets = petRepository.findAllFiltered(offset, validPageSize, query, species, ownerType, orgType, sort)
            val totalPages = if (total == 0) 1 else (total + validPageSize - 1) / validPageSize

            call.respond(HttpStatusCode.OK, PaginatedPetsResponse(
                pets = pets,
                total = total,
                page = page,
                pageSize = validPageSize,
                totalPages = totalPages
            ))
        }

        // Get current user's pets
        authenticate("auth-jwt") {
            get("/my") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val pets = petRepository.findByOwnerId(userId)
                call.respond(HttpStatusCode.OK, pets)
            }
        }

        // Create a new pet (authenticated)
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val request = call.receive<CreatePetRequest>()

                if (request.name.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet name is required"))
                }

                if (request.species.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Species is required"))
                }

                // Check if creating for an organization
                val ownerId: String
                val ownerIsOrg: Boolean

                if (request.actingAsOrgId != null) {
                    // Verify user can manage this org
                    if (!organizationRepository.canManageOrg(userId, request.actingAsOrgId)) {
                        return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't manage this organization"))
                    }
                    ownerId = request.actingAsOrgId
                    ownerIsOrg = true
                } else {
                    ownerId = userId
                    ownerIsOrg = false
                }

                val pet = petRepository.createPet(
                    name = request.name,
                    species = request.species,
                    breed = request.breed,
                    bio = request.bio,
                    sex = request.sex,
                    birthYear = request.birthYear,
                    birthMonth = request.birthMonth,
                    birthDay = request.birthDay,
                    petStatus = request.petStatus,
                    ownerId = ownerId,
                    ownerIsOrg = ownerIsOrg
                )

                if (pet != null) {
                    call.respond(HttpStatusCode.Created, pet)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create pet"))
                }
            }
        }

        // Get pet by ID (public)
        get("/{id}") {
            val petId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

            val petWithOwner = petRepository.findById(petId)

            if (petWithOwner != null) {
                call.respond(HttpStatusCode.OK, petWithOwner)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Pet not found"))
            }
        }

        // Update pet (only owner)
        authenticate("auth-jwt") {
            put("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val petId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

                // Check ownership
                if (!petRepository.isOwner(petId, userId)) {
                    return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't own this pet"))
                }

                val request = call.receive<UpdatePetRequest>()

                val success = petRepository.updatePet(
                    petId = petId,
                    name = request.name,
                    species = request.species,
                    breed = request.breed,
                    bio = request.bio,
                    petStatus = request.petStatus,
                    sex = request.sex,
                    birthYear = request.birthYear,
                    birthMonth = request.birthMonth,
                    birthDay = request.birthDay
                )

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Pet updated"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update pet"))
                }
            }
        }

        // Delete pet (only owner)
        authenticate("auth-jwt") {
            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val petId = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

                // Check ownership (individual or org they manage)
                val canDelete = petRepository.isOwner(petId, userId) ||
                    organizationRepository.getOrganizationsByManager(userId).any { petRepository.isOrgOwner(petId, it.organization.id) }

                if (!canDelete) {
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't own this pet"))
                }

                val success = petRepository.deletePet(petId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Pet deleted"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete pet"))
                }
            }
        }

        // Get pet ownership history (public)
        get("/{id}/history") {
            val petId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

            val petWithOwner = petRepository.findById(petId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Pet not found"))

            val history = petRepository.getOwnershipHistory(petId)

            call.respond(HttpStatusCode.OK, OwnershipHistoryResponse(
                petId = petId,
                petName = petWithOwner.pet.name,
                history = history
            ))
        }

        // Transfer pet to another user or organization
        authenticate("auth-jwt") {
            post("/{id}/transfer") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))

                val petId = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Pet ID required"))

                val request = call.receive<TransferPetRequest>()

                // Validate request
                if (request.toUserId == null && request.toOrgId == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Must specify toUserId or toOrgId"))
                }

                if (request.toUserId != null && request.toOrgId != null) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot specify both toUserId and toOrgId"))
                }

                val validReasons = listOf("adoption", "surrender", "rescue", "sale", "gift")
                if (request.reason !in validReasons) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid reason. Must be one of: ${validReasons.joinToString(", ")}")
                    )
                }

                // Check if user owns the pet (directly or through org)
                val currentOwner = petRepository.getCurrentOwnerId(petId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Pet not found"))

                val (currentOwnerId, currentOwnerIsOrg) = currentOwner

                val canTransfer = if (currentOwnerIsOrg) {
                    // Check if user manages the org that owns the pet
                    organizationRepository.canManageOrg(userId, currentOwnerId)
                } else {
                    // Check if user is the individual owner
                    currentOwnerId == userId
                }

                if (!canTransfer) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't own this pet"))
                }

                // Perform transfer
                val toOwnerId = request.toUserId ?: request.toOrgId!!
                val toOwnerIsOrg = request.toOrgId != null

                val success = petRepository.transferPet(petId, toOwnerId, toOwnerIsOrg, request.reason)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Pet transferred successfully"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to transfer pet"))
                }
            }
        }
    }
}
