package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Base64

open class EkinoProvider : MainAPI() {
    override var mainUrl = "https://ekino-tv.pl/"
    override var name = "ekino-tv.pl"
    override var lang = "pl"
    override val hasMainPage = true
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private suspend fun fetchDocument(url: String): Document? {
        return try {
            val response = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0"))
            response.document
        } catch (e: Exception) {
            println("Error fetching document: ${e.localizedMessage}")
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = fetchDocument(mainUrl) ?: return HomePageResponse(emptyList())
        val categories = ArrayList<HomePageList>()

        val bannerMovies = document.select(".slider .slide")
        val bannerItems = bannerMovies.mapNotNull { item ->
            val name = item.selectFirst(".title")?.text() ?: return@mapNotNull null
            val href = mainUrl + (item.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val poster = item.selectFirst("img[src]")?.attr("src")?.let { "https:$it" }
            
            MovieSearchResponse(name, href, this.name, TvType.Movie, poster, null)
        }
        categories.add(HomePageList("Główne banery", bannerItems))

        val popularMovies = document.select(".mostPopular .list li")
        val popularItems = popularMovies.mapNotNull { item ->
            val name = item.select(".title a").text()
            val href = mainUrl + (item.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val poster = item.selectFirst("img[src]")?.attr("src")?.let { "https:$it" }
            val year = item.select(".cates").text().split("|").firstOrNull()?.trim()?.toIntOrNull()

            MovieSearchResponse(name, href, this.name, TvType.Movie, poster, year)
        }
        categories.add(HomePageList("Popularne filmy", popularItems))

        return HomePageResponse(categories)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/qf/?q=$query"
        val document = fetchDocument(url) ?: return emptyList()
        val searchResults = document.select(".movie-wrap div.movie")

        return searchResults.mapNotNull { element ->
            val href = mainUrl + (element.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val poster = element.selectFirst("img[src]")?.attr("src")?.let { "https:$it" }
            val name = element.selectFirst(".title")?.text() ?: return@mapNotNull null
            val type = if (element.select(".type").text().contains("Serial")) TvType.TvSeries else TvType.Movie

            if (type == TvType.TvSeries) {
                TvSeriesSearchResponse(name, href, this.name, type, poster, null, null)
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
        val linkContainer = document.select("#link-list").outerHtml()

        return MovieLoadResponse(
            title,
            url,
            name,
            TvType.Movie,
            linkContainer,
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
        return try {
            val document = if (data.startsWith("http")) {
                fetchDocument(data)?.selectFirst("#link-list")
            } else {
                Jsoup.parse(data)
            } ?: return false

            document.select("a[data-iframe]").forEach { item ->
                val encodedUrl = item.attr("data-iframe")
                val decodedUrl = String(Base64.getDecoder().decode(encodedUrl))
                loadExtractor(decodedUrl, subtitleCallback, callback)
            }
            true
        } catch (e: Exception) {
            println("Error in loadLinks: ${e.localizedMessage}")
            false
        }
    }
}

data class LinkElement(
    @JsonProperty("src") val src: String
)
