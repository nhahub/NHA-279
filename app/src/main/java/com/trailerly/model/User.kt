package com.trailerly.model

data class User(
    val uid: String,
    val name: String,
    val email: String,
    val initials: String,
    val profilePictureUrl: String? = null
)
