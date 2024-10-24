package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

open class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/"
    override var name = "ekino-tv.pl"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private suspend fun fetchDocument(url: String): Document? {
        return try {
            val response = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0"))
            response.document
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = fetchDocument(url) ?: return MovieLoadResponse("Error", url, name, TvType.Movie, "", "", null, "Unable to load")

        val title = document.select("h1.title").text()
        val posterUrl = "https:" + document.select("#single-poster img").attr("src")
        val plot = document.select(".descriptionMovie").text()
        
        // Zbieranie linków do odtwarzania
        val linksData = document.select(".tab-pane[id^='fplayer_']") // selektor do linków
        val data = linksData.mapNotNull { it.attr("id").replace("fplayer_", "") }.joinToString(",")
        
        return MovieLoadResponse(
            title,
            url,
            name,
            TvType.Movie,
            data,
            posterUrl,
            null,
            plot
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(",").forEach { playerId ->
            val videoUrl = "$mainUrl/player/$playerId" // Budowanie URL dla odtwarzacza
            loadExtractor(videoUrl, subtitleCallback, callback)
        }
        return true
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)
