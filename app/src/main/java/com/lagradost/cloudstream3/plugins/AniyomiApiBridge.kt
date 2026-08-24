package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities as CloudStreamQualities
import java.io.File

class AniyomiApiBridge(
    private val apkFile: File,
    private val mainClassName: String,
    loader: AniyomiApkLoader
) : MainAPI() {

    private val instance: Any? = try {
        loader.loadExtensionClass(apkFile, mainClassName)
    } catch (e: Exception) {
        null
    }

    override var name: String = apkFile.nameWithoutExtension
    override var mainUrl: String = "https://aniyomi.org"
    override var supportedTypes: Set<TvType> = setOf(TvType.Anime)
    override var hasMainPage: Boolean = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageList? {
        return null
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (instance == null) return emptyList()
        val results = mutableListOf<SearchResponse>()
        try {
            val searchMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "searchAnimeParse" || it.name == "fetchSearchAnime" || it.name.contains("searchAnime")
            } ?: return emptyList()

            val response = try {
                searchMethod.invoke(instance, query, 1, emptyList<Any>())
            } catch (e: Exception) {
                searchMethod.invoke(instance, 1, query, null)
            }

            val animesList = try {
                val getAnimesMethod = response?.javaClass?.getMethod("getAnimes")
                getAnimesMethod?.invoke(response) as? List<*>
            } catch (e: Exception) {
                response as? List<*>
            } ?: return emptyList()

            for (anime in animesList) {
                if (anime == null) continue
                val title = try { anime.javaClass.getMethod("getTitle").invoke(anime) as? String } catch (e: Exception) { null } ?: ""
                val url = try { anime.javaClass.getMethod("getUrl").invoke(anime) as? String } catch (e: Exception) { null } ?: ""
                val thumbnailUrl = try { anime.javaClass.getMethod("getThumbnail_url").invoke(anime) as? String } catch (e: Exception) { null } ?: ""

                val searchResponse = newAnimeSearchResponse(title, url, TvType.Anime) {
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
        if (instance == null) return null
        return try {
            val sAnimeClass = instance.javaClass.classLoader?.loadClass("eu.kanade.tachiyomi.animesource.model.SAnime")
            val sAnimeInstance = sAnimeClass?.getMethod("create")?.invoke(null)
            if (sAnimeInstance != null && sAnimeClass != null) {
                sAnimeClass.getMethod("setUrl", String::class.java).invoke(sAnimeInstance, url)
            }

            val detailsMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchAnimeDetails" || it.name == "getAnimeDetails" || it.name.contains("animeDetails")
            }
            if (detailsMethod != null && sAnimeInstance != null) {
                detailsMethod.invoke(instance, sAnimeInstance)
            }

            val targetObj = sAnimeInstance ?: url
            val title = try { targetObj.javaClass.getMethod("getTitle").invoke(targetObj) as? String } catch (e: Exception) { null } ?: "Anime"
            val posterUrl = try { targetObj.javaClass.getMethod("getThumbnail_url").invoke(targetObj) as? String } catch (e: Exception) { null } ?: ""
            val description = try { targetObj.javaClass.getMethod("getDescription").invoke(targetObj) as? String } catch (e: Exception) { null } ?: ""

            val episodeListMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchEpisodeList" || it.name == "getEpisodeList" || it.name.contains("episodeList")
            }
            val episodesRaw = episodeListMethod?.invoke(instance, targetObj) as? List<*> ?: emptyList<Any>()

            val episodeList = mutableListOf<Episode>()

            for (ep in episodesRaw) {
                if (ep == null) continue
                val epUrl = try { ep.javaClass.getMethod("getUrl").invoke(ep) as? String } catch (e: Exception) { null } ?: continue
                val epName = try { ep.javaClass.getMethod("getName").invoke(ep) as? String } catch (e: Exception) { null } ?: "Episódio"
                val epNumber = try { ep.javaClass.getMethod("getEpisode_number").invoke(ep) as? Float } catch (e: Exception) { null } ?: 0f

                val episode = newEpisode(epUrl) {
                    this.name = epName
                    this.episode = epNumber.toInt()
                }
                episodeList.add(episode)
            }

            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = posterUrl
                this.plot = description
                this.addEpisodes(TvType.Anime, episodeList.reversed())
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
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (instance == null) return false
        try {
            val sEpisodeClass = instance.javaClass.classLoader?.loadClass("eu.kanade.tachiyomi.animesource.model.SEpisode")
            val sEpisodeInstance = sEpisodeClass?.getMethod("create")?.invoke(null)
            if (sEpisodeInstance != null && sEpisodeClass != null) {
                sEpisodeClass.getMethod("setUrl", String::class.java).invoke(sEpisodeInstance, data)
            }

            val videoListMethod = instance.javaClass.methods.firstOrNull { 
                it.name == "fetchVideoList" || it.name == "getVideoList" || it.name.contains("videoList")
            } ?: return false

            val targetArg = sEpisodeInstance ?: data
            val videoList = videoListMethod.invoke(instance, targetArg) as? List<*> ?: return false

            for (video in videoList) {
                if (video == null) continue

                val videoUrl = try { video.javaClass.getMethod("getVideoUrl").invoke(video) as? String } catch (e: Exception) { null }
                    ?: try { video.javaClass.getMethod("getUrl").invoke(video) as? String } catch (e: Exception) { null }
                    ?: continue

                val qualityStr = try { video.javaClass.getMethod("getQuality").invoke(video) as? String } catch (e: Exception) { "720p" }

                val link = ExtractorLink(
                    source = name,
                    name = "$name - $qualityStr",
                    url = videoUrl,
                    referer = mainUrl,
                    quality = CloudStreamQualities.Unknown.value,
                    isM3u8 = videoUrl.contains(".m3u8")
                )

                callback(link)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

