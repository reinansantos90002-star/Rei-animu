package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.serialization.Serializable

@Serializable
data class AniyomiExtensionDto(
    val name: String,
    val pkg: String,
    val apk: String,
    val version: String,
    val code: Int,
    val lang: String
)

object AniyomiRepoParser {
    suspend fun fetchPluginsFromRepo(repoUrl: String): List<AniyomiExtensionDto> {
        return try {
            val indexUrl = if (repoUrl.endsWith("/")) "${repoUrl}index.min.json" else "$repoUrl/index.min.json"
            val response = app.get(indexUrl).text
            parseJson<List<AniyomiExtensionDto>>(response)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
