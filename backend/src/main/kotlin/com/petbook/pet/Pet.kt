package com.petbook.pet

import kotlinx.serialization.Serializable

@Serializable
data class Pet(
    val id: String,
    val name: String,
    val species: String,
    val breed: String? = null,
    val bio: String? = null,
    val petStatus: String = "owned",
    val sex: String? = null,           // male, female, unknown
    val birthYear: Int? = null,        // For approximate birthdays
    val birthMonth: Int? = null,       // 1-12, optional
    val birthDay: Int? = null,         // 1-31, optional
    val profileImageUrl: String? = null
)

@Serializable
data class PetWithOwner(
    val pet: Pet,
    val ownerId: String,
    val ownerName: String,
    val ownerType: String = "individual",  // individual or organization
    val ownershipStatus: String
)

@Serializable
data class CreatePetRequest(
    val name: String,
    val species: String,
    val breed: String? = null,
    val bio: String? = null,
    val sex: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val petStatus: String? = null,     // for_adoption, for_sale, needs_help (default: owned)
    val actingAsOrgId: String? = null  // If provided, pet is owned by org
)

@Serializable
data class UpdatePetRequest(
    val name: String? = null,
    val species: String? = null,
    val breed: String? = null,
    val bio: String? = null,
    val petStatus: String? = null,
    val sex: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null
)

@Serializable
data class TransferPetRequest(
    val toUserId: String? = null,   // Transfer to individual
    val toOrgId: String? = null,    // Transfer to organization
    val reason: String              // adoption, surrender, rescue, sale, gift
)

@Serializable
data class OwnershipRecord(
    val ownerId: String,
    val ownerName: String,
    val ownerType: String,      // individual or organization
    val status: String,         // current or past
    val startDate: String?,
    val endDate: String?,
    val transferReason: String?
)

@Serializable
data class OwnershipHistoryResponse(
    val petId: String,
    val petName: String,
    val history: List<OwnershipRecord>
)

@Serializable
data class PaginatedPetsResponse(
    val pets: List<PetWithOwner>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)
