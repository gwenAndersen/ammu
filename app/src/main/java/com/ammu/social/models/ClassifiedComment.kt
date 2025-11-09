@file:Suppress("KDocMissingDocumentation")

package com.ammu.social.models

import kotlinx.serialization.Serializable

@Serializable
data class ClassifiedComment(
    val id: String,
    val text: String,
    val from: String = "",
    val priority: String = "Pending",
    val reason: String = ""
)
