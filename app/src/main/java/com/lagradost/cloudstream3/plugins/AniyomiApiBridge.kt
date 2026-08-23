package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
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

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            val searchMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "searchAnimeParse" || it.name == "fetchSearchAnime" 
            } ?: return emptyList()

            val response = searchMethod.invoke(instance, query, 1, emptyList<Any>())
            
            val animesList = try {
                val getAnimesMethod = response.javaClass.getMethod("getAnimes")
                getAnimesMethod.invoke(response) as? List<*>
            } catch (e: Exception) {
                response as? List<*>
            } ?: return emptyList()

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

    /**
     * Extrai os detalhes do anime e monta a lista de episódios no CloudStream
     */
    override suspend fun load(url: String): LoadResponse? {
        return try {
            // 1. Criar um objeto SAnime fictício para passar para a extensão do Aniyomi
            val sAnimeClass = instance.javaClass.classLoader?.loadClass("eu.kanade.tachiyomi.animesource.model.SAnime")
                ?: return null
            val sAnimeInstance = sAnimeClass.getMethod("create").invoke(null)
            sAnimeClass.getMethod("setUrl", String::class.java).invoke(sAnimeInstance, url)

            // 2. Chama o método de buscar detalhes (animeDetailsParse ou fetchAnimeDetails)
            val detailsMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchAnimeDetails" || it.name == "getAnimeDetails" 
            }
            if (detailsMethod != null) {
                detailsMethod.invoke(instance, sAnimeInstance)
            }

            val title = sAnimeClass.getMethod("getTitle").invoke(sAnimeInstance) as? String ?: "Anime"
            val posterUrl = sAnimeClass.getMethod("getThumbnail_url").invoke(sAnimeInstance) as? String ?: ""
            val description = sAnimeClass.getMethod("getDescription").invoke(sAnimeInstance) as? String ?: ""

            // 3. Buscar a lista de episódios (fetchEpisodeList ou episodeListParse)
            val episodeListMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchEpisodeList" || it.name == "getEpisodeList" 
            }
            val episodesRaw = episodeListMethod?.invoke(instance, sAnimeInstance) as? List<*> ?: emptyList<Any>()

            val episodeList = mutableListOf<Episode>()

            // 4. Mapear cada SEpisode para o modelo Episode do CloudStream
            for (ep in episodesRaw) {
                if (ep == null) continue
                val epUrl = ep.javaClass.getMethod("getUrl").invoke(ep) as? String ?: continue
                val epName = ep.javaClass.getMethod("getName").invoke(ep) as? String ?: "Episódio"
                val epNumber = ep.javaClass.getMethod("getEpisode_number").invoke(ep) as? Float ?: 0f

                val episode = newEpisode(epUrl) {
                    this.name = epName
                    this.episode = epNumber.toInt()
                }
                episodeList.add(episode)
            }

            // Inverte a lista se os episódios vierem do mais recente para o mais antigo
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = posterUrl
                this.plot = description
                this.episodes = episodeList.reversed()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

