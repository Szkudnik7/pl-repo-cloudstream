package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import org.jsoup.select.Elements
open class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/" // (zmien)
    override var name = "ekino-tv.pl" // (zmien)
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )
    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val lists = document.select(".item-list")
        val categories = ArrayList<HomePageList>()
        for (l in lists) {
            val title = capitalizeString(l.parent()!!.select("h3").text().lowercase().trim())
            val items = l.select(".poster").map { i ->
                val a = i.parent()!!
                val name = a.attr("title")
                val href = a.attr("href")
                val poster = i.select("img[src]").attr("src")
                val year = a.select(".year").text().toIntOrNull()
                MovieSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.Movie,
                    poster,
                    year
                )
            }
            categories.add(HomePageList(title, items))
        }
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?phrase=$query" // (zmien)
        try {
            val document = app.get(url).document
            val lists = document.select("#search-results > div") // (zmien)
            if (!lists.empty()) {
                return SearchResults(lists, query)
            } else {
                return emptyList()
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse search results", e)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = if (data.startsWith("http"))
            app.get(data).document.select("#link-list").first() // (zmien)
        else Jsoup.parse(data)
        document?.select(".link-to-video")?.apmap { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return false
            loadExtractor(link, subtitleCallback, callback)
            true
        }
    }

    override suspend fun loadSeriesLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = if (data.startsWith("http"))
            app.get(data).document.select("#link-list").first() // (zmien)
        else Jsoup.parse(data)
        document?.select(".link-to-video")?.apmap { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return false
            loadExtractor(link, subtitleCallback, callback)
            true
        }
    }

    override suspend fun loadSeriesPageLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = if (data.startsWith("http"))
            app.get(data).document.select("#link-list").first() // (zmien)
        else Jsoup.parse(data)
        document?.select(".link-to-video")?.apmap { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return false
            loadExtractor(link, subtitleCallback, callback)
            true
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("http")) {
            return loadSeriesPageLinks(data, isCasting, subtitleCallback, callback)
        } else {
            val links = data.split(",")
            for (link in links) {
                val parsedLink = link.split("?")[1].split("=")[0]
                if (parsedLink.startsWith("/")) {
                    val url = "https://ekino-tv.pl/$parsedLink"
                    return loadLinks(url, isCasting, subtitleCallback, callback)
                } else {
                    return false
                }
            }
        }
    }

    override suspend fun loadSeriesPage(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = if (data.startsWith("http"))
            app.get(data).document.select("#link-list").first() // (zmien)
        else Jsoup.parse(data)
        document?.select(".link-to-video")?.apmap { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return false
            loadExtractor(link, subtitleCallback, callback)
            true
        }
    }

    override suspend fun loadSeriesLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = if (data.startsWith("http"))
            app.get(data).document.select("#link-list").first() // (zmien)
        else Jsoup.parse(data)
        document?.select(".link-to-video")?.apmap { item ->
            val decoded = base64Decode(item.select("a").attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src ?: return false
            loadExtractor(link, subtitleCallback, callback)
            true
        }
    }

    override suspend fun searchAndLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return search(data) + loadLinks(data, isCasting, subtitleCallback, callback)
    }
}
data class SearchResults(val links: List<ExtractorLink>, val query: String) : Collection<SearchResult> {

    override fun iterator(): Iterator<SearchResult> = object: Iterator<SearchResult>() {
        var pos = 0
        override operator fun hasNext(): Boolean = pos < links.size
        override operator fun next(): SearchResult? {
            if (pos >= links.size) {
                throw RuntimeException("No more search results")
            }
            val link = links[pos]
            pos += 1
            return SearchResult(link, query)
        }
    }

    override fun toString() = "$query -> ${links.map { it.toString() }.joinToString(",")}"
}

data class EmptyList : Collection<SearchResult> {

}
data class SearchResult(val link: String, val query: String) : Comparable<SearchResult> {
    override fun compareTo(other: SearchResult): Int {
        return this.query.compareTo(other.query)
    }
}
