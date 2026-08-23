package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.app
import kotlinx.serialization.Serializable

@Serializable
data class AniyomiExtensionData(
    @JsonProperty("name") val name: String,
    @JsonProperty("pkg") val pkg: String,
    @JsonProperty("apk") val apk: String,
    @JsonProperty("lang") val lang: String,
    @JsonProperty("code") val code: Int,
    @JsonProperty("version") val version: String,
    @JsonProperty("class") val mainClass: String? = null
)

object AniyomiRepoParser {

    /**
     * Lê a URL de um repositório Aniyomi (index.json) e retorna a lista de dados dos APKs
     */
    suspend fun fetchAniyomiRepository(repoUrl: String): List<AniyomiExtensionData> {
        return try {
            val cleanUrl = if (repoUrl.endsWith("index.json")) repoUrl else "$repoUrl/index.json"
            val responseText = app.get(cleanUrl).text
            parseJson<List<AniyomiExtensionData>>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
