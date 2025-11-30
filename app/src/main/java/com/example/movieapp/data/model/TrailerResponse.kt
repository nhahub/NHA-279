package com.example.movieapp.data.model

data class Video(
    val id: String,
    val key: String,
    val site: String,
    val type: String,
    val name: String,
    val official: Boolean
)

data class TrailerResponse(
    val results: List<Video>
)
