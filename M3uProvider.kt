package com.tuusuario.m3u

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class M3uProvider : MainAPI() {
    companion object {
        // Cambia esto por la URL de tu lista M3U o IPTV
        const val LIST_URL = "https://ejemplo.com/mi-lista.m3u"
    }

    override var mainUrl = LIST_URL
    override var name = "Mi Lista M3U"
    override var lang = "es"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    data class Channel(
        val name: String,
        val url: String,
        val logo: String?,
        val group: String?,
        val headers: Map<String, String>
    )

    private var cache: List<Channel>? = null

    private suspend fun getChannels(): List<Channel> {
        cache?.let { return it }
        val text = app.get(LIST_URL).text
        return parseM3u(text).also { cache = it }
    }

    // Parser de M3U: lee #EXTINF (nombre, logo, grupo) y #EXTVLCOPT (headers)
    private fun parseM3u(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var curName: String? = null
        var curLogo: String? = null
        var curGroup: String? = null
        val curHeaders = mutableMapOf<String, String>()

        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("#EXTINF") -> {
                    curName = line.substringAfterLast(",").trim()
                    curLogo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                    curGroup = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val opt = line.removePrefix("#EXTVLCOPT:")
                    when {
                        opt.startsWith("http-referrer=") ->
                            curHeaders["Referer"] = opt.substringAfter("=")
                        opt.startsWith("http-user-agent=") ->
                            curHeaders["User-Agent"] = opt.substringAfter("=")
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    result.add(
                        Channel(
                            name = curName ?: line,
                            url = line,
                            logo = curLogo,
                            group = curGroup,
                            headers = curHeaders.toMap()
                        )
                    )
                    curName = null; curLogo = null; curGroup = null
                    curHeaders.clear()
                }
            }
        }
        return result
    }

    private fun Channel.toSearchResponse(): SearchResponse =
        newLiveSearchResponse(name, url, TvType.Live) {
            this.posterUrl = logo
        }

    // Página principal: un carrusel por grupo (group-title)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val lists = getChannels()
            .groupBy { it.group ?: "Sin grupo" }
            .map { (group, items) ->
                HomePageList(group, items.map { it.toSearchResponse() }, isHorizontalImages = true)
            }
        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> =
        getChannels()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toSearchResponse() }

    override suspend fun load(url: String): LoadResponse {
        val ch = getChannels().first { it.url == url }
        return newLiveStreamLoadResponse(ch.name, url, url) {
            this.posterUrl = ch.logo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ch = getChannels().firstOrNull { it.url == data } ?: return false
        callback(
            newExtractorLink(
                source = name,
                name = ch.name,
                url = ch.url,
                type = if (ch.url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.headers = ch.headers
                this.referer = ch.headers["Referer"] ?: ""
            }
        )
        return true
    }
}
