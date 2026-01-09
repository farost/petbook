package com.petbook.post

import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: String,
    val content: String,
    val authorId: String,
    val authorName: String,
    val authorType: String = "individual",  // individual or organization
    val petId: String? = null,       // If posted on a pet's wall
    val petName: String? = null,
    val petSpecies: String? = null,
    val targetUserId: String? = null,    // If posted on a user's wall
    val targetUserName: String? = null,
    val targetOrgId: String? = null,     // If posted on an org's wall
    val targetOrgName: String? = null,
    val createdAt: String? = null,
    val imageUrl: String? = null     // Optional image URL attached to the post
)

@Serializable
data class CreatePostRequest(
    val content: String,
    val petId: String? = null,           // Optional: post on a pet's wall
    val actingAsOrgId: String? = null,   // If posting as an organization (from)
    val targetUserId: String? = null,    // Optional: post on a user's wall (to)
    val targetOrgId: String? = null,     // Optional: post on an org's wall (to)
    val imageUrl: String? = null         // Optional: URL of image to attach
)

@Serializable
data class PostFeed(
    val posts: List<Post>
)
