package com.ammu.social.models

import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: String,
    val text: String,
    val author: String,
    var priority: String = "",
    var reason: String = ""
)
