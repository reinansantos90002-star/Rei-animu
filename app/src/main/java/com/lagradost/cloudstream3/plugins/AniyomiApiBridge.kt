package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.newAnimeSearchResponse
import java.io.File

class AniyomiApiBridge(
    private val apkFile: File,
    private val mainClassName: String,
    loader: AniyomiApkLoader
) : MainAPI() {

    private val instance: Any = loader.loadExtensionClass(apkFile, mainClassName)
        ?: throw IllegalStateException("Falha ao instanciar a classe da extensão: $mainClassName")

    override var name: String = try {
        instance.javaClass.getMethod("getName").invoke(instance) as String
    } catch (e: Exception) {
        "Aniyomi Extension"
    }

    override var mainUrl: String = try {
        instance.javaClass.getMethod("getBasesUrl").invoke(instance) as? String 
            ?: instance.javaClass.getMethod("baseUrl").invoke(instance) as String
    } catch (e: Exception) {
        ""
    }

    override val supportedTypes = setOf(TvType.Anime)
    override var hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageList? {
        return null
    }

    /**
     * Mapeia a busca do Aniyomi para os modelos nativos do CloudStream
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            // Busca o método de pesquisa da extensão (Aniyomi usa fetchSearchAnime ou searchAnimeParse)
            val searchMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "searchAnimeParse" || it.name == "fetchSearchAnime" 
            } ?: return emptyList()

            // Invoca a busca por reflexão
            val response = searchMethod.invoke(instance, query, 1, emptyList<Any>())
            
            // Extrai a lista de animes (AnimesPage.animes)
            val animesList = try {
                val getAnimesMethod = response.javaClass.getMethod("getAnimes")
                getAnimesMethod.invoke(response) as? List<*>
            } catch (e: Exception) {
                response as? List<*>
            } ?: return emptyList()

            // Converte cada SAnime do Aniyomi para um SearchResponse do CloudStream
            for (anime in animesList) {
                if (anime == null) continue
                
                val title = anime.javaClass.getMethod("getTitle").invoke(anime) as? String ?: ""
                val url = anime.javaClass.getMethod("getUrl").invoke(anime) as? String ?: ""
                val thumbnailUrl = anime.javaClass.getMethod("getThumbnail_url").invoke(anime) as? String ?: ""

                val searchResponse = newAnimeSearchResponse(title, url) {
                    this.posterUrl = thumbnailUrl
                }
                results.add(searchResponse)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        return false
    }
}

