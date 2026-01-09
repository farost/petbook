package com.petbook.user

data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val name: String,
    val bio: String? = null,
    val location: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null
)
