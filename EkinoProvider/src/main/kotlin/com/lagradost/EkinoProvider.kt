package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/"
    override var name = "Ekino"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    private val interceptor = CloudflareKiller()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document: Document = app.get(mainUrl).document
        val categories = ArrayList<HomePageList>()
        val listElements: Elements = document.select(".mainWrap")

        for (listElement in listElements) {
            val titleElement = listElement.selectFirst("h3")
            val title = titleElement?.text()?.capitalize() ?: "Kategoria"
            val items = ArrayList<SearchResponse>()
            val elements = listElement.select(".nowa-poster")

            for (element in elements) {
                val posterElement = element.selectFirst("img")
                val linkElement = element.selectFirst("a")
                val poster = posterElement?.attr("src")
                val href = linkElement?.attr("href")
                val itemName = linkElement?.attr("title")

                if (poster != null && href != null && itemName != null) {
                    items.add(
                        MovieSearchResponse(
                            title = itemName,
                            url = properUrl(href) ?: "",
                            apiName = this.name,
                            type = TvType.Movie,
                            posterUrl = properUrl(poster) ?: "",
                            posterHeaders = interceptor.getCookieHeaders(mainUrl).toMap()
                        )
                    )
                }
            }
            categories.add(HomePageList(title, items))
        }
        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukaj?phrase=$query"
        val document: Document = app.get(url, interceptor = interceptor).document
        val lists: Elements = document.select("#advanced-search > div")
        val searchResults = ArrayList<SearchResponse>()

        val movies: Elements = lists.getOrNull(1)?.select("div:not(.clearfix)") ?: Elements()
        val series: Elements = lists.getOrNull(3)?.select("div:not(.clearfix)") ?: Elements()

        searchResults.addAll(getVideos(TvType.Movie, movies, url))
        searchResults.addAll(getVideos(TvType.TvSeries, series, url))
        
        return searchResults
    }

    private fun getVideos(type: TvType, items: Elements, url: String): List<SearchResponse> {
        val videos = ArrayList<SearchResponse>()
        for (item in items) {
            val linkElement = item.selectFirst("a")
            val imgElement = item.selectFirst("a > img")
            val titleElement = item.selectFirst(".title")

            val href = linkElement?.attr("href")
            val img = imgElement?.attr("src")?.replace("/thumb/", "/big/")
            val name = titleElement?.text()

            if (href != null && img != null && name != null) {
                videos.add(
                    if (type == TvType.TvSeries) {
                        TvSeriesSearchResponse(
                            name = name,
                            url = properUrl(href) ?: "",
                            apiName = this.name,
                            type = type,
                            posterUrl = properUrl(img) ?: "",
                            posterHeaders = interceptor.getCookieHeaders(url).toMap()
                        )
                    } else {
                        MovieSearchResponse(
                            name = name,
                            url = properUrl(href) ?: "",
                            apiName = this.name,
                            type = type,
                            posterUrl = properUrl(img) ?: "",
                            posterHeaders = interceptor.getCookieHeaders(url).toMap()
                        )
                    }
                )
            }
        }
        return videos
    }

    override suspend fun load(url: String): LoadResponse {
        val document: Document = app.get(url, interceptor = interceptor).document
        if (document.title().startsWith("Logowanie")) {
            throw RuntimeException("This page requires login. Unable to scrape.")
        }

        val title = document.select("span[itemprop=name]").text().ifEmpty { document.select("h1").text() }
        val data = document.select("#link-list").outerHtml()
        val posterUrl = document.select("#single-poster img").attr("src")
        val plot = document.select(".description").text()
        val episodesElements = document.select("#episode-list a[href]")

        if (episodesElements.isEmpty()) {
            return MovieLoadResponse(
                title = title,
                url = properUrl(url) ?: "",
                apiName = name,
                type = TvType.Movie,
                data = data,
                posterUrl = properUrl(posterUrl) ?: "",
                plot = plot
            )
        } else {
            val episodes = ArrayList<Episode>()
            for (episode in episodesElements) {
                val episodeTitle = episode.text()
                val regex = Regex("""\[s(\d{1,3})e(\d{1,3})]""").find(episodeTitle)
                if (regex != null) {
                    episodes.add(
                        Episode(
                            url = properUrl(episode.attr("href")) ?: "",
                            name = episodeTitle.split("]").last().trim(),
                            season = regex.groupValues[1].toIntOrNull(),
                            episode = regex.groupValues[2].toIntOrNull()
                        )
                    )
                }
            }

            return TvSeriesLoadResponse(
                title = title,
                url = properUrl(url) ?: "",
                apiName = name,
                type = TvType.TvSeries,
                episodes = episodes,
                posterUrl = properUrl(posterUrl) ?: "",
                plot = plot
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = when {
            data.startsWith("http") -> app.get(data).document
            data.startsWith("URL") -> app.get(properUrl(data) ?: "").document
            else -> Jsoup.parse(data)
        }
        val videoLinks = document.select(".link-to-video a[data-iframe]")

        for (item in videoLinks) {
            val decoded = base64Decode(item.attr("data-iframe"))
            val link = tryParseJson<LinkElement>(decoded)?.src
            if (link != null) {
                loadExtractor(link, subtitleCallback, callback)
            }
        }
        return true
    }

    private fun properUrl(inUrl: String?): String? {
        return inUrl?.let { fixUrl(it.replace("^URL".toRegex(), "/")) }
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)
