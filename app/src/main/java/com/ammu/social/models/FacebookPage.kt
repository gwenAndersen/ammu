package com.ammu.social.models

import kotlinx.serialization.Serializable

@Serializable
data class FacebookPage(
    val id: String,
    val name: String,
    val accessToken: String
)
