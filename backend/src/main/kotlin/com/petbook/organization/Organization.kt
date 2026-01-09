package com.petbook.organization

data class Organization(
    val id: String,
    val name: String,
    val orgType: String,  // shelter, rescue, breeder, vet_clinic
    val bio: String?,
    val location: String?,
    val establishedYear: Int? = null,
    val establishedMonth: Int? = null,
    val establishedDay: Int? = null
)

data class OrganizationWithRole(
    val organization: Organization,
    val role: String  // owner, admin, member
)

data class OrganizationWithPets(
    val organization: Organization,
    val pets: List<OrgPetSummary>,
    val followerCount: Int = 0
)

data class OrgPetSummary(
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
