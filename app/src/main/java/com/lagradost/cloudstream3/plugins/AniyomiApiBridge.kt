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
import com.lagradost.cloudstream3.Qualities
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.Qualities as CloudStreamQualities
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

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val sAnimeClass = instance.javaClass.classLoader?.loadClass("eu.kanade.tachiyomi.animesource.model.SAnime")
                ?: return null
            val sAnimeInstance = sAnimeClass.getMethod("create").invoke(null)
            sAnimeClass.getMethod("setUrl", String::class.java).invoke(sAnimeInstance, url)

            val detailsMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchAnimeDetails" || it.name == "getAnimeDetails" 
            }
            if (detailsMethod != null) {
                detailsMethod.invoke(instance, sAnimeInstance)
            }

            val title = sAnimeClass.getMethod("getTitle").invoke(sAnimeInstance) as? String ?: "Anime"
            val posterUrl = sAnimeClass.getMethod("getThumbnail_url").invoke(sAnimeInstance) as? String ?: ""
            val description = sAnimeClass.getMethod("getDescription").invoke(sAnimeInstance) as? String ?: ""

            val episodeListMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchEpisodeList" || it.name == "getEpisodeList" 
            }
            val episodesRaw = episodeListMethod?.invoke(instance, sAnimeInstance) as? List<*> ?: emptyList<Any>()

            val episodeList = mutableListOf<Episode>()

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

    /**
     * Puxa os links de mídia (.mp4, .m3u8) da extensão Aniyomi e injeta no player
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            // 1. Instanciar um SEpisode com a URL recebida
            val sEpisodeClass = instance.javaClass.classLoader?.loadClass("eu.kanade.tachiyomi.animesource.model.SEpisode")
                ?: return false
            val sEpisodeInstance = sEpisodeClass.getMethod("create").invoke(null)
            sEpisodeClass.getMethod("setUrl", String::class.java).invoke(sEpisodeInstance, data)

            // 2. Chamar o método de buscar vídeos da extensão (getVideoList ou fetchVideoList)
            val videoListMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchVideoList" || it.name == "getVideoList" 
            } ?: return false

            val videoList = videoListMethod.invoke(instance, sEpisodeInstance) as? List<*> ?: return false

            // 3. Mapear cada Video do Aniyomi para ExtractorLink do CloudStream
            for (video in videoList) {
                if (video == null) continue

                val videoUrl = try { video.javaClass.getMethod("getVideoUrl").invoke(video) as? String } catch (e: Exception) { null }
                    ?: try { video.javaClass.getMethod("getUrl").invoke(video) as? String } catch (e: Exception) { null }
                    ?: continue

                val qualityStr = try { video.javaClass.getMethod("getQuality").invoke(video) as? String } catch (e: Exception) { "720p" }

                // Define se o link é HLS (.m3u8) ou stream direto
                val isM3u8 = videoUrl.contains(".m3u8")

                val link = ExtractorLink(
                    source = name,
                    name = "$name - $qualityStr",
                    url = videoUrl,
                    referer = mainUrl,
                    quality = CloudStreamQualities.Unknown.value,
                    isM3u8 = isM3u8
                )

                // Devolve o link direto para o CloudStream tocar
                offsetCallback(link)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

