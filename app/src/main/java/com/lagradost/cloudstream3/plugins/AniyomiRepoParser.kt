package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AniyomiExtensionData(
    @JsonProperty("name") val name: String,
    @JsonProperty("pkg") val pkg: String,
    @JsonProperty("apk") val apk: String,
    @JsonProperty("lang") val lang: String? = "all",
    @JsonProperty("code") val code: Int? = 1,
    @JsonProperty("version") val version: String? = "1.0",
    @JsonProperty("class") val mainClass: String? = null
)

object AniyomiRepoParser {

    /**
     * Lê a URL de um repositório Aniyomi (index.json) e retorna a lista de extensões disponíveis
     */
    suspend fun fetchAniyomiRepository(repoUrl: String): List<AniyomiExtensionData> {
        return try {
            val cleanUrl = if (repoUrl.endsWith("index.json")) repoUrl else "${repoUrl.trimEnd('/')}/index.json"
            val responseText = app.get(cleanUrl).text
            parseJson<List<AniyomiExtensionData>>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Faz o download do APK da extensão do Aniyomi para a pasta temporária do app
     */
    suspend fun downloadExtensionApk(context: Context, baseUrl: String, apkFileName: String): File? {
        return try {
            val cleanBase = baseUrl.trimEnd('/')
            val fullUrl = "$cleanBase/$apkFileName"
            val responseBytes = app.get(fullUrl).body.bytes()

            val cacheFile = File(context.codeCacheDir, apkFileName)
            cacheFile.writeBytes(responseBytes)
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
