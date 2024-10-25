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
            app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0")).document
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/qf/?q=$query"
        val document = fetchDocument(url) ?: return emptyList()
        
        val searchResults = document.select(".movie-wrap div.movie")
        return searchResults.mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = element.selectFirst("img[src]")?.attr("src")?.let { "https:$it" }
            val name = element.selectFirst(".title")?.text().orEmpty()
            val type = if (element.select(".type").text().contains("Serial")) TvType.TvSeries else TvType.Movie
            
            if (type == TvType.TvSeries) {
                TvSeriesSearchResponse(name, href, this.name, type, poster, null)
            } else {
                MovieSearchResponse(name, href, this.name, type, poster, null)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = fetchDocument(url) ?: return MovieLoadResponse("Error", url, name, TvType.Movie, "", "", null, "Unable to load")

        val title = document.selectFirst("h1.title")?.text().orEmpty()
        val posterUrl = document.selectFirst("#single-poster img")?.attr("src")?.let { "https:$it" }
        val plot = document.selectFirst(".descriptionMovie")?.text().orEmpty()
        
        // Wyciągnięcie linków z przycisków
        val linkList = document.select("#link-list a[data-iframe]").mapNotNull { btn ->
            val encodedUrl = btn.attr("data-iframe")
            val decodedUrl = String(android.util.Base64.decode(encodedUrl, android.util.Base64.DEFAULT))
            ExtractorLink(name, name, decodedUrl, "", Qualities.Unknown.value, true)
        }
        
        return MovieLoadResponse(
            title,
            url,
            name,
            TvType.Movie,
            linkList,
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
        if (data.startsWith("http")) {
            val document = fetchDocument(data) ?: return false
            document.select("a[data-iframe]").forEach { item ->
                val videoUrl = String(android.util.Base64.decode(item.attr("data-iframe"), android.util.Base64.DEFAULT))
                loadExtractor(videoUrl, subtitleCallback, callback)
            }
        } else {
            Jsoup.parse(data).select("a[data-iframe]").forEach { item ->
                val videoUrl = String(android.util.Base64.decode(item.attr("data-iframe"), android.util.Base64.DEFAULT))
                loadExtractor(videoUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}
