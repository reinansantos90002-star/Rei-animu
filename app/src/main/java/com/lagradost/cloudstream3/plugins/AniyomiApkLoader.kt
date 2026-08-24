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

    private val instance: Any = loader.loadExtensionClass(apkFile, mainClassName)
        ?: throw IllegalStateException("Falha ao instanciar a classe da extensão: $mainClassName")

    private val isNsfwExtension: Boolean = try {
        val method = instance.javaClass.methods.firstOrNull { it.name == "isNsfw" || it.name == "getIsNsfw" }
        (method?.invoke(instance) as? Boolean) ?: false
    } catch (e: Exception) {
        false
    }

    override var name: String = try {
        (instance.javaClass.getMethod("getName").invoke(instance) as? String) ?: "Aniyomi Extension"
    } catch (e: Exception) {
        "Aniyomi Extension"
    }

    override var mainUrl: String = try {
        val url = instance.javaClass.methods.firstOrNull { it.name == "getBasesUrl" || it.name == "baseUrl" }?.invoke(instance) as? String
        url ?: "https://aniyomi.org"
    } catch (e: Exception) {
        "https://aniyomi.org"
    }

    override var supportedTypes = if (isNsfwExtension) setOf(TvType.NSFW) else setOf(TvType.Anime)
    override var hasMainPage = true

    // ... Mantenha o restante das 196 linhas (getMainPage, search, load, loadLinks) exatamente como estavam ...
}
